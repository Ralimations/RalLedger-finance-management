package com.ralledger.ui.navigation

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.ralledger.MainActivity
import com.ralledger.RalLedgerApplication
import com.ralledger.testing.markWelcomeCompleteForMainActivityTest
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

class QuickAddDescriptionRestorationComposeTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val prepareApp = object : ExternalResource() {
        override fun before() {
            markWelcomeCompleteForMainActivityTest()
            val application = InstrumentationRegistry.getInstrumentation().targetContext
                .applicationContext as RalLedgerApplication
            runBlocking { application.container.appLockPreferences.setEnabled(false) }
        }

        override fun after() {
            val application = InstrumentationRegistry.getInstrumentation().targetContext
                .applicationContext as RalLedgerApplication
            runBlocking { application.container.appLockPreferences.setEnabled(false) }
        }
    }

    @get:Rule
    val rule: TestRule = RuleChain.outerRule(prepareApp).around(composeRule)

    @Test
    fun descriptionInputSurvivesActivityRecreation() {
        composeRule.onNodeWithContentDescription("Quick actions").performClick()
        composeRule.onNodeWithText("Expense").performClick()
        composeRule.onNodeWithText("Add details (optional)").performClick()

        composeRule.onNodeWithTag("quick-add-description")
            .performScrollTo()
            .performTextInput("Remember this purchase")
        composeRule.onNodeWithTag("quick-add-description").assertTextContains("Remember this purchase")

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("quick-add-description")
            .performScrollTo()
            .assertTextContains("Remember this purchase")
    }
}
