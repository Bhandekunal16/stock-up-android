package com.example

import android.content.Context
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.security.AppLockConfig
import com.example.security.AppLockCryptoManager
import com.example.security.AppLockRepository
import com.example.security.AuthStepMode
import com.example.security.BiometricAvailability
import com.example.security.BiometricOutcome
import com.example.security.CredentialVerifyResult
import com.example.security.DataStoreAppLockRepository
import com.example.security.DeviceAuthCapabilities
import com.example.security.DeviceAuthCapabilityProvider
import com.example.security.PreferredAuthMethod
import com.example.security.StateSealer
import com.example.security.StoredAppLockRecord
import com.example.ui.screens.AppLockScreen
import com.example.ui.theme.StockFolioTheme
import com.example.ui.viewmodel.AppLockController
import com.example.ui.viewmodel.AppLockUiState
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class InMemoryAesGcmSealer : StateSealer {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val random = SecureRandom()

    override fun seal(plainText: String): String {
        val iv = ByteArray(12).apply { random.nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return "v1:${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
    }

    override fun unseal(cipherPayload: String): String? {
        val parts = cipherPayload.split(":")
        if (parts.size != 3 || parts[0] != "v1") return null
        return try {
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ct = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}

private class FakeAppLockRepository(
    private val cryptoManager: AppLockCryptoManager,
    private val timeProvider: () -> Long,
) : AppLockRepository {
    private var sealedBlob: String? = null
    private var provisioned: Boolean = false
    private val _configFlow = MutableStateFlow(AppLockConfig())
    override val configFlow: Flow<AppLockConfig> = _configFlow.asStateFlow()

    override suspend fun getConfig(): AppLockConfig {
        val blob = sealedBlob ?: return if (provisioned) AppLockConfig(isAppLockEnabled = true, isTampered = true) else AppLockConfig()
        val plain = cryptoManager.unsealState(blob) ?: return AppLockConfig(isAppLockEnabled = true, isTampered = true)
        val record = StoredAppLockRecord.fromJson(plain) ?: return AppLockConfig(isAppLockEnabled = true, isTampered = true)
        return record.toPublicConfig(isTampered = false)
    }

    private fun readRecord(): StoredAppLockRecord? {
        val blob = sealedBlob ?: return StoredAppLockRecord()
        val plain = cryptoManager.unsealState(blob) ?: return null
        return StoredAppLockRecord.fromJson(plain)
    }

    private fun writeRecord(transform: (StoredAppLockRecord) -> StoredAppLockRecord) {
        val current = readRecord() ?: StoredAppLockRecord(hasExplicitToggle = true, isAppLockEnabled = true)
        val updated = transform(current)
        sealedBlob = cryptoManager.sealState(updated.toJson())
        provisioned = updated.isAppLockEnabled || !updated.pinHash.isNullOrBlank() || !updated.passwordHash.isNullOrBlank()
        _configFlow.value = updated.toPublicConfig(isTampered = false)
    }

    override suspend fun initializeDefaultIfNeeded(capabilities: DeviceAuthCapabilities): AppLockConfig {
        val cfg = getConfig()
        if (!cfg.hasExplicitToggle && !cfg.isTampered) {
            val autoEnable = capabilities.hasAnyDeviceLock
            writeRecord { current ->
                if (current.hasExplicitToggle) current else current.copy(
                    isAppLockEnabled = autoEnable,
                    preferredMethod = if (capabilities.canUseBiometric) {
                        PreferredAuthMethod.BIOMETRIC_FIRST
                    } else if (capabilities.hasDeviceCredential) {
                        PreferredAuthMethod.DEVICE_CREDENTIAL
                    } else {
                        PreferredAuthMethod.BIOMETRIC_FIRST
                    },
                )
            }
        }
        return getConfig()
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        writeRecord { it.copy(hasExplicitToggle = true, isAppLockEnabled = enabled) }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        writeRecord { it.copy(isBiometricEnabled = enabled) }
    }

    override suspend fun setDeviceCredentialEnabled(enabled: Boolean) {
        writeRecord { it.copy(isDeviceCredentialEnabled = enabled) }
    }

    override suspend fun setPreferredMethod(method: PreferredAuthMethod) {
        writeRecord { it.copy(preferredMethod = method) }
    }

    override suspend fun setAppPin(pin: CharArray) {
        try {
            val hashed = cryptoManager.hashSecret(pin)
            writeRecord {
                it.copy(
                    hasExplicitToggle = true,
                    isAppLockEnabled = true,
                    pinSalt = hashed.saltBase64,
                    pinHash = hashed.hashBase64,
                    failedAttempts = 0,
                    lockoutUntilEpochMs = 0L,
                )
            }
        } finally {
            AppLockCryptoManager.wipe(pin)
        }
    }

    override suspend fun clearAppPin() {
        writeRecord {
            it.copy(
                pinSalt = null,
                pinHash = null,
                preferredMethod = if (it.preferredMethod == PreferredAuthMethod.APP_PIN) {
                    PreferredAuthMethod.BIOMETRIC_FIRST
                } else {
                    it.preferredMethod
                },
            )
        }
    }

    override suspend fun setAppPassword(password: CharArray) {
        try {
            val hashed = cryptoManager.hashSecret(password)
            writeRecord {
                it.copy(
                    hasExplicitToggle = true,
                    isAppLockEnabled = true,
                    passwordSalt = hashed.saltBase64,
                    passwordHash = hashed.hashBase64,
                    failedAttempts = 0,
                    lockoutUntilEpochMs = 0L,
                )
            }
        } finally {
            AppLockCryptoManager.wipe(password)
        }
    }

    override suspend fun clearAppPassword() {
        writeRecord {
            it.copy(
                passwordSalt = null,
                passwordHash = null,
                preferredMethod = if (it.preferredMethod == PreferredAuthMethod.APP_PASSWORD) {
                    PreferredAuthMethod.BIOMETRIC_FIRST
                } else {
                    it.preferredMethod
                },
            )
        }
    }

    override suspend fun verifyAppPin(attempt: CharArray): CredentialVerifyResult =
        verify(attempt, { it.pinSalt }, { it.pinHash })

    override suspend fun verifyAppPassword(attempt: CharArray): CredentialVerifyResult =
        verify(attempt, { it.passwordSalt }, { it.passwordHash })

    override suspend fun resetFailedAttempts() {
        writeRecord { it.copy(failedAttempts = 0, lockoutUntilEpochMs = 0L) }
    }

    private fun verify(
        attempt: CharArray,
        saltSel: (StoredAppLockRecord) -> String?,
        hashSel: (StoredAppLockRecord) -> String?,
    ): CredentialVerifyResult {
        return try {
            val now = timeProvider()
            val record = readRecord() ?: return CredentialVerifyResult.LockedOut(30L, now + 30_000L)
            if (record.lockoutUntilEpochMs > now) {
                val rem = ((record.lockoutUntilEpochMs - now + 999L) / 1000L).coerceAtLeast(1L)
                return CredentialVerifyResult.LockedOut(rem, record.lockoutUntilEpochMs)
            }
            val salt = saltSel(record)
            val hash = hashSel(record)
            if (salt.isNullOrBlank() || hash.isNullOrBlank()) return CredentialVerifyResult.NotConfigured
            if (cryptoManager.verifySecret(attempt, salt, hash)) {
                writeRecord { it.copy(failedAttempts = 0, lockoutUntilEpochMs = 0L) }
                CredentialVerifyResult.Success
            } else {
                val nextAttempts = record.failedAttempts + 1
                val lockoutSec = DataStoreAppLockRepository.computeLockoutSeconds(nextAttempts)
                val lockoutUntil = if (lockoutSec > 0L) now + lockoutSec * 1000L else 0L
                writeRecord { it.copy(failedAttempts = nextAttempts, lockoutUntilUntilMs = lockoutUntil) }
                CredentialVerifyResult.Invalid(
                    failedAttempts = nextAttempts,
                    attemptsBeforeLockout = (DataStoreAppLockRepository.MAX_FREE_ATTEMPTS - nextAttempts).coerceAtLeast(0),
                    lockoutSeconds = lockoutSec,
                    lockoutUntilEpochMs = lockoutUntil,
                )
            }
        } finally {
            AppLockCryptoManager.wipe(attempt)
        }
    }

    private fun StoredAppLockRecord.copy(failedAttempts: Int, lockoutUntilUntilMs: Long): StoredAppLockRecord =
        this.copy(failedAttempts = failedAttempts, lockoutUntilEpochMs = lockoutUntilUntilMs)

    fun tamperBlobForTest() {
        sealedBlob = "v1:invalid:tampered"
        _configFlow.value = AppLockConfig(isAppLockEnabled = true, isTampered = true)
    }
}

private class FakeCapabilityProvider(
    var capabilities: DeviceAuthCapabilities,
) : DeviceAuthCapabilityProvider {
    override fun inspect(): DeviceAuthCapabilities = capabilities
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLockTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createCrypto() = AppLockCryptoManager(stateSealer = InMemoryAesGcmSealer(), iterations = 1_000)

    @Test
    fun `scenario 1 and 2 - biometric available auto prompts first and unlocks on success`() = runTest {
        val nowProvider = { 10_000L + testScheduler.currentTime }
        val repo = FakeAppLockRepository(createCrypto(), nowProvider)
        val caps = FakeCapabilityProvider(
            DeviceAuthCapabilities(
                biometricAvailability = BiometricAvailability.AVAILABLE,
                hasDeviceCredential = true,
            ),
        )
        val controller = AppLockController(repo, caps, backgroundScope, nowProvider)
        runCurrent()

        val initial = controller.uiState.value
        assertTrue(initial.isInitialized)
        assertTrue(initial.isAuthRequired)
        assertFalse(initial.isUnlocked)
        assertEquals(AuthStepMode.BIOMETRIC_PROMPT, initial.activeStep)
        assertTrue(initial.shouldAutoLaunchBiometric)

        controller.onBiometricPromptStarted()
        assertFalse(controller.uiState.value.shouldAutoLaunchBiometric)
        assertTrue(controller.uiState.value.isExternalPromptInFlight)

        controller.onBiometricOutcome(BiometricOutcome.Success)
        runCurrent()
        assertTrue(controller.uiState.value.isUnlocked)
        assertFalse(controller.uiState.value.isExternalPromptInFlight)
    }

    @Test
    fun `scenario 3 - biometric disabled in settings skips biometric prompt and uses fallback`() = runTest {
        val nowProvider = { 10_000L + testScheduler.currentTime }
        val repo = FakeAppLockRepository(createCrypto(), nowProvider)
        repo.setAppLockEnabled(true)
        repo.setBiometricEnabled(false)
        repo.setAppPin("4829".toCharArray())

        val caps = FakeCapabilityProvider(
            DeviceAuthCapabilities(
                biometricAvailability = BiometricAvailability.AVAILABLE,
                hasDeviceCredential = true,
            ),
        )
        val controller = AppLockController(repo, caps, backgroundScope, nowProvider)
        runCurrent()

        val state = controller.uiState.value
        assertTrue(state.isAuthRequired)
        assertFalse(state.isUnlocked)
        assertFalse(state.shouldAutoLaunchBiometric)
        assertNotEquals(AuthStepMode.BIOMETRIC_PROMPT, state.activeStep)
        assertFalse(state.canUseBiometricNow)
        assertTrue(state.canUseDeviceCredentialNow)
        assertTrue(state.canUseAppPinNow)
    }

    @Test
    fun `scenario 4 and 5 - no biometric enrolled or no hardware falls back to device credential`() = runTest {
        val nowProvider = { 10_000L + testScheduler.currentTime }
        val repo = FakeAppLockRepository(createCrypto(), nowProvider)
        val caps = FakeCapabilityProvider(
            DeviceAuthCapabilities(
                biometricAvailability = BiometricAvailability.NONE_ENROLLED,
                hasDeviceCredential = true,
            ),
        )
        val controller = AppLockController(repo, caps, backgroundScope, nowProvider)
        runCurrent()

        val state = controller.uiState.value
        assertTrue(state.isAuthRequired)
        assertFalse(state.isUnlocked)
        assertFalse(state.shouldAutoLaunchBiometric)
        assertEquals(AuthStepMode.FALLBACK_CHOOSER, state.activeStep)
        assertTrue(state.canUseDeviceCredentialNow)

        controller.onDeviceCredentialStarted()
        assertTrue(controller.uiState.value.isExternalPromptInFlight)
        controller.onDeviceCredentialOutcome(success = true)
        runCurrent()
        assertTrue(controller.uiState.value.isUnlocked)
    }

    @Test
    fun `scenario 6 - device without lock and no app credentials unlocks directly until app PIN set`() = runTest {
        val nowProvider = { 10_000L + testScheduler.currentTime }
        val repo = FakeAppLockRepository(createCrypto(), nowProvider)
        val caps = FakeCapabilityProvider(
            DeviceAuthCapabilities(
                biometricAvailability = BiometricAvailability.NO_HARDWARE,
                hasDeviceCredential = false,
            ),
        )
        val controller = AppLockController(repo, caps, backgroundScope, nowProvider)
        runCurrent()

        assertFalse(controller.uiState.value.isAuthRequired)
        assertTrue(controller.uiState.value.isUnlocked)

        // Setting App PIN activates App Lock automatically
        controller.saveAppPin("1357".toCharArray())
        runCurrent()
        assertTrue(controller.uiState.value.isAuthRequired)
        controller.lockNow()
        assertFalse(controller.uiState.value.isUnlocked)
        assertEquals(AuthStepMode.APP_PIN_ENTRY, controller.uiState.value.activeStep)
    }

    @Test
    fun `scenario 7 - incorrect app PIN enforces progressive lockouts and wipes buffer`() = runTest {
        val nowProvider = { 10_000L + testScheduler.currentTime }
        val crypto = createCrypto()
        val repo = FakeAppLockRepository(crypto, nowProvider)
        val pinInput = "7391".toCharArray()
        repo.setAppPin(pinInput)
        assertTrue(pinInput.all { it == '\u0000' })

        val caps = FakeCapabilityProvider(DeviceAuthCapabilities(BiometricAvailability.NO_HARDWARE, false))
        val controller = AppLockController(repo, caps, backgroundScope, nowProvider)
        runCurrent()

        assertFalse(controller.uiState.value.isUnlocked)

        // Attempt 1 & 2 wrong (no lockout yet)
        controller.submitAppPin("0000".toCharArray())
        runCurrent()
        assertEquals(1, controller.uiState.value.config.failedAttempts)
        assertEquals(0L, controller.uiState.value.lockoutRemainingSeconds)

        controller.submitAppPin("1111".toCharArray())
        runCurrent()
        assertEquals(2, controller.uiState.value.config.failedAttempts)
        assertEquals(0L, controller.uiState.value.lockoutRemainingSeconds)

        // Attempt 3 wrong -> 15s lockout
        controller.submitAppPin("2222".toCharArray())
        runCurrent()
        assertEquals(3, controller.uiState.value.config.failedAttempts)
        assertEquals(15L, controller.uiState.value.lockoutRemainingSeconds)

        // Even correct PIN rejected while locked out
        controller.submitAppPin("7391".toCharArray())
        runCurrent()
        assertFalse(controller.uiState.value.isUnlocked)

        // Advance time past 15s lockout and fail attempt 4 -> 30s lockout
        advanceTimeBy(16_000L)
        runCurrent()
        assertEquals(0L, controller.uiState.value.lockoutRemainingSeconds)

        controller.submitAppPin("3333".toCharArray())
        runCurrent()
        assertEquals(4, controller.uiState.value.config.failedAttempts)
        assertEquals(30L, controller.uiState.value.lockoutRemainingSeconds)

        // Advance past 30s and enter correct PIN -> unlocks & resets failedAttempts
        advanceTimeBy(31_000L)
        runCurrent()
        controller.submitAppPin("7391".toCharArray())
        runCurrent()
        assertTrue(controller.uiState.value.isUnlocked)
        assertEquals(0, controller.uiState.value.config.failedAttempts)
        assertEquals(0L, controller.uiState.value.lockoutRemainingSeconds)
    }

    @Test
    fun `scenario 8 - incorrect app password enforces lockout and valid password unlocks`() = runTest {
        val nowProvider = { 5_000L + testScheduler.currentTime }
        val repo = FakeAppLockRepository(createCrypto(), nowProvider)
        repo.setAppPassword("Stock#2026".toCharArray())

        val caps = FakeCapabilityProvider(DeviceAuthCapabilities(BiometricAvailability.NO_HARDWARE, false))
        val controller = AppLockController(repo, caps, backgroundScope, nowProvider)
        runCurrent()

        assertEquals(AuthStepMode.APP_PASSWORD_ENTRY, controller.uiState.value.activeStep)

        repeat(3) {
            controller.submitAppPassword("wrong-pass".toCharArray())
            runCurrent()
        }
        assertFalse(controller.uiState.value.isUnlocked)
        assertEquals(15L, controller.uiState.value.lockoutRemainingSeconds)

        advanceTimeBy(16_000L)
        runCurrent()
        controller.submitAppPassword("Stock#2026".toCharArray())
        runCurrent()
        assertTrue(controller.uiState.value.isUnlocked)
        assertEquals(0, controller.uiState.value.config.failedAttempts)
    }

    @Test
    fun `scenario 9 and 10 - biometric cancel and lockouts remain locked and switch to fallback`() = runTest {
        val nowProvider = { 10_000L + testScheduler.currentTime }
        val repo = FakeAppLockRepository(createCrypto(), nowProvider)
        repo.setAppPin("2468".toCharArray())

        val caps = FakeCapabilityProvider(
            DeviceAuthCapabilities(BiometricAvailability.AVAILABLE, hasDeviceCredential = false),
        )
        val controller = AppLockController(repo, caps, backgroundScope, nowProvider)
        runCurrent()

        // Cancel stays locked and switches to PIN fallback
        controller.onBiometricPromptStarted()
        controller.onBiometricOutcome(BiometricOutcome.Cancelled)
        assertFalse(controller.uiState.value.isUnlocked)
        assertEquals(AuthStepMode.APP_PIN_ENTRY, controller.uiState.value.activeStep)

        // Temporary lockout marks biometric locked out
        controller.onBiometricOutcome(BiometricOutcome.TemporaryLockout("Too many attempts"))
        assertFalse(controller.uiState.value.isUnlocked)
        assertTrue(controller.uiState.value.isBiometricLockedOut)
        assertFalse(controller.uiState.value.canUseBiometricNow)

        // Permanent lockout keeps biometric disabled until re-unlocked
        controller.onBiometricOutcome(BiometricOutcome.PermanentLockout("Locked until PIN"))
        assertFalse(controller.uiState.value.isUnlocked)
        assertTrue(controller.uiState.value.isBiometricLockedOut)
    }

    @Test
    fun `scenario 11 - backgrounding locks app while config rotation does not`() = runTest {
        val nowProvider = { 10_000L + testScheduler.currentTime }
        val repo = FakeAppLockRepository(createCrypto(), nowProvider)
        val caps = FakeCapabilityProvider(
            DeviceAuthCapabilities(BiometricAvailability.AVAILABLE, hasDeviceCredential = true),
        )
        val controller = AppLockController(repo, caps, backgroundScope, nowProvider)
        runCurrent()

        controller.onBiometricPromptStarted()
        // System dialog puts Activity into ON_STOP: should NOT clear isExternalPromptInFlight
        controller.onAppBackgrounded(isChangingConfigurations = false)
        assertTrue(controller.uiState.value.isExternalPromptInFlight)

        controller.onBiometricOutcome(BiometricOutcome.Success)
        runCurrent()
        assertTrue(controller.uiState.value.isUnlocked)

        // Rotation (isChangingConfigurations = true) preserves unlocked session
        controller.onAppBackgrounded(isChangingConfigurations = true)
        assertTrue(controller.uiState.value.isUnlocked)

        // Genuine backgrounding relocks immediately and re-arms biometric prompt on foreground
        controller.onAppBackgrounded(isChangingConfigurations = false)
        assertFalse(controller.uiState.value.isUnlocked)
        controller.onAppForegrounded()
        assertFalse(controller.uiState.value.isUnlocked)
        assertTrue(controller.uiState.value.shouldAutoLaunchBiometric)
    }

    @Test
    fun `scenario 12 - restart preserves persisted credentials, lockout, and tamper detection`() = runTest {
        val nowProvider = { 100_000L + testScheduler.currentTime }
        val crypto = createCrypto()
        val repo = FakeAppLockRepository(crypto, nowProvider)
        val caps = FakeCapabilityProvider(DeviceAuthCapabilities(BiometricAvailability.NO_HARDWARE, false))

        // Session 1: configure PIN & trigger lockout
        val controller1 = AppLockController(repo, caps, backgroundScope, nowProvider)
        runCurrent()
        controller1.saveAppPin("5599".toCharArray())
        runCurrent()
        controller1.lockNow()
        repeat(3) {
            controller1.submitAppPin("0000".toCharArray())
            runCurrent()
        }
        assertEquals(15L, controller1.uiState.value.lockoutRemainingSeconds)

        // Session 2: simulated cold restart over same repository
        val controller2 = AppLockController(repo, caps, backgroundScope, nowProvider)
        runCurrent()
        assertTrue(controller2.uiState.value.isAuthRequired)
        assertFalse(controller2.uiState.value.isUnlocked)
        assertTrue(controller2.uiState.value.config.hasAppPin)
        assertEquals(3, controller2.uiState.value.config.failedAttempts)
        assertEquals(15L, controller2.uiState.value.lockoutRemainingSeconds)

        // Tampered sealed state fails closed (remains locked)
        repo.tamperBlobForTest()
        runCurrent()
        assertTrue(controller2.uiState.value.config.isTampered)
        assertTrue(controller2.uiState.value.isAuthRequired)
        assertFalse(controller2.uiState.value.isUnlocked)
    }

    @Test
    fun `scenario 13 - settings changes update method priority and clean disable when last method removed`() = runTest {
        val nowProvider = { 10_000L + testScheduler.currentTime }
        val repo = FakeAppLockRepository(createCrypto(), nowProvider)
        val caps = FakeCapabilityProvider(DeviceAuthCapabilities(BiometricAvailability.NO_HARDWARE, false))
        val controller = AppLockController(repo, caps, backgroundScope, nowProvider)
        runCurrent()

        controller.saveAppPin("1234".toCharArray())
        controller.saveAppPassword("secret99".toCharArray())
        runCurrent()

        controller.setPreferredMethod(PreferredAuthMethod.APP_PASSWORD)
        runCurrent()
        assertEquals(PreferredAuthMethod.APP_PASSWORD, controller.uiState.value.config.preferredMethod)

        controller.removeAppPin()
        runCurrent()
        assertFalse(controller.uiState.value.config.hasAppPin)
        assertTrue(controller.uiState.value.config.isAppLockEnabled)

        // Removing final auth method automatically disables App Lock so user cannot lock themselves out
        controller.removeAppPassword()
        runCurrent()
        assertFalse(controller.uiState.value.config.hasAppPassword)
        assertFalse(controller.uiState.value.config.isAppLockEnabled)
        assertTrue(controller.uiState.value.isUnlocked)
    }

    @Test
    fun `crypto manager produces unique salts and detects tampering`() {
        val crypto = createCrypto()
        val h1 = crypto.hashSecret("2580".toCharArray())
        val h2 = crypto.hashSecret("2580".toCharArray())
        assertNotEquals(h1.saltBase64, h2.saltBase64)
        assertNotEquals(h1.hashBase64, h2.hashBase64)
        assertTrue(crypto.verifySecret("2580".toCharArray(), h1.saltBase64, h1.hashBase64))
        assertFalse(crypto.verifySecret("2581".toCharArray(), h1.saltBase64, h1.hashBase64))

        val sealed = crypto.sealState("{\"isAppLockEnabled\":true}")
        assertNotNull(crypto.unsealState(sealed))
        assertNull(crypto.unsealState(sealed.dropLast(2) + "AA"))
    }

    @Test
    fun `datastore repository persists encrypted config across calls`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = DataStoreAppLockRepository(
            context = context,
            cryptoManager = createCrypto(),
            timeProvider = { 50_000L },
        )
        repo.setAppPin("9087".toCharArray())
        val cfg = repo.getConfig()
        assertTrue(cfg.isAppLockEnabled)
        assertTrue(cfg.hasAppPin)
        assertFalse(cfg.isTampered)
        assertTrue(repo.verifyAppPin("9087".toCharArray()) is CredentialVerifyResult.Success)
    }

    @Test
    fun `app lock screen renders PIN keypad and submits 4 digit PIN`() {
        var submittedPin: String? = null
        val state = AppLockUiState(
            isInitialized = true,
            isUnlocked = false,
            isAuthRequired = true,
            activeStep = AuthStepMode.APP_PIN_ENTRY,
            config = AppLockConfig(isAppLockEnabled = true, hasAppPin = true),
        )

        composeRule.setContent {
            StockFolioTheme {
                AppLockScreen(
                    uiState = state,
                    onLaunchBiometric = {},
                    onLaunchDeviceCredential = {},
                    onSelectStep = {},
                    onSubmitAppPin = { chars -> submittedPin = String(chars) },
                    onSubmitAppPassword = {},
                )
            }
        }

        composeRule.onNodeWithText("Enter App PIN").assertIsDisplayed()
        composeRule.onNodeWithTag("pin_key_4").performScrollTo().performClick()
        composeRule.onNodeWithTag("pin_key_2").performScrollTo().performClick()
        composeRule.onNodeWithTag("pin_key_8").performScrollTo().performClick()
        composeRule.onNodeWithTag("pin_key_6").performScrollTo().performClick()
        composeRule.onNodeWithTag("submit_app_pin_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals("4286", submittedPin)
    }
}
