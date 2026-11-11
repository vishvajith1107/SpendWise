package com.spendwise.domain.categorizer

import com.spendwise.domain.model.ExpenseCategory
import java.util.Locale

/**
 * Fully offline keyword-based transaction auto-categorizer optimized for Indian merchants & UPI handles.
 */
object CategoryClassifier {

    private fun wordRegex(words: List<String>): Regex =
        Regex("""\b(?:${words.joinToString("|")})\b""", RegexOption.IGNORE_CASE)

    private val FOOD_CONTAINS = listOf(
        "swiggy", "zomato", "coffee", "starbucks", "domino", "pizza", "mcdonald",
        "burger", "biryani", "restaurant", "bakery", "sweets", "canteen", "dhaba",
        "dining", "eatfit", "faasos", "behrouz", "barbeque", "ice cream", "baskin",
        "haldiram", "subway", "starbucks"
    )
    private val FOOD_WORDS = wordRegex(listOf("chai", "tea", "cafe", "kfc", "hotel", "mess"))

    private val GROCERY_CONTAINS = listOf(
        "blinkit", "zepto", "instamart", "bigbasket", "bbnow", "dmart", "d-mart",
        "reliance fresh", "reliance smart", "more supermarket", "spencers", "kirana",
        "grocer", "supermarket", "dairy", "country delight", "jiomart", "nature's basket"
    )
    private val GROCERY_WORDS = wordRegex(listOf("mart", "milk", "fruits", "vegetables"))

    private val TRANSPORT_CONTAINS = listOf(
        "rapido", "namma yatri", "irctc", "indigo", "air india", "vistara", "akasa",
        "spicejet", "makemytrip", "redbus", "abhibus", "petrol", "diesel", "hpcl",
        "bpcl", "indianoil", "iocl", "nayara", "fastag", "parking"
    )
    private val TRANSPORT_WORDS = wordRegex(
        listOf("uber", "ola", "metro", "rail", "fuel", "shell", "toll", "cab", "auto", "train", "flight")
    )

    private val BILLS_CONTAINS = listOf(
        "airtel", "vodafone", "bsnl", "act fibernet", "hathway", "broadband",
        "recharge", "tata play", "bescom", "msedcl", "tangedco", "electricity",
        "water bill", "piped gas", "indane", "bharat gas", "hp gas", "cylinder",
        "postpaid", "insurance", "maintenance"
    )
    private val BILLS_WORDS = wordRegex(
        listOf("jio", "vi", "wifi", "dth", "power", "rent", "lic", "cred", "emi", "loan")
    )

    private val SHOPPING_CONTAINS = listOf(
        "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tatacliq",
        "croma", "vijay sales", "reliance digital", "decathlon", "zudio", "westside",
        "pantaloons", "trends", "lenskart", "apple store"
    )
    private val SHOPPING_WORDS = wordRegex(listOf("ikea", "mall"))

    private val ENTERTAINMENT_CONTAINS = listOf(
        "bookmyshow", "cinepolis", "netflix", "prime video", "hotstar", "jiocinema",
        "sonyliv", "zee5", "spotify", "youtube", "playstation"
    )
    private val ENTERTAINMENT_WORDS = wordRegex(listOf("pvr", "inox", "movie", "cinema", "gaming", "steam", "xbox"))

    private val HEALTH_CONTAINS = listOf(
        "apollo", "medplus", "pharmeasy", "netmeds", "pharmacy", "medical",
        "chemist", "hospital", "clinic", "diagnostic", "lal path", "thyrocare",
        "practo", "fitness"
    )
    private val HEALTH_WORDS = wordRegex(listOf("1mg", "doctor", "cult", "gym"))

    fun classify(
        merchant: String,
        description: String = "",
        rawSms: String? = null,
        isCredit: Boolean = false
    ): ExpenseCategory {
        if (isCredit) return ExpenseCategory.INCOME

        val haystack = buildString {
            append(merchant)
            append(' ')
            append(description)
            if (!rawSms.isNullOrBlank()) {
                append(' ')
                append(rawSms)
            }
        }.lowercase(Locale.ROOT)

        return when {
            FOOD_CONTAINS.any { haystack.contains(it) } || FOOD_WORDS.containsMatchIn(haystack) ->
                ExpenseCategory.FOOD
            GROCERY_CONTAINS.any { haystack.contains(it) } || GROCERY_WORDS.containsMatchIn(haystack) ->
                ExpenseCategory.GROCERIES
            TRANSPORT_CONTAINS.any { haystack.contains(it) } || TRANSPORT_WORDS.containsMatchIn(haystack) ->
                ExpenseCategory.TRANSPORT
            BILLS_CONTAINS.any { haystack.contains(it) } || BILLS_WORDS.containsMatchIn(haystack) ->
                ExpenseCategory.BILLS
            SHOPPING_CONTAINS.any { haystack.contains(it) } || SHOPPING_WORDS.containsMatchIn(haystack) ->
                ExpenseCategory.SHOPPING
            ENTERTAINMENT_CONTAINS.any { haystack.contains(it) } || ENTERTAINMENT_WORDS.containsMatchIn(haystack) ->
                ExpenseCategory.ENTERTAINMENT
            HEALTH_CONTAINS.any { haystack.contains(it) } || HEALTH_WORDS.containsMatchIn(haystack) ->
                ExpenseCategory.HEALTH
            merchant.contains("@") || Regex("""\bupi\b""", RegexOption.IGNORE_CASE).containsMatchIn(haystack) ->
                ExpenseCategory.TRANSFER
            else -> ExpenseCategory.OTHER
        }
    }
}
