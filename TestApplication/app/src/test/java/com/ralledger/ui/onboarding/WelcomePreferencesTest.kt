package com.ralledger.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomePreferencesTest {
    @Test
    fun freshInstallShowsWelcomeUntilCompletionIsStored() {
        assertFalse(resolveWelcomeCompletion(storedValue = null, existingInstall = false))
        assertTrue(resolveWelcomeCompletion(storedValue = true, existingInstall = false))
    }

    @Test
    fun existingInstallWithoutWelcomeMarkerSkipsWelcome() {
        assertTrue(resolveWelcomeCompletion(storedValue = null, existingInstall = true))
    }

    @Test
    fun storedCompletionRemainsAuthoritativeForReturningUsers() {
        assertTrue(resolveWelcomeCompletion(storedValue = true, existingInstall = true))
    }
}
