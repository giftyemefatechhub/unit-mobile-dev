package com.example.software_eng

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import java.util.Locale

// ─────────── Robel work start ───────────
data class Device(
    val id: Int,
    val name: String,
    val description: String,
    val status: Boolean,
    val type: String,
    val value: Double
)

const val BASE_URL = "http://192.168.0.100:5001"
private const val REQ_VOICE = 42              // voice recognizer request-code

class MainActivity : ComponentActivity() {

    /** latest device list for voice matching */
    private var cachedDevices: List<Device> = emptyList()

    // HTTP client
    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }).build()

    // ───────── Voice helpers (Robel) ─────────
    private fun launchVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: device <name> on/off")
        }
        startActivityForResult(i, REQ_VOICE)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != REQ_VOICE || res != Activity.RESULT_OK) return
        val spoken = data?.getStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS
        )?.firstOrNull()?.lowercase(Locale.getDefault()) ?: return
        Log.d("Voice", "heard → $spoken")
        handleVoice(spoken)
    }

    private fun handleVoice(text: String) {
        // expected:  device <device-name> on|off
        val parts = text.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts[0] != "device" || parts.size < 2) return

        val desiredStatus = when (parts.last()) {
            "on"  -> true
            "off" -> false
            else  -> return
        }

        val spokenName = if (parts.size == 2) "device"
        else parts.subList(1, parts.lastIndex).joinToString(" ")

        val key = spokenName.lowercase().replace("\\s+".toRegex(), "")
        val dev = cachedDevices.firstOrNull { d ->
            d.name.lowercase().replace("\\s+".toRegex(), "") == key
        } ?: run { Log.d("Voice", "No match for $spokenName"); return }

        if (dev.status == desiredStatus) {
            Log.d("Voice", "${dev.name} already ${if (desiredStatus) "ON" else "OFF"}"); return
        }

        Log.d("Voice","Toggling ${dev.name} → ${if (desiredStatus) "ON" else "OFF"}")
        toggleDevice(dev.id, dev.name, dev.status) { Log.d("Voice", it) }
        SocketManager.emitUpdate(dev.name, desiredStatus)
    }
    // ─────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SocketManager.connect()
        enableEdgeToEdge()

        setContent {
            var darkMode by remember { mutableStateOf(false) }
            Software_engTheme(darkTheme = darkMode) {
                // 🔐 Injected login/auth flow (Gifty's logic)
                var isLoggedIn by remember { mutableStateOf(false) }

                if (isLoggedIn) {
                    DeviceUI(
                        onLogout      = { isLoggedIn = false },
                        darkMode      = darkMode,
                        onToggleTheme = { darkMode = !darkMode }
                    )
                } else {
                    AuthScreen { isLoggedIn = true }   // ← Gifty
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.disconnect()
    }

    // ─────────── UI (Robel) ───────────
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DeviceUI(
        onLogout: () -> Unit,
        darkMode: Boolean,
        onToggleTheme: () -> Unit
    ) {
        var allDevices      by remember { mutableStateOf<List<Device>>(emptyList()) }
        var selectedDevices by remember { mutableStateOf<List<Device>>(emptyList()) }
        var showAddDialog   by remember { mutableStateOf(false) }
        var statusMessage   by remember { mutableStateOf("Loading...") }
        val scope = rememberCoroutineScope()

        // initial fetch + live polling
        LaunchedEffect(Unit) {
            allDevices = fetchDevices().also { cachedDevices = it }
            SocketManager.onUpdate {
                scope.launch {
                    allDevices = fetchDevices().also { cachedDevices = it }
                    selectedDevices = selectedDevices.map { sel ->
                        allDevices.firstOrNull { it.id == sel.id } ?: sel
                    }
                }
            }
            while (true) {
                delay(3000)
                val latest = fetchDevices().also { cachedDevices = it }
                if (latest != allDevices) {
                    allDevices = latest
                    selectedDevices = selectedDevices.map { sel ->
                        latest.firstOrNull { it.id == sel.id } ?: sel
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Devices") },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle theme"
                            )
                        }
                        IconButton(onClick = { launchVoice() }) {
                            Icon(Icons.Filled.Mic, "Voice Command")
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, "Add Device")
                        }
                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Logout", color = MaterialTheme.colorScheme.onPrimary) }
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                Modifier.padding(paddingValues).padding(16.dp)
            ) {
                if (selectedDevices.isEmpty()) {
                    Text("No devices added. Tap + to add one.")
                }
                Spacer(Modifier.height(12.dp))

                selectedDevices.forEach { device ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Name: ${device.name}")
                            Text("Type: ${device.type}")
                            Text("Status: ${if (device.status) "ON" else "OFF"}")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                toggleDevice(device.id, device.name, device.status) { result ->
                                    statusMessage = result
                                    SocketManager.emitUpdate(device.name, !device.status)
                                    scope.launch(Dispatchers.IO) {
                                        val upd = fetchDevices().first { it.id == device.id }
                                        withContext(Dispatchers.Main) {
                                            selectedDevices = selectedDevices.map {
                                                if (it.id == upd.id) upd else it
                                            }
                                        }
                                    }
                                }
                            }) { Text("Toggle") }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Status: $statusMessage")
            }

            // dialog (unchanged)
            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Add a Device") },
                    text = {
                        LazyColumn {
                            items(allDevices.filter { dev ->
                                selectedDevices.none { it.id == dev.id }
                            }) { dev ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(dev.name, Modifier.weight(1f))
                                    Button(onClick = { selectedDevices += dev }) { Text("Add") }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAddDialog = false }) { Text("Done") }
                    }
                )
            }
        }
    }

    // ─────────── network helpers (Robel) ───────────
    private suspend fun fetchDevices(): List<Device> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$BASE_URL/device/all").get().build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@withContext emptyList()
            val arr = JSONObject(res.body!!.string())
                .getJSONObject("data").getJSONArray("devices")
            List(arr.length()) { i ->
                arr.getJSONObject(i).run {
                    Device(
                        id    = getInt("id"),
                        name  = getString("name"),
                        description = getString("description"),
                        status      = getBoolean("status"),
                        type        = getString("type"),
                        value       = getDouble("value")
                    )
                }
            }
        }
    }

    private fun toggleDevice(
        id: Int,
        deviceName: String,
        currentStatus: Boolean,
        onResult: (String) -> Unit
    ) {
        val newStatus = !currentStatus
        val body = JSONObject().apply {
            put("name", deviceName)
            put("status", newStatus)
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("$BASE_URL/device/$id")
            .patch(body)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("Failed: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                onResult("Device $id updated: ${response.code}")
            }
        })
    }
}
// ─────────── Robel work end ───────────
