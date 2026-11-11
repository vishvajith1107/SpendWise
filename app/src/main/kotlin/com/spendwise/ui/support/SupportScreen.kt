package com.spendwise.ui.support

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spendwise.domain.support.SupportInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    var copiedTrigger by remember { mutableIntStateOf(0) }
    var noEmailClientTrigger by remember { mutableIntStateOf(0) }

    fun hasPerm(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    fun isBatteryUnrestricted(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    var receiveSmsOk by remember { mutableStateOf(hasPerm(Manifest.permission.RECEIVE_SMS)) }
    var readSmsOk by remember { mutableStateOf(hasPerm(Manifest.permission.READ_SMS)) }
    var notifOk by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPerm(Manifest.permission.POST_NOTIFICATIONS)
        )
    }
    var batteryOk by remember { mutableStateOf(isBatteryUnrestricted()) }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                receiveSmsOk = hasPerm(Manifest.permission.RECEIVE_SMS)
                readSmsOk = hasPerm(Manifest.permission.READ_SMS)
                notifOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    hasPerm(Manifest.permission.POST_NOTIFICATIONS)
                batteryOk = isBatteryUnrestricted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(copiedTrigger) {
        if (copiedTrigger > 0) {
            snackbarHostState.showSnackbar("Copied ${SupportInfo.SUPPORT_EMAIL}")
        }
    }
    LaunchedEffect(noEmailClientTrigger) {
        if (noEmailClientTrigger > 0) {
            snackbarHostState.showSnackbar("No email app found. Copy ${SupportInfo.SUPPORT_EMAIL} instead.")
        }
    }

    fun launchSupportEmail(subject: String, body: String) {
        val uri = Uri.parse(
            "mailto:${SupportInfo.SUPPORT_EMAIL}" +
                "?subject=${Uri.encode(subject)}" +
                "&body=${Uri.encode(body)}"
        )
        val intent = Intent(Intent.ACTION_SENDTO, uri)
        runCatching { context.startActivity(intent) }
            .onFailure { noEmailClientTrigger++ }
    }

    val diagnosticsFooter = remember {
        "\n\n--- App Info (No personal data included) ---\n" +
            "App Version: ${SupportInfo.APP_VERSION}\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Contact Support Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Contact Support",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Need help, spotted a bug, or want support for another bank SMS format? Reach out anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    SupportInfo.SUPPORT_EMAIL,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Sample support address — update once official mail ID is ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(SupportInfo.SUPPORT_EMAIL))
                                    copiedTrigger++
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Email")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                launchSupportEmail(
                                    subject = "[SpendWise v${SupportInfo.APP_VERSION}] Support Request",
                                    body = "Hi SpendWise Team,\n\nDescribe your question or issue here:$diagnosticsFooter"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Email, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Email Support")
                        }
                        OutlinedButton(
                            onClick = {
                                launchSupportEmail(
                                    subject = "[SpendWise v${SupportInfo.APP_VERSION}] New Bank SMS Request",
                                    body = "Hi SpendWise Team,\n\nBank Name:\nSender ID (e.g. AX-HDFCBK-S):\nAnonymized SMS Template (replace personal digits with XXXX):" +
                                        diagnosticsFooter
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Sms, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bank Format")
                        }
                    }
                }
            }

            // Diagnostics / System Health Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "System Health & Permissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    HealthRow("Auto SMS Capture", receiveSmsOk)
                    HealthRow("SMS Inbox Scan (READ_SMS)", readSmsOk)
                    HealthRow("Budget Alerts (Notifications)", notifOk)
                    HealthRow("Battery Unrestricted", batteryOk)
                    HealthRow("100% Offline (No Internet Permission)", true)

                    if (!receiveSmsOk || !notifOk || !batteryOk) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Open App Permissions")
                        }
                    }
                }
            }

            // FAQ Section
            Text(
                "Frequently Asked Questions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SupportInfo.FAQS.forEach { faq ->
                var expanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = faq.question,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                        AnimatedVisibility(visible = expanded) {
                            Text(
                                text = faq.answer,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (ok) "OK" else "Check",
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}
