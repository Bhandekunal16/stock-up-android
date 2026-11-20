package com.example.security

enum class BiometricAvailability(val label: String, val detail: String) {
    AVAILABLE("Enrolled & Ready", "Device biometrics can unlock StockFolio"),
    NONE_ENROLLED("Not Enrolled", "Biometric hardware is present, but no fingerprint or face is enrolled in Android Settings"),
    NO_HARDWARE("Unavailable", "This device does not have biometric authentication hardware"),
    HW_UNAVAILABLE("Temporarily Unavailable", "Biometric sensor is currently unavailable"),
    UNSUPPORTED("Unsupported", "Biometric authentication is not supported on this Android configuration"),
}

enum class PreferredAuthMethod(val label: String, val description: String) {
    BIOMETRIC_FIRST(
        label = "Biometric First",
        description = "Check biometrics automatically, then device screen lock or app PIN/password",
    ),
    DEVICE_CREDENTIAL(
        label = "Device Screen Lock",
        description = "Use your device PIN, pattern, or password directly",
    ),
    APP_PIN(
        label = "App PIN",
        description = "Use your StockFolio numeric PIN first",
    ),
    APP_PASSWORD(
        label = "App Password",
        description = "Use your StockFolio password first",
    ),
}

enum class AuthStepMode {
    BIOMETRIC_PROMPT,
    FALLBACK_CHOOSER,
    APP_PIN_ENTRY,
    APP_PASSWORD_ENTRY,
}

data class DeviceAuthCapabilities(
    val biometricAvailability: BiometricAvailability = BiometricAvailability.NO_HARDWARE,
    val hasDeviceCredential: Boolean = false,
) {
    val canUseBiometric: Boolean
        get() = biometricAvailability == BiometricAvailability.AVAILABLE

    val hasAnyDeviceLock: Boolean
        get() = canUseBiometric || hasDeviceCredential
}

data class AppLockConfig(
    val hasExplicitToggle: Boolean = false,
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = true,
    val isDeviceCredentialEnabled: Boolean = true,
    val preferredMethod: PreferredAuthMethod = PreferredAuthMethod.BIOMETRIC_FIRST,
    val hasAppPin: Boolean = false,
    val hasAppPassword: Boolean = false,
    val failedAttempts: Int = 0,
    val lockoutUntilEpochMs: Long = 0L,
    val isTampered: Boolean = false,
) {
    fun hasAnyEnabledMethod(capabilities: DeviceAuthCapabilities): Boolean {
        val bioReady = isBiometricEnabled && capabilities.canUseBiometric
        val deviceReady = isDeviceCredentialEnabled && capabilities.hasDeviceCredential
        return bioReady || deviceReady || hasAppPin || hasAppPassword
    }
}

sealed interface BiometricOutcome {
    data object Success : BiometricOutcome
    data object Cancelled : BiometricOutcome
    data object FailedAttempt : BiometricOutcome
    data class TemporaryLockout(val message: String) : BiometricOutcome
    data class PermanentLockout(val message: String) : BiometricOutcome
    data class Unavailable(val message: String) : BiometricOutcome
    data class Error(val errorCode: Int, val message: String) : BiometricOutcome
}

sealed interface CredentialVerifyResult {
    data object Success : CredentialVerifyResult
    data class Invalid(
        val failedAttempts: Int,
        val attemptsBeforeLockout: Int,
        val lockoutSeconds: Long,
        val lockoutUntilEpochMs: Long,
    ) : CredentialVerifyResult

    data class LockedOut(
        val remainingSeconds: Long,
        val lockoutUntilEpochMs: Long,
    ) : CredentialVerifyResult

    data object NotConfigured : CredentialVerifyResult
}
