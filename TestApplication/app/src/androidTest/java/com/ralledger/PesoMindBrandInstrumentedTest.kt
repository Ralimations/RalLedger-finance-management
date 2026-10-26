package com.ralledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PesoMindBrandInstrumentedTest {
    @Test
    fun launcherBrandChangesWithoutChangingTheInstallIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("com.ralledger", context.packageName)
        assertEquals("PesoMind", context.packageManager.getApplicationLabel(context.applicationInfo).toString())
        assertNotNull(context.packageManager.getApplicationIcon(context.applicationInfo))
    }
}
