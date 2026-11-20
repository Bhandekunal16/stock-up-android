package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.security.AndroidDeviceAuthCapabilityProvider
import com.example.security.AppLockConfig
import com.example.security.AppLockCryptoManager
import com.example.security.AppLockRepository
import com.example.security.AuthStepMode
import com.example.security.BiometricOutcome
import com.example.security.CredentialVerifyResult
import com.example.security.DataStoreAppLockRepository
import com.example.security.DeviceAuthCapabilities
import com.example.security.DeviceAuthCapabilityProvider
import com.example.security.PreferredAuthMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppLockUiState(
    val isInitialized: Boolean = false,
    val isUnlocked: Boolean = false,
    val isAuthRequired: Boolean = false,
    val activeStep: AuthStepMode = AuthStepMode.BIOMETRIC_PROMPT,
    val capabilities: DeviceAuthCapabilities = DeviceAuthCapabilities(),
    val config: AppLockConfig = AppLockConfig(),
    val shouldAutoLaunchBiometric: Boolean = false,
    val isExternalPromptInFlight: Boolean = false,
    val isBiometricLockedOut: Boolean = false,
    val isVerifying: Boolean = false,
    val lockoutRemainingSeconds: Long = 0L,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
) {
    val canUseBiometricNow: Boolean
        get() = config.isBiometricEnabled && capabilities.canUseBiometric && !isBiometricLockedOut

    val canUseDeviceCredentialNow: Boolean
        get() = config.isDeviceCredentialEnabled && capabilities.hasDeviceCredential

    val canUseAppPinNow: Boolean
        get() = config.hasAppPin

    val canUseAppPasswordNow: Boolean
        get() = config.hasAppPassword
}

/**
 * Pure, testable controller coordinating App Lock priority, BiometricPrompt outcomes,
 * Device Credential fallbacks, App PIN/Password verification, retry lockouts, and background locking.
 */
