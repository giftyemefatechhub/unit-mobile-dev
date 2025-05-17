package com.example.software_eng


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Background Image
        Image(
            painter = painterResource(id = R.drawable.back_mob),
            contentDescription = "Background Image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Foreground Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x80000000)) // Semi-transparent dark overlay
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo Image
            Image(
                painter = painterResource(id = R.drawable.homesync_logo),
                contentDescription = "HomeSync Logo",
                modifier = Modifier
                    .size(width = 248.dp, height = 232.dp)
                    .padding(bottom = 16.dp)
            )

            // App Name
            Text(
                text = "HomeSync",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Login Button
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1C),
                    contentColor = Color(0xFFF5F5DC)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("Log In")
            }

            // Register Button
            Button(
                onClick = onRegisterClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF5F5DC),
                    contentColor = Color(0xFF1C1C1C)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Register")
            }
        }
    }
}
