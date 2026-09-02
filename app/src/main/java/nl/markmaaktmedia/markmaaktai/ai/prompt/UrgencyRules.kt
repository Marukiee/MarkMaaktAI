package nl.markmaaktmedia.markmaaktai.ai.prompt

/**
 * The second opinion on whether something is urgent.
 *
 * A 1.5B model asked "is this urgent?" says yes far too often. It reads intensity
 * rather than consequence, so a sale ending tonight, a group chat going quickly and
 * anything with an exclamation mark all came back urgent, and once everything is
 * urgent the badge means nothing and the sound is just noise.
 *
 * The prompt was tightened, but a prompt is a request and this is a rule. The model
 * may only confirm urgency, never create it: something is urgent when the model says
 * so AND the messages actually contain a reason. Whatever the model says, an app that
 * cannot produce an emergency is never urgent.
 */
object UrgencyRules {

    /**
     * @param modelSaidUrgent what the summariser returned.
     * @param packageName the app the burst came from.
     * @param category the category the summariser picked.
     * @param text every message in the burst, plus the summary, run together.
     */
    fun isUrgent(
        modelSaidUrgent: Boolean,
        packageName: String,
        category: String,
        text: String,
    ): Boolean {
        if (!modelSaidUrgent) return false

        val lower = text.lowercase()
        val app = packageName.lowercase()

        if (NEVER_URGENT_APPS.any { it in app }) return false
        if (category in NEVER_URGENT_CATEGORIES) return false
        // A discount that ends tonight is a deadline, and it is still not urgent.
        if (PROMOTIONAL.any { it in lower }) return false

        return SIGNALS.any { it in lower } || TIME_PRESSURE.containsMatchIn(lower)
    }

    /** Nothing from these can be an emergency, whatever words are in it. */
    private val NEVER_URGENT_APPS = setOf(
        "com.instagram", "com.facebook", "com.zhiliaoapp.musically", "com.twitter",
        "com.x.android", "com.snapchat", "com.reddit", "com.pinterest", "com.linkedin",
        "com.spotify", "com.netflix", "com.google.android.youtube", "com.tumblr",
        "com.discord", "com.twitch", "com.strava", "com.duolingo", "news", "nieuws",
    )

    private val NEVER_URGENT_CATEGORIES = setOf("social", "system")

    /** Marketing borrows the language of urgency, which is exactly why it is out. */
    private val PROMOTIONAL = setOf(
        "korting", "aanbieding", "aanbiedingen", "actie loopt", "op = op", "black friday",
        "uitverkoop", "sale", "discount", "deal", "offer ends", "limited time", "coupon",
        "abonneer", "nieuwsbrief", "newsletter", "unsubscribe", "win een", "maak kans",
    )

    /** Words that name a real consequence, not a tone of voice. */
    private val SIGNALS = setOf(
        // Money
        "betalen", "betaling", "betaald", "factuur", "incasso", "aanmaning", "openstaand",
        "automatische incasso", "mislukt", "geweigerd", "saldo", "overschrijving", "tikkie",
        "payment", "invoice", "overdue", "unpaid", "declined", "failed payment", "past due",
        // Time
        "deadline", "vervalt", "verloopt", "verlopen", "laatste dag", "uiterlijk",
        "expires", "expiring", "due today", "due tomorrow",
        // Things going wrong
        "geannuleerd", "annulering", "vertraging", "vertraagd", "uitgevallen", "storing",
        "spoed", "dringend", "urgent", "cancelled", "canceled", "delayed", "postponed",
        "problem with your", "action required", "actie vereist",
        // Access and safety
        "verificatiecode", "inlogpoging", "wachtwoord", "beveiliging", "verdachte",
        "verification code", "login attempt", "security alert", "suspicious",
        "alarm", "noodgeval", "emergency", "ambulance", "spoedeisende",
        // Someone waiting
        "kun je bellen", "bel me", "graag antwoord", "wacht op je", "reageer je nog",
        "call me", "waiting for your", "please confirm", "bevestig",
        // Appointments
        "afspraak", "afgesproken tijd", "appointment", "reschedule", "verzet",
    )

    /**
     * A clock time or a date this week, spelled out.
     *
     * "Morgen om 9:00" is a deadline the model was right about. "Binnenkort" is not,
     * and neither is a message that only feels timely.
     */
    private val TIME_PRESSURE = Regex(
        "(vandaag|vanavond|vanmiddag|morgen|today|tonight|tomorrow)" +
            "[^.!?]{0,40}\\b([01]?\\d|2[0-3])[:.][0-5]\\d\\b",
    )
}
