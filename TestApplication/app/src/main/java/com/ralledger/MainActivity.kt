package com.ralledger

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import com.ralledger.ui.navigation.RalLedgerMainScreen
import com.ralledger.ui.theme.RalLedgerTheme
import com.ralledger.ui.theme.ThemeSettings

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as RalLedgerApplication
        setContent {
            val themeSettings by app.container.themePreferences.settings.collectAsState(initial = ThemeSettings())
            val lockEnabled by app.container.appLockPreferences.enabled.collectAsState(initial = null as Boolean?)
            val lifecycleOwner = LocalLifecycleOwner.current
            var isForeground by remember {
                mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
            var authenticated by remember { mutableStateOf(false) }
            var promptInProgress by remember { mutableStateOf(false) }
            var unlockAttempt by remember { mutableIntStateOf(0) }
            var lockMessage by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> isForeground = true
                        Lifecycle.Event.ON_STOP -> {
                            isForeground = false
                            authenticated = false
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            DisposableEffect(lockEnabled) {
                if (lockEnabled == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            }

            LaunchedEffect(lockEnabled, isForeground, authenticated, unlockAttempt) {
                if (lockEnabled == true && isForeground && !authenticated && !promptInProgress && canUseAppLock()) {
                    promptInProgress = true
                    authenticate(
                        title = "Unlock RalLedger",
                        subtitle = "Confirm your device identity to view your finances",
                        onSuccess = { promptInProgress = false; authenticated = true; lockMessage = null },
                        onError = { promptInProgress = false; lockMessage = it }
                    )
                }
            }

            RalLedgerTheme(settings = themeSettings) {
                when {
                    lockEnabled == null -> Surface(modifier = Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    lockEnabled == true && !authenticated -> AppLockScreen(
                        message = lockMessage,
                        deviceLockAvailable = canUseAppLock(),
                        promptInProgress = promptInProgress,
                        onUnlock = { if (!promptInProgress) unlockAttempt++ },
                        onOpenSecuritySettings = { openDeviceSecuritySettings() }
                    )
                    else -> RalLedgerMainScreen(
                        appContainer = app.container,
                        appLockAvailable = canUseAppLock(),
                        onEnableAppLock = {
                            if (!canUseAppLock()) {
                                lockMessage = "Set a screen lock in Android Settings before enabling app lock."
                            } else if (!promptInProgress) {
                                promptInProgress = true
                                authenticate(
                                    title = "Enable RalLedger app lock",
                                    subtitle = "Confirm your device identity to protect this app",
                                    onSuccess = {
                                        promptInProgress = false
                                        authenticated = true
                                        lockMessage = null
                                        scope.launch { app.container.appLockPreferences.setEnabled(true) }
                                    },
                                    onError = { promptInProgress = false; lockMessage = it }
                                )
                            }
                        },
                        onDisableAppLock = { scope.launch { app.container.appLockPreferences.setEnabled(false) } },
                        onOpenSecuritySettings = { openDeviceSecuritySettings() }
                    )
                }
            }
        }
    }

    private fun canUseAppLock(): Boolean {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            true
        }
    }

    private fun authenticate(title: String, subtitle: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            infoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            infoBuilder.setDeviceCredentialAllowed(true)
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )
        prompt.authenticate(infoBuilder.build())
    }

    private fun openDeviceSecuritySettings() {
        startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }
}

@androidx.compose.runtime.Composable
private fun AppLockScreen(
    message: String?,
    deviceLockAvailable: Boolean,
    promptInProgress: Boolean,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("RalLedger is locked", style = MaterialTheme.typography.headlineSmall)
            Text("Authenticate with your device to continue.", modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp)) }
            Button(onClick = onUnlock, enabled = deviceLockAvailable && !promptInProgress) {
                Text(if (promptInProgress) "Waiting for authentication…" else "Unlock")
            }
            if (!deviceLockAvailable) {
                Button(onClick = onOpenSecuritySettings, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Set up device security")
                }
            }
        }
    }
}
