package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.AuthStepMode
import com.example.security.BiometricAvailability
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteAmber
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCanvas
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteKpiCard
import com.example.ui.theme.GraphiteNavSurface
import com.example.ui.theme.GraphiteOnAccent
import com.example.ui.theme.GraphiteRed
import com.example.ui.theme.GraphiteTextMuted
import com.example.ui.theme.GraphiteTextPrimary
import com.example.ui.theme.GraphiteTextSecondary
import com.example.ui.viewmodel.AppLockUiState

@Composable
fun AppLockScreen(
    uiState: AppLockUiState,
    onLaunchBiometric: () -> Unit,
    onLaunchDeviceCredential: () -> Unit,
    onSelectStep: (AuthStepMode) -> Unit,
    onSubmitAppPin: (CharArray) -> Unit,
    onSubmitAppPassword: (CharArray) -> Unit,
) {
    // Prevent back button bypass while the app lock gate is active.
    BackHandler(enabled = true) {}

    LaunchedEffect(uiState.shouldAutoLaunchBiometric) {
        if (uiState.shouldAutoLaunchBiometric) {
            onLaunchBiometric()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(GraphiteCanvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("app_lock_screen"),
        contentAlignment = Alignment.Center,
    ) {
        val horizontalPadding = if (maxWidth < 380.dp) 16.dp else 24.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Brand & Security Header
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = GraphiteAccentViolet.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, GraphiteAccentViolet.copy(alpha = 0.32f)),
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = "StockFolio Security",
                        tint = GraphiteAccentViolet,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Unlock StockFolio",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = GraphiteTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Verify your identity to access portfolio holdings, analytics, and signals.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GraphiteTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            if (uiState.config.isTampered) {
                SecurityAlertBanner(
                    title = "Security Check Required",
                    message = "Stored security preferences could not be verified. Unlock with an active method or reconfigure App Lock in Settings.",
                    tint = GraphiteAmber,
                    icon = Icons.Outlined.WarningAmber,
                    tag = "applock_tamper_banner",
                )
            }

            if (uiState.lockoutRemainingSeconds > 0L) {
                SecurityAlertBanner(
                    title = "Too Many Failed Attempts",
                    message = "App PIN/password entry locked for ${uiState.lockoutRemainingSeconds}s. Wait or unlock with biometrics/device lock.",
                    tint = GraphiteRed,
                    icon = Icons.Outlined.ErrorOutline,
                    tag = "applock_lockout_banner",
                )
            } else if (!uiState.errorMessage.isNullOrBlank()) {
                SecurityAlertBanner(
                    title = "Authentication Required",
                    message = uiState.errorMessage,
                    tint = GraphiteRed,
                    icon = Icons.Outlined.ErrorOutline,
                    tag = "applock_error_banner",
                )
            }

            when (uiState.activeStep) {
                AuthStepMode.BIOMETRIC_PROMPT,
                AuthStepMode.FALLBACK_CHOOSER -> {
                    BiometricAndFallbackCard(
                        uiState = uiState,
                        onLaunchBiometric = onLaunchBiometric,
                        onLaunchDeviceCredential = onLaunchDeviceCredential,
                        onSelectStep = onSelectStep,
                    )
                }

                AuthStepMode.APP_PIN_ENTRY -> {
                    AppPinEntryCard(
                        uiState = uiState,
                        onSubmitPin = onSubmitAppPin,
                        onLaunchBiometric = onLaunchBiometric,
                        onLaunchDeviceCredential = onLaunchDeviceCredential,
                        onSelectStep = onSelectStep,
                    )
                }

                AuthStepMode.APP_PASSWORD_ENTRY -> {
                    AppPasswordEntryCard(
                        uiState = uiState,
                        onSubmitPassword = onSubmitAppPassword,
                        onLaunchBiometric = onLaunchBiometric,
                        onLaunchDeviceCredential = onLaunchDeviceCredential,
                        onSelectStep = onSelectStep,
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityAlertBanner(
    title: String,
    message: String,
    tint: androidx.compose.ui.graphics.Color,
    icon: ImageVector,
    tag: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = tint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message,
                    color = GraphiteTextPrimary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun BiometricAndFallbackCard(
    uiState: AppLockUiState,
    onLaunchBiometric: () -> Unit,
    onLaunchDeviceCredential: () -> Unit,
    onSelectStep: (AuthStepMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GraphiteKpiCard,
        border = BorderStroke(1.dp, GraphiteBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(GraphiteCard)
                    .border(1.dp, GraphiteBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (uiState.canUseBiometricNow) Icons.Outlined.Fingerprint else Icons.Outlined.Lock,
                    contentDescription = "Authentication icon",
                    tint = GraphiteAccentViolet,
                    modifier = Modifier.size(28.dp),
                )
            }

            val statusHeadline = when {
                uiState.canUseBiometricNow -> "Biometric Verification Ready"
                uiState.isBiometricLockedOut -> "Biometrics Locked Out"
                uiState.capabilities.biometricAvailability == BiometricAvailability.NONE_ENROLLED ->
                    "No Biometrics Enrolled"
                uiState.canUseDeviceCredentialNow -> "Device Screen Lock Ready"
                else -> "Choose an Unlock Method"
            }
            val statusSubtext = when {
                uiState.canUseBiometricNow ->
                    "Use your fingerprint or face, or choose an alternative device or app unlock method below."
                uiState.isBiometricLockedOut ->
                    "System biometrics are locked. Unlock using your device PIN/pattern/password or app credentials."
                else -> uiState.capabilities.biometricAvailability.detail
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = statusHeadline,
                    style = MaterialTheme.typography.titleMedium,
                    color = GraphiteTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = statusSubtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = GraphiteTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            if (uiState.canUseBiometricNow) {
                Button(
                    onClick = onLaunchBiometric,
                    enabled = !uiState.isExternalPromptInFlight,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GraphiteAccentViolet,
                        contentColor = GraphiteOnAccent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("unlock_biometric_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Biometrics", fontWeight = FontWeight.SemiBold)
                }
            }

            if (uiState.canUseDeviceCredentialNow) {
                val isPrimary = !uiState.canUseBiometricNow
                if (isPrimary) {
                    Button(
                        onClick = onLaunchDeviceCredential,
                        enabled = !uiState.isExternalPromptInFlight,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GraphiteAccentViolet,
                            contentColor = GraphiteOnAccent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("unlock_device_credential_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhonelinkLock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use Device Screen Lock", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    FallbackOptionButton(
                        label = "Use Device Screen Lock (PIN / Pattern / Password)",
                        icon = Icons.Outlined.PhonelinkLock,
                        enabled = !uiState.isExternalPromptInFlight,
                        tag = "unlock_device_credential_button",
                        onClick = onLaunchDeviceCredential,
                    )
                }
            }

            if (uiState.canUseAppPinNow) {
                FallbackOptionButton(
                    label = "Use App PIN",
                    icon = Icons.Outlined.Pin,
                    enabled = true,
                    tag = "switch_to_app_pin_button",
                    onClick = { onSelectStep(AuthStepMode.APP_PIN_ENTRY) },
                )
            }

            if (uiState.canUseAppPasswordNow) {
                FallbackOptionButton(
                    label = "Use App Password",
                    icon = Icons.Outlined.Password,
                    enabled = true,
                    tag = "switch_to_app_password_button",
                    onClick = { onSelectStep(AuthStepMode.APP_PASSWORD_ENTRY) },
                )
            }
        }
    }
}

@Composable
private fun AppPinEntryCard(
    uiState: AppLockUiState,
    onSubmitPin: (CharArray) -> Unit,
    onLaunchBiometric: () -> Unit,
    onLaunchDeviceCredential: () -> Unit,
    onSelectStep: (AuthStepMode) -> Unit,
) {
    var pinDigits by remember { mutableStateOf("") }
    val lockedOut = uiState.lockoutRemainingSeconds > 0L

    DisposableEffect(Unit) {
        onDispose { pinDigits = "" }
    }

    fun submitCurrentPin() {
        if (pinDigits.length !in 4..6 || lockedOut || uiState.isVerifying) return
        val chars = pinDigits.toCharArray()
        pinDigits = ""
        onSubmitPin(chars)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GraphiteKpiCard,
        border = BorderStroke(1.dp, GraphiteBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Enter App PIN",
                    style = MaterialTheme.typography.titleMedium,
                    color = GraphiteTextPrimary,
                )
                Text(
                    text = "Enter your 4–6 digit StockFolio PIN",
                    style = MaterialTheme.typography.bodySmall,
                    color = GraphiteTextSecondary,
                )
            }

            // Masked PIN Dots Indicator (6 slots)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                for (index in 0 until 6) {
                    val filled = index < pinDigits.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (filled) GraphiteAccentViolet else GraphiteNavSurface)
                            .border(
                                width = 1.5.dp,
                                color = if (filled) GraphiteAccentViolet else GraphiteBorder,
                                shape = CircleShape,
                            ),
                    )
                }
            }

            // Custom Numeric Keypad
            val keypadRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                keypadRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { digit ->
                            PinKeypadDigitButton(
                                label = digit,
                                enabled = !lockedOut && !uiState.isVerifying && pinDigits.length < 6,
                                tag = "pin_key_$digit",
                                modifier = Modifier.weight(1f),
                                onClick = { pinDigits += digit },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        onClick = { pinDigits = "" },
                        enabled = !lockedOut && !uiState.isVerifying && pinDigits.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        color = GraphiteNavSurface,
                        border = BorderStroke(1.dp, GraphiteBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("pin_key_clear"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Clear",
                                color = if (pinDigits.isNotEmpty()) GraphiteTextPrimary else GraphiteTextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    PinKeypadDigitButton(
                        label = "0",
                        enabled = !lockedOut && !uiState.isVerifying && pinDigits.length < 6,
                        tag = "pin_key_0",
                        modifier = Modifier.weight(1f),
                        onClick = { pinDigits += "0" },
                    )

                    Surface(
                        onClick = {
                            if (pinDigits.isNotEmpty()) {
                                pinDigits = pinDigits.dropLast(1)
                            }
                        },
                        enabled = !lockedOut && !uiState.isVerifying && pinDigits.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        color = GraphiteNavSurface,
                        border = BorderStroke(1.dp, GraphiteBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("pin_key_backspace"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                contentDescription = "Delete last digit",
                                tint = if (pinDigits.isNotEmpty()) GraphiteTextPrimary else GraphiteTextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { submitCurrentPin() },
                enabled = pinDigits.length in 4..6 && !lockedOut && !uiState.isVerifying,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GraphiteAccentViolet,
                    contentColor = GraphiteOnAccent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("submit_app_pin_button"),
            ) {
                if (uiState.isVerifying) {
                    CircularProgressIndicator(
                        color = GraphiteOnAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Unlock with PIN", fontWeight = FontWeight.SemiBold)
            }

            AlternativeAuthFooter(
                uiState = uiState,
                currentStep = AuthStepMode.APP_PIN_ENTRY,
                onLaunchBiometric = onLaunchBiometric,
                onLaunchDeviceCredential = onLaunchDeviceCredential,
                onSelectStep = onSelectStep,
            )
        }
    }
}

@Composable
private fun AppPasswordEntryCard(
    uiState: AppLockUiState,
    onSubmitPassword: (CharArray) -> Unit,
    onLaunchBiometric: () -> Unit,
    onLaunchDeviceCredential: () -> Unit,
    onSelectStep: (AuthStepMode) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val lockedOut = uiState.lockoutRemainingSeconds > 0L

    DisposableEffect(Unit) {
        onDispose { password = "" }
    }

    fun submitCurrentPassword() {
        if (password.isEmpty() || lockedOut || uiState.isVerifying) return
        val chars = password.toCharArray()
        password = ""
        onSubmitPassword(chars)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GraphiteKpiCard,
        border = BorderStroke(1.dp, GraphiteBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Enter App Password",
                    style = MaterialTheme.typography.titleMedium,
                    color = GraphiteTextPrimary,
                )
                Text(
                    text = "Enter your StockFolio application password to unlock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GraphiteTextSecondary,
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                enabled = !lockedOut && !uiState.isVerifying,
                singleLine = true,
                label = { Text("App Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submitCurrentPassword() },
                ),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("app_password_visibility_toggle"),
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
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
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_password_input"),
            )

            Button(
                onClick = { submitCurrentPassword() },
                enabled = password.isNotEmpty() && !lockedOut && !uiState.isVerifying,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GraphiteAccentViolet,
                    contentColor = GraphiteOnAccent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("submit_app_password_button"),
            ) {
                if (uiState.isVerifying) {
                    CircularProgressIndicator(
                        color = GraphiteOnAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Unlock", fontWeight = FontWeight.SemiBold)
            }

            AlternativeAuthFooter(
                uiState = uiState,
                currentStep = AuthStepMode.APP_PASSWORD_ENTRY,
                onLaunchBiometric = onLaunchBiometric,
                onLaunchDeviceCredential = onLaunchDeviceCredential,
                onSelectStep = onSelectStep,
            )
        }
    }
}

@Composable
private fun PinKeypadDigitButton(
    label: String,
    enabled: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = GraphiteNavSurface,
        border = BorderStroke(1.dp, GraphiteBorder),
        modifier = modifier
            .heightIn(min = 52.dp)
            .testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) GraphiteTextPrimary else GraphiteTextMuted,
            )
        }
    }
}

@Composable
private fun FallbackOptionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GraphiteBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(tag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GraphiteAccentViolet,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = GraphiteTextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun AlternativeAuthFooter(
    uiState: AppLockUiState,
    currentStep: AuthStepMode,
    onLaunchBiometric: () -> Unit,
    onLaunchDeviceCredential: () -> Unit,
    onSelectStep: (AuthStepMode) -> Unit,
) {
    val hasAlternatives = uiState.canUseBiometricNow ||
        uiState.canUseDeviceCredentialNow ||
        (currentStep != AuthStepMode.APP_PIN_ENTRY && uiState.canUseAppPinNow) ||
        (currentStep != AuthStepMode.APP_PASSWORD_ENTRY && uiState.canUseAppPasswordNow)

    if (!hasAlternatives) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Other unlock options",
            color = GraphiteTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (uiState.canUseBiometricNow) {
            FallbackOptionButton(
                label = "Use Biometrics",
                icon = Icons.Outlined.Fingerprint,
                enabled = !uiState.isExternalPromptInFlight,
                tag = "alt_biometric_button",
                onClick = onLaunchBiometric,
            )
        }
        if (uiState.canUseDeviceCredentialNow) {
            FallbackOptionButton(
                label = "Use Device Screen Lock",
                icon = Icons.Outlined.PhonelinkLock,
                enabled = !uiState.isExternalPromptInFlight,
                tag = "alt_device_credential_button",
                onClick = onLaunchDeviceCredential,
            )
        }
        if (currentStep != AuthStepMode.APP_PIN_ENTRY && uiState.canUseAppPinNow) {
            TextButton(
                onClick = { onSelectStep(AuthStepMode.APP_PIN_ENTRY) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("alt_app_pin_button"),
            ) {
                Text("Use App PIN instead", color = GraphiteAccentViolet, fontWeight = FontWeight.SemiBold)
            }
        }
        if (currentStep != AuthStepMode.APP_PASSWORD_ENTRY && uiState.canUseAppPasswordNow) {
            TextButton(
                onClick = { onSelectStep(AuthStepMode.APP_PASSWORD_ENTRY) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("alt_app_password_button"),
            ) {
                Text("Use App Password instead", color = GraphiteAccentViolet, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
