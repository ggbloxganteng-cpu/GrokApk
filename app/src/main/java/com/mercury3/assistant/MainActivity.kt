package com.mercury3.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mercury3.assistant.data.ChatRepo
import com.mercury3.assistant.data.SecurePrefs
import com.mercury3.assistant.service.FloatingBubbleService
import kotlinx.coroutines.launch

data class UiMsg(val text: String, val isUser: Boolean, val id: Long = System.currentTimeMillis())

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SecurePrefs
    private lateinit var repo: ChatRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SecurePrefs(this)
        repo = ChatRepo(prefs)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00D4FF),
                    background = Color(0xFF0A0A0F),
                    surface = Color(0xFF16161F),
                    surfaceVariant = Color(0xFF1E1E2A)
                )
            ) {
                var screen by remember {
                    mutableStateOf(if (prefs.apiKey.isBlank()) "setup" else "chat")
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (screen) {
                        "setup" -> SetupScreen(
                            prefs = prefs,
                            onDone = { screen = "chat" },
                            onRequestOverlay = { requestOverlay() }
                        )
                        "chat" -> ChatScreen(
                            prefs = prefs,
                            repo = repo,
                            onSettings = { screen = "settings" },
                            onStartBubble = { startBubble() }
                        )
                        "settings" -> SettingsScreen(
                            prefs = prefs,
                            onBack = { screen = "chat" }
                        )
                    }
                }
            }
        }
    }

    private fun requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun startBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlay()
            Toast.makeText(this, "Berikan izin overlay dulu", Toast.LENGTH_SHORT).show()
        } else {
            FloatingBubbleService.start(this)
            Toast.makeText(this, "Bubble aktif", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun SetupScreen(prefs: SecurePrefs, onDone: () -> Unit, onRequestOverlay: () -> Unit) {
    var key by remember { mutableStateOf(prefs.apiKey) }
    var show by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Grok Build", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Floating AI · Mercury 3", color = Color(0xFF00D4FF))
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("API Key Mercury 3") },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            prefs.apiKey = key.trim()
            onRequestOverlay()
            onDone()
        }, modifier = Modifier.fillMaxWidth(), enabled = key.isNotBlank()) {
            Text("Simpan & Lanjut")
        }
        TextButton(onClick = onDone) { Text("Lewati") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    prefs: SecurePrefs,
    repo: ChatRepo,
    onSettings: () -> Unit,
    onStartBubble: () -> Unit
) {
    val ctx = LocalContext.current
    var messages by remember { mutableStateOf(listOf<UiMsg>()) }
    var input by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    var contextText by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(prefs.privacyMode) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Grok Build", fontWeight = FontWeight.Bold)
                    Text("Mercury 3", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00D4FF))
                }
            },
            actions = {
                IconButton(onClick = {
                    privacy = !privacy
                    prefs.privacyMode = privacy
                    if (privacy) contextText = ""
                }) {
                    Icon(
                        if (privacy) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null,
                        tint = if (privacy) Color.Red else Color.White
                    )
                }
                IconButton(onClick = onStartBubble) {
                    Icon(Icons.Default.BubbleChart, "Bubble")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, "Settings")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        if (privacy) {
            Text(
                "Mode Privasi aktif — konteks layar dimatikan",
                Modifier.fillMaxWidth().background(Color(0x33FF0000)).padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF8A80)
            )
        }

        if (contextText.isNotBlank() && !privacy) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Konteks: ${contextText.take(40)}…", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { contextText = "" }) {
                    Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Tanyakan sesuatu tentang layar / kode / dokumen", color = Color.Gray)
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (msg.isUser) Color(0xFF00D4FF).copy(0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            SelectionContainer {
                                Text(msg.text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (!msg.isUser && msg.text.isNotBlank()) {
                                TextButton(onClick = {
                                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                                    cm.setPrimaryClip(ClipData.newPlainText("answer", msg.text))
                                    Toast.makeText(ctx, "Disalin", Toast.LENGTH_SHORT).show()
                                }, contentPadding = PaddingValues(4.dp)) {
                                    Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Salin", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (streaming) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF00D4FF))
                        Spacer(Modifier.width(8.dp))
                        Text("Mercury 3…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(Modifier.padding(8.dp)) {
                if (!privacy) {
                    OutlinedTextField(
                        value = contextText,
                        onValueChange = { contextText = it },
                        placeholder = { Text("Tempel / ketik konteks layar (opsional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Tanya…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(
                        onClick = {
                            if (input.isBlank() || streaming) return@FilledIconButton
                            val q = input.trim()
                            input = ""
                            messages = messages + UiMsg(q, true)
                            streaming = true
                            val aiId = System.currentTimeMillis()
                            messages = messages + UiMsg("", false, aiId)
                            scope.launch {
                                try {
                                    repo.stream(q, if (privacy) null else contextText.ifBlank { null }).collect { chunk ->
                                        messages = messages.map {
                                            if (it.id == aiId) it.copy(text = it.text + chunk) else it
                                        }
                                    }
                                } catch (e: Exception) {
                                    messages = messages.map {
                                        if (it.id == aiId) it.copy(text = "Error: ${e.message}") else it
                                    }
                                } finally {
                                    streaming = false
                                }
                            }
                        },
                        enabled = input.isNotBlank() && !streaming
                    ) {
                        Icon(Icons.Default.Send, "Kirim")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: SecurePrefs, onBack: () -> Unit) {
    var key by remember { mutableStateOf(prefs.apiKey) }
    var url by remember { mutableStateOf(prefs.baseUrl) }
    var temp by remember { mutableFloatStateOf(prefs.temperature) }
    var tokens by remember { mutableIntStateOf(prefs.maxTokens) }
    var show by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API Key") },
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { show = !show }) {
                        Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Default: https://api.inceptionai.com/v1/") }
            )
            Text("Temperature: ${"%.1f".format(temp)}")
            Slider(value = temp, onValueChange = { temp = it }, valueRange = 0f..1.5f)
            Text("Max Tokens: $tokens")
            Slider(value = tokens.toFloat(), onValueChange = { tokens = it.toInt() }, valueRange = 256f..4096f)
            Button(
                onClick = {
                    prefs.apiKey = key.trim()
                    prefs.baseUrl = url.trim().ifBlank { "https://api.inceptionai.com/v1/" }
                    prefs.temperature = temp
                    prefs.maxTokens = tokens
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Simpan") }
        }
    }
}
