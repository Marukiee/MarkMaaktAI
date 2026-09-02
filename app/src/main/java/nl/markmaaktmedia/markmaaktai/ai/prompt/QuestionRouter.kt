package nl.markmaaktmedia.markmaaktai.ai.prompt

/**
 * Works out what a question is actually about before anything is fetched for it.
 *
 * This exists because of one bad answer. "Hoe hard gaat de Python in de Efteling"
 * used to have the phone searched for it, the word `python` matched a notification
 * that had nothing to do with a rollercoaster, that line was pasted into the prompt
 * under "only answer from these", and a 1.5B model duly explained that the Efteling
 * and Python are two rollercoasters. The context was the problem, not the model.
 *
 * So context is now opt-in per question. A question about the world gets the web and
 * nothing else. A question about this phone gets the phone. Asking for everything
 * every time is what buries the one line that mattered under eleven that did not,
 * which on a 1280 token context is the difference between an answer and a blank.
 */
object QuestionRouter {

    /** What the question wants behind it. */
    data class Route(
        /** Search the notification and screenshot index for matching lines. */
        val usePhoneContext: Boolean,
        /** Ignore the search and take everything recent: "what did I miss today". */
        val wantsRecap: Boolean,
        /** Worth sending to a search engine, when the user has web search on. */
        val useWeb: Boolean,
    )

    fun route(question: String, hasImage: Boolean): Route {
        val text = question.lowercase()
        val words = text.split(NON_WORD).filter { it.isNotBlank() }.toSet()

        val recap = RECAP_PHRASES.any { it in text } ||
            (words.any { it in RECAP_VERBS } && words.any { it in OWN_SCOPE })
        val phone = recap || PHONE_HINTS.any { it in text } || words.any { it in PHONE_WORDS }

        // A recap is built from what is on the phone, so the web has nothing to add
        // and would only crowd the context out. A photo question is answered from the
        // picture and its own coordinates unless it asks about the world around it.
        val web = when {
            recap -> false
            hasImage -> true
            phone -> false
            question.isBlank() -> false
            else -> true
        }

        return Route(usePhoneContext = phone, wantsRecap = recap, useWeb = web)
    }

    /**
     * Turns a question into something a search engine answers well.
     *
     * Search engines rank on content words, so the politeness around the question is
     * dropped. The words themselves keep their order and their spelling, because a
     * stemmed bag of words finds the encyclopedia entry and the phrase finds the page
     * that actually has the number on it.
     */
    fun searchQuery(question: String): String {
        val cleaned = question.trim().trimEnd('?', '.', '!', ' ')
        // Repeatedly, because they stack: "kun je zoeken hoe hard ..." has two.
        var stripped = cleaned.lowercase()
        while (true) {
            val next = LEAD_IN.replace(stripped, "").trim()
            if (next == stripped || next.isBlank()) break
            stripped = next
        }
        return stripped.ifBlank { cleaned }.take(MAX_QUERY_CHARS)
    }

    /**
     * Terms to match notifications and screenshots on.
     *
     * Only the words that carry the subject. A question is phrased nothing like the
     * notification it is about, so the filler in it matches everything and ranks
     * nothing, which is exactly how an unrelated line ends up in the prompt.
     */
    fun contentTerms(question: String): List<String> = question
        .lowercase()
        .split(NON_WORD)
        .map { it.trim() }
        .filter { it.length >= MIN_TERM_LENGTH && it !in FILLER }
        .distinct()
        .take(MAX_TERMS)

    private val NON_WORD = Regex("[^\\p{L}\\p{Nd}]+")

    private const val MIN_TERM_LENGTH = 4
    private const val MAX_TERMS = 6
    private const val MAX_QUERY_CHARS = 120

    /** Openers that say nothing about the subject. */
    private val LEAD_IN = Regex(
        "^(kun je |kan je |kunt u |weet je |vertel me |zoeken |zoek op |zoek |google |" +
            "can you |could you |please |tell me |search for |look up |what is |wat is )",
    )

    /** Whole phrases, so "mijn dag" only counts as one thing. */
    private val RECAP_PHRASES = setOf(
        "mijn dag", "vat samen", "samenvatting", "wat heb ik gemist", "wat is er gebeurd",
        "wat speelt er", "aandacht nodig", "mijn aandacht", "wat moet ik weten",
        "my day", "summarise my day", "summarize my day", "what did i miss",
        "needs my attention", "catch me up", "what happened today",
    )

    private val RECAP_VERBS = setOf(
        "samenvatten", "samenvat", "vat", "overzicht", "recap", "summarise", "summarize",
        "briefing", "digest",
    )

    private val OWN_SCOPE = setOf(
        "dag", "dagen", "week", "vandaag", "gisteren", "meldingen", "berichten", "alles",
        "day", "week", "today", "yesterday", "notifications", "messages", "everything",
    )

    /** Phrases that only make sense about this phone. */
    private val PHONE_HINTS = setOf(
        "op mijn telefoon", "in mijn meldingen", "heb ik een bericht", "heeft iemand",
        "on my phone", "in my notifications", "did anyone", "did i get",
        "mijn screenshots", "my screenshots", "ongelezen", "unread",
    )

    private val PHONE_WORDS = setOf(
        "melding", "meldingen", "notificatie", "notificaties", "appje", "appjes",
        "whatsapp", "telegram", "signal", "sms", "inbox", "mailtje", "gemist",
        "screenshot", "screenshots", "schermafbeelding", "schermafbeeldingen",
        "notification", "notifications", "screenshotted", "voicemail",
    )

    /**
     * Words too common to match on. Longer than a stop word list, because this one is
     * not protecting a search index, it is protecting a 1280 token context.
     */
    private val FILLER = setOf(
        "wat", "wie", "waar", "hoe", "welke", "waarom", "wanneer", "over", "voor", "van",
        "met", "een", "het", "die", "dat", "deze", "der", "den", "zijn", "heeft", "hebben",
        "kun", "kan", "kunt", "weet", "moet", "gaat", "gaan", "doet", "doen", "wordt",
        "mijn", "jouw", "hun", "ons", "onze", "veel", "hard", "goed", "even", "nog",
        "niet", "wel", "ook", "maar", "want", "omdat", "naar", "door", "bij", "aan",
        "the", "what", "who", "where", "how", "which", "why", "when", "about", "from",
        "with", "and", "for", "did", "does", "have", "has", "was", "were", "this", "that",
        "there", "their", "your", "much", "many", "very", "just", "some", "any", "please",
        "tell", "give", "make", "know", "need", "want", "like", "into", "than", "then",
    )
}
