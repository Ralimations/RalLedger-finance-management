package com.ralledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ralledger.ui.navigation.RalLedgerMainScreen
import com.ralledger.ui.theme.RalLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as RalLedgerApplication
        setContent {
            RalLedgerTheme {
                RalLedgerMainScreen(appContainer = app.container)
            }
        }
    }
}
