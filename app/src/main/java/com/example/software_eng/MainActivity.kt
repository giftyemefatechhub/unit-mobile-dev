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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.software_eng.ui.theme.Software_engTheme
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// ─────────── Robel work start ───────────
data class Device(
    val id: Int,
    val name: String,
    val description: String,
    val status: Boolean,
    val type: String,
    val value: Double
)

const val BASE_URL = "http://192.168.0.32:5001"
private const val REQ_VOICE = 42

val activityLog = mutableStateListOf<String>()

fun getCurrentTime(): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
}

class MainActivity : ComponentActivity() {

    private var cachedDevices: List<Device> = emptyList()

    // --- New: Cookie support for refresh token ---
    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar) // <-- Add cookie support
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }).build()

    private fun launchVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: device <name> on/off")
        }
        startActivityForResult(i, REQ_VOICE)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != REQ_VOICE || res != RESULT_OK) return
        val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.lowercase(Locale.getDefault()) ?: return
        Log.d("Voice", "heard → $spoken")
        handleVoice(spoken)
    }

    private fun handleVoice(text: String) {
        val parts = text.lowercase(Locale.getDefault()).split("\\s+".toRegex()).filter { it.isNotEmpty() }

        if (parts.size < 2) {
            Log.d("Voice", "Not enough words: $text")
            return
        }

        val desiredStatus = when (parts.last()) {
            "on" -> true
            "off" -> false
            else -> {
                Log.d("Voice", "No status command (on/off) found in: $text")
                return
            }
        }

        // Device name handling (two-word or one-word input)
        val spokenName = if (parts.size >= 3)
            parts.subList(0, parts.lastIndex).joinToString(" ")
        else
            parts[0]

        val cleanedSpoken = spokenName
            .replace("_", "")    // remove underscores (if spoken)
            .replace("\\s+".toRegex(), "") // remove spaces
            .lowercase(Locale.getDefault())

        Log.d("Voice", "Looking for device matching: $cleanedSpoken")

        val dev = cachedDevices.firstOrNull {
            it.name
                .replace("_", "")   // remove underscores from device name
                .replace("\\s+".toRegex(), "") // remove any accidental spaces
                .lowercase(Locale.getDefault()) == cleanedSpoken
        }

        if (dev == null) {
            Log.d("Voice", "No match found for: $cleanedSpoken")
            return
        }

        if (dev.status == desiredStatus) {
            Log.d("Voice", "${dev.name} already ${if (desiredStatus) "ON" else "OFF"}")
            return
        }

        Log.d("Voice", "Toggling ${dev.name} to ${if (desiredStatus) "ON" else "OFF"}")
        toggleDevice(dev.id, dev.name, dev.status) {
            Log.d("Voice", it)
        }
        SocketManager.emitUpdate(dev.name, desiredStatus)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SocketManager.connect()
        enableEdgeToEdge()

        setContent {
            var darkMode by remember { mutableStateOf(false) }
            var showDashboard by remember { mutableStateOf(true) }
            var authIsLogin by remember { mutableStateOf(true) }
            var isLoggedIn by remember { mutableStateOf(false) }
            var logoutJob by remember { mutableStateOf<Job?>(null) }

            val lifecycleOwner = LocalLifecycleOwner.current

            // Auto logout logic
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            if (isLoggedIn) {
                                logoutJob = CoroutineScope(Dispatchers.Main).launch {
                                    delay(5 * 60 * 1000) // 5 minutes
                                    isLoggedIn = false
                                    showDashboard = true
                                    activityLog.add("Auto-logged out due to inactivity at ${getCurrentTime()}")
                                }
                            }
                        }

                        Lifecycle.Event.ON_START -> {
                            logoutJob?.cancel()
                            logoutJob = null
                        }

                        else -> Unit
                    }
                }

                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            Software_engTheme(darkTheme = darkMode) {
                if (showDashboard) {
                    HomeScreen(
                        onLoginClick = {
                            authIsLogin = true   // ← Set to login mode
                            showDashboard = false
                        },
                        onRegisterClick = {
                            authIsLogin = false  // ← Set to register mode
                            showDashboard = false
                        }
                    )
                } else {
                    var isLoggedIn by remember { mutableStateOf(false) }
                    var showActivityLog by remember { mutableStateOf(false) }

                    if (isLoggedIn) {
                        if (showActivityLog) {
                            ActivityLogScreen { showActivityLog = false }
                        } else {
                            BackHandler {
                                isLoggedIn = false
                                showDashboard = true
                            }

                            DeviceUI(
                                onLogout = {
                                    isLoggedIn = false
                                    showDashboard = true
                                },
                                darkMode = darkMode,
                                onToggleTheme = { darkMode = !darkMode },
                                onShowActivityLog = { showActivityLog = true }
                            )
                        }
                    } else {
                        BackHandler {
                            // Go back to HomeScreen when back is pressed
                            showDashboard = true
                        }
                        AuthScreen(
                            onLoginSuccess = {
                                isLoggedIn = true
                                activityLog.add("Logged in at ${getCurrentTime()}")
                            },
                            initialIsLogin = authIsLogin // ← Add this
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.disconnect()
    }

    // ─────────── Robel work end ───────────

    // **ADDED**: suspend function to query backend search endpoint
    private suspend fun searchDevices(query: String): List<Device> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/device/search?q=${URLEncoder.encode(query, "UTF-8")}"
        val reqBuilder = Request.Builder()
            .url(url)
            .get()
        TokenManager.accessToken?.let {
            reqBuilder.addHeader("Authorization", "Bearer $it")
        }
        client.newCall(reqBuilder.build()).execute().use { res ->
            if (!res.isSuccessful) return@withContext emptyList()
            val arr = JSONObject(res.body!!.string())
                .getJSONObject("data").getJSONArray("devices")
            List(arr.length()) { i ->
                arr.getJSONObject(i).run {
                    Device(
                        id = getInt("id"),
                        name = getString("name"),
                        description = getString("description"),
                        status = getBoolean("status"),
                        type = getString("type"),
                        value = getDouble("value")
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DeviceUI(
        onLogout: () -> Unit,
        darkMode: Boolean,
        onToggleTheme: () -> Unit,
        onShowActivityLog: () -> Unit
    ) {
        var showProfileScreen by remember { mutableStateOf(false) }
        var showAddDialog by remember { mutableStateOf(false) } // Define the variable here
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<Device>>(emptyList()) }
        var allDevices by remember { mutableStateOf<List<Device>>(emptyList()) }
        var selectedDevices by remember { mutableStateOf<List<Device>>(emptyList()) }
        var statusMessage by remember { mutableStateOf("Loading...") }
        val scope = rememberCoroutineScope()

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

        // **ADDED**: call backend when query changes
        LaunchedEffect(searchQuery) {
            searchResults = if (searchQuery.isBlank()) emptyList()
            else allDevices.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Filled.Home, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Home-Sync", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle theme"
                            )
                        }
                        IconButton(onClick = onShowActivityLog) {
                            Icon(Icons.Default.List, "Activity Log")
                        }
                        IconButton(onClick = { showProfileScreen = true }) {
                            Icon(Icons.Default.AccountCircle, "Profile")
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, "Add Device") // This is the Add Device button
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
                if (showProfileScreen) {
                    // Show Profile Screen
                    ProfileScreen(
                        userId = 1, // Replace with actual user ID
                        onProfileUpdateSuccess = {
                            // Handle profile update success
                            showProfileScreen = false
                        },
                        onError = { errorMessage ->
                            // Handle error
                            showProfileScreen = false
                        },
                        usernameInitial = " ", // Pass the usernameInitial value here
                        emailInitial = " " // Pass the initial email value
                    )
                } else {
                    // Search Bar UI
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search icon")
                        },
                        placeholder = { Text("Search devices…") }
                    )

                    Spacer(Modifier.height(12.dp))

                    AnimatedVisibility(
                        visible = selectedDevices.isEmpty(),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut()
                    ) {
                        Text(
                            "No devices added. Tap + to add one.",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    val displayed = if (searchQuery.isBlank()) selectedDevices
                    else searchResults

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(displayed) { device ->
                            DeviceCard(
                                device = device,
                                onToggle = { toToggle ->
                                    toggleDevice(toToggle.id, toToggle.name, toToggle.status) {
                                        statusMessage = it
                                    }
                                    SocketManager.emitUpdate(toToggle.name, !toToggle.status)
                                    selectedDevices = selectedDevices.map {
                                        if (it.id == toToggle.id) it.copy(status = !it.status)
                                        else it
                                    }
                                    activityLog.add("Toggled ${toToggle.name} to ${if (!toToggle.status) "ON" else "OFF"} at ${getCurrentTime()}")
                                },
                                onRemove = { toRemove ->
                                    selectedDevices = selectedDevices.filterNot { it.id == toRemove.id }
                                    activityLog.add("Removed ${toRemove.name} at ${getCurrentTime()}")
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Status: $statusMessage")
                }
            }

            if (showAddDialog) {
                AddDeviceDialog(
                    allDevices = allDevices,
                    selectedDevices = selectedDevices,
                    onAdd = { selectedDevices += it },
                    onDismiss = { showAddDialog = false }
                )
            }
        }
    }

    @Composable
    private fun DeviceCard(
        device: Device,
        onToggle: (Device) -> Unit,
        onRemove: (Device) -> Unit // <- added
    ) {
        val cardColor = if (device.status)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow

        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(device.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${device.type} • ${device.value}", style = MaterialTheme.typography.bodySmall)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = device.status,
                        onCheckedChange = { onToggle(device) },
                        thumbContent = {
                            Icon(
                                if (device.status) Icons.Filled.Check else Icons.Filled.Close,
                                null,
                                Modifier.size(12.dp)
                            )
                        }
                    )
                    TextButton(onClick = { onRemove(device) }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

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
                            verticalAlignment = Alignment.CenterVertically
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

    private suspend fun fetchDevices(): List<Device> = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder()
            .url("$BASE_URL/device/all")
            .get()

        TokenManager.accessToken?.let {
            reqBuilder.addHeader("Authorization", "Bearer $it")
        }

        val req = reqBuilder.build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@withContext emptyList()
            val arr = JSONObject(res.body!!.string())
                .getJSONObject("data").getJSONArray("devices")
            List(arr.length()) { i ->
                arr.getJSONObject(i).run {
                    Device(
                        id = getInt("id"),
                        name = getString("name"),
                        description = getString("description"),
                        status = getBoolean("status"),
                        type = getString("type"),
                        value = getDouble("value")
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

        val reqBuilder = Request.Builder()
            .url("$BASE_URL/device/$id")
            .patch(body)
            .addHeader("Content-Type", "application/json")

        TokenManager.accessToken?.let {
            reqBuilder.addHeader("Authorization", "Bearer $it")
        }

        val req = reqBuilder.build()

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
