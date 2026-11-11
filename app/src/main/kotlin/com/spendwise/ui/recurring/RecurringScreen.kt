package com.spendwise.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.domain.model.ExpenseCategory
import com.spendwise.domain.model.RecurringFrequency
import com.spendwise.domain.model.RecurringRule
import com.spendwise.ui.components.icon
import com.spendwise.ui.components.tintColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    viewModel: RecurringViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = isSystemInDarkTheme()
    val creditColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring Bills & Income", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddSheet() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Rule") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.EventRepeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("No recurring rules yet", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Add WiFi, rent, subscriptions, or regular allowances so SpendWise logs or reminds you on schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.rules, key = { it.id }) { rule ->
                    val catColor = if (rule.isCredit) creditColor else rule.category.tintColor()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.startEditing(rule) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = catColor.copy(alpha = 0.14f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            rule.category.icon(),
                                            contentDescription = null,
                                            tint = catColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${rule.frequency.label} · Next: ${rule.nextDueDate.format(dateFmt)}" +
                                            if (rule.autoLog) " · Auto-log" else " · Manual",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${if (rule.isCredit) "+" else "-"}${uiState.currency}${"%.2f".format(rule.amount)}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (rule.isCredit) creditColor else MaterialTheme.colorScheme.error
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.logNow(rule) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Log Now", style = MaterialTheme.typography.labelMedium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (rule.isActive) "Active" else "Paused",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Switch(
                                        checked = rule.isActive,
                                        onCheckedChange = { viewModel.toggleActive(rule, it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAddSheet || uiState.editingRule != null) {
        RecurringRuleFormSheet(
            currency = uiState.currency,
            initialRule = uiState.editingRule,
            defaultIncludeCredits = uiState.defaultIncludeCreditsInBudget,
            onDismiss = { viewModel.dismissSheet() },
            onSave = { viewModel.saveRule(it) },
            onDelete = uiState.editingRule?.let { r -> { viewModel.deleteRule(r) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringRuleFormSheet(
    currency: String,
    initialRule: RecurringRule?,
    defaultIncludeCredits: Boolean,
    onDismiss: () -> Unit,
    onSave: (RecurringRule) -> Unit,
    onDelete: (() -> Unit)?
) {
    val isEdit = initialRule != null
    val today = remember { LocalDate.now() }
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }

    var title by remember(initialRule) { mutableStateOf(initialRule?.title ?: "") }
    var amountText by remember(initialRule) {
        mutableStateOf(
            initialRule?.amount?.let { amt ->
                if (amt % 1.0 == 0.0) "%.0f".format(Locale.US, amt) else "%.2f".format(Locale.US, amt)
            } ?: ""
        )
    }
    var isCredit by remember(initialRule) { mutableStateOf(initialRule?.isCredit ?: false) }
    var includeInBudget by remember(initialRule) {
        mutableStateOf(
            if (initialRule != null && initialRule.isCredit) initialRule.includeInBudget
            else defaultIncludeCredits
        )
    }
    var category by remember(initialRule) {
        mutableStateOf(initialRule?.category ?: ExpenseCategory.BILLS)
    }
    var frequency by remember(initialRule) {
        mutableStateOf(initialRule?.frequency ?: RecurringFrequency.MONTHLY)
    }
    var nextDueDate by remember(initialRule) { mutableStateOf(initialRule?.nextDueDate ?: today) }
    var autoLog by remember(initialRule) { mutableStateOf(initialRule?.autoLog ?: true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isEdit) "Edit Recurring Rule" else "New Recurring Bill / Income",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isCredit,
                    onClick = {
                        isCredit = false
                        if (category == ExpenseCategory.INCOME) category = ExpenseCategory.BILLS
                    },
                    label = { Text("Bill / Expense") },
                    leadingIcon = if (!isCredit) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = isCredit,
                    onClick = {
                        isCredit = true
                        category = ExpenseCategory.INCOME
                    },
                    label = { Text("Allowance / Income") },
                    leadingIcon = if (isCredit) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; errorMsg = null },
                label = { Text("Title (e.g. WiFi Bill, Gym, Salary)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it; errorMsg = null },
                label = { Text("Amount ($currency)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Text(currency, modifier = Modifier.padding(start = 6.dp)) }
            )

            if (errorMsg != null) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            if (isCredit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Include in Daily Limit when logged", modifier = Modifier.weight(1f))
                    Switch(checked = includeInBudget, onCheckedChange = { includeInBudget = it })
                }
            }

            Text("Frequency", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurringFrequency.entries.forEach { freq ->
                    FilterChip(
                        selected = frequency == freq,
                        onClick = { frequency = freq },
                        label = { Text(freq.label) }
                    )
                }
            }

            Text("Category", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExpenseCategory.entries) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.displayName) },
                        leadingIcon = {
                            Icon(cat.icon(), null, Modifier.size(16.dp), tint = cat.tintColor())
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Next Due: ${nextDueDate.format(dateFmt)}", style = MaterialTheme.typography.labelLarge)
                Row {
                    IconButton(onClick = { nextDueDate = nextDueDate.minusDays(1) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Previous day")
                    }
                    IconButton(onClick = { nextDueDate = nextDueDate.plusDays(1) }) {
                        Icon(Icons.Default.Add, contentDescription = "Next day")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-log when due", fontWeight = FontWeight.Medium)
                    Text(
                        "Automatically records transaction when due date arrives",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoLog, onCheckedChange = { autoLog = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isEdit && onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
                Button(
                    onClick = {
                        val amt = amountText.trim().replace(",", ".").toDoubleOrNull()
                        if (title.isBlank()) {
                            errorMsg = "Please enter a title"
                            return@Button
                        }
                        if (amt == null || amt <= 0.0) {
                            errorMsg = "Enter a valid positive amount"
                            return@Button
                        }
                        val rule = (initialRule ?: RecurringRule(title = "", amount = 0.0)).copy(
                            title = title.trim(),
                            amount = amt,
                            category = category,
                            isCredit = isCredit,
                            includeInBudget = if (isCredit) includeInBudget else false,
                            frequency = frequency,
                            nextDueDate = nextDueDate,
                            autoLog = autoLog,
                            isActive = initialRule?.isActive ?: true
                        )
                        onSave(rule)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Rule")
                }
            }
        }
    }
}
