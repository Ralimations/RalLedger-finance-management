package com.ralledger.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPreferencesTest {
    @Test
    fun missingPreferenceDefaultsToDisabled() {
        assertFalse(decodeAppLockEnabled(null))
    }

    @Test
    fun storedPreferenceIsRespected() {
        assertTrue(decodeAppLockEnabled(true))
        assertFalse(decodeAppLockEnabled(false))
    }
}
