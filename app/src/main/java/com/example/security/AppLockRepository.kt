package com.example.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.appLockDataStore: DataStore<Preferences> by preferencesDataStore(name = "stockfolio_app_lock")

data class StoredAppLockRecord(
    val hasExplicitToggle: Boolean = false,
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = true,
    val isDeviceCredentialEnabled: Boolean = true,
    val preferredMethod: PreferredAuthMethod = PreferredAuthMethod.BIOMETRIC_FIRST,
    val pinSalt: String? = null,
    val pinHash: String? = null,
    val passwordSalt: String? = null,
    val passwordHash: String? = null,
    val failedAttempts: Int = 0,
    val lockoutUntilEpochMs: Long = 0L,
) {
    fun toPublicConfig(isTampered: Boolean = false): AppLockConfig = AppLockConfig(
        hasExplicitToggle = hasExplicitToggle,
        isAppLockEnabled = isAppLockEnabled,
        isBiometricEnabled = isBiometricEnabled,
        isDeviceCredentialEnabled = isDeviceCredentialEnabled,
        preferredMethod = preferredMethod,
        hasAppPin = !pinSalt.isNullOrBlank() && !pinHash.isNullOrBlank(),
        hasAppPassword = !passwordSalt.isNullOrBlank() && !passwordHash.isNullOrBlank(),
        failedAttempts = failedAttempts,
        lockoutUntilEpochMs = lockoutUntilEpochMs,
        isTampered = isTampered,
    )

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("hasExplicitToggle", hasExplicitToggle)
        obj.put("isAppLockEnabled", isAppLockEnabled)
        obj.put("isBiometricEnabled", isBiometricEnabled)
        obj.put("isDeviceCredentialEnabled", isDeviceCredentialEnabled)
        obj.put("preferredMethod", preferredMethod.name)
        if (pinSalt != null) obj.put("pinSalt", pinSalt)
        if (pinHash != null) obj.put("pinHash", pinHash)
        if (passwordSalt != null) obj.put("passwordSalt", passwordSalt)
        if (passwordHash != null) obj.put("passwordHash", passwordHash)
        obj.put("failedAttempts", failedAttempts)
        obj.put("lockoutUntilEpochMs", lockoutUntilEpochMs)
        return obj.toString()
    }

    companion object {
        fun fromJson(raw: String): StoredAppLockRecord? {
            return try {
                val obj = JSONObject(raw)
                val prefRaw = obj.optString("preferredMethod", PreferredAuthMethod.BIOMETRIC_FIRST.name)
                val pref = PreferredAuthMethod.entries.firstOrNull { it.name == prefRaw }
                    ?: PreferredAuthMethod.BIOMETRIC_FIRST
                StoredAppLockRecord(
                    hasExplicitToggle = obj.optBoolean("hasExplicitToggle", false),
                    isAppLockEnabled = obj.optBoolean("isAppLockEnabled", false),
                    isBiometricEnabled = obj.optBoolean("isBiometricEnabled", true),
                    isDeviceCredentialEnabled = obj.optBoolean("isDeviceCredentialEnabled", true),
                    preferredMethod = pref,
                    pinSalt = obj.optString("pinSalt").takeIf { it.isNotBlank() },
                    pinHash = obj.optString("pinHash").takeIf { it.isNotBlank() },
                    passwordSalt = obj.optString("passwordSalt").takeIf { it.isNotBlank() },
                    passwordHash = obj.optString("passwordHash").takeIf { it.isNotBlank() },
                    failedAttempts = obj.optInt("failedAttempts", 0).coerceAtLeast(0),
                    lockoutUntilEpochMs = obj.optLong("lockoutUntilEpochMs", 0L).coerceAtLeast(0L),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

interface AppLockRepository {
    val configFlow: Flow<AppLockConfig>
    suspend fun getConfig(): AppLockConfig
    suspend fun initializeDefaultIfNeeded(capabilities: DeviceAuthCapabilities): AppLockConfig
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setDeviceCredentialEnabled(enabled: Boolean)
    suspend fun setPreferredMethod(method: PreferredAuthMethod)
    suspend fun setAppPin(pin: CharArray)
    suspend fun clearAppPin()
    suspend fun setAppPassword(password: CharArray)
    suspend fun clearAppPassword()
    suspend fun verifyAppPin(attempt: CharArray): CredentialVerifyResult
    suspend fun verifyAppPassword(attempt: CharArray): CredentialVerifyResult
    suspend fun resetFailedAttempts()
}

class DataStoreAppLockRepository(
    private val context: Context,
    private val cryptoManager: AppLockCryptoManager = AppLockCryptoManager(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) : AppLockRepository {

    private val encryptedBlobKey = stringPreferencesKey("encrypted_applock_blob")
    private val lockProvisionedKey = booleanPreferencesKey("applock_provisioned_flag")

    override val configFlow: Flow<AppLockConfig> = context.appLockDataStore.data.map { prefs ->
        decodeConfig(prefs)
    }

    override suspend fun getConfig(): AppLockConfig {
        val prefs = context.appLockDataStore.data.first()
        return decodeConfig(prefs)
    }

    override suspend fun initializeDefaultIfNeeded(capabilities: DeviceAuthCapabilities): AppLockConfig {
        var resultConfig = getConfig()
        if (!resultConfig.hasExplicitToggle && !resultConfig.isTampered) {
            val autoEnable = capabilities.hasAnyDeviceLock
            updateRecord { current ->
                if (current.hasExplicitToggle) {
                    current
                } else {
                    current.copy(
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
            resultConfig = getConfig()
        }
        return resultConfig
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        updateRecord { current ->
            current.copy(
                hasExplicitToggle = true,
                isAppLockEnabled = enabled,
            )
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        updateRecord { current ->
            current.copy(isBiometricEnabled = enabled)
        }
    }

    override suspend fun setDeviceCredentialEnabled(enabled: Boolean) {
        updateRecord { current ->
            current.copy(isDeviceCredentialEnabled = enabled)
        }
    }

    override suspend fun setPreferredMethod(method: PreferredAuthMethod) {
        updateRecord { current ->
            current.copy(preferredMethod = method)
        }
    }

    override suspend fun setAppPin(pin: CharArray) {
        try {
            val hashed = cryptoManager.hashSecret(pin)
            updateRecord { current ->
                current.copy(
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
        updateRecord { current ->
            val nextPref = if (current.preferredMethod == PreferredAuthMethod.APP_PIN) {
                PreferredAuthMethod.BIOMETRIC_FIRST
            } else {
                current.preferredMethod
            }
            current.copy(
                pinSalt = null,
                pinHash = null,
                preferredMethod = nextPref,
            )
        }
    }

    override suspend fun setAppPassword(password: CharArray) {
        try {
            val hashed = cryptoManager.hashSecret(password)
            updateRecord { current ->
                current.copy(
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
        updateRecord { current ->
            val nextPref = if (current.preferredMethod == PreferredAuthMethod.APP_PASSWORD) {
                PreferredAuthMethod.BIOMETRIC_FIRST
            } else {
                current.preferredMethod
            }
            current.copy(
                passwordSalt = null,
                passwordHash = null,
                preferredMethod = nextPref,
            )
        }
    }

    override suspend fun verifyAppPin(attempt: CharArray): CredentialVerifyResult {
        return verifyStoredCredential(
            attempt = attempt,
            saltSelector = { it.pinSalt },
            hashSelector = { it.pinHash },
        )
    }

    override suspend fun verifyAppPassword(attempt: CharArray): CredentialVerifyResult {
        return verifyStoredCredential(
            attempt = attempt,
            saltSelector = { it.passwordSalt },
            hashSelector = { it.passwordHash },
        )
    }

    override suspend fun resetFailedAttempts() {
        updateRecord { current ->
            if (current.failedAttempts == 0 && current.lockoutUntilEpochMs == 0L) {
                current
            } else {
                current.copy(failedAttempts = 0, lockoutUntilEpochMs = 0L)
            }
        }
    }

    private suspend fun verifyStoredCredential(
        attempt: CharArray,
        saltSelector: (StoredAppLockRecord) -> String?,
        hashSelector: (StoredAppLockRecord) -> String?,
    ): CredentialVerifyResult {
        return try {
            val now = timeProvider()
            val prefs = context.appLockDataStore.data.first()
            val record = decodeRecord(prefs)
                ?: return CredentialVerifyResult.LockedOut(30L, now + 30_000L)

            if (record.lockoutUntilEpochMs > now) {
                val remaining = ((record.lockoutUntilEpochMs - now + 999L) / 1000L).coerceAtLeast(1L)
                return CredentialVerifyResult.LockedOut(remaining, record.lockoutUntilEpochMs)
            }

            val salt = saltSelector(record)
            val expectedHash = hashSelector(record)
            if (salt.isNullOrBlank() || expectedHash.isNullOrBlank()) {
                return CredentialVerifyResult.NotConfigured
            }

            val matched = cryptoManager.verifySecret(attempt, salt, expectedHash)
            if (matched) {
                updateRecord { current ->
                    current.copy(failedAttempts = 0, lockoutUntilEpochMs = 0L)
                }
                CredentialVerifyResult.Success
            } else {
                val nextAttempts = record.failedAttempts + 1
                val lockoutSec = computeLockoutSeconds(nextAttempts)
                val lockoutUntil = if (lockoutSec > 0L) now + lockoutSec * 1000L else 0L
                updateRecord { current ->
                    current.copy(
                        failedAttempts = nextAttempts,
                        lockoutUntilEpochMs = lockoutUntil,
                    )
                }
                val attemptsBeforeLockout = (MAX_FREE_ATTEMPTS - nextAttempts).coerceAtLeast(0)
                CredentialVerifyResult.Invalid(
                    failedAttempts = nextAttempts,
                    attemptsBeforeLockout = attemptsBeforeLockout,
                    lockoutSeconds = lockoutSec,
                    lockoutUntilEpochMs = lockoutUntil,
                )
            }
        } finally {
            AppLockCryptoManager.wipe(attempt)
        }
    }

    private fun decodeConfig(prefs: Preferences): AppLockConfig {
        val blob = prefs[encryptedBlobKey]
        val provisioned = prefs[lockProvisionedKey] ?: false
        if (blob == null) {
            return if (provisioned) {
                AppLockConfig(isAppLockEnabled = true, isTampered = true)
            } else {
                AppLockConfig()
            }
        }
        val plain = cryptoManager.unsealState(blob)
            ?: return AppLockConfig(isAppLockEnabled = true, isTampered = true)
        val record = StoredAppLockRecord.fromJson(plain)
            ?: return AppLockConfig(isAppLockEnabled = true, isTampered = true)
        return record.toPublicConfig(isTampered = false)
    }

    private fun decodeRecord(prefs: Preferences): StoredAppLockRecord? {
        val blob = prefs[encryptedBlobKey] ?: return StoredAppLockRecord()
        val plain = cryptoManager.unsealState(blob) ?: return null
        return StoredAppLockRecord.fromJson(plain)
    }

    private suspend fun updateRecord(transform: (StoredAppLockRecord) -> StoredAppLockRecord) {
        context.appLockDataStore.edit { prefs ->
            val current = decodeRecord(prefs) ?: StoredAppLockRecord(
                hasExplicitToggle = true,
                isAppLockEnabled = true,
            )
            val updated = transform(current)
            val sealed = cryptoManager.sealState(updated.toJson())
            prefs[encryptedBlobKey] = sealed
            prefs[lockProvisionedKey] = updated.isAppLockEnabled ||
                !updated.pinHash.isNullOrBlank() ||
                !updated.passwordHash.isNullOrBlank()
        }
    }

    companion object {
        const val MAX_FREE_ATTEMPTS = 3

        fun computeLockoutSeconds(failedAttempts: Int): Long = when {
            failedAttempts < MAX_FREE_ATTEMPTS -> 0L
            failedAttempts == MAX_FREE_ATTEMPTS -> 15L
            failedAttempts == MAX_FREE_ATTEMPTS + 1 -> 30L
            failedAttempts == MAX_FREE_ATTEMPTS + 2 -> 60L
            else -> 300L
        }
    }
}
