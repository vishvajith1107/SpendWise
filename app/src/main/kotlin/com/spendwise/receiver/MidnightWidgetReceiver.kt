package com.spendwise.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.spendwise.ui.widget.BudgetWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MidnightWidgetReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                BudgetWidgetUpdater.updateNow(context)
                scheduleNextMidnightUpdate(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_MIDNIGHT_UPDATE = "com.spendwise.ACTION_MIDNIGHT_UPDATE"
        private const val REQUEST_CODE = 1001

        fun scheduleNextMidnightUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, MidnightWidgetReceiver::class.java).apply {
                action = ACTION_MIDNIGHT_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Trigger 5 seconds after midnight so LocalDate.now() has cleanly rolled over
            val nextMidnightMillis = LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() + 5_000L

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC,
                nextMidnightMillis,
                pendingIntent
            )
        }
    }
}
