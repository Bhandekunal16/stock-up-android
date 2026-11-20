package com.example.security

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

interface DeviceAuthCapabilityProvider {
    fun inspect(): DeviceAuthCapabilities
}

class AndroidDeviceAuthCapabilityProvider(
    private val context: Context,
) : DeviceAuthCapabilityProvider {
    override fun inspect(): DeviceAuthCapabilities {
        val biometricManager = BiometricManager.from(context)
        val bioResult = biometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        val bioAvailability = when (bioResult) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HW_UNAVAILABLE
            else -> BiometricAvailability.UNSUPPORTED
        }

        val hasDeviceCredential = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricManager.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.isDeviceSecure == true
        }

        return DeviceAuthCapabilities(
            biometricAvailability = bioAvailability,
            hasDeviceCredential = hasDeviceCredential,
        )
    }
}

object BiometricPromptHelper {

    fun launchBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Unlock StockFolio",
        subtitle: String = "Verify your identity with biometrics",
        negativeButtonText: String = "Use Other Options",
        onOutcome: (BiometricOutcome) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onOutcome(BiometricOutcome.Success)
                }

                override fun onAuthenticationFailed() {
                    onOutcome(BiometricOutcome.FailedAttempt)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val text = errString.toString().ifBlank { "Biometric authentication error" }
                    val outcome = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> BiometricOutcome.Cancelled
                        BiometricPrompt.ERROR_LOCKOUT -> BiometricOutcome.TemporaryLockout(text)
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricOutcome.PermanentLockout(text)
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE -> BiometricOutcome.Unavailable(text)
                        else -> BiometricOutcome.Error(errorCode, text)
                    }
                    onOutcome(outcome)
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
            .setConfirmationRequired(false)
            .setNegativeButtonText(negativeButtonText)
            .build()

        prompt.authenticate(promptInfo)
    }

    fun launchDeviceCredentialPromptApi30Plus(
        activity: FragmentActivity,
        title: String = "Unlock StockFolio",
        subtitle: String = "Use your device PIN, pattern, or password",
        onOutcome: (BiometricOutcome) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onOutcome(BiometricOutcome.Unavailable("Use legacy keyguard prompt below API 30"))
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onOutcome(BiometricOutcome.Success)
                }

                override fun onAuthenticationFailed() {
                    onOutcome(BiometricOutcome.FailedAttempt)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val text = errString.toString().ifBlank { "Device credential cancelled" }
                    val outcome = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> BiometricOutcome.Cancelled
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> BiometricOutcome.Unavailable(text)
                        else -> BiometricOutcome.Error(errorCode, text)
                    }
                    onOutcome(outcome)
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(DEVICE_CREDENTIAL)
            .build()

        prompt.authenticate(promptInfo)
    }

    @Suppress("DEPRECATION")
    fun createLegacyDeviceCredentialIntent(
        context: Context,
        title: String = "Unlock StockFolio",
        description: String = "Confirm your device PIN, pattern, or password",
    ): Intent? {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return null
        if (!km.isDeviceSecure) return null
        return km.createConfirmDeviceCredentialIntent(title, description)
    }
}
