package com.ralledger.ui.navigation

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.MainActivity
import com.ralledger.ui.settings.AppLockPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

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