class AppLockController(
    private val repository: AppLockRepository,
    private val capabilityProvider: DeviceAuthCapabilityProvider,
    private val scope: CoroutineScope,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var lockoutTickerJob: Job? = null
    private var hasAutoPromptedThisLockCycle = false

    init {
        scope.launch {
            val capabilities = capabilityProvider.inspect()
            val initialConfig = repository.initializeDefaultIfNeeded(capabilities)
            val required = computeAuthRequired(initialConfig, capabilities)
            val initialStep = resolveInitialStep(initialConfig, capabilities, bioLockedOut = false)
            val autoBiometric = required &&
                initialStep == AuthStepMode.BIOMETRIC_PROMPT &&
                initialConfig.isBiometricEnabled &&
                capabilities.canUseBiometric

            _uiState.value = AppLockUiState(
                isInitialized = true,
                isUnlocked = !required,
                isAuthRequired = required,
                activeStep = initialStep,
                capabilities = capabilities,
                config = initialConfig,
                shouldAutoLaunchBiometric = autoBiometric,
            )
            syncLockoutTicker(initialConfig.lockoutUntilEpochMs)

            repository.configFlow.collect { updatedConfig ->
                val latestCaps = capabilityProvider.inspect()
                val req = computeAuthRequired(updatedConfig, latestCaps)
                _uiState.update { current ->
                    val unlockedNow = if (!req) true else current.isUnlocked
                    val nextStep = if (unlockedNow) {
                        current.activeStep
                    } else {
                        ensureValidStep(current.activeStep, updatedConfig, latestCaps, current.isBiometricLockedOut)
                    }
                    current.copy(
                        isInitialized = true,
                        capabilities = latestCaps,
                        config = updatedConfig,
                        isAuthRequired = req,
                        isUnlocked = unlockedNow,
                        activeStep = nextStep,
                    )
                }
                syncLockoutTicker(updatedConfig.lockoutUntilEpochMs)
            }
        }
    }

    fun refreshCapabilitiesAndCheckLock() {
        val caps = capabilityProvider.inspect()
        _uiState.update { current ->
            val req = computeAuthRequired(current.config, caps)
            val unlocked = if (!req) true else current.isUnlocked
            val shouldAuto = if (!unlocked && !hasAutoPromptedThisLockCycle && !current.isExternalPromptInFlight) {
                val step = resolveInitialStep(current.config, caps, current.isBiometricLockedOut)
                step == AuthStepMode.BIOMETRIC_PROMPT &&
                    current.config.isBiometricEnabled &&
                    caps.canUseBiometric &&
                    !current.isBiometricLockedOut
            } else {
                false
            }
            val nextStep = if (!unlocked && !hasAutoPromptedThisLockCycle) {
                resolveInitialStep(current.config, caps, current.isBiometricLockedOut)
            } else {
                current.activeStep
            }
            current.copy(
                capabilities = caps,
                isAuthRequired = req,
                isUnlocked = unlocked,
                activeStep = nextStep,
                shouldAutoLaunchBiometric = shouldAuto,
            )
        }
    }

    fun onAppBackgrounded(isChangingConfigurations: Boolean) {
        val current = _uiState.value
        if (isChangingConfigurations || current.isExternalPromptInFlight) return
        if (!current.isInitialized || !current.isAuthRequired) return

        hasAutoPromptedThisLockCycle = false
        _uiState.update {
            it.copy(
                isUnlocked = false,
                shouldAutoLaunchBiometric = false,
                isBiometricLockedOut = false,
                errorMessage = null,
                statusMessage = null,
            )
        }
    }

    fun onAppForegrounded() {
        val current = _uiState.value
        if (!current.isInitialized) return
        val caps = capabilityProvider.inspect()
        val req = computeAuthRequired(current.config, caps)

        if (!req) {
            _uiState.update {
                it.copy(
                    capabilities = caps,
                    isAuthRequired = false,
                    isUnlocked = true,
                    shouldAutoLaunchBiometric = false,
                )
            }
            return
        }

        if (!current.isUnlocked && !current.isExternalPromptInFlight) {
            val step = resolveInitialStep(current.config, caps, current.isBiometricLockedOut)
            val autoBio = !hasAutoPromptedThisLockCycle &&
                step == AuthStepMode.BIOMETRIC_PROMPT &&
                current.config.isBiometricEnabled &&
                caps.canUseBiometric &&
                !current.isBiometricLockedOut

            _uiState.update {
                it.copy(
                    capabilities = caps,
                    isAuthRequired = true,
                    activeStep = step,
                    shouldAutoLaunchBiometric = autoBio,
                )
            }
        }
    }

    fun lockNow() {
        val current = _uiState.value
        val caps = capabilityProvider.inspect()
        val req = computeAuthRequired(current.config, caps)
        if (!req) {
            _messages.tryEmit("Configure a lock method in App Lock settings first.")
            return
        }
        hasAutoPromptedThisLockCycle = false
        val step = resolveInitialStep(current.config, caps, bioLockedOut = false)
        val autoBio = step == AuthStepMode.BIOMETRIC_PROMPT &&
            current.config.isBiometricEnabled &&
            caps.canUseBiometric
        _uiState.update {
            it.copy(
                capabilities = caps,
                isAuthRequired = true,
                isUnlocked = false,
                isBiometricLockedOut = false,
                activeStep = step,
                shouldAutoLaunchBiometric = autoBio,
                errorMessage = null,
                statusMessage = null,
            )
        }
    }

    fun onBiometricPromptStarted() {
        hasAutoPromptedThisLockCycle = true
        _uiState.update {
            it.copy(
                shouldAutoLaunchBiometric = false,
                isExternalPromptInFlight = true,
                errorMessage = null,
            )
        }
    }

    fun onBiometricOutcome(outcome: BiometricOutcome) {
        when (outcome) {
            is BiometricOutcome.Success -> {
                _uiState.update {
                    it.copy(
                        isExternalPromptInFlight = false,
                        isUnlocked = true,
                        isBiometricLockedOut = false,
                        shouldAutoLaunchBiometric = false,
                        errorMessage = null,
                        statusMessage = null,
                    )
                }
                scope.launch { repository.resetFailedAttempts() }
            }

            is BiometricOutcome.FailedAttempt -> {
                // System prompt remains open until max attempts or user cancel; do NOT unlock.
                _uiState.update {
                    it.copy(
                        errorMessage = "Biometric not recognized. Try again or use another unlock option.",
                    )
                }
            }

            is BiometricOutcome.Cancelled -> {
                val state = _uiState.value
                val nextStep = resolveFallbackStepAfterBiometricFailure(state.config, state.capabilities)
                _uiState.update {
                    it.copy(
                        isExternalPromptInFlight = false,
                        isUnlocked = false,
                        shouldAutoLaunchBiometric = false,
                        activeStep = nextStep,
                        errorMessage = "Biometric authentication cancelled. Choose an alternative method.",
                    )
                }
            }

            is BiometricOutcome.TemporaryLockout -> {
                val state = _uiState.value
                val nextStep = resolveFallbackStepAfterBiometricFailure(state.config, state.capabilities)
                _uiState.update {
                    it.copy(
                        isExternalPromptInFlight = false,
                        isUnlocked = false,
                        isBiometricLockedOut = true,
                        shouldAutoLaunchBiometric = false,
                        activeStep = nextStep,
                        errorMessage = "Biometrics temporarily locked: ${outcome.message}. Use device lock or app credentials.",
                    )
                }
            }

            is BiometricOutcome.PermanentLockout -> {
                val state = _uiState.value
                val nextStep = resolveFallbackStepAfterBiometricFailure(state.config, state.capabilities)
                _uiState.update {
                    it.copy(
                        isExternalPromptInFlight = false,
                        isUnlocked = false,
                        isBiometricLockedOut = true,
                        shouldAutoLaunchBiometric = false,
                        activeStep = nextStep,
                        errorMessage = "Biometrics locked by system: ${outcome.message}. Please unlock with device screen lock or app PIN/password.",
                    )
                }
            }

            is BiometricOutcome.Unavailable -> {
                val caps = capabilityProvider.inspect()
                val state = _uiState.value
                val nextStep = resolveFallbackStepAfterBiometricFailure(state.config, caps)
                _uiState.update {
                    it.copy(
                        isExternalPromptInFlight = false,
                        isUnlocked = false,
                        capabilities = caps,
                        shouldAutoLaunchBiometric = false,
                        activeStep = nextStep,
                        errorMessage = outcome.message,
                    )
                }
            }

            is BiometricOutcome.Error -> {
                val state = _uiState.value
                val nextStep = resolveFallbackStepAfterBiometricFailure(state.config, state.capabilities)
                _uiState.update {
                    it.copy(
                        isExternalPromptInFlight = false,
                        isUnlocked = false,
                        shouldAutoLaunchBiometric = false,
                        activeStep = nextStep,
                        errorMessage = outcome.message,
                    )
                }
            }
        }
    }

    fun onDeviceCredentialStarted() {
        _uiState.update {
            it.copy(
                isExternalPromptInFlight = true,
                shouldAutoLaunchBiometric = false,
                errorMessage = null,
            )
        }
    }

    fun onDeviceCredentialOutcome(success: Boolean, message: String? = null) {
        if (success) {
            _uiState.update {
                it.copy(
                    isExternalPromptInFlight = false,
                    isUnlocked = true,
                    isBiometricLockedOut = false,
                    errorMessage = null,
                    statusMessage = null,
                )
            }
            scope.launch { repository.resetFailedAttempts() }
        } else {
            _uiState.update {
                it.copy(
                    isExternalPromptInFlight = false,
                    isUnlocked = false,
                    errorMessage = message ?: "Device authentication cancelled. Try again or use App PIN / Password.",
                )
            }
        }
    }

    fun selectAuthStep(step: AuthStepMode) {
        _uiState.update {
            it.copy(
                activeStep = step,
                errorMessage = null,
                statusMessage = null,
            )
        }
    }

    fun submitAppPin(pin: CharArray) {
        if (_uiState.value.isVerifying) {
            AppLockCryptoManager.wipe(pin)
            return
        }
        scope.launch {
            _uiState.update { it.copy(isVerifying = true, errorMessage = null) }
            when (val res = repository.verifyAppPin(pin)) {
                is CredentialVerifyResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            isUnlocked = true,
                            isBiometricLockedOut = false,
                            errorMessage = null,
                        )
                    }
                }
                is CredentialVerifyResult.Invalid -> {
                    val msg = if (res.lockoutSeconds > 0L) {
                        "Too many incorrect PIN attempts. Locked for ${res.lockoutSeconds}s."
                    } else {
                        "Incorrect PIN. ${res.attemptsBeforeLockout} attempt${if (res.attemptsBeforeLockout == 1) "" else "s"} remaining before cooldown."
                    }
                    _uiState.update { it.copy(isVerifying = false, errorMessage = msg) }
                }
                is CredentialVerifyResult.LockedOut -> {
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            errorMessage = "PIN locked. Try again in ${res.remainingSeconds}s or use another unlock method.",
                        )
                    }
                }
                is CredentialVerifyResult.NotConfigured -> {
                    _uiState.update {
                        it.copy(isVerifying = false, errorMessage = "App PIN is not configured.")
                    }
                }
            }
        }
    }

    fun submitAppPassword(password: CharArray) {
        if (_uiState.value.isVerifying) {
            AppLockCryptoManager.wipe(password)
            return
        }
        scope.launch {
            _uiState.update { it.copy(isVerifying = true, errorMessage = null) }
            when (val res = repository.verifyAppPassword(password)) {
                is CredentialVerifyResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            isUnlocked = true,
                            isBiometricLockedOut = false,
                            errorMessage = null,
                        )
                    }
                }
                is CredentialVerifyResult.Invalid -> {
                    val msg = if (res.lockoutSeconds > 0L) {
                        "Too many incorrect password attempts. Locked for ${res.lockoutSeconds}s."
                    } else {
                        "Incorrect password. ${res.attemptsBeforeLockout} attempt${if (res.attemptsBeforeLockout == 1) "" else "s"} remaining before cooldown."
                    }
                    _uiState.update { it.copy(isVerifying = false, errorMessage = msg) }
                }
                is CredentialVerifyResult.LockedOut -> {
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            errorMessage = "Password locked. Try again in ${res.remainingSeconds}s or use another unlock method.",
                        )
                    }
                }
                is CredentialVerifyResult.NotConfigured -> {
                    _uiState.update {
                        it.copy(isVerifying = false, errorMessage = "App Password is not configured.")
                    }
                }
            }
        }
    }

    // --- Settings Actions ---

    fun setAppLockEnabled(enabled: Boolean) {
        scope.launch {
            val caps = capabilityProvider.inspect()
            val cfg = repository.getConfig()
            if (enabled && !cfg.hasAnyEnabledMethod(caps)) {
                _messages.emit("No lock method ready. Configure an App PIN, App Password, or enable a device lock first.")
                return@launch
            }
            repository.setAppLockEnabled(enabled)
            _messages.emit(if (enabled) "App Lock enabled" else "App Lock disabled")
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        scope.launch {
            repository.setBiometricEnabled(enabled)
            _messages.emit(if (enabled) "Biometric unlock enabled" else "Biometric unlock disabled")
        }
    }

    fun setDeviceCredentialEnabled(enabled: Boolean) {
        scope.launch {
            repository.setDeviceCredentialEnabled(enabled)
            _messages.emit(if (enabled) "Device screen lock fallback enabled" else "Device screen lock fallback disabled")
        }
    }

    fun setPreferredMethod(method: PreferredAuthMethod) {
        scope.launch {
            repository.setPreferredMethod(method)
            _messages.emit("Preferred unlock method: ${method.label}")
        }
    }

    suspend fun verifyCurrentPin(pin: CharArray): Boolean {
        return repository.verifyAppPin(pin) is CredentialVerifyResult.Success
    }

    suspend fun verifyCurrentPassword(password: CharArray): Boolean {
        return repository.verifyAppPassword(password) is CredentialVerifyResult.Success
    }

    fun saveAppPin(pin: CharArray) {
        scope.launch {
            repository.setAppPin(pin)
            _messages.emit("App PIN saved securely")
        }
    }

    fun removeAppPin() {
        scope.launch {
            repository.clearAppPin()
            val caps = capabilityProvider.inspect()
            val cfg = repository.getConfig()
            if (cfg.isAppLockEnabled && !cfg.hasAnyEnabledMethod(caps)) {
                repository.setAppLockEnabled(false)
                _messages.emit("App PIN removed and App Lock disabled (no remaining lock method)")
            } else {
                _messages.emit("App PIN removed")
            }
        }
    }

    fun saveAppPassword(password: CharArray) {
        scope.launch {
            repository.setAppPassword(password)
            _messages.emit("App Password saved securely")
        }
    }

    fun removeAppPassword() {
        scope.launch {
            repository.clearAppPassword()
            val caps = capabilityProvider.inspect()
            val cfg = repository.getConfig()
            if (cfg.isAppLockEnabled && !cfg.hasAnyEnabledMethod(caps)) {
                repository.setAppLockEnabled(false)
                _messages.emit("App Password removed and App Lock disabled (no remaining lock method)")
            } else {
                _messages.emit("App Password removed")
            }
        }
    }

    private fun computeAuthRequired(config: AppLockConfig, caps: DeviceAuthCapabilities): Boolean {
        if (config.isTampered) return true
        return config.isAppLockEnabled && config.hasAnyEnabledMethod(caps)
    }

    private fun resolveInitialStep(
        config: AppLockConfig,
        caps: DeviceAuthCapabilities,
        bioLockedOut: Boolean,
    ): AuthStepMode {
        val bioReady = config.isBiometricEnabled && caps.canUseBiometric && !bioLockedOut
        val devReady = config.isDeviceCredentialEnabled && caps.hasDeviceCredential
        val pinReady = config.hasAppPin
        val pwdReady = config.hasAppPassword

        return when (config.preferredMethod) {
            PreferredAuthMethod.BIOMETRIC_FIRST -> when {
                bioReady -> AuthStepMode.BIOMETRIC_PROMPT
                devReady -> AuthStepMode.FALLBACK_CHOOSER
                pinReady -> AuthStepMode.APP_PIN_ENTRY
                pwdReady -> AuthStepMode.APP_PASSWORD_ENTRY
                else -> AuthStepMode.FALLBACK_CHOOSER
            }
            PreferredAuthMethod.DEVICE_CREDENTIAL -> when {
                devReady -> AuthStepMode.FALLBACK_CHOOSER
                bioReady -> AuthStepMode.BIOMETRIC_PROMPT
                pinReady -> AuthStepMode.APP_PIN_ENTRY
                pwdReady -> AuthStepMode.APP_PASSWORD_ENTRY
                else -> AuthStepMode.FALLBACK_CHOOSER
            }
            PreferredAuthMethod.APP_PIN -> when {
                pinReady -> AuthStepMode.APP_PIN_ENTRY
                bioReady -> AuthStepMode.BIOMETRIC_PROMPT
                devReady -> AuthStepMode.FALLBACK_CHOOSER
                pwdReady -> AuthStepMode.APP_PASSWORD_ENTRY
                else -> AuthStepMode.FALLBACK_CHOOSER
            }
            PreferredAuthMethod.APP_PASSWORD -> when {
                pwdReady -> AuthStepMode.APP_PASSWORD_ENTRY
                bioReady -> AuthStepMode.BIOMETRIC_PROMPT
                devReady -> AuthStepMode.FALLBACK_CHOOSER
                pinReady -> AuthStepMode.APP_PIN_ENTRY
                else -> AuthStepMode.FALLBACK_CHOOSER
            }
        }
    }

    private fun resolveFallbackStepAfterBiometricFailure(
        config: AppLockConfig,
        caps: DeviceAuthCapabilities,
    ): AuthStepMode {
        return when {
            config.isDeviceCredentialEnabled && caps.hasDeviceCredential -> AuthStepMode.FALLBACK_CHOOSER
            config.hasAppPin -> AuthStepMode.APP_PIN_ENTRY
            config.hasAppPassword -> AuthStepMode.APP_PASSWORD_ENTRY
            else -> AuthStepMode.FALLBACK_CHOOSER
        }
    }

    private fun ensureValidStep(
        currentStep: AuthStepMode,
        config: AppLockConfig,
        caps: DeviceAuthCapabilities,
        bioLockedOut: Boolean,
    ): AuthStepMode {
        return when (currentStep) {
            AuthStepMode.APP_PIN_ENTRY -> if (config.hasAppPin) currentStep else resolveInitialStep(config, caps, bioLockedOut)
            AuthStepMode.APP_PASSWORD_ENTRY -> if (config.hasAppPassword) currentStep else resolveInitialStep(config, caps, bioLockedOut)
            AuthStepMode.BIOMETRIC_PROMPT -> if (config.isBiometricEnabled && caps.canUseBiometric && !bioLockedOut) {
                currentStep
            } else {
                resolveFallbackStepAfterBiometricFailure(config, caps)
            }
            AuthStepMode.FALLBACK_CHOOSER -> currentStep
        }
    }

    private fun syncLockoutTicker(lockoutUntilEpochMs: Long) {
        lockoutTickerJob?.cancel()
        val now = timeProvider()
        if (lockoutUntilEpochMs <= now) {
            _uiState.update { it.copy(lockoutRemainingSeconds = 0L) }
            return
        }
        lockoutTickerJob = scope.launch {
            while (true) {
                val currentNow = timeProvider()
                val diffMs = lockoutUntilEpochMs - currentNow
                if (diffMs <= 0L) {
                    _uiState.update { it.copy(lockoutRemainingSeconds = 0L) }
                    break
                }
                val sec = (diffMs + 999L) / 1000L
                _uiState.update { it.copy(lockoutRemainingSeconds = sec) }
                delay(1000L)
            }
        }
    }
}

class AppLockViewModel(application: Application) : AndroidViewModel(application) {
    val controller = AppLockController(
        repository = DataStoreAppLockRepository(application.applicationContext),
        capabilityProvider = AndroidDeviceAuthCapabilityProvider(application.applicationContext),
        scope = viewModelScope,
    )

    val uiState: StateFlow<AppLockUiState> = controller.uiState
    val messages: SharedFlow<String> = controller.messages
}
