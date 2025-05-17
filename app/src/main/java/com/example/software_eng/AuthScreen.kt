package com.example.software_eng

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
// done by me - Gifty
// ─────────── Token Manager ───────────
object TokenManager {
    var accessToken: String? = null
}

// ─────────── Color Palette ───────────
private val BeigeGrey = Color(0xFFECE8E1)
private val Black = Color(0xFF1C1C1C)
private val White = Color.White

// ─────────── Auth UI ───────────
@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit
) {
    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BeigeGrey)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .shadow(12.dp, RoundedCornerShape(20.dp))
                .background(White, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text(
                text = "HomeSync",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isLogin) "Login to your space" else "Join HomeSync today",
                fontSize = 18.sp,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(visible = !isLogin) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!isLogin) Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (isLogin) {
                        loginUser(username, password) { success, result ->
                            if (success) {
                                onLoginSuccess()
                                message = "Login success 🎉"
                            } else {
                                message = result ?: "Login failed"
                            }
                        }
                    } else {
                        registerUser(username, email, password) {
                            message = it
                            if (it.contains("success", ignoreCase = true)) isLogin = true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Black, contentColor = White)
            ) {
                Text(if (isLogin) "Login" else "Register", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { isLogin = !isLogin },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isLogin) "Don't have an account? Register"
                    else "Already have an account? Login",
                    color = Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (message.isNotBlank()) {
                Text(
                    text = message,
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// ─────────── Login Logic ───────────
fun loginUser(
    username: String,
    password: String,
    onResult: (Boolean, String?) -> Unit
) {
    val json = JSONObject().apply {
        put("username", username)
        put("password", password)
    }

    val body = json.toString().toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("$BASE_URL/user/login")
        .post(body)
        .addHeader("Content-Type", "application/json")
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onResult(false, "Login failed: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()
            println("LOGIN RESPONSE → ${response.code} :: $responseBody")

            if (!response.isSuccessful || responseBody == null) {
                onResult(false, "Invalid credentials")
                return
            }

            try {
                val token = JSONObject(responseBody)
                    .getJSONObject("data")
                    .getString("accessToken")

                TokenManager.accessToken = token
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, "Error parsing login response")
            }
        }
    })
}

// ─────────── Registration Logic ───────────
fun registerUser(
    username: String,
    email: String,
    password: String,
    onResult: (String) -> Unit
) {
    val json = JSONObject().apply {
        put("username", username)
        put("email", email)
        put("password", password)
    }

    println("Registering user: $username | $email | $password")

    val requestBody = json.toString().toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("$BASE_URL/user/register")
        .post(requestBody)
        .addHeader("Content-Type", "application/json")
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onResult("Registration failed: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string()
            println("REGISTER RESPONSE → ${response.code} :: $body")

            if (response.isSuccessful) {
                onResult("Registration success. Please login.")
            } else {
                onResult("Registration failed: $body")
            }
        }
    })
}
