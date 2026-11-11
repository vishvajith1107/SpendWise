package com.spendwise.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spendwise.domain.budget.BudgetState
import com.spendwise.ui.components.TransactionFormSheet
import com.spendwise.ui.components.TransactionItem
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToAnalytics: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    onNavigateToSupport: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh date automatically when returning to the app after midnight
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDateIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    uiState.budgetState?.currency ?: "₹",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 18.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("SpendWise", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToRecurring) {
                        Icon(Icons.Default.EventRepeat, contentDescription = "Recurring Bills")
                    }
                    IconButton(onClick = onNavigateToSupport) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Help & Support")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showAddSheet() },
                icon = { Icon(Icons.Default.Add, "Add") },
                text = { Text("Add Entry") }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Budget Card
            item {
                uiState.budgetState?.let { budget ->
                    BudgetSummaryCard(budget = budget)
                }
            }

            // Quick Actions Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedCard(
                        onClick = onNavigateToAnalytics,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Insights,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Analytics",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    OutlinedCard(
                        onClick = onNavigateToRecurring,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.EventRepeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (uiState.upcomingRecurringCount > 0)
                                    "Bills (${uiState.upcomingRecurringCount} due)"
                                else "Recurring Bills",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Today's Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${uiState.todayTransactions.size} transaction${if (uiState.todayTransactions.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.todayTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(38.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "No transactions yet today!",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Incoming bank debit & credit SMS will appear here automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(uiState.todayTransactions, key = { it.id }) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        currency = uiState.budgetState?.currency ?: "₹",
                        onClick = { viewModel.startEditing(transaction) },
                        onDelete = { viewModel.deleteTransaction(transaction) }
                    )
                }
            }

        }
    }

    if (uiState.showAddTransactionSheet) {
        TransactionFormSheet(
            currency = uiState.budgetState?.currency ?: "₹",
            defaultIncludeCreditsInBudget = uiState.defaultIncludeCreditsInBudget,
            onDismiss = { viewModel.hideAddSheet() },
            onSave = { tx -> viewModel.saveManualTransaction(tx) }
        )
    }

    uiState.editingTransaction?.let { editing ->
        TransactionFormSheet(
            currency = uiState.budgetState?.currency ?: "₹",
            initialTransaction = editing,
            defaultIncludeCreditsInBudget = uiState.defaultIncludeCreditsInBudget,
            onDismiss = { viewModel.stopEditing() },
            onSave = { updated -> viewModel.updateTransaction(updated) },
            onDelete = { toDelete -> viewModel.deleteTransaction(toDelete) }
        )
    }
}

@Composable
fun BudgetSummaryCard(budget: BudgetState) {
    val remaining = budget.remainingToday
    val isOverBudget = remaining < 0
    val fillFraction = if (budget.totalAvailable > 0) {
        (budget.spentToday / budget.totalAvailable).coerceIn(0.0, 1.0).toFloat()
    } else 1f

    val gradientColors = if (isOverBudget) {
        listOf(Color(0xFFB71C1C), Color(0xFFD32F2F))
    } else {
        listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(22.dp)
        ) {
            Column {
                Text(
                    text = if (isOverBudget) "Over Budget by" else "Remaining to Spend Today",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${budget.currency}${"%.2f".format(abs(remaining))}",
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { fillFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isOverBudget) Color(0xFFFFCDD2) else Color(0xFFC8E6C9),
                    trackColor = Color.White.copy(alpha = 0.25f)
                )

                if (budget.creditedIncludedToday > 0.0) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "Includes +${budget.currency}${"%.2f".format(budget.creditedIncludedToday)} credited today",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BudgetStatItem(
                        label = "Daily Limit",
                        value = "${budget.currency}${"%.0f".format(budget.dailyBudget)}"
                    )
                    BudgetStatItem(
                        label = "Spent Today",
                        value = "${budget.currency}${"%.2f".format(budget.spentToday)}"
                    )
                    BudgetStatItem(
                        label = "Rollover",
                        value = "${if (budget.rolloverBalance >= 0) "+" else "-"}${budget.currency}${"%.2f".format(abs(budget.rolloverBalance))}"
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
