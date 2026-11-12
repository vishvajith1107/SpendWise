package com.spendwise.domain.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    fun canUseBiometric(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock SpendWise",
        subtitle: String = "Use your fingerprint or biometrics to continue",
        hasPinFallback: Boolean,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val userCancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onError(if (userCancelled) null else errString.toString())
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (!hasPinFallback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                Authenticators.BIOMETRIC_STRONG or
                    Authenticators.BIOMETRIC_WEAK or
                    Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            builder.setAllowedAuthenticators(
                Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK
            )
            builder.setNegativeButtonText(if (hasPinFallback) "Use PIN" else "Cancel")
        }

        prompt.authenticate(builder.build())
    }
}
