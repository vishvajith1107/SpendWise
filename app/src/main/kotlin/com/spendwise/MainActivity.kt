package com.spendwise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.RecurringRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.security.BiometricAuthHelper
import com.spendwise.domain.security.PinSecurityHelper
import com.spendwise.ui.SpendWiseNavHost
import com.spendwise.ui.onboarding.InitialSetupDialog
import com.spendwise.ui.security.PinLockScreen
import com.spendwise.ui.theme.SpendWiseTheme
import com.spendwise.ui.widget.BudgetWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var recurringRepository: RecurringRepository

    @Inject
    lateinit var transactionRepository: TransactionRepository

    private var lastBackgroundedElapsedMs: Long = 0L
    private var needsPinOnResume by mutableStateOf(true)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions are checked dynamically wherever used */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestStartupPermissionsIfNeeded()

        setContent {
            val prefs by preferencesRepository.userPreferences.collectAsState(initial = null)
            var previousHadLock by remember { mutableStateOf<Boolean?>(null) }
            var pinError by remember { mutableStateOf<String?>(null) }
            var biometricPromptAttempted by remember { mutableStateOf(false) }

            val biometricHardwareAvailable = remember {
                BiometricAuthHelper.canUseBiometric(this@MainActivity)
            }

            fun triggerBiometricUnlock(hasPinFallback: Boolean) {
                BiometricAuthHelper.authenticate(
                    activity = this@MainActivity,
                    hasPinFallback = hasPinFallback,
                    onSuccess = {
                        pinError = null
                        needsPinOnResume = false
                    },
                    onError = { msg ->
                        if (!msg.isNullOrBlank()) {
                            pinError = msg
                        }
                    }
                )
            }

            LaunchedEffect(prefs, needsPinOnResume) {
                val currentPrefs = prefs ?: return@LaunchedEffect
                val hasPinNow = !currentPrefs.pinHash.isNullOrBlank()
                val biometricReady = currentPrefs.biometricEnabled && biometricHardwareAvailable
                val hasLockNow = hasPinNow || biometricReady

                if (previousHadLock == false && hasLockNow) {
                    // Lock was just configured in Settings/Onboarding during this unlocked session
                    needsPinOnResume = false
                }
                if (!hasLockNow) {
                    needsPinOnResume = false
                    pinError = null
                }
                previousHadLock = hasLockNow

                if (hasLockNow && needsPinOnResume && biometricReady && !biometricPromptAttempted) {
                    biometricPromptAttempted = true
                    triggerBiometricUnlock(hasPinFallback = hasPinNow)
                } else if (!needsPinOnResume) {
                    biometricPromptAttempted = false
                }
            }

            val darkTheme = when (prefs?.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            SpendWiseTheme(darkTheme = darkTheme) {
                val loadedPrefs = prefs
                val hasPin = !loadedPrefs?.pinHash.isNullOrBlank()
                val biometricReady = (loadedPrefs?.biometricEnabled == true) && biometricHardwareAvailable
                val isLocked = (hasPin || biometricReady) && needsPinOnResume

                when {
                    loadedPrefs == null -> {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {}
                    }
                    isLocked -> {
                        PinLockScreen(
                            title = "Unlock SpendWise",
                            subtitle = if (hasPin) {
                                "Enter your 4-digit passcode"
                            } else {
                                "Use fingerprint to unlock SpendWise"
                            },
                            errorMessage = pinError,
                            showBiometricButton = biometricReady,
                            allowPinEntry = hasPin,
                            onUseBiometric = {
                                pinError = null
                                triggerBiometricUnlock(hasPinFallback = hasPin)
                            },
                            onPinComplete = { entered ->
                                if (PinSecurityHelper.verifyPin(entered, loadedPrefs.pinHash)) {
                                    pinError = null
                                    needsPinOnResume = false
                                } else {
                                    pinError = "Incorrect passcode. Please try again."
                                }
                            }
                        )
                    }
                    else -> {
                        SpendWiseNavHost()
                        if (!loadedPrefs.onboardingCompleted) {
                            InitialSetupDialog(
                                initialPrefs = loadedPrefs,
                                onSave = { period, amount, retentionDays, pinHash, biometric ->
                                    lifecycleScope.launch {
                                        preferencesRepository.completeInitialSetup(
                                            budgetPeriod = period,
                                            dailyBudget = amount,
                                            dataRetentionDays = retentionDays,
                                            pinHash = pinHash,
                                            biometricEnabled = biometric
                                        )
                                        if (retentionDays > 0) {
                                            transactionRepository.pruneOlderThan(retentionDays)
                                        }
                                        BudgetWidgetUpdater.update(applicationContext)
                                    }
                                },
                                onSkip = {
                                    lifecycleScope.launch {
                                        preferencesRepository.updateOnboardingCompleted(true)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (lastBackgroundedElapsedMs > 0L &&
            (SystemClock.elapsedRealtime() - lastBackgroundedElapsedMs) > LOCK_TIMEOUT_MS
        ) {
            needsPinOnResume = true
        }
        lifecycleScope.launch {
            val currentPrefs = preferencesRepository.userPreferences.first()
            if (currentPrefs.onboardingCompleted && currentPrefs.dataRetentionDays > 0) {
                transactionRepository.pruneOlderThan(currentPrefs.dataRetentionDays)
            }
            val added = recurringRepository.processDueAutoRules()
            if (added > 0 || (currentPrefs.onboardingCompleted && currentPrefs.dataRetentionDays > 0)) {
                BudgetWidgetUpdater.update(applicationContext)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lastBackgroundedElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun requestStartupPermissionsIfNeeded() {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECEIVE_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECEIVE_SMS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    companion object {
        private const val LOCK_TIMEOUT_MS = 30_000L
    }
}
