package com.spendwise.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.spendwise.data.repository.CategoryRepository
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.budget.BudgetEngine
import com.spendwise.domain.categorizer.CategoryClassifier
import com.spendwise.domain.model.AppCategory
import com.spendwise.domain.parser.SmsParser
import com.spendwise.ui.notification.BudgetNotificationHelper
import com.spendwise.ui.widget.BudgetWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var budgetEngine: BudgetEngine

    @Inject
    lateinit var categoryRepository: CategoryRepository

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val receivedAt = LocalDateTime.now()

        // Reconstruct full message bodies (SMS may be split across multiple PDUs)
        val grouped = messages.groupBy { it.originatingAddress }
        val parsedTransactions = grouped.mapNotNull { (sender, parts) ->
            if (sender == null) return@mapNotNull null
            val fullBody = parts.joinToString("") { it.messageBody }
            SmsParser.parse(sender, fullBody, receivedAt)
        }

        if (parsedTransactions.isEmpty()) return

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val prefs = preferencesRepository.userPreferences.first()
                val learnedRules = categoryRepository.getLearnedMerchantMap()
                var anyInserted = false
                for (parsed in parsedTransactions) {
                    val learnedKey = learnedRules[CategoryClassifier.normalizeMerchant(parsed.merchant)]
                    val withCategory = if (!learnedKey.isNullOrBlank()) {
                        val (cat, customKey) = AppCategory.splitKey(learnedKey)
                        parsed.copy(category = cat, customCategoryKey = customKey)
                    } else {
                        parsed
                    }
                    val transaction = if (withCategory.isCredit) {
                        withCategory.copy(includeInBudget = prefs.includeCreditsInBudget)
                    } else {
                        withCategory
                    }
                    val inserted = transactionRepository.insertTransaction(transaction)
                    if (inserted) {
                        anyInserted = true
                        val budgetState = budgetEngine.calculateBudgetState(prefs)
                        BudgetNotificationHelper.showPaymentNotification(
                            context = context,
                            transaction = transaction,
                            budgetState = budgetState
                        )
                    }
                }
                if (anyInserted) {
                    BudgetWidgetUpdater.updateNow(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
