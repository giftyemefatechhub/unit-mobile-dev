package com.example.software_eng


import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
// done by me - Gifty
@RunWith(AndroidJUnit4::class)
class ActivityLogScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeLogs = listOf(
        "Logged in at 12:00 PM",
        "Toggled Lamp ON",
        "Toggled Heater OFF"
    )

    @Before
    fun setup() {
        activityLog.clear()
        activityLog.addAll(fakeLogs)
    }

    @Test
    fun activityLogScreen_displaysLogsAndBackButton() {
        var backClicked = false

        composeTestRule.setContent {
            ActivityLogScreen(onBack = { backClicked = true })
        }

        // Check title exists
        composeTestRule.onNodeWithText("Activity Log").assertIsDisplayed()

        // Check all logs are displayed in reversed order
        fakeLogs.reversed().forEach { log ->
            composeTestRule.onNodeWithText("• $log").assertIsDisplayed()
        }

        // Click back button and verify callback triggers
        composeTestRule.onNode(hasContentDescription("Back")).performClick()
        assert(backClicked)
    }
}
