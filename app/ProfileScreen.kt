package com.example.software_eng

import android.telecom.Call
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    currentUsername: String,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf(currentUsername) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFECE8E1))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(20.dp))
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(24.dp)
            ) {
                Text("Update Profile", fontSize = 22.sp, color = Color.Black)

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (password != confirmPassword) {
                            message = "Passwords do not match."
                            return@Button
                        }

                        updateProfile(username, password) {
                            message = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1C),
                        contentColor = Color.White
                    )
                ) {
                    Text("Save Changes")
                }

                Spacer(Modifier.height(12.dp))

                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// ─────────── Profile Update API Call ───────────
fun updateProfile(
    newUsername: String,
    newPassword: String,
    onResult: (String) -> Unit
) {
    val json = JSONObject().apply {
        put("username", newUsername)
        put("password", newPassword)
    }

    val body = json.toString().toRequestBody("application/json".toMediaType())

    val req = Request.Builder()
        .url("$BASE_URL/user/profile")
        .patch(body)
        .addHeader("Content-Type", "application/json")
        .apply { TokenManager.addAuthHeader() }
        .build()

    OkHttpClient().newCall(req).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onResult("Profile update failed: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            val resBody = response.body?.string()
            println("PROFILE UPDATE RESPONSE → ${response.code} :: $resBody")

            if (response.isSuccessful) {
                onResult("Profile updated successfully.")
            } else {
                onResult("Update failed: $resBody")
            }
        }
    })
}
