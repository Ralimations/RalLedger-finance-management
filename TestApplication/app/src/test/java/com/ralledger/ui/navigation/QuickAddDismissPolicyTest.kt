package com.ralledger.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickAddDismissPolicyTest {
    @Test
    fun emptyFormDismissesWithoutConfirmation() {
        assertEquals(QuickAddDismissAction.DISMISS, quickAddDismissAction(hasUserEdited = false, isSaving = false))
    }

    @Test
    fun editedFormRequiresDiscardConfirmation() {
        assertEquals(QuickAddDismissAction.CONFIRM_DISCARD, quickAddDismissAction(hasUserEdited = true, isSaving = false))
    }

    @Test
    fun savingFormCannotBeDismissed() {
        assertEquals(QuickAddDismissAction.IGNORE, quickAddDismissAction(hasUserEdited = true, isSaving = true))
    }
}
