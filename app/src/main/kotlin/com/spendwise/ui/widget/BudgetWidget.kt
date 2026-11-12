package com.spendwise.ui.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.spendwise.MainActivity
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.domain.budget.BudgetEngine
import com.spendwise.domain.budget.BudgetState
import com.spendwise.domain.model.BudgetPeriod
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlin.math.abs

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun budgetEngine(): BudgetEngine
    fun preferencesRepository(): PreferencesRepository
}

class BudgetWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val prefs = entryPoint.preferencesRepository().userPreferences.first()
        val budgetState = entryPoint.budgetEngine().calculateBudgetState(prefs)

        provideContent {
            WidgetContent(budgetState = budgetState)
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetContent(budgetState: BudgetState) {
    val isNoLimit = budgetState.period == BudgetPeriod.NO_LIMIT
    val remaining = budgetState.remainingToday
    val isOverBudget = !isNoLimit && remaining < 0
    val bgColor = when {
        isNoLimit -> Color(0xFF00695C)
        isOverBudget -> Color(0xFFB71C1C)
        else -> Color(0xFF1B5E20)
    }
    val headlineLabel = when {
        isNoLimit -> "SPENT THIS MONTH"
        isOverBudget -> "OVER BUDGET"
        budgetState.period == BudgetPeriod.WEEKLY -> "REMAINING THIS WEEK"
        budgetState.period == BudgetPeriod.MONTHLY -> "REMAINING THIS MONTH"
        else -> "REMAINING TODAY"
    }
    val primaryAmount = if (isNoLimit) budgetState.periodSpent else abs(remaining)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bgColor))
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = headlineLabel,
            style = TextStyle(
                color = ColorProvider(Color.White.copy(alpha = 0.8f)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        Text(
            text = "${budgetState.currency}${"%.2f".format(primaryAmount)}",
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Today: ${budgetState.currency}${"%.2f".format(budgetState.spentToday)}",
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.85f)),
                    fontSize = 11.sp
                )
            )
            if (!isNoLimit) {
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "• Limit: ${budgetState.currency}${"%.0f".format(budgetState.dailyBudget)}",
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.85f)),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
