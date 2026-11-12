package com.spendwise.domain.support

data class FaqItem(
    val question: String,
    val answer: String
)

object SupportInfo {
    // Sample support address — replace with your dedicated support email before publishing
    const val SUPPORT_EMAIL = "support@spendwise.example"
    const val APP_VERSION = "2.2"
    const val RELEASE_DOWNLOAD_URL = "https://github.com/vishvajith1107/SpendWise/releases/latest/download/SpendWise.apk"
    const val REPOSITORY_URL = "https://github.com/vishvajith1107/SpendWise"

    val FAQS = listOf(
        FaqItem(
            question = "Why didn't an SMS from my bank appear automatically?",
            answer = "Make sure SMS permission is allowed and Battery is set to Unrestricted in Settings -> App Battery Settings. Some banking or carrier setups use RCS instead of standard SMS. You can also run Settings -> Scan SMS Inbox to import any missed bank alerts from the last 30 days."
        ),
        FaqItem(
            question = "How does daily budget rollover work?",
            answer = "If your daily limit is ₹130 and you spend ₹24 today, the unspent ₹106 rolls over so tomorrow starts with ₹236 available (₹130 + ₹106). Overspending carries forward as a negative balance until recovered or reset in Settings."
        ),
        FaqItem(
            question = "How do credited amounts affect my daily limit?",
            answer = "By default, incoming bank credits and refunds are recorded in History without changing your spending limit. You can turn on 'Include Credits in Daily Limit' globally in Settings, or toggle it per entry by tapping any transaction."
        ),
        FaqItem(
            question = "Can I edit transactions captured from SMS?",
            answer = "Yes. Tap any card on Home or History (or its Edit button) to change the merchant/sender name, amount, category, date, Debit/Credit type, or notes while keeping the original SMS text for reference."
        ),
        FaqItem(
            question = "Does SpendWise send my financial data anywhere?",
            answer = "No. SpendWise declares zero INTERNET permission in its Android manifest. All SMS parsing, categories, charts, recurring rules, and backups stay 100% locally on your phone."
        )
    )
}
