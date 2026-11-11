package com.spendwise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.lifecycleScope
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.RecurringRepository
import com.spendwise.domain.security.PinSecurityHelper
import com.spendwise.ui.SpendWiseNavHost
import com.spendwise.ui.security.PinLockScreen
import com.spendwise.ui.theme.SpendWiseTheme
import com.spendwise.ui.widget.BudgetWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var recurringRepository: RecurringRepository

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
            var previousHadPin by remember { mutableStateOf<Boolean?>(null) }
            var pinError by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(prefs) {
                val currentPrefs = prefs ?: return@LaunchedEffect
                val hasPinNow = !currentPrefs.pinHash.isNullOrBlank()
                if (previousHadPin == false && hasPinNow) {
                    // PIN was just configured in Settings during this unlocked session
                    needsPinOnResume = false
                }
                if (!hasPinNow) {
                    needsPinOnResume = false
                    pinError = null
                }
                previousHadPin = hasPinNow
            }

            val darkTheme = when (prefs?.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            SpendWiseTheme(darkTheme = darkTheme) {
                val loadedPrefs = prefs
                when {
                    loadedPrefs == null -> {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {}
                    }
                    !loadedPrefs.pinHash.isNullOrBlank() && needsPinOnResume -> {
                        PinLockScreen(
                            title = "Unlock SpendWise",
                            subtitle = "Enter your 4-digit passcode",
                            errorMessage = pinError,
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
            val added = recurringRepository.processDueAutoRules()
            if (added > 0) {
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
