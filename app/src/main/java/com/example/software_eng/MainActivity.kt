package com.example.software_eng

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.software_eng.ui.theme.Software_engTheme
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import kotlin.time.Duration.Companion.seconds


// ─────────── Robel work start ───────────
data class Device(
    val id: Int,
    val name: String,
    val description: String,
    val status: Boolean,
    val type: String,
    val value: Double
)

const val BASE_URL  = "http://192.168.0.100:5001"
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
    // ─────────── (Robel) ───────────
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
                delay(3.seconds)
                val latest = fetchDevices().also { cachedDevices = it }
                if (latest != allDevices) {
                    allDevices = latest
                    selectedDevices = selectedDevices.map { sel ->
                        latest.firstOrNull { it.id == sel.id } ?: sel
                    }
                }
            }
        }
        // ─────────── (Robel) ───────────
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Home,          // ✅ always present
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Smart-Home")
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle theme"
                            )
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, "Add Device")
                        }
                        OutlinedButton(
                            onClick = onLogout,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Logout") }
                    }
                )
            },
            floatingActionButton = {
                // modern FAB to launch voice directly
                FloatingActionButton(onClick = { launchVoice() }) {
                    Icon(Icons.Filled.Mic, null)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                AnimatedVisibility(
                    visible = selectedDevices.isEmpty(),
                    enter   = fadeIn() + slideInVertically(),
                    exit    = fadeOut()
                ) {
                    Text("No devices added. Tap + to add one.",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                }

                Spacer(Modifier.height(12.dp))

                // modern list with LazyColumn
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(selectedDevices) { device ->
                        DeviceCard(device = device,
                            onToggle = { toToggle ->
                                toggleDevice(toToggle.id, toToggle.name, toToggle.status) {
                                    statusMessage = it
                                }
                                SocketManager.emitUpdate(toToggle.name, !toToggle.status)
                                // optimistic UI
                                selectedDevices = selectedDevices.map {
                                    if (it.id == toToggle.id) it.copy(status = !it.status)
                                    else it
                                }
                            })
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Status: $statusMessage")
            }

            // dialog (unchanged)
            if (showAddDialog) {
                AddDeviceDialog(
                    allDevices       = allDevices,
                    selectedDevices  = selectedDevices,
                    onAdd            = { selectedDevices += it },
                    onDismiss        = { showAddDialog = false }
                )
            }
        }
    }

    // ─────────── (Robel) ───────────
    @Composable
    private fun DeviceCard(device: Device, onToggle: (Device) -> Unit) {
        val cardColor = if (device.status)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow

        Card(
            colors  = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier  = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(device.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${device.type} • ${device.value}", style = MaterialTheme.typography.bodySmall)
                }

                // switch looks more “smart-home” than a plain button
                Switch(
                    checked = device.status,
                    onCheckedChange = { onToggle(device) },
                    thumbContent = if (device.status) {
                        { Icon(Icons.Filled.Check, null, Modifier.size(12.dp)) }
                    } else {
                        { Icon(Icons.Filled.Close, null, Modifier.size(12.dp)) }
                    }
                )
            }
        }
    }
    // ─────────── (Robel) ───────────
    @Composable
    private fun AddDeviceDialog(
        allDevices: List<Device>,
        selectedDevices: List<Device>,
        onAdd: (Device) -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add a Device") },
            text = {
                LazyColumn {
                    items(allDevices.filter { dev ->
                        selectedDevices.none { it.id == dev.id }
                    }) { dev ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(dev) }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(dev.name, Modifier.weight(1f))
                            Icon(Icons.Filled.AddCircle, null)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        )
    }
    // ───────────────────────────────────

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
    // ─────────── (Robel) ───────────
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
