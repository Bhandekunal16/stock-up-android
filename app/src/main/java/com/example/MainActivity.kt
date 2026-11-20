package com.example

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.BiometricOutcome
import com.example.security.BiometricPromptHelper
import com.example.ui.navigation.StockFolioApp
import com.example.ui.screens.AppLockScreen
import com.example.ui.theme.GraphiteAccentViolet
import com.example.ui.theme.GraphiteCanvas
import com.example.ui.theme.StockFolioTheme
import com.example.ui.theme.resolveIsDarkTheme
import com.example.ui.viewmodel.AppLockViewModel
import com.example.ui.viewmodel.ThemeViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val appLockViewModel: AppLockViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val appLockState by appLockViewModel.uiState.collectAsStateWithLifecycle()
            val isDark = resolveIsDarkTheme(themeMode)
            val lifecycleOwner = LocalLifecycleOwner.current

            val legacyDeviceCredentialLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val ok = result.resultCode == Activity.RESULT_OK
                appLockViewModel.controller.onDeviceCredentialOutcome(
                    success = ok,
                    message = if (ok) null else "Device screen lock verification was cancelled.",
                )
            }

            val launchBiometric: () -> Unit = {
                appLockViewModel.controller.onBiometricPromptStarted()
                BiometricPromptHelper.launchBiometricPrompt(
                    activity = this@MainActivity,
                    onOutcome = appLockViewModel.controller::onBiometricOutcome,
                )
            }

            val launchDeviceCredential: () -> Unit = {
                val controller = appLockViewModel.controller
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    controller.onDeviceCredentialStarted()
                    BiometricPromptHelper.launchDeviceCredentialPromptApi30Plus(
                        activity = this@MainActivity,
                    ) { outcome ->
                        when (outcome) {
                            is BiometricOutcome.Success -> controller.onDeviceCredentialOutcome(true)
                            is BiometricOutcome.FailedAttempt -> Unit
                            is BiometricOutcome.Cancelled -> controller.onDeviceCredentialOutcome(
                                success = false,
                                message = "Device screen lock cancelled. Choose another unlock option.",
                            )
                            is BiometricOutcome.Unavailable -> controller.onDeviceCredentialOutcome(
                                success = false,
                                message = outcome.message,
                            )
                            is BiometricOutcome.TemporaryLockout,
                            is BiometricOutcome.PermanentLockout,
                            is BiometricOutcome.Error -> controller.onDeviceCredentialOutcome(
                                success = false,
                                message = "Unable to verify device screen lock.",
                            )
                        }
                    }
                } else {
                    val intent = BiometricPromptHelper.createLegacyDeviceCredentialIntent(this@MainActivity)
                    if (intent != null) {
                        controller.onDeviceCredentialStarted()
                        legacyDeviceCredentialLauncher.launch(intent)
                    } else {
                        controller.onDeviceCredentialOutcome(
                            success = false,
                            message = "Device screen lock is not configured on this device.",
                        )
                    }
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> appLockViewModel.controller.onAppForegrounded()
                        Lifecycle.Event.ON_STOP -> appLockViewModel.controller.onAppBackgrounded(isChangingConfigurations)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(appLockState.isAuthRequired, appLockState.isUnlocked) {
                if (appLockState.isAuthRequired && !appLockState.isUnlocked) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(isDark) {
                val barStyle = if (isDark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle,
                )
            }

            StockFolioTheme(themeMode = themeMode) {
                when {
                    !appLockState.isInitialized -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(GraphiteCanvas),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = GraphiteAccentViolet,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }

                    appLockState.isAuthRequired && !appLockState.isUnlocked -> {
                        AppLockScreen(
                            uiState = appLockState,
                            onLaunchBiometric = launchBiometric,
                            onLaunchDeviceCredential = launchDeviceCredential,
                            onSelectStep = appLockViewModel.controller::selectAuthStep,
                            onSubmitAppPin = appLockViewModel.controller::submitAppPin,
                            onSubmitAppPassword = appLockViewModel.controller::submitAppPassword,
                        )
                    }

                    else -> {
                        StockFolioApp(
                            themeMode = themeMode,
                            appLockController = appLockViewModel.controller,
                            onSelectThemeMode = themeViewModel::setThemeMode,
                            onCycleThemeMode = themeViewModel::cycleThemeMode,
                        )
                    }
                }
            }
        }
    }
}
