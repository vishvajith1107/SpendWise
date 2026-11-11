package com.spendwise.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object BudgetWidgetUpdater {
    fun update(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BudgetWidget().updateAll(context)
            } catch (_: Exception) {
                // If widget is not placed on home screen, ignore safely
            }
        }
    }
}
