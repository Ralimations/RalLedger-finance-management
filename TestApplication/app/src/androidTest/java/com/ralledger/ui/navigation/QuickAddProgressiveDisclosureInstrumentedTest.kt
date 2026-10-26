package com.ralledger.ui.navigation

import android.os.SystemClock
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.MainActivity
import com.ralledger.RalLedgerApplication
import com.ralledger.domain.model.Account
import com.ralledger.domain.model.AccountType
import com.ralledger.domain.model.CategoryType
import com.ralledger.domain.model.Money
import com.ralledger.domain.model.TransactionType
import com.ralledger.ui.settings.AppLockPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class QuickAddProgressiveDisclosureInstrumentedTest {
    @Test
    fun optionalDetailsRevealEditableDateAndTimeWithoutPosting() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appLockPreferences = AppLockPreferences(instrumentation.targetContext)
        runBlocking { appLockPreferences.setEnabled(false) }
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Quick actions") })
            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Expense") })
            assertTrue(waitForUi(instrumentation) { "Add details (optional)" in visibleText(instrumentation) })
            assertFalse("Optional date, notes, and description should start collapsed", "Notes (optional)" in visibleText(instrumentation))

            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Add details (optional)") })
            assertTrue(waitForUi(instrumentation) {
                val text = visibleText(instrumentation)
                "Description (optional)" in text && "Notes (optional)" in text &&
                    text.any { it.startsWith("Date ·") } && text.any { it.startsWith("Time ·") }
            })

            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Date ·") })
            assertTrue(waitForUi(instrumentation) { "Set date" in visibleText(instrumentation) })
            assertTrue(performClick(instrumentation, "Cancel"))

            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Time ·") })
            assertTrue(waitForUi(instrumentation) { "Set time" in visibleText(instrumentation) })
            assertTrue(performClick(instrumentation, "Cancel"))
        } finally {
            scenario?.close()
            runBlocking { appLockPreferences.setEnabled(false) }
        }
    }

    @Test
    fun quickAddPersistsTransferNotesFeeCategoryAndTimestamp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as RalLedgerApplication
        val repository = application.container.repository
        val appLockPreferences = application.container.appLockPreferences
        val sourceId = "phase2-source-${UUID.randomUUID()}"
        val destinationId = "phase2-destination-${UUID.randomUUID()}"
        val sourceName = "A Phase II QA source ${sourceId.takeLast(6)}"
        val destinationName = "B Phase II QA destination ${destinationId.takeLast(6)}"
        var wasLockEnabled = false

        val expenseCategory = runBlocking {
            val startup = application.startupState.first { it !is com.ralledger.StartupState.Recovering }
            assertTrue("Application startup recovery must succeed", startup is com.ralledger.StartupState.Ready)
            val accountsBefore = repository.accountsFlow.first()
            val transactionsBefore = repository.transactionsFlow.first()
            assumeTrue(
                "Skip the write journey unless the app profile is empty",
                accountsBefore.isEmpty() && transactionsBefore.isEmpty()
            )
            wasLockEnabled = appLockPreferences.enabled.first()
            appLockPreferences.setEnabled(false)
            repository.createAccount(
                Account(
                    id = sourceId,
                    name = sourceName,
                    accountType = AccountType.PHYSICAL_WALLET,
                    openingBalance = Money.parse("500.00")!!
                )
            )
            repository.createAccount(
                Account(
                    id = destinationId,
                    name = destinationName,
                    accountType = AccountType.E_WALLET
                )
            )
            repository.categoriesFlow.first { categories ->
                categories.any { it.type == CategoryType.EXPENSE && !it.isArchived }
            }.first { it.type == CategoryType.EXPENSE && !it.isArchived }
        }

        var scenario: ActivityScenario<MainActivity>? = null
        val description = "Phase II QA transfer ${sourceId.takeLast(6)}"
        val notes = "Fee and notes save check"
        val startedAt = System.currentTimeMillis()

        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Quick actions") })
            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Transfer") })
            assertTrue(waitForUi(instrumentation) {
                val text = visibleText(instrumentation)
                sourceName in text && destinationName in text
            })

            assertTrue(
                "Could not set amount. Inputs: ${describeInputNodes(instrumentation)}",
                waitForUi(instrumentation) { setEditableFieldText(instrumentation, 0, "100.00") }
            )
            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Add details (optional)") })
            assertTrue(waitForUi(instrumentation) {
                setEditableFieldText(instrumentation, 1, "10.00")
            })
            assertTrue(waitForUi(instrumentation) { expenseCategory.name in visibleText(instrumentation) })
            assertTrue(waitForUi(instrumentation) {
                scrollUntilVisible(instrumentation, "Description (optional)")
            })
            assertTrue("Could not set description. Inputs: ${describeInputNodes(instrumentation)}", waitForUi(instrumentation) {
                setEditableFieldText(instrumentation, 2, description)
            })
            assertTrue("Could not set notes. Inputs: ${describeInputNodes(instrumentation)}", waitForUi(instrumentation) {
                setEditableFieldText(instrumentation, 3, notes)
            })
            assertTrue(
                "Description and notes were not visible after entry. Inputs: ${describeInputNodes(instrumentation)}",
                waitForUi(instrumentation) {
                    description in visibleText(instrumentation) && notes in visibleText(instrumentation)
                }
            )
            assertTrue(waitForUi(instrumentation) {
                performClickWithScroll(instrumentation, "Save transaction")
            })

            val savedTransfer = runBlocking {
                try {
                    withTimeout(10_000L) {
                        repository.transactionsFlow.first { rows ->
                            rows.any { it.transactionType == TransactionType.TRANSFER && it.description == description }
                        }.first { it.transactionType == TransactionType.TRANSFER && it.description == description }
                    }
                } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                    throw AssertionError(
                        "Transfer was not recorded with the entered description. Visible text: ${visibleText(instrumentation)}",
                        timeout
                    )
                }
            }
            assertEquals(Money.parse("100.00"), savedTransfer.amount)
            assertEquals(Money.parse("10.00"), savedTransfer.fee)
            assertEquals(sourceId, savedTransfer.accountId)
            assertEquals(destinationId, savedTransfer.destinationAccountId)
            assertEquals(expenseCategory.id, savedTransfer.categoryId)
            assertEquals(notes, savedTransfer.notes)
            assertTrue("The edited transaction timestamp should be preserved", savedTransfer.timestamp >= startedAt)

            val balances = runBlocking {
                try {
                    withTimeout(10_000L) {
                        repository.accountsFlow.first { accounts ->
                            accounts.any { it.id == sourceId && it.currentLedgerBalance == Money.parse("390.00") } &&
                                accounts.any { it.id == destinationId && it.currentLedgerBalance == Money.parse("100.00") }
                        }
                    }
                } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                    throw AssertionError("Transfer persisted but both account balances did not update", timeout)
                }
            }
            assertEquals(Money.parse("390.00"), balances.first { it.id == sourceId }.currentLedgerBalance)
            assertEquals(Money.parse("100.00"), balances.first { it.id == destinationId }.currentLedgerBalance)
        } finally {
            scenario?.close()
            runBlocking { appLockPreferences.setEnabled(wasLockEnabled) }
        }
    }

    private fun waitForUi(
        instrumentation: android.app.Instrumentation,
        timeoutMillis: Long = 30_000L,
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
        val result = mutableSetOf<String>()
        try {
            collectText(root, result)
        } finally {
            root.recycle()
        }
        return result
    }

    private fun collectText(node: AccessibilityNodeInfo, result: MutableSet<String>) {
        node.text?.toString()?.takeIf(String::isNotBlank)?.let(result::add)
        node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(result::add)
        node.hintText?.toString()?.takeIf(String::isNotBlank)?.let(result::add)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectText(child, result)
            } finally {
                child.recycle()
            }
        }
    }

    private fun performClick(instrumentation: android.app.Instrumentation, label: String): Boolean {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return false
        return try {
            clickNode(root, label)
        } finally {
            root.recycle()
        }
    }

    private fun setEditableFieldText(
        instrumentation: android.app.Instrumentation,
        fieldIndex: Int,
        value: String
    ): Boolean {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return false
        return try {
            setEditableFieldText(root, fieldIndex, value, intArrayOf(0))
        } finally {
            root.recycle()
        }
    }

    private fun setEditableFieldText(
        node: AccessibilityNodeInfo,
        fieldIndex: Int,
        value: String,
        currentIndex: IntArray
    ): Boolean {
        val supportsSetText = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        if (node.isEditable || supportsSetText) {
            val current = currentIndex[0]++
            if (current == fieldIndex) {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }
                return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (setEditableFieldText(child, fieldIndex, value, currentIndex)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun describeInputNodes(instrumentation: android.app.Instrumentation): String {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return "no active root"
        return try {
            val descriptions = mutableListOf<String>()
            collectInputDescriptions(root, descriptions)
            descriptions.joinToString(" | ")
        } finally {
            root.recycle()
        }
    }

    private fun collectInputDescriptions(node: AccessibilityNodeInfo, result: MutableList<String>) {
        val supportsSetText = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        if (node.isEditable || supportsSetText) {
            result += "class=${node.className},text=${node.text},hint=${node.hintText},desc=${node.contentDescription},editable=${node.isEditable},setText=$supportsSetText"
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectInputDescriptions(child, result)
            } finally {
                child.recycle()
            }
        }
    }

    private fun performClickWithScroll(
        instrumentation: android.app.Instrumentation,
        label: String
    ): Boolean {
        repeat(8) {
            if (performClick(instrumentation, label)) return true
            if (!performScrollForward(instrumentation)) return false
            instrumentation.waitForIdleSync()
        }
        return false
    }

    private fun performScrollForward(instrumentation: android.app.Instrumentation): Boolean {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return false
        return try {
            scrollForward(root)
        } finally {
            root.recycle()
        }
    }

    private fun scrollUntilVisible(
        instrumentation: android.app.Instrumentation,
        label: String
    ): Boolean {
        repeat(8) {
            if (label in visibleText(instrumentation)) return true
            if (!performScrollForward(instrumentation)) return false
            instrumentation.waitForIdleSync()
        }
        return label in visibleText(instrumentation)
    }

    private fun scrollForward(node: AccessibilityNodeInfo): Boolean {
        if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (scrollForward(child)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun clickNode(node: AccessibilityNodeInfo, label: String): Boolean {
        val matches = node.text?.toString()?.contains(label, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(label, ignoreCase = true) == true
        if (matches) {
            var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
            while (candidate != null) {
                val current = candidate
                val clicked = current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val parent = if (clicked) null else current.parent
                current.recycle()
                if (clicked) return true
                candidate = parent
            }
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (clickNode(child, label)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }
}
