package com.spendwise.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.parser.SmsParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var transactionRepository: TransactionRepository

    // BroadcastReceivers are short-lived; use SupervisorJob so crashes don't kill the scope
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val receivedAt = LocalDateTime.now()

        // Reconstruct full message body (may be split across PDUs)
        val grouped = messages.groupBy { it.originatingAddress }
        for ((sender, parts) in grouped) {
            if (sender == null) continue
            val fullBody = parts.joinToString("") { it.messageBody }

            val transaction = SmsParser.parse(sender, fullBody, receivedAt) ?: continue

            receiverScope.launch {
                val inserted = transactionRepository.insertTransaction(transaction)
                if (inserted) {
                    // Trigger widget update
                    com.spendwise.ui.widget.BudgetWidgetUpdater.update(context)
                }
            }
        }
    }
}
