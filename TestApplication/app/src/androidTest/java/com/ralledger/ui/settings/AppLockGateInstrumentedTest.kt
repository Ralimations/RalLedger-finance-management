package com.ralledger.ui.settings

import android.app.KeyguardManager
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class AppLockGateInstrumentedTest {
    @Test
    fun navigationUsesFullAccessibleDestinationName() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preferences = AppLockPreferences(instrumentation.targetContext)
        runBlocking { preferences.setEnabled(false) }

        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            var descriptions = emptySet<String>()
            val accountsTabNamed = waitForUi(instrumentation) {
                descriptions = visibleContentDescriptions(instrumentation)
                "Accounts" in descriptions
            }
            assertTrue(
                "The account tab must keep its full accessible name when its visual label adapts",
                accountsTabNamed
            )
        } finally {
            scenario?.close()
            runBlocking { preferences.setEnabled(false) }
        }
    }

    @Test
    fun enabledLockShowsGateAndSecuresWindow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assumeFalse(
            "This UI test requires an emulator without a configured credential",
            keyguard.isDeviceSecure
        )

        val preferences = AppLockPreferences(context)
        runBlocking { preferences.setEnabled(true) }

        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)

            var visibleText = emptySet<String>()
            val gateVisible = waitForUi(instrumentation) {
                visibleText = visibleAccessibilityText(instrumentation)
                "PesoMind is locked" in visibleText
            }
            assertTrue(
                "Expected the app-lock gate in the visible UI, found: $visibleText",
                gateVisible
            )

            var secureWindow = false
            scenario.onActivity { activity ->
                secureWindow = activity.window.attributes.flags and
                    WindowManager.LayoutParams.FLAG_SECURE != 0
            }
            assertTrue(
                "The locked app window must block screenshots and recents previews",
                secureWindow
            )
        } finally {
            scenario?.close()
            runBlocking { preferences.setEnabled(false) }
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

    private fun visibleAccessibilityText(
        instrumentation: android.app.Instrumentation
    ): Set<String> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptySet()
        val text = mutableSetOf<String>()
        try {
            collectText(root, text)
        } finally {
            root.recycle()
        }
        return text
    }

    private fun visibleContentDescriptions(
        instrumentation: android.app.Instrumentation
    ): Set<String> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptySet()
        val descriptions = mutableSetOf<String>()
        try {
            collectDescriptions(root, descriptions)
        } finally {
            root.recycle()
        }
        return descriptions
    }

    private fun collectDescriptions(node: AccessibilityNodeInfo, descriptions: MutableSet<String>) {
        node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(descriptions::add)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                collectDescriptions(child, descriptions)
            } finally {
                child.recycle()
            }
        }
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
}
