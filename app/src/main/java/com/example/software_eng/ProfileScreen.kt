package com.example.software_eng

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
@Composable
fun ProfileScreen(
    userId: Int,
    usernameInitial: String, // This is passed dynamically when the user logs in
    emailInitial: String,    // Initial email of the logged-in user
    onProfileUpdateSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    var username by remember { mutableStateOf(usernameInitial) } // Dynamically set the username
    var email by remember { mutableStateOf(emailInitial) } // Dynamically set the email
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) } // For loading state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {


        // Username Field (user can change their username here)
        OutlinedTextField(
            value = username,
            onValueChange = { username = it }, // Update username dynamically
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Email Field (user can change their email here)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Update Button
        Button(
            onClick = {
                if (username.isNotEmpty() && email.isNotEmpty()) {
                    isLoading = true
                    updateUserProfile(userId, username, email) { success, result ->
                        isLoading = false
                        if (success) {
                            onProfileUpdateSuccess() // Notify success
                        } else {
                            errorMessage = result ?: "Update failed"
                            onError(errorMessage) // Handle error
                        }
                    }
                } else {
                    errorMessage = "Please enter both username and email"
                    onError(errorMessage) // Handle missing fields
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading // Disable button while loading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Update Profile")
            }
        }

        // Error Message
        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                color = Color.Red,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

fun updateUserProfile(
    userId: Int,
    username: String,
    email: String,
    onResult: (Boolean, String?) -> Unit
) {
    val json = JSONObject().apply {
        put("username", username)
        put("email", email)
    }

    val body = json.toString().toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("$BASE_URL/user/$userId") // Update user's profile
        .patch(body)
        .addHeader("Content-Type", "application/json") // No token needed
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onResult(false, "Failed: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()
            println("PROFILE UPDATE RESPONSE → ${response.code} :: $responseBody")

            if (!response.isSuccessful || responseBody == null) {
                onResult(false, "Failed to update profile")
                return
            }

            try {
                val data = JSONObject(responseBody).getJSONObject("data")
                val updatedUsername = data.getString("username")
                val updatedEmail = data.getString("email")

                // Log the updated profile for demonstration
                println("Updated Profile: $updatedUsername, $updatedEmail")
                onResult(true, null) // Successfully updated
            } catch (e: Exception) {
                onResult(false, "Error parsing response")
            }
        }
    })
}
