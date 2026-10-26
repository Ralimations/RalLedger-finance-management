package com.ralledger.ui.onboarding

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.MainActivity
import com.ralledger.RalLedgerApplication
import com.ralledger.StartupState
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpgradeDataCompatibilityInstrumentedTest {
    @Test
    fun seedSyntheticLedgerBeforeUpgrade() {
        requireUpgradePhase("seed")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as RalLedgerApplication
        val repository = application.container.repository

        runBlocking {
            val startup = withTimeout(10_000) {
                application.startupState.first { it != StartupState.Recovering }
            }
            assertEquals(StartupState.Ready, startup)
            assertFalse(repository.accountsFlow.first().any { it.id == TEST_ACCOUNT_ID })

            repository.createAccount(
                Account(
                    id = TEST_ACCOUNT_ID,
                    name = TEST_ACCOUNT_NAME,
                    accountType = AccountType.PHYSICAL_WALLET,
                    openingBalance = TEST_BALANCE,
                    description = "Synthetic upgrade verification data"
                )
            )

            val account = repository.accountsFlow.first().first { it.id == TEST_ACCOUNT_ID }
            assertEquals(TEST_ACCOUNT_NAME, account.name)
            assertEquals(TEST_BALANCE, account.openingBalance)
            val openingEntry = repository.transactionsFlow.first().first {
                it.accountId == TEST_ACCOUNT_ID && it.transactionType == TransactionType.OPENING_BALANCE
            }
            assertEquals(TEST_BALANCE, openingEntry.amount)
        }
    }

    @Test
    fun upgradedInstallKeepsLedgerAndOpensDashboardInsteadOfWelcome() {
        requireUpgradePhase("verify")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as RalLedgerApplication
        val repository = application.container.repository

        runBlocking {
            val startup = withTimeout(10_000) {
                application.startupState.first { it != StartupState.Recovering }
            }
            assertEquals(StartupState.Ready, startup)

            val account = repository.accountsFlow.first().firstOrNull { it.id == TEST_ACCOUNT_ID }
            assertNotNull("The pre-upgrade synthetic account must survive the package update", account)
            assertEquals(TEST_ACCOUNT_NAME, account?.name)
            assertEquals(TEST_BALANCE, account?.openingBalance)

            val openingEntry = repository.transactionsFlow.first().firstOrNull {
                it.accountId == TEST_ACCOUNT_ID && it.transactionType == TransactionType.OPENING_BALANCE
            }
            assertNotNull("The pre-upgrade opening-balance journal entry must survive", openingEntry)
            assertEquals(TEST_BALANCE, openingEntry?.amount)
            assertTrue(
                "Existing financial records should bypass first-run welcome",
                application.container.welcomePreferences.isComplete.first()
            )
        }

        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val dashboardVisible = waitForUi(instrumentation) {
                "AVAILABLE MONEY" in visibleText(instrumentation)
            }
            val uiText = visibleText(instrumentation)
            val welcomeVisible = "Welcome to PesoMind" in uiText
            assertFalse("An existing install must not show first-run welcome", welcomeVisible)
            assertTrue("The upgraded install should open the dashboard. Visible text: $uiText", dashboardVisible)
        } finally {
            scenario?.close()
        }
    }

    private fun waitForUi(
        instrumentation: android.app.Instrumentation,
        timeoutMillis: Long = 10_000L,
        condition: () -> Boolean
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return true
            SystemClock.sleep(50L)
        }
        return false
    }

    private fun visibleText(instrumentation: android.app.Instrumentation): Set<String> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptySet()
        val text = mutableSetOf<String>()
        try {
            collectText(root, text)
        } finally {
            root.recycle()
        }
        return text
    }

    private fun collectText(node: AccessibilityNodeInfo, text: MutableSet<String>) {
        node.text?.toString()?.takeIf(String::isNotBlank)?.let(text::add)
        node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(text::add)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectText(child, text)
            } finally {
                child.recycle()
            }
        }
    }

    private fun requireUpgradePhase(expected: String) {
        assumeTrue(
            "This isolated test requires -e upgradePhase $expected",
            InstrumentationRegistry.getArguments().getString("upgradePhase") == expected
        )
    }

    private companion object {
        const val TEST_ACCOUNT_ID = "upgrade-check-synthetic-account"
        const val TEST_ACCOUNT_NAME = "Upgrade check synthetic wallet"
        val TEST_BALANCE = Money(24_680L)
    }
}
