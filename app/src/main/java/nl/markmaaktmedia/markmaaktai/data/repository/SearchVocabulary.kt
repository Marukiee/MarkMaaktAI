package nl.markmaaktmedia.markmaaktai.data.repository

/**
 * Widens a search term to the words a screenshot would actually contain.
 *
 * Full text search matches strings, and nobody writes their search the way the thing
 * they are looking for is written. "Vliegtickets" appears in exactly no screenshot:
 * a boarding pass says gate, vlucht, KLM, PNR, and a booking confirmation says
 * reservering. Searching the word you have in mind for the thing and finding nothing
 * reads as a broken feature, which it effectively was.
 *
 * So a term is expanded into its neighbourhood before it reaches the index. This is a
 * word list rather than embeddings on purpose: it is a handful of kilobytes, it runs
 * in microseconds on the phone, and when it gets something wrong it is one line to
 * fix. An embedding model for this would cost a download and a model load to answer a
 * search box.
 *
 * Both languages are in every group. The UI is Dutch and half of what is on a Dutch
 * phone is in English, often in the same screenshot.
 */
object SearchVocabulary {

    /**
     * Terms to search for, given what the user typed.
     *
     * The original words always come first and always survive; the expansion is added
     * behind them, so an exact match still ranks above a related one.
     */
    fun expand(words: List<String>): List<String> {
        if (words.isEmpty()) return emptyList()
        val out = LinkedHashSet(words)
        words.forEach { word ->
            val stem = word.stripped()
            GROUPS.forEach { group ->
                if (group.any { it.sharesStemWith(stem) }) out += group
            }
        }
        return out.take(MAX_TERMS)
    }

    /** The category chip a search should also light up, when the words point at one. */
    fun categoryFor(words: List<String>): String? {
        val stems = words.map { it.stripped() }.toSet()
        return CATEGORIES.entries.firstOrNull { (_, group) ->
            group.any { term -> stems.any { term.sharesStemWith(it) } }
        }?.key
    }

    /**
     * Dutch plurals and diminutives, taken off well enough for a prefix search.
     *
     * Not a stemmer. It turns "vliegtickets" into "vliegticket" so the group below
     * matches, and that is the whole ambition.
     */
    private fun String.stripped(): String {
        var word = lowercase()
        listOf("en", "s", "je", "tje", "pje").forEach { suffix ->
            if (word.length > suffix.length + 3 && word.endsWith(suffix)) {
                word = word.dropLast(suffix.length)
                return word
            }
        }
        return word
    }

    /**
     * Whether two words are the same word.
     *
     * Compared on their shared opening rather than one starting with the other,
     * because Dutch plurals do not simply add letters: factuur becomes facturen and
     * loses a u on the way, so neither form is a prefix of the other. Five characters
     * of agreement is enough to be the same word and short enough to still separate
     * "vlucht" from "vluchteling".
     */
    private fun String.sharesStemWith(other: String): Boolean {
        if (this == other) return true
        val shared = commonPrefixWith(other).length
        return shared >= MIN_SHARED && shared >= minOf(length, other.length) - MAX_ENDING_DRIFT
    }

    private const val MIN_SHARED = 5
    private const val MAX_ENDING_DRIFT = 2

    private const val MAX_TERMS = 14

    private val TRAVEL = listOf(
        "vliegticket", "ticket", "tickets", "vlucht", "vluchten", "flight", "boarding",
        "boardingpass", "instapkaart", "gate", "luchthaven", "airport", "schiphol",
        "reservering", "reservation", "booking", "boeking", "bestemming", "vertrek",
        "aankomst", "departure", "arrival", "klm", "transavia", "ryanair", "easyjet",
        "trein", "train", "ns", "perron", "spoor", "stoel", "seat", "bagage", "baggage",
        "hotel", "check-in", "checkin", "pnr", "e-ticket",
    )

    private val FINANCE = listOf(
        "factuur", "rekening", "invoice", "betaling", "payment", "betaald", "iban",
        "bedrag", "total", "totaal", "euro", "eur", "prijs", "price", "tikkie", "bon",
        "kassabon", "receipt", "bank", "overschrijving", "transactie", "abonnement",
        "subscription", "btw", "incasso",
    )

    private val DELIVERY = listOf(
        "pakket", "parcel", "package", "bezorging", "delivery", "bezorgd", "track",
        "tracking", "postnl", "dhl", "dpd", "ups", "verzending", "shipment", "order",
        "bestelling", "retour", "return", "zending",
    )

    private val RECIPE = listOf(
        "recept", "recipe", "ingredient", "ingredienten", "ingredients", "oven",
        "bereiding", "koken", "bakken", "gram", "eetlepel", "theelepel", "minuten",
        "portie", "servings",
    )

    private val CONTACT = listOf(
        "adres", "address", "postcode", "telefoonnummer", "nummer", "phone", "mail",
        "e-mail", "email", "contact", "straat", "huisnummer", "woonplaats",
    )

    private val CREDENTIAL = listOf(
        "code", "pincode", "wachtwoord", "password", "inloggen", "login", "qr",
        "barcode", "voucher", "kortingscode", "activatiecode", "verificatiecode",
    )

    private val APPOINTMENT = listOf(
        "afspraak", "appointment", "agenda", "kalender", "calendar", "uitnodiging",
        "invite", "meeting", "vergadering", "datum", "tijdstip", "locatie",
    )

    private val GROUPS = listOf(
        TRAVEL, FINANCE, DELIVERY, RECIPE, CONTACT, CREDENTIAL, APPOINTMENT,
    )

    /** Maps onto the category the indexer assigns, so the chip and the search agree. */
    private val CATEGORIES = linkedMapOf(
        "travel" to TRAVEL,
        "finance" to FINANCE,
        "delivery" to DELIVERY,
        "recipe" to RECIPE,
    )
}
