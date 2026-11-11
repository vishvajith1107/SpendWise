package com.spendwise.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spendwise.domain.support.SupportInfo
import com.spendwise.ui.security.PinLockScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    onNavigateToRecurring: () -> Unit,
    onNavigateToSupport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsState(initial = null)
    val isBusy by viewModel.isBusy.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currency = prefs?.currency ?: "₹"

    var budgetText by remember(prefs?.dailyBudget) {
        mutableStateOf(prefs?.dailyBudget?.let { "%.0f".format(it) } ?: "")
    }
    var budgetError by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var pinSetupStage by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarMessage = it
            viewModel.clearStatusMessage()
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.scanSmsInbox()
        } else {
            viewModel.reportStatus("SMS permission denied. Allow SMS access in App Info to scan your inbox.")
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val csv = viewModel.buildCsvExport()
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(csv.toByteArray(Charsets.UTF_8))
                    }
                    viewModel.reportStatus("Transactions exported to CSV!")
                } catch (e: Exception) {
                    viewModel.reportStatus("Export failed: ${e.message ?: "Unable to save file"}")
                }
            }
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = viewModel.buildJsonBackup()
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                    viewModel.reportStatus("Full local backup saved!")
                } catch (e: Exception) {
                    viewModel.reportStatus("Backup failed: ${e.message ?: "Unable to save file"}")
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                    if (json.isBlank()) {
                        viewModel.reportStatus("Selected backup file is empty")
                    } else {
                        viewModel.restoreJsonBackup(json)
                    }
                } catch (e: Exception) {
                    viewModel.reportStatus("Restore failed: ${e.message ?: "Unable to read file"}")
                }
            }
        }
    }

    fun checkBatteryUnrestricted(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    var isUnrestricted by remember { mutableStateOf(checkBatteryUnrestricted()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isUnrestricted = checkBatteryUnrestricted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        snackbarHost = {
            snackbarMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { snackbarMessage = null }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(msg)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Daily Budget Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Daily Budget Limit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "The target maximum amount you want to spend each day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = {
                            budgetText = it
                            budgetError = false
                        },
                        label = { Text("Daily Amount ($currency)") },
                        isError = budgetError,
                        supportingText = if (budgetError) ({ Text("Please enter a valid positive number") }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Text(currency, modifier = Modifier.padding(start = 6.dp), fontSize = 16.sp) }
                    )

                    Button(
                        onClick = {
                            val amount = budgetText.toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                budgetError = true
                            } else {
                                viewModel.updateDailyBudget(amount)
                                snackbarMessage = "Daily budget updated!"
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Limit")
                    }
                }
            }

            // Rollover Toggle Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rollover Unused Budget", fontWeight = FontWeight.SemiBold)
                        Text(
                            "If enabled, any money left unspent today is carried over to boost tomorrow's budget. Overspending also carries over.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = prefs?.rolloverEnabled ?: true,
                        onCheckedChange = { viewModel.updateRolloverEnabled(it) }
                    )
                }
            }

            // Credited Amount Default Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Include Credits in Daily Limit", fontWeight = FontWeight.SemiBold)
                        Text(
                            "When enabled, incoming bank credit/refund SMS are automatically added to your daily spending limit. You can still toggle this per entry by tapping any transaction.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = prefs?.includeCreditsInBudget ?: false,
                        onCheckedChange = { viewModel.updateIncludeCreditsInBudget(it) }
                    )
                }
            }

            // Currency & Appearance Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Currency & Theme", fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("₹", "$", "€", "£", "¥", "AED")) { symbol ->
                            FilterChip(
                                selected = currency == symbol,
                                onClick = { viewModel.updateCurrency(symbol) },
                                label = { Text(symbol) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currentTheme = prefs?.themeMode ?: "SYSTEM"
                        listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (key, label) ->
                            FilterChip(
                                selected = currentTheme == key,
                                onClick = { viewModel.updateThemeMode(key) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // Recurring Bills & Income Shortcut Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToRecurring)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.EventRepeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Recurring Bills & Income", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Automate rent, subscriptions, allowances, or salary schedules",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // Historical SMS Inbox Importer Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Sms,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Import Past Bank SMS", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Scan your SMS inbox from the last 30 days offline to recover missed bank debits or credits on their original dates without duplicates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = {
                            val hasReadSms = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_SMS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasReadSms) {
                                viewModel.scanSmsInbox()
                            } else {
                                smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan Last 30 Days SMS")
                    }
                }
            }

            // Backup & Export Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Export & Local Backup", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Export your ledger as CSV for spreadsheets, or save/restore a complete offline JSON backup of transactions, recurring rules, and settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = {
                                csvExportLauncher.launch("spendwise-transactions-${LocalDate.now()}.csv")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export CSV")
                        }
                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = {
                                backupExportLauncher.launch("spendwise-backup-${LocalDate.now()}.json")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Full Backup")
                        }
                    }
                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = {
                            restoreLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restore from Backup (.json)")
                    }
                }
            }

            // App Passcode Lock Card
            val hasPin = !prefs?.pinHash.isNullOrBlank()
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("App Passcode Lock", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Require a 4-digit PIN when opening SpendWise",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = hasPin,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    pinSetupStage = 0
                                    firstPin = ""
                                    pinError = null
                                    showPinSetupDialog = true
                                } else {
                                    viewModel.removePin()
                                }
                            }
                        )
                    }
                    if (hasPin) {
                        TextButton(
                            onClick = {
                                pinSetupStage = 0
                                firstPin = ""
                                pinError = null
                                showPinSetupDialog = true
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Change 4-Digit PIN")
                        }
                    }
                }
            }

            // Reset Rollover Base
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Reset Rollover Calculation", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Resets the rollover accumulation to start clean from today without affecting past transaction history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            viewModel.resetBudgetStartDay()
                            snackbarMessage = "Rollover reset to today!"
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset Rollover to Today")
                    }
                }
            }

            // Background Reliability / Battery Optimization Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isUnrestricted) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = if (isUnrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Background SMS Reliability", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        if (isUnrestricted) {
                            "Battery usage is set to Unrestricted. Incoming bank SMS alerts will process reliably in the background."
                        } else {
                            "Battery optimization is enabled. Set Battery to 'Unrestricted' in App Info so your phone never puts SMS tracking to sleep."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.SettingsApplications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isUnrestricted) "App Battery Settings" else "Set Unrestricted")
                    }
                }
            }

            // Help & Support Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToSupport)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Help & Support", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Diagnostics, FAQs, or contact ${SupportInfo.SUPPORT_EMAIL}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("100% Offline & Private • v${SupportInfo.APP_VERSION}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "SpendWise operates entirely offline with zero internet permissions. Your financial SMS messages and budgets never leave this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showPinSetupDialog) {
        Dialog(
            onDismissRequest = {
                showPinSetupDialog = false
                pinSetupStage = 0
                firstPin = ""
                pinError = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            key(pinSetupStage) {
                PinLockScreen(
                    title = if (pinSetupStage == 0) "Set 4-Digit Passcode" else "Confirm Passcode",
                    subtitle = if (pinSetupStage == 0) {
                        "Choose a 4-digit PIN to lock SpendWise"
                    } else {
                        "Re-enter the same 4-digit PIN"
                    },
                    errorMessage = pinError,
                    onPinComplete = { pin ->
                        if (pinSetupStage == 0) {
                            firstPin = pin
                            pinSetupStage = 1
                            pinError = null
                        } else if (pin == firstPin) {
                            viewModel.setPin(pin)
                            showPinSetupDialog = false
                            pinSetupStage = 0
                            firstPin = ""
                            pinError = null
                        } else {
                            pinError = "Passcodes did not match. Try again."
                            pinSetupStage = 0
                            firstPin = ""
                        }
                    },
                    onCancel = {
                        showPinSetupDialog = false
                        pinSetupStage = 0
                        firstPin = ""
                        pinError = null
                    }
                )
            }
        }
    }
}
