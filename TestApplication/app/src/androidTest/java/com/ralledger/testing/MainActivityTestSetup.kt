package com.ralledger.testing

import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.RalLedgerApplication
import kotlinx.coroutines.runBlocking

/** Starts UI-flow tests as a returning user unless a test explicitly covers first-run onboarding. */
fun markWelcomeCompleteForMainActivityTest() {
    val application = InstrumentationRegistry.getInstrumentation().targetContext
        .applicationContext as RalLedgerApplication
    runBlocking { application.container.welcomePreferences.markComplete() }
}
