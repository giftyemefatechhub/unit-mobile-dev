package com.example.software_eng

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {
    // done by me - Gifty
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun authScreen_showsLoginFieldsAndToggleWorks() {
        composeTestRule.setContent {
            AuthScreen(onLoginSuccess = {})
        }

        // Initial state: Login screen
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()

        // Toggle to registration
        composeTestRule.onNodeWithText("Don't have an account? Register").performClick()

        // Check registration fields
        composeTestRule.onNodeWithText("Email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Register").assertIsDisplayed()

        // Toggle back to login
        composeTestRule.onNodeWithText("Already have an account? Login").performClick()

        // Confirm login screen restored
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
        composeTestRule.onNodeWithText("Email").assertDoesNotExist()
    }
}
