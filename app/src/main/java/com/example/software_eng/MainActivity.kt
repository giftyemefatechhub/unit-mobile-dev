package com.example.software_eng

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.software_eng.ui.theme.Software_engTheme
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

// Robel work start
// Data class for Device (can be moved to a separate Device.kt file)
data class Device(
    val id: Int,
    val name: String,
    val description: String,
    val status: Boolean,
    val type: String,
    val value: Double
)

// change this for connection to remote server IPV4 address
const val BASE_URL = "http://192.168.0.100:5000"

class MainActivity : ComponentActivity() {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // -> Keep socket manager as is
        SocketManager.connect()

        enableEdgeToEdge()
        setContent {
            // state to hold current theme mode
            var darkMode by remember { mutableStateOf(false) }

            Software_engTheme(darkTheme = darkMode) {
                // 🔐 Injected login/auth flow (Gifty's logic)
                var isLoggedIn by remember { mutableStateOf(false) }

                if (isLoggedIn) {
                    DeviceUI(
                        onLogout = { isLoggedIn = false },
                        darkMode = darkMode,
                        onToggleTheme = { darkMode = !darkMode }
                    )
                } else {
                    AuthScreen(onLoginSuccess = {
                        isLoggedIn = true
                    })
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.disconnect()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DeviceUI(
        onLogout: () -> Unit,
        darkMode: Boolean,
        onToggleTheme: () -> Unit
    ) {
        var allDevices by remember { mutableStateOf<List<Device>>(emptyList()) }
        var selectedDevices by remember { mutableStateOf<List<Device>>(emptyList()) }
        var showAddDialog by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("Loading...") }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            // initial load & subscribe to updates
            allDevices = fetchDevices()
            SocketManager.onUpdate {
                scope.launch {
                    allDevices = fetchDevices()
                    // keep selectedDevices up to date
                    selectedDevices = selectedDevices.map { sel ->
                        allDevices.firstOrNull { it.id == sel.id } ?: sel
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Devices") },
                    actions = {
                        // theme toggle button
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle theme"
                            )
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, "Add Device")
                        }
                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Logout", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                if (selectedDevices.isEmpty()) {
                    Text("No devices added. Tap + to add one.")
                }
                Spacer(modifier = Modifier.height(12.dp))

                selectedDevices.forEach { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Name: ${device.name}")
                            Text("Type: ${device.type}")
                            Text("Status: ${if (device.status) "ON" else "OFF"}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                toggleDevice(device.id, device.name, device.status) { result ->
                                    statusMessage = result
                                    SocketManager.emitUpdate(device.name, !device.status)
                                    // immediate refresh of this one device
                                    scope.launch(Dispatchers.IO) {
                                        val updated = fetchDevices().first { it.id == device.id }
                                        withContext(Dispatchers.Main) {
                                            selectedDevices = selectedDevices.map {
                                                if (it.id == updated.id) updated else it
                                            }
                                        }
                                    }
                                }
                            }) {
                                Text("Toggle")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Status: $statusMessage")
            }

            // Add‐Device Dialog
            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Add a Device") },
                    text = {
                        LazyColumn {
                            items(allDevices.filter { dev -> selectedDevices.none { it.id == dev.id } }) { dev ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(dev.name, modifier = Modifier.weight(1f))
                                    Button(onClick = {
                                        selectedDevices = selectedDevices + dev
                                    }) {
                                        Text("Add")
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Done")
                        }
                    }
                )
            }
        }
    }

    private suspend fun fetchDevices(): List<Device> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/device/all")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(bodyString)
            val arr = root.getJSONObject("data").getJSONArray("devices")
            val list = mutableListOf<Device>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += Device(
                    id = o.getInt("id"),
                    name = o.getString("name"),
                    description = o.getString("description"),
                    status = o.getBoolean("status"),
                    type = o.getString("type"),
                    value = o.getDouble("value")
                )
            }
            list
        }
    }

    private fun toggleDevice(id: Int, deviceName: String, currentStatus: Boolean, onResult: (String) -> Unit) {
        val newStatus = !currentStatus
        val json = JSONObject().apply {
            put("name", deviceName)
            put("status", newStatus)
        }
        Log.d("ToggleDevice", "Sending JSON to /device/$id: $json")

        val reqBody = json.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$BASE_URL/device/$id")
            .patch(reqBody)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("Failed: ${e.message}")
                Log.e("ToggleDevice", "Request failed", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResult("Device $id updated: ${it.code}")
                    Log.d("ToggleDevice", "Response: ${it.body?.string()}")
                }
            }
        })
    }
} // Robel work end
