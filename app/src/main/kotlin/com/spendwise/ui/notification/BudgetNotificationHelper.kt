package com.spendwise.ui.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.spendwise.MainActivity
import com.spendwise.R
import com.spendwise.domain.budget.BudgetState
import com.spendwise.domain.model.Transaction
import kotlin.math.abs

object BudgetNotificationHelper {

    private const val CHANNEL_ID = "spend_alerts"
    private const val CHANNEL_NAME = "Spending Alerts"
    private const val CHANNEL_DESC = "Notifications showing remaining daily budget after each payment"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showPaymentNotification(
        context: Context,
        transaction: Transaction,
        budgetState: BudgetState
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        createNotificationChannel(context)

        val currency = budgetState.currency
        val remaining = budgetState.remainingToday
        val isOverBudget = remaining < 0

        val (title, summaryLine, accentColor) = if (transaction.isCredit) {
            val statusText = if (transaction.includeInBudget) {
                "Added to daily limit • Remaining today: $currency${"%.2f".format(remaining)}"
            } else {
                "Not added to daily limit • Remaining today: $currency${"%.2f".format(remaining)}"
            }
            Triple(
                "Credited +$currency${"%.2f".format(transaction.amount)} • ${transaction.merchant}",
                statusText,
                0xFF2E7D32.toInt()
            )
        } else {
            val spentLine = if (isOverBudget) {
                "Over budget by $currency${"%.2f".format(abs(remaining))}! (Spent today: $currency${"%.2f".format(budgetState.spentToday)})"
            } else {
                "Remaining today: $currency${"%.2f".format(remaining)} (Spent today: $currency${"%.2f".format(budgetState.spentToday)})"
            }
            Triple(
                "Paid $currency${"%.2f".format(transaction.amount)} • ${transaction.merchant}",
                spentLine,
                if (isOverBudget) 0xFFB71C1C.toInt() else 0xFF1B5E20.toInt()
            )
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(accentColor)
            .setContentTitle(title)
            .setContentText(summaryLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryLine))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = (transaction.upiRef?.hashCode() ?: System.currentTimeMillis().toInt())
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
