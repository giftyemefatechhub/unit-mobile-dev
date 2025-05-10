package com.example.software_eng

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class AuthLogicTest {
    // done by me - Gifty
    @Test
    fun testUserRegistration_and_Login_successful() {
        val testUsername = "testuser_${UUID.randomUUID()}"  // unique
        val testEmail = "$testUsername@example.com"
        val testPassword = "Aa1@Test!"  //  backend-compliant

        // Register
        var registrationResponse: String? = null
        val latch1 = Object()
        registerUser(testUsername, testEmail, testPassword) {
            synchronized(latch1) {
                registrationResponse = it
                latch1.notify()
            }
        }

        synchronized(latch1) {
            if (registrationResponse == null) latch1.wait(5000)
        }

        assertNotNull("Registration did not complete", registrationResponse)
        assertTrue(
            "Registration failed: $registrationResponse",
            registrationResponse!!.contains("success", ignoreCase = true)
        )

        // Login
        var loginSuccess: Boolean? = null
        var loginError: String? = null
        val latch2 = Object()
        loginUser(testUsername, testPassword) { success, error ->
            synchronized(latch2) {
                loginSuccess = success
                loginError = error
                latch2.notify()
            }
        }

        synchronized(latch2) {
            if (loginSuccess == null) latch2.wait(5000)
        }

        assertTrue("Login failed: $loginError", loginSuccess == true)
        assertNotNull("Token should not be null after login", TokenManager.accessToken)
    }
}
