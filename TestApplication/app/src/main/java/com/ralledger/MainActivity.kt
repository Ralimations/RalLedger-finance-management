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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.ralledger.ui.navigation.RalLedgerMainScreen
import com.ralledger.ui.theme.RalLedgerTheme
import com.ralledger.ui.theme.ThemeSettings
import com.ralledger.ui.theme.ThemeMode
import com.ralledger.ui.widgets.EXTRA_WIDGET_TRANSACTION_TYPE
import com.ralledger.ui.widgets.normalizeWidgetTransactionType

class MainActivity : FragmentActivity() {
    private var pendingWidgetTransactionType by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition {
            (application as RalLedgerApplication).startupState.value == StartupState.Recovering
        }
        super.onCreate(savedInstanceState)
        pendingWidgetTransactionType = normalizeWidgetTransactionType(
            intent.getStringExtra(EXTRA_WIDGET_TRANSACTION_TYPE)
        )
        val app = application as RalLedgerApplication
        setContent {
            val startupState by app.startupState.collectAsState()
            val themeSettings by app.container.themePreferences.settings.collectAsState(initial = ThemeSettings())
            val darkTheme = when (themeSettings.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            if (startupState == StartupState.Ready) {
            val lockEnabled by app.container.appLockPreferences.enabled.collectAsState(initial = null as Boolean?)
            val welcomeComplete by app.container.welcomePreferences.isComplete.collectAsState(initial = null as Boolean?)
            val lifecycleOwner = LocalLifecycleOwner.current
            var isForeground by remember {
                mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
            var authenticated by remember { mutableStateOf(false) }
            var promptInProgress by remember { mutableStateOf(false) }
            var unlockAttempt by remember { mutableIntStateOf(0) }
            var lockMessage by remember { mutableStateOf<String?>(null) }
            var welcomeSaveInProgress by remember { mutableStateOf(false) }
            var welcomeSaveError by remember { mutableStateOf(false) }
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

            LaunchedEffect(lockEnabled, welcomeComplete, isForeground, authenticated, unlockAttempt) {
                if (welcomeComplete == true && lockEnabled == true && isForeground && !authenticated && !promptInProgress && canUseAppLock()) {
                    promptInProgress = true
                    authenticate(
                        title = "Unlock PesoMind",
                        subtitle = "Confirm your device identity to view your finances",
                        onSuccess = { promptInProgress = false; authenticated = true; lockMessage = null },
                        onError = { promptInProgress = false; lockMessage = it }
                    )
                }
            }

            RalLedgerTheme(settings = themeSettings) {
                when {
                    lockEnabled == null || welcomeComplete == null -> {
                        PesoMindOpeningScreen(isLoading = true, onRetry = {})
                    }
                    welcomeComplete != true -> PesoMindWelcomeScreen(
                        saveInProgress = welcomeSaveInProgress,
                        showSaveError = welcomeSaveError,
                        onGetStarted = {
                            if (!welcomeSaveInProgress) {
                                welcomeSaveInProgress = true
                                welcomeSaveError = false
                                scope.launch {
                                    try {
                                        app.container.welcomePreferences.markComplete()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        welcomeSaveError = true
                                    } finally {
                                        welcomeSaveInProgress = false
                                    }
                                }
                            }
                        }
                    )
                    lockEnabled == true && !authenticated -> AppLockScreen(
                        message = lockMessage,
                        deviceLockAvailable = canUseAppLock(),
                        promptInProgress = promptInProgress,
                        onUnlock = { if (!promptInProgress) unlockAttempt++ },
                        onOpenSecuritySettings = { openDeviceSecuritySettings() }
                    )
                    else -> RalLedgerMainScreen(
                        appContainer = app.container,
                        widgetQuickAddType = pendingWidgetTransactionType,
                        onWidgetQuickAddHandled = { pendingWidgetTransactionType = null },
                        appLockAvailable = canUseAppLock(),
                        onEnableAppLock = {
                            if (!canUseAppLock()) {
                                lockMessage = "Set a screen lock in Android Settings before enabling app lock."
                            } else if (!promptInProgress) {
                                promptInProgress = true
                                authenticate(
                                    title = "Enable PesoMind app lock",
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
            } else {
                RalLedgerTheme(settings = themeSettings) {
                    PesoMindOpeningScreen(
                        isLoading = startupState == StartupState.Recovering,
                        onRetry = app::retryStartupRecovery
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWidgetTransactionType = normalizeWidgetTransactionType(
            intent.getStringExtra(EXTRA_WIDGET_TRANSACTION_TYPE)
        )
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

@Composable
private fun PesoMindWelcomeScreen(
    saveInProgress: Boolean,
    showSaveError: Boolean,
    onGetStarted: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.pesomind_mark),
                    contentDescription = stringResource(R.string.pesomind_mark_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(132.dp)
                )
                Text(
                    text = stringResource(R.string.welcome_title),
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.welcome_description),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (showSaveError) {
                    Text(
                        text = stringResource(R.string.welcome_save_error),
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = onGetStarted,
                    enabled = !saveInProgress,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (saveInProgress) R.string.welcome_saving else R.string.welcome_get_started
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PesoMindOpeningScreen(isLoading: Boolean, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.pesomind_wordmark),
                contentDescription = stringResource(R.string.app_tagline),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.9f).height(100.dp)
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = 20.dp).size(28.dp),
                    strokeWidth = 2.dp
                )
                Text("Preparing your data", modifier = Modifier.padding(top = 12.dp))
            } else {
                Text(
                    "PesoMind couldn't finish recovering your backup.",
                    modifier = Modifier.padding(top = 20.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Try again")
                }
            }
        }
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
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("PesoMind is locked", style = MaterialTheme.typography.headlineSmall)
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
