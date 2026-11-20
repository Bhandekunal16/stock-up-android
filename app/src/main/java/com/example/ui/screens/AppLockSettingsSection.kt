package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.BiometricAvailability
import com.example.security.PreferredAuthMethod
import com.example.ui.components.GraphiteSectionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteAmber
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteGreen
import com.example.ui.theme.GraphiteKpiCard
import com.example.ui.theme.GraphiteKpiDivider
import com.example.ui.theme.GraphiteNavSurface
import com.example.ui.theme.GraphiteOnAccent
import com.example.ui.theme.GraphiteRed
import com.example.ui.theme.GraphiteTextMuted
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.GraphiteTextSecondary
import com.example.ui.viewmodel.AppLockController
import com.example.ui.viewmodel.AppLockUiState
import kotlinx.coroutines.launch

@Composable
fun AppLockSettingsCard(
    uiState: AppLockUiState,
    controller: AppLockController,
) {
    val scope = rememberCoroutineScope()
    var showPinDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    if (showPinDialog) {
        PinSetupDialog(
            hasExistingPin = uiState.config.hasAppPin,
            onVerifyCurrentPin = { controller.verifyCurrentPin(it) },
            onSavePin = {
                controller.saveAppPin(it)
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false },
        )
    }

    if (showPasswordDialog) {
        PasswordSetupDialog(
            hasExistingPassword = uiState.config.hasAppPassword,
            onVerifyCurrentPassword = { controller.verifyCurrentPassword(it) },
            onSavePassword = {
                controller.saveAppPassword(it)
                showPasswordDialog = false
            },
            onDismiss = { showPasswordDialog = false },
        )
    }

    val bioColor = when (uiState.capabilities.biometricAvailability) {
        BiometricAvailability.AVAILABLE -> GraphiteGreen
        BiometricAvailability.NONE_ENROLLED -> GraphiteAmber
        else -> GraphiteTextSecondary
    }

    GraphiteSectionCard(
        title = "App Lock & Security",
        subtitle = "Protect StockFolio with biometrics, device lock, or app credentials",
        headerAction = {
            StatusBadge(
                label = if (uiState.isAuthRequired) "Protected" else "Unlocked",
                color = if (uiState.isAuthRequired) GraphiteGreen else GraphiteTextSecondary,
                modifier = Modifier.testTag("app_lock_status_badge"),
            )
        },
    ) {
        // Read-only Device Capabilities Summary
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = GraphiteNavSurface,
            border = BorderStroke(1.dp, GraphiteBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Device Security Capabilities",
                    color = GraphiteTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                CapabilityStatusRow(
                    icon = Icons.Outlined.Fingerprint,
                    label = "Biometrics",
                    badgeLabel = uiState.capabilities.biometricAvailability.label,
                    badgeColor = bioColor,
                    detail = uiState.capabilities.biometricAvailability.detail,
                )
                HorizontalDivider(thickness = 1.dp, color = GraphiteKpiDivider)
                CapabilityStatusRow(
                    icon = Icons.Outlined.PhonelinkLock,
                    label = "Device Screen Lock",
                    badgeLabel = if (uiState.capabilities.hasDeviceCredential) "Configured" else "Not Configured",
                    badgeColor = if (uiState.capabilities.hasDeviceCredential) GraphiteGreen else GraphiteTextSecondary,
                    detail = if (uiState.capabilities.hasDeviceCredential) {
                        "System PIN, pattern, or password is available as fallback"
                    } else {
                        "Set a screen lock in Android Settings or configure an App PIN/Password below"
                    },
                )
            }
        }

        // Master App Lock Toggle
        SecuritySwitchRow(
            icon = Icons.Outlined.Shield,
            title = "Require App Lock",
            subtitle = if (uiState.isAuthRequired) {
                "Locks automatically when StockFolio leaves the foreground"
            } else {
                "Enable protection on app launch and background resume"
            },
            checked = uiState.config.isAppLockEnabled,
            enabled = true,
            tag = "app_lock_enabled_switch",
            onCheckedChange = { enabled ->
                if (enabled && !uiState.config.hasAnyEnabledMethod(uiState.capabilities)) {
                    showPinDialog = true
                } else {
                    controller.setAppLockEnabled(enabled)
                }
            },
        )

        HorizontalDivider(thickness = 1.dp, color = GraphiteKpiDivider)

        // Biometric Toggle
        SecuritySwitchRow(
            icon = Icons.Outlined.Fingerprint,
            title = "Biometric Authentication",
            subtitle = if (uiState.capabilities.canUseBiometric) {
                "Check fingerprint or face automatically first"
            } else {
                uiState.capabilities.biometricAvailability.detail
            },
            checked = uiState.config.isBiometricEnabled && uiState.capabilities.canUseBiometric,
            enabled = uiState.capabilities.canUseBiometric,
            tag = "biometric_enabled_switch",
            onCheckedChange = { controller.setBiometricEnabled(it) },
        )

        // Device Credential Toggle
        SecuritySwitchRow(
            icon = Icons.Outlined.PhonelinkLock,
            title = "Device Screen Lock Fallback",
            subtitle = if (uiState.capabilities.hasDeviceCredential) {
                "Allow system PIN, pattern, or password fallback"
            } else {
                "No system screen lock configured on this device"
            },
            checked = uiState.config.isDeviceCredentialEnabled && uiState.capabilities.hasDeviceCredential,
            enabled = uiState.capabilities.hasDeviceCredential,
            tag = "device_credential_enabled_switch",
            onCheckedChange = { controller.setDeviceCredentialEnabled(it) },
        )

        HorizontalDivider(thickness = 1.dp, color = GraphiteKpiDivider)

        // App PIN Configuration Row
        CredentialManageRow(
            icon = Icons.Outlined.Pin,
            title = "Application PIN",
            subtitle = if (uiState.config.hasAppPin) "Configured (4–6 numeric digits)" else "Not configured",
            isConfigured = uiState.config.hasAppPin,
            configureTag = "configure_app_pin_button",
            removeTag = "remove_app_pin_button",
            onConfigure = { showPinDialog = true },
            onRemove = { controller.removeAppPin() },
        )

        // App Password Configuration Row
        CredentialManageRow(
            icon = Icons.Outlined.Password,
            title = "Application Password",
            subtitle = if (uiState.config.hasAppPassword) "Configured (salted PBKDF2 hash)" else "Not configured",
            isConfigured = uiState.config.hasAppPassword,
            configureTag = "configure_app_password_button",
            removeTag = "remove_app_password_button",
            onConfigure = { showPasswordDialog = true },
            onRemove = { controller.removeAppPassword() },
        )

        HorizontalDivider(thickness = 1.dp, color = GraphiteKpiDivider)

        // Preferred Auth Priority Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Preferred Authentication Priority",
                color = GraphiteTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            PreferredAuthMethod.entries.forEach { method ->
                val isMethodAvailable = when (method) {
                    PreferredAuthMethod.BIOMETRIC_FIRST -> true
                    PreferredAuthMethod.DEVICE_CREDENTIAL ->
                        uiState.capabilities.hasDeviceCredential && uiState.config.isDeviceCredentialEnabled
                    PreferredAuthMethod.APP_PIN -> uiState.config.hasAppPin
                    PreferredAuthMethod.APP_PASSWORD -> uiState.config.hasAppPassword
                }
                val selected = uiState.config.preferredMethod == method
                Surface(
                    onClick = {
                        if (isMethodAvailable) {
                            controller.setPreferredMethod(method)
                        } else {
                            scope.launch {
                                when (method) {
                                    PreferredAuthMethod.APP_PIN -> showPinDialog = true
                                    PreferredAuthMethod.APP_PASSWORD -> showPasswordDialog = true
                                    else -> Unit
                                }
                            }
                        }
                    },
                    enabled = isMethodAvailable || method == PreferredAuthMethod.APP_PIN || method == PreferredAuthMethod.APP_PASSWORD,
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) GraphiteAccentViolet.copy(alpha = 0.10f) else GraphiteNavSurface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) GraphiteAccentViolet.copy(alpha = 0.55f) else GraphiteBorder,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preferred_method_${method.name.lowercase()}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = GraphiteAccentViolet,
                                unselectedColor = GraphiteTextSecondary,
                            ),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = method.label,
                                color = if (isMethodAvailable) GraphiteTextPrimary else GraphiteTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (isMethodAvailable) {
                                    method.description
                                } else {
                                    "Not configured yet"
                                },
                                color = GraphiteTextMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isAuthRequired) {
            OutlinedButton(
                onClick = { controller.lockNow() },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GraphiteAccentViolet.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("lock_app_now_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = GraphiteAccentViolet,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lock App Now", color = GraphiteAccentViolet, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CapabilityStatusRow(
    icon: ImageVector,
    label: String,
    badgeLabel: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    detail: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GraphiteAccentViolet,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    color = GraphiteTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            StatusBadge(label = badgeLabel, color = badgeColor, showDot = false)
        }
        Text(
            text = detail,
            color = GraphiteTextSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SecuritySwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) GraphiteAccentViolet else GraphiteTextMuted,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) GraphiteTextPrimary else GraphiteTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = GraphiteTextSecondary,
                fontSize = 12.sp,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GraphiteOnAccent,
                checkedTrackColor = GraphiteAccentViolet,
                uncheckedThumbColor = GraphiteTextSecondary,
                uncheckedTrackColor = GraphiteNavSurface,
                uncheckedBorderColor = GraphiteBorder,
            ),
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun CredentialManageRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isConfigured: Boolean,
    configureTag: String,
    removeTag: String,
    onConfigure: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GraphiteAccentViolet,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = GraphiteTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    color = GraphiteTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onConfigure,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GraphiteBorder),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(configureTag),
            ) {
                Text(
                    text = if (isConfigured) "Change" else "Set Up",
                    color = GraphiteTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (isConfigured) {
                OutlinedButton(
                    onClick = onRemove,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GraphiteRed.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(removeTag),
                ) {
                    Text(
                        text = "Remove",
                        color = GraphiteRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinSetupDialog(
    hasExistingPin: Boolean,
    onVerifyCurrentPin: suspend (CharArray) -> Boolean,
    onSavePin: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            currentPin = ""
            newPin = ""
            confirmPin = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GraphiteKpiCard,
        titleContentColor = GraphiteTextPrimary,
        title = {
            Text(
                text = if (hasExistingPin) "Change Application PIN" else "Set Application PIN",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose a 4–6 digit numeric PIN stored as a salted PBKDF2 hash.",
                    color = GraphiteTextSecondary,
                    fontSize = 12.sp,
                )
                if (hasExistingPin) {
                    DialogSecretField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) currentPin = it },
                        label = "Current PIN",
                        isNumeric = true,
                        tag = "dialog_current_pin_input",
                    )
                }
                DialogSecretField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) newPin = it },
                    label = "New PIN (4–6 digits)",
                    isNumeric = true,
                    tag = "dialog_new_pin_input",
                )
                DialogSecretField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) confirmPin = it },
                    label = "Confirm PIN",
                    isNumeric = true,
                    tag = "dialog_confirm_pin_input",
                )
                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        color = GraphiteRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    if (newPin.length !in 4..6 || !newPin.all { it.isDigit() }) {
                        errorText = "PIN must be 4 to 6 numeric digits."
                        return@Button
                    }
                    if (newPin != confirmPin) {
                        errorText = "PIN entries do not match."
                        return@Button
                    }
                    scope.launch {
                        isSaving = true
                        if (hasExistingPin) {
                            val currentChars = currentPin.toCharArray()
                            val ok = onVerifyCurrentPin(currentChars)
                            if (!ok) {
                                isSaving = false
                                errorText = "Current PIN is incorrect."
                                return@launch
                            }
                        }
                        val nextChars = newPin.toCharArray()
                        currentPin = ""
                        newPin = ""
                        confirmPin = ""
                        isSaving = false
                        onSavePin(nextChars)
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GraphiteAccentViolet,
                    contentColor = GraphiteOnAccent,
                ),
                modifier = Modifier.testTag("dialog_save_pin_button"),
            ) {
                Text("Save PIN", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GraphiteTextSecondary)
            }
        },
    )
}

