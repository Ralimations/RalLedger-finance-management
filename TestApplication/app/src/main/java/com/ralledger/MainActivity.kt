package com.ralledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ralledger.ui.navigation.RalLedgerMainScreen
import com.ralledger.ui.theme.RalLedgerTheme
import com.ralledger.ui.theme.ThemeSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as RalLedgerApplication
        setContent {
            val themeSettings by app.container.themePreferences.settings.collectAsState(initial = ThemeSettings())
            RalLedgerTheme(settings = themeSettings) {
                RalLedgerMainScreen(appContainer = app.container)
            }
        }
    }
}
