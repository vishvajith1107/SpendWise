package com.spendwise.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spendwise.data.repository.UserPreferences
import com.spendwise.domain.model.BudgetPeriod
import com.spendwise.domain.security.BiometricAuthHelper
import com.spendwise.domain.security.PinSecurityHelper
import com.spendwise.ui.security.PinLockScreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InitialSetupDialog(
    initialPrefs: UserPreferences,
    onSave: (
        budgetPeriod: BudgetPeriod,
        budgetAmount: Double,
        dataRetentionDays: Int,
        pinHash: String?,
        biometricEnabled: Boolean
    ) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val biometricAvailable = remember(context) { BiometricAuthHelper.canUseBiometric(context) }

    var selectedPeriod by remember { mutableStateOf(initialPrefs.budgetPeriod) }
    var budgetText by remember {
        val v = initialPrefs.dailyBudget
        mutableStateOf(if (v % 1.0 == 0.0) v.toInt().toString() else v.toString())
    }
    var budgetError by remember { mutableStateOf(false) }

    var retentionDays by remember { mutableStateOf(initialPrefs.dataRetentionDays) }

    var pendingPinHash by remember { mutableStateOf(initialPrefs.pinHash) }
    var biometricEnabled by remember { mutableStateOf(initialPrefs.biometricEnabled && biometricAvailable) }

    var showPinCreator by remember { mutableStateOf(false) }
    var pinStage by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            if (showPinCreator) {
                PinLockScreen(
                    title = if (pinStage == 0) "Create 4-Digit PIN" else "Confirm 4-Digit PIN",
                    subtitle = if (pinStage == 0) {
                        "Choose a 4-digit passcode to lock SpendWise"
                    } else {
                        "Re-enter the same 4-digit passcode"
                    },
                    errorMessage = pinError,
                    onPinComplete = { pin ->
                        if (pinStage == 0) {
                            firstPin = pin
                            pinStage = 1
                            pinError = null
                        } else if (pin == firstPin) {
                            pendingPinHash = PinSecurityHelper.hashPin(pin)
                            showPinCreator = false
                            pinStage = 0
                            firstPin = ""
                            pinError = null
                        } else {
                            pinError = "Passcodes did not match. Try again."
                            pinStage = 0
                            firstPin = ""
                        }
                    },
                    onCancel = {
                        showPinCreator = false
                        pinStage = 0
                        firstPin = ""
                        pinError = null
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Welcome to SpendWise",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Set your preferred limit style, data window, and app lock. Everything stays 100% on your device and can be changed anytime in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1. Budget Mode
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Budget Limit Mode", fontWeight = FontWeight.SemiBold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BudgetPeriod.entries.forEach { period ->
                                        FilterChip(
                                            selected = selectedPeriod == period,
                                            onClick = {
                                                selectedPeriod = period
                                                budgetError = false
                                            },
                                            label = { Text(period.label) }
                                        )
                                    }
                                }

                                if (selectedPeriod != BudgetPeriod.NO_LIMIT) {
                                    OutlinedTextField(
                                        value = budgetText,
                                        onValueChange = {
                                            budgetText = it
                                            budgetError = false
                                        },
                                        label = { Text("${selectedPeriod.label} Limit (${initialPrefs.currency})") },
                                        isError = budgetError,
                                        supportingText = if (budgetError) {
                                            { Text("Enter a valid amount above 0") }
                                        } else null,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        "Track all expenses freely without a budget cap.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 2. Data Retention / Time Period
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Data Time Period", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "How much transaction & SMS history SpendWise keeps by default.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val options = listOf(
                                    7 to "7 Days",
                                    30 to "1 Month",
                                    90 to "3 Months",
                                    365 to "1 Year",
                                    0 to "Keep All"
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    options.forEach { (days, label) ->
                                        FilterChip(
                                            selected = retentionDays == days,
                                            onClick = { retentionDays = days },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. App Lock Preference
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("App Lock Preference", fontWeight = FontWeight.SemiBold)

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
                                            Text("4-Digit PIN Lock", fontWeight = FontWeight.Medium)
                                            Text(
                                                if (pendingPinHash != null) "PIN configured" else "Optional passcode protection",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = pendingPinHash != null,
                                        onCheckedChange = { enable ->
                                            if (enable) {
                                                pinStage = 0
                                                firstPin = ""
                                                pinError = null
                                                showPinCreator = true
                                            } else {
                                                pendingPinHash = null
                                            }
                                        }
                                    )
                                }

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
                                            Icons.Default.Fingerprint,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("Fingerprint Unlock", fontWeight = FontWeight.Medium)
                                            Text(
                                                if (biometricAvailable) {
                                                    "Use phone fingerprint to open SpendWise"
                                                } else {
                                                    "No fingerprint enrolled in Android settings"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = biometricEnabled,
                                        enabled = biometricAvailable,
                                        onCheckedChange = { biometricEnabled = it }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Keep Defaults")
                        }
                        Button(
                            onClick = {
                                val parsed = budgetText.toDoubleOrNull()
                                if (selectedPeriod != BudgetPeriod.NO_LIMIT && (parsed == null || parsed <= 0.0)) {
                                    budgetError = true
                                } else {
                                    onSave(
                                        selectedPeriod,
                                        parsed ?: initialPrefs.dailyBudget,
                                        retentionDays,
                                        pendingPinHash,
                                        biometricEnabled
                                    )
                                }
                            },
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Text("Save & Start")
                        }
                    }
                }
            }
        }
    }
}
