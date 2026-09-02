package nl.markmaaktmedia.markmaaktai.data.repository

import kotlinx.coroutines.flow.Flow
import nl.markmaaktmedia.markmaaktai.data.db.CapturedNotificationEntity
import nl.markmaaktmedia.markmaaktai.data.db.NotificationDao
import nl.markmaaktmedia.markmaaktai.data.db.SummaryDao
import nl.markmaaktmedia.markmaaktai.data.db.SummaryEntity
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao,
    private val summaryDao: SummaryDao,
    private val settings: SettingsRepository,
) {

    fun observeSummaries(): Flow<List<SummaryEntity>> = summaryDao.observeAll()

    fun observeUnreadCount(): Flow<Int> = summaryDao.observeUnreadCount()

    fun observeKnownPackages(): Flow<List<String>> = notificationDao.observeKnownPackages()

    suspend fun record(notification: CapturedNotificationEntity): Long =
        notificationDao.insertWithIndex(notification)

    suspend fun pendingForCluster(clusterKey: String): List<CapturedNotificationEntity> =
        notificationDao.pendingForCluster(clusterKey)

    suspend fun pendingCountInWindow(clusterKey: String, windowMinutes: Int): Int {
        val since = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(windowMinutes.toLong())
        return notificationDao.pendingCountSince(clusterKey, since)
    }

    suspend fun saveSummary(summary: SummaryEntity, sourceIds: List<Long>): Long {
        val id = summaryDao.insert(summary)
        notificationDao.attachToSummary(sourceIds, id)
        return id
    }

    suspend fun summaryById(id: Long): SummaryEntity? = summaryDao.byId(id)

    suspend fun sourcesFor(summaryId: Long): List<CapturedNotificationEntity> =
        notificationDao.forSummary(summaryId)

    suspend fun markRead(id: Long) = summaryDao.markRead(id)

    suspend fun deleteSummary(id: Long) = summaryDao.delete(id)

    /** Used by the undo action on a swipe, which puts the row back exactly as it was. */
    suspend fun restoreSummary(summary: SummaryEntity) = summaryDao.restore(summary)

    /**
     * Finds the notifications worth handing to the model for a question about them.
     *
     * Full text search first, because a question usually names a person, an app or a
     * subject. When the question has no usable words left after the stop list, or the
     * search comes up empty, it falls back to everything from the last day, which is
     * what makes "what did I miss" work at all.
     */
    suspend fun contextFor(question: String, limit: Int = 30): List<String> {
        val matched = search(question, limit)
        val rows = matched.ifEmpty { recent(limit) }
        return rows.map { it.asPromptLine() }
    }

    suspend fun search(question: String, limit: Int = 30): List<CapturedNotificationEntity> =
        searchTerms(toTerms(question), limit)

    /**
     * Searches on terms someone else already picked out.
     *
     * The chat routes its questions through `QuestionRouter`, which knows which words
     * carry the subject. Handing them straight over keeps one idea of what a question
     * is about instead of two lists of stop words drifting apart.
     *
     * Everything that matched is then ranked by how many distinct terms it hit, and
     * anything that only matched on a single short term is dropped. An OR search finds
     * plenty; the point of this is deciding what is worth the context it would take up.
     */
    suspend fun searchTerms(
        terms: List<String>,
        limit: Int = 30,
    ): List<CapturedNotificationEntity> {
        if (terms.isEmpty()) return emptyList()
        val match = terms.joinToString(" OR ") { "$it*" }
        val rows = runCatching { notificationDao.search(match, limit * 3) }
            .getOrDefault(emptyList())

        return rows
            .map { row -> row to row.hits(terms) }
            .filter { (_, hits) -> hits.isNotEmpty() }
            .filter { (_, hits) -> hits.size > 1 || hits.any { it.length >= STRONG_TERM } }
            .sortedByDescending { (row, hits) -> hits.size * 1_000_000L + row.postedAt / 100_000L }
            .take(limit)
            .map { (row, _) -> row }
    }

    private fun CapturedNotificationEntity.hits(terms: List<String>): List<String> {
        val haystack = (title + " " + body + " " + appLabel).lowercase()
        return terms.filter { it in haystack }
    }

    suspend fun recent(limit: Int = 30, withinHours: Int = 24): List<CapturedNotificationEntity> {
        val since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(withinHours.toLong())
        return notificationDao.since(since, limit)
    }

    suspend fun recentSummaries(limit: Int = 20, withinHours: Int = 24): List<SummaryEntity> {
        val since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(withinHours.toLong())
        return summaryDao.since(since, limit)
    }

    /** Keeps the database from growing without bound on a phone that never gets cleaned. */
    suspend fun purgeOld() {
        val days = settings.current().retentionDays
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        notificationDao.purgeOlderThan(cutoff)
    }

    private fun CapturedNotificationEntity.asPromptLine(): String {
        val time = timeFormat.format(Date(postedAt))
        val who = title.ifBlank { appLabel }
        return "$time, $appLabel, $who: ${body.take(280)}"
    }

    /**
     * Builds an FTS4 MATCH expression from a plain question.
     *
     * Words are OR'd rather than AND'd and given a prefix wildcard, because a
     * question is phrased nothing like the notification it is about. Punctuation and
     * quotes are stripped: an unbalanced quote is a syntax error in MATCH, and a
     * crash on a question with an apostrophe in it would be a silly way to lose.
     */
    private fun toTerms(question: String): List<String> = question
        .lowercase()
        .split(NON_WORD)
        .map { it.trim() }
        .filter { it.length >= MIN_WORD_LENGTH && it !in stopWords }
        .distinct()
        .take(MAX_TERMS)

    private companion object {
        val NON_WORD = Regex("[^\\p{L}\\p{Nd}]+")
        const val MIN_WORD_LENGTH = 3
        const val MAX_TERMS = 8

        /** A term this long carries a subject on its own. Shorter ones need company. */
        const val STRONG_TERM = 6

        val timeFormat = SimpleDateFormat("EEE HH:mm", Locale.getDefault())

        /** Dutch and English filler that would match everything and rank nothing. */
        val stopWords = setOf(
            "wat", "wie", "waar", "hoe", "welke", "over", "voor", "van", "met", "een", "het",
            "die", "dat", "deze", "der", "den", "zei", "zegt", "stuurde", "vandaag", "gisteren",
            "vanmiddag", "vanochtend", "vanavond", "mij", "ik", "jij", "hij", "zij", "heeft",
            "the", "what", "who", "where", "how", "which", "about", "from", "with", "and",
            "for", "did", "said", "send", "sent", "today", "yesterday", "this", "that", "was",
            "were", "have", "has", "any", "message", "messages", "bericht", "berichten",
        )
    }
}
