package com.ralledger.ui.navigation

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.MainActivity
import com.ralledger.RalLedgerApplication
import com.ralledger.ui.settings.AppLockPreferences
import com.ralledger.testing.markWelcomeCompleteForMainActivityTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class QuickAddProgressiveDisclosureInstrumentedTest {
    @Test
    fun unsavedQuickAddDraftRestoresAcrossActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as RalLedgerApplication
        val repository = application.container.repository
        val appLockPreferences = application.container.appLockPreferences
        val initialEntryIds = runBlocking { repository.transactionsFlow.first().map { it.journalEntryId }.toSet() }
        markWelcomeCompleteForMainActivityTest()
        runBlocking { appLockPreferences.setEnabled(false) }
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Quick actions") })
            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Expense") })
            assertTrue(waitForUi(instrumentation) { setEditableFieldText(instrumentation, 0, "12.34") })
            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Add details (optional)") })
            assertTrue(waitForUi(instrumentation) { setEditableFieldText(instrumentation, 1, "Draft Merchant") })
            assertTrue(waitForUi(instrumentation) { scrollUntilVisible(instrumentation, "Notes (optional)") })
            assertTrue(
                "Could not edit the visible Notes field. Inputs: ${describeInputNodes(instrumentation)}",
                waitForUi(instrumentation) { setEditableFieldText(instrumentation, 2, "Restored note") }
            )
            assertTrue(waitForUi(instrumentation) { "Restored note" in editableFieldTexts(instrumentation) })
            assertTrue(waitForUi(instrumentation) { scrollBackwardUntilVisible(instrumentation, "Add expense") })

            scenario.recreate()

            val restored = waitForUi(instrumentation) {
                "Add expense" in visibleText(instrumentation) &&
                    "Hide details" in visibleText(instrumentation) &&
                    "12.34" in editableFieldTexts(instrumentation) &&
                    "Draft Merchant" in editableFieldTexts(instrumentation)
            }
            assertTrue(
                "Draft did not restore. visible=${visibleText(instrumentation)} inputs=${editableFieldTexts(instrumentation)}",
                restored
            )
            assertTrue(waitForUi(instrumentation) { scrollUntilVisible(instrumentation, "Notes (optional)") })
            assertTrue(
                "Draft note did not restore. inputs=${editableFieldTexts(instrumentation)}",
                "Restored note" in editableFieldTexts(instrumentation)
            )
            assertTrue(waitForUi(instrumentation) { performClickWithScroll(instrumentation, "Cancel") })
            assertTrue(waitForUi(instrumentation) { "Discard this transaction?" in visibleText(instrumentation) })
            assertTrue(waitForUi(instrumentation) { performClick(instrumentation, "Discard") })
            val finalEntryIds = runBlocking { repository.transactionsFlow.first().map { it.journalEntryId }.toSet() }
            assertEquals("Restoring and discarding the draft must not post a transaction", initialEntryIds, finalEntryIds)
        } finally {
            scenario?.close()
            runBlocking { appLockPreferences.setEnabled(false) }
        }
    }

    @Test
    fun optionalDetailsRevealEditableDateAndTimeWithoutPosting() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appLockPreferences = AppLockPreferences(instrumentation.targetContext)
        markWelcomeCompleteForMainActivityTest()
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
                scrollUntilVisible(instrumentation, "Description (optional)")
            })

            assertTrue(waitForUi(instrumentation) { performClickWithScroll(instrumentation, "Date ·") })
            assertTrue(waitForUi(instrumentation) { "Set date" in visibleText(instrumentation) })
            assertTrue(performClick(instrumentation, "Cancel"))

            assertTrue(waitForUi(instrumentation) { performClickWithScroll(instrumentation, "Time ·") })
            assertTrue(waitForUi(instrumentation) { "Set time" in visibleText(instrumentation) })
            assertTrue(performClick(instrumentation, "Cancel"))
        } finally {
            scenario?.close()
            runBlocking { appLockPreferences.setEnabled(false) }
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

    private fun editableFieldTexts(instrumentation: android.app.Instrumentation): List<String> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        return try {
            val values = mutableListOf<String>()
            collectEditableFieldTexts(root, values)
            values
        } finally {
            root.recycle()
        }
    }

    private fun collectEditableFieldTexts(node: AccessibilityNodeInfo, result: MutableList<String>) {
        val supportsSetText = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        if (node.isEditable || supportsSetText) result += node.text?.toString().orEmpty()
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectEditableFieldTexts(child, result)
            } finally {
                child.recycle()
            }
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

    private fun scrollBackwardUntilVisible(
        instrumentation: android.app.Instrumentation,
        label: String
    ): Boolean {
        repeat(12) {
            if (label in visibleText(instrumentation)) return true
            if (!performScrollBackward(instrumentation)) return false
            instrumentation.waitForIdleSync()
        }
        return label in visibleText(instrumentation)
    }

    private fun performScrollBackward(instrumentation: android.app.Instrumentation): Boolean {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return false
        return try {
            scrollBackward(root)
        } finally {
            root.recycle()
        }
    }

    private fun scrollBackward(node: AccessibilityNodeInfo): Boolean {
        if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (scrollBackward(child)) return true
            } finally {
                child.recycle()
            }
        }
        return false
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
