package com.spendwise.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.domain.analytics.AnalyticsPeriod
import com.spendwise.domain.analytics.AnalyticsSnapshot
import com.spendwise.domain.analytics.CategorySpend
import com.spendwise.domain.analytics.DailySpendPoint
import com.spendwise.ui.components.TransactionFormSheet
import com.spendwise.ui.components.icon
import com.spendwise.ui.components.tintColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()
    val positiveGreen = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    var viewingCategorySpend by remember { mutableStateOf<CategorySpend?>(null) }
    var selectedDayForTransactions by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spending Analytics", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val snapshot = uiState.snapshot
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Period filter chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnalyticsPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = uiState.selectedPeriod == period,
                            onClick = { viewModel.selectPeriod(period) },
                            label = { Text(period.label, maxLines = 1) }
                        )
                    }
                }
            }

            if (snapshot == null || (snapshot.totalSpent == 0.0 && snapshot.totalCredited == 0.0)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Insights,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text("No data in this period", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Transactions captured from SMS or added manually will appear here as charts and breakdowns.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                return@LazyColumn
            }

            // KPI Summary Grid (2x2)
            item {
                KpiSummaryGrid(
                    snapshot = snapshot,
                    currency = uiState.currency,
                    positiveGreen = positiveGreen
                )
            }

            // Daily Trend Bar Chart
            item {
                DailyTrendCard(
                    series = snapshot.dailySeries.takeLast(30),
                    dailyBudget = uiState.dailyBudget,
                    currency = uiState.currency,
                    positiveGreen = positiveGreen
                )
            }

            // Category Breakdown Donut + List
            if (snapshot.categoryBreakdown.isNotEmpty()) {
                item {
                    CategoryBreakdownCard(
                        categories = snapshot.categoryBreakdown,
                        totalSpent = snapshot.totalSpent,
                        currency = uiState.currency,
                        onCategoryClick = { clickedCat ->
                            viewingCategorySpend = clickedCat
                        }
                    )
                }
            }

            // Highest Spend Day highlight
            snapshot.highestSpendDay?.let { (peakDate, peakAmount) ->
                item {
                    val dateFmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")
                    Card(
                        onClick = { selectedDayForTransactions = peakDate },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Peak Spend Day", style = MaterialTheme.typography.labelMedium)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "• Tap to view",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        peakDate.format(dateFmt),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "${uiState.currency}${"%.2f".format(peakAmount)}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 16.sp
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Top Merchants Card
            if (snapshot.topMerchants.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Store,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Top Merchants & Recipients",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            snapshot.topMerchants.forEach { stat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stat.merchant,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${stat.count} transaction${if (stat.count == 1) "" else "s"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "${uiState.currency}${"%.2f".format(stat.totalAmount)}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Payment Method / Bank Breakdown Card
            if (snapshot.bankBreakdown.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "By Bank / Source",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            snapshot.bankBreakdown.forEach { bankStat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${bankStat.bank} (${bankStat.count})",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "${uiState.currency}${"%.2f".format(bankStat.totalSpent)}",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    viewingCategorySpend?.let { catSpend ->
        CategoryTransactionsSheet(
            categorySpend = catSpend,
            allTransactions = uiState.allTransactions,
            categories = uiState.categories,
            currency = uiState.currency,
            initialPeriod = uiState.selectedPeriod,
            onDismiss = { viewingCategorySpend = null },
            onTransactionClick = { tx -> viewModel.startEditing(tx) },
            onTransactionDelete = { tx -> viewModel.deleteTransaction(tx) }
        )
    }

    selectedDayForTransactions?.let { day ->
        val dayTxs = remember(uiState.allTransactions, day) {
            uiState.allTransactions.filter { it.date == day }
        }
        val isPeak = uiState.snapshot?.highestSpendDay?.first == day
        DayTransactionsSheet(
            date = day,
            currency = uiState.currency,
            transactions = dayTxs,
            categories = uiState.categories,
            isPeakDay = isPeak,
            onDismiss = { selectedDayForTransactions = null },
            onTransactionClick = { tx -> viewModel.startEditing(tx) },
            onTransactionDelete = { tx -> viewModel.deleteTransaction(tx) }
        )
    }

    uiState.editingTransaction?.let { editing ->
        TransactionFormSheet(
            currency = uiState.currency,
            initialTransaction = editing,
            defaultIncludeCreditsInBudget = uiState.defaultIncludeCreditsInBudget,
            categories = uiState.categories,
            categoryUsageCounts = uiState.categoryUsageCounts,
            learnedMerchantRules = uiState.learnedMerchantRules,
            onCreateCategory = viewModel::createCustomCategory,
            onDismiss = { viewModel.stopEditing() },
            onSave = { updated -> viewModel.updateTransaction(updated) },
            onDelete = { toDelete -> viewModel.deleteTransaction(toDelete) }
        )
    }
}

@Composable
private fun KpiSummaryGrid(
    snapshot: AnalyticsSnapshot,
    currency: String,
    positiveGreen: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                modifier = Modifier.weight(1f),
                label = "Total Spent",
                value = "$currency${"%.2f".format(snapshot.totalSpent)}",
                valueColor = MaterialTheme.colorScheme.error
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                label = "Daily Average",
                value = "$currency${"%.2f".format(snapshot.avgDailySpend)}",
                subtext = "across ${snapshot.daysInPeriod}d"
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                modifier = Modifier.weight(1f),
                label = "Total Credited",
                value = "+$currency${"%.2f".format(snapshot.totalCredited)}",
                valueColor = positiveGreen
            )
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = positiveGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Under Limit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${snapshot.daysUnderLimit}/${snapshot.daysInPeriod} days",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = positiveGreen
                    )
                    Text(
                        "${snapshot.currentUnderLimitStreak}-day current streak",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtext: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtext != null) {
                Text(
                    subtext,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DailyTrendCard(
    series: List<DailySpendPoint>,
    dailyBudget: Double,
    currency: String,
    positiveGreen: Color
) {
    val errorColor = MaterialTheme.colorScheme.error
    val guideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val dateFmt = DateTimeFormatter.ofPattern("dd MMM")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Daily Spend vs. Limit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (dailyBudget > 0.0) {
                        "Daily Target: $currency${"%.0f".format(dailyBudget)}"
                    } else {
                        "No Limit"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (series.isNotEmpty()) {
                val maxVal = if (dailyBudget > 0.0) {
                    max(series.maxOf { it.spent }, dailyBudget).coerceAtLeast(1.0)
                } else {
                    series.maxOf { it.spent }.coerceAtLeast(1.0)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(top = 6.dp)
                ) {
                    val count = series.size
                    val slotWidth = size.width / count.coerceAtLeast(1)
                    val barWidth = (slotWidth * 0.62f).coerceIn(4f, 36f)
                    val chartHeight = size.height

                    series.forEachIndexed { idx, point ->
                        val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                        // Subtle track background
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, 0f),
                            size = Size(barWidth, chartHeight),
                            cornerRadius = CornerRadius(6f, 6f)
                        )

                        val barFraction = (point.spent / maxVal).toFloat().coerceIn(0f, 1f)
                        val barHeight = chartHeight * barFraction
                        if (barHeight > 0f) {
                            drawRoundRect(
                                color = if (point.isOverLimit) errorColor else positiveGreen,
                                topLeft = Offset(left, chartHeight - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }

                    // Dashed horizontal reference line when a limit is active
                    if (dailyBudget > 0.0) {
                        val limitY = chartHeight * (1f - (dailyBudget / maxVal).toFloat().coerceIn(0f, 1f))
                        drawLine(
                            color = guideColor,
                            start = Offset(0f, limitY),
                            end = Offset(size.width, limitY),
                            strokeWidth = 2.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        series.first().date.format(dateFmt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        series.last().date.format(dateFmt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    categories: List<CategorySpend>,
    totalSpent: Double,
    currency: String,
    onCategoryClick: (CategorySpend) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Spending by Category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tap to view",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    val strokeWidth = 28.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    var startAngle = -90f

                    categories.forEach { catSpend ->
                        val sweep = ((catSpend.percentage / 100f) * 360f).coerceAtLeast(1.5f)
                        drawArc(
                            color = androidx.compose.ui.graphics.Color(catSpend.colorHex),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        startAngle += (catSpend.percentage / 100f) * 360f
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$currency${"%.0f".format(totalSpent)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            categories.forEach { catSpend ->
                val catColor = androidx.compose.ui.graphics.Color(catSpend.colorHex)
                Surface(
                    onClick = { onCategoryClick(catSpend) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(catColor.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        com.spendwise.ui.components.iconByKey(catSpend.iconKey),
                                        contentDescription = null,
                                        tint = catColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        catSpend.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${catSpend.count} tx · ${"%.1f".format(catSpend.percentage)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "$currency${"%.2f".format(catSpend.amount)}",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "View category transactions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { (catSpend.percentage / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = catColor,
                            trackColor = catColor.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        }
    }
}
