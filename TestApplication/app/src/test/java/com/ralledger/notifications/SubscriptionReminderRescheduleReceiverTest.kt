package com.ralledger.notifications

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionReminderRescheduleReceiverTest {
    @Test
    fun acceptsOnlyDeclaredSystemRescheduleActions() {
        assertTrue(isAllowedSubscriptionReminderRescheduleAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(isAllowedSubscriptionReminderRescheduleAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(isAllowedSubscriptionReminderRescheduleAction(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(isAllowedSubscriptionReminderRescheduleAction(Intent.ACTION_DATE_CHANGED))
    }

    @Test
    fun ignoresMissingAndUnrelatedActions() {
        assertFalse(isAllowedSubscriptionReminderRescheduleAction(null))
        assertFalse(isAllowedSubscriptionReminderRescheduleAction("com.example.UNTRUSTED"))
    }
}
