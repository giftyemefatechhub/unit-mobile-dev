package com.example.software_eng

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthErrorHandlingTest {
    // done by me - Gifty
    @Test
    fun testInvalidLogin_failsWithWrongPassword() {
        val testUsername = "nonexistentuser"
        val wrongPassword = "WrongPass123!"

        var loginSuccess: Boolean? = null
        var loginError: String? = null
        val latch = Object()

        loginUser(testUsername, wrongPassword) { success, error ->
            synchronized(latch) {
                loginSuccess = success
                loginError = error
                latch.notify()
            }
        }

        synchronized(latch) {
            if (loginSuccess == null) latch.wait(5000)
        }

        assertFalse("Login should fail for invalid credentials", loginSuccess == true)
        assertNotNull("Expected error message for invalid login", loginError)
    }
}