@Composable
private fun PasswordSetupDialog(
    hasExistingPassword: Boolean,
    onVerifyCurrentPassword: suspend (CharArray) -> Boolean,
    onSavePassword: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            currentPassword = ""
            newPassword = ""
            confirmPassword = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GraphiteKpiCard,
        titleContentColor = GraphiteTextPrimary,
        title = {
            Text(
                text = if (hasExistingPassword) "Change Application Password" else "Set Application Password",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Minimum 6 characters. Stored securely with a salted PBKDF2 hash.",
                    color = GraphiteTextSecondary,
                    fontSize = 12.sp,
                )
                if (hasExistingPassword) {
                    DialogSecretField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = "Current Password",
                        isNumeric = false,
                        tag = "dialog_current_password_input",
                    )
                }
                DialogSecretField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "New Password (min 6 chars)",
                    isNumeric = false,
                    tag = "dialog_new_password_input",
                )
                DialogSecretField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Confirm Password",
                    isNumeric = false,
                    tag = "dialog_confirm_password_input",
                )
                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        color = GraphiteRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    if (newPassword.length < 6) {
                        errorText = "Password must be at least 6 characters."
                        return@Button
                    }
                    if (newPassword != confirmPassword) {
                        errorText = "Passwords do not match."
                        return@Button
                    }
                    scope.launch {
                        isSaving = true
                        if (hasExistingPassword) {
                            val currentChars = currentPassword.toCharArray()
                            val ok = onVerifyCurrentPassword(currentChars)
                            if (!ok) {
                                isSaving = false
                                errorText = "Current password is incorrect."
                                return@launch
                            }
                        }
                        val nextChars = newPassword.toCharArray()
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                        isSaving = false
                        onSavePassword(nextChars)
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GraphiteAccentViolet,
                    contentColor = GraphiteOnAccent,
                ),
                modifier = Modifier.testTag("dialog_save_password_button"),
            ) {
                Text("Save Password", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GraphiteTextSecondary)
            }
        },
    )
}

@Composable
private fun DialogSecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isNumeric: Boolean,
    tag: String,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isNumeric) KeyboardType.NumberPassword else KeyboardType.Password,
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                    tint = GraphiteTextSecondary,
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GraphiteAccentViolet,
            unfocusedBorderColor = GraphiteBorder,
            focusedLabelColor = GraphiteAccentViolet,
            unfocusedLabelColor = GraphiteTextSecondary,
            focusedTextColor = GraphiteTextPrimary,
            unfocusedTextColor = GraphiteTextPrimary,
            focusedContainerColor = GraphiteNavSurface,
            unfocusedContainerColor = GraphiteNavSurface,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}
