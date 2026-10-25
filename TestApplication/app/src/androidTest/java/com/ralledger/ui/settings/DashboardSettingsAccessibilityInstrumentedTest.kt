package com.ralledger.ui.settings

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.MainActivity
import com.ralledger.ui.dashboard.DashboardPreferences
import com.ralledger.ui.dashboard.DashboardWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class DashboardSettingsAccessibilityInstrumentedTest {
    @Test
    fun dashboardWidgetSwitchExposesItsLabelAndStateAsOneControl() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appLockPreferences = AppLockPreferences(instrumentation.targetContext)
        runBlocking { appLockPreferences.setEnabled(false) }

        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)

            val settingsReachable = waitForUi(instrumentation) {
                performClickOnLabeledNode(instrumentation, "Settings")
            }
            assertTrue(
                "Settings must be reachable from the app bar; visible: ${visibleAccessibilityText(instrumentation)}",
                settingsReachable
            )
            val dashboardReachable = waitForUi(instrumentation) {
                performClickOnLabeledNode(instrumentation, "Dashboard")
            }
            assertTrue(
                "The Dashboard settings section must be reachable; visible: ${visibleAccessibilityText(instrumentation)}",
                dashboardReachable
            )
            assertTrue("The dashboard widget controls must appear", waitForUi(instrumentation) {
                "Dashboard widgets" in visibleAccessibilityText(instrumentation)
            })
            assertTrue(
                "Recent transactions must be announced as a labeled switch; controls: ${accessibilityControls(instrumentation)}",
                waitForUi(instrumentation) {
                    hasLabeledCheckableNode(instrumentation, "Recent transactions")
                }
            )
        } finally {
            scenario?.close()
            runBlocking { appLockPreferences.setEnabled(false) }
        }
    }

    @Test
    fun dashboardWidgetOrderPersistsThroughActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val appLockPreferences = AppLockPreferences(targetContext)
        val dashboardPreferences = DashboardPreferences(targetContext)
        runBlocking { appLockPreferences.setEnabled(false) }
        val originalSettings = runBlocking { dashboardPreferences.settings.first() }

        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            assertTrue(waitForUi(instrumentation) { performClickOnLabeledNode(instrumentation, "Settings") })
            assertTrue(waitForUi(instrumentation) { performClickOnLabeledNode(instrumentation, "Dashboard") })
            assertTrue(
                "Dashboard widget settings should finish opening; visible: ${visibleAccessibilityText(instrumentation)}",
                waitForUi(instrumentation) { "Dashboard widgets" in visibleAccessibilityText(instrumentation) }
            )

            val initialOrder = dashboardWidgetOrder(instrumentation)
            assertTrue(
                "Dashboard widget titles should be exposed in order; found $initialOrder",
                initialOrder.size >= 2
            )
            val expectedOrder = initialOrder.toMutableList().apply {
                this[0] = initialOrder[1]
                this[1] = initialOrder[0]
            }
            assertTrue(
                "The first widget should move down one position",
                waitForUi(instrumentation) {
                    performClickOnLabeledNode(instrumentation, "Move ${initialOrder.first().title} down")
                }
            )
            assertTrue("The moved order should appear before restarting", waitForUi(instrumentation) {
                dashboardWidgetOrder(instrumentation) == expectedOrder
            })

            scenario.recreate()
            assertTrue(waitForUi(instrumentation) { performClickOnLabeledNode(instrumentation, "Settings") })
            assertTrue(waitForUi(instrumentation) { performClickOnLabeledNode(instrumentation, "Dashboard") })
            assertTrue(waitForUi(instrumentation) {
                "Dashboard widgets" in visibleAccessibilityText(instrumentation)
            })
            assertTrue(
                "The dashboard widget order should persist after Activity recreation",
                waitForUi(instrumentation) { dashboardWidgetOrder(instrumentation) == expectedOrder }
            )
        } finally {
            scenario?.close()
            runBlocking {
                appLockPreferences.setEnabled(false)
                dashboardPreferences.restore(originalSettings)
            }
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

    private fun performClickOnLabeledNode(
        instrumentation: android.app.Instrumentation,
        label: String
    ): Boolean {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return false
        return try {
            clickLabeledNode(root, label)
        } finally {
            root.recycle()
        }
    }

    private fun clickLabeledNode(node: AccessibilityNodeInfo, label: String): Boolean {
        val matches = node.text?.toString()?.contains(label, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(label, ignoreCase = true) == true
        if (matches && clickNodeOrClickableAncestor(node)) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (clickLabeledNode(child, label)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun clickNodeOrClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        while (candidate != null) {
            val current = candidate
            val clicked = current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val parent = if (clicked) null else current.parent
            current.recycle()
            if (clicked) return true
            candidate = parent
        }
        return false
    }

    private fun visibleAccessibilityText(instrumentation: android.app.Instrumentation): Set<String> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptySet()
        val text = mutableSetOf<String>()
        try {
            collectText(root, text)
        } finally {
            root.recycle()
        }
        return text
    }

    private fun accessibilityControls(instrumentation: android.app.Instrumentation): List<String> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        val controls = mutableListOf<String>()
        try {
            collectControls(root, controls)
        } finally {
            root.recycle()
        }
        return controls
    }

    private fun collectControls(node: AccessibilityNodeInfo, output: MutableList<String>) {
        val text = node.text?.toString().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        if (node.isClickable || node.isCheckable ||
            text.contains("Recent", ignoreCase = true) || description.contains("Recent", ignoreCase = true)
        ) {
            output += "${node.className}[text=$text, description=$description, clickable=${node.isClickable}, checkable=${node.isCheckable}, checked=${node.isChecked}]"
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectControls(child, output)
            } finally {
                child.recycle()
            }
        }
    }

    private fun hasLabeledCheckableNode(
        instrumentation: android.app.Instrumentation,
        label: String
    ): Boolean {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return false
        return try {
            findLabeledCheckableNode(root, label)
        } finally {
            root.recycle()
        }
    }

    private fun dashboardWidgetOrder(instrumentation: android.app.Instrumentation): List<DashboardWidget> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        val order = mutableListOf<DashboardWidget>()
        try {
            collectDashboardWidgetOrder(root, order)
        } finally {
            root.recycle()
        }
        return order
    }

    private fun collectDashboardWidgetOrder(node: AccessibilityNodeInfo, output: MutableList<DashboardWidget>) {
        val text = node.text?.toString()
        DashboardWidget.entries.firstOrNull { it.title == text }?.let(output::add)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectDashboardWidgetOrder(child, output)
            } finally {
                child.recycle()
            }
        }
    }

    private fun findLabeledCheckableNode(node: AccessibilityNodeInfo, label: String): Boolean {
        val matches = node.text?.toString()?.contains(label, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(label, ignoreCase = true) == true
        if (node.isCheckable && matches) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (findLabeledCheckableNode(child, label)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun collectText(node: AccessibilityNodeInfo, output: MutableSet<String>) {
        node.text?.toString()?.takeIf(String::isNotBlank)?.let(output::add)
        node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(output::add)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectText(child, output)
            } finally {
                child.recycle()
            }
        }
    }
}
