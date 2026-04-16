package com.example.voiceassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.voiceassistant.data.AppSettings
import com.example.voiceassistant.data.ProviderType
import com.example.voiceassistant.data.presetFor
import com.example.voiceassistant.domain.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val hasMicPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission.value = granted
    }

    val speaker = remember { VoiceSpeaker(context) }
    val recognizer = remember {
        VoiceRecognizer(
            context = context,
            onResult = { text -> viewModel.sendMessage(text) },
            onError = { viewModel.clearError() },
            onListeningStateChanged = { isListening = it }
        )
    }

    LaunchedEffect(uiState.messages.size) {
        val last = uiState.messages.lastOrNull()
        if (last?.role == Role.ASSISTANT) {
            speaker.speak(last.text)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.release()
            speaker.release()
        }
    }

    if (showSettings) {
        SettingsScreen(
            current = uiState.settings,
            onBack = { showSettings = false },
            onSave = {
                viewModel.updateSettings(it)
                showSettings = false
            }
        )
        return
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0B0616), Color(0xFF1F0F45), Color(0xFF10071F))
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("${uiState.settings.assistantName} Studio") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            Column {
                AnimatedOrbHeader()

                uiState.error?.let {
                    Text(text = it, color = Color(0xFFFF8AAE), modifier = Modifier.padding(top = 8.dp))
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.messages) { message ->
                        val isUser = message.role == Role.USER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(0.86f),
                                color = if (isUser) Color(0xFF5A3DFF).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = message.text,
                                    color = Color.White,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Color(0xFFA887FF), modifier = Modifier.padding(bottom = 8.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureCard("Generate")
                    FeatureCard("Create")
                    FeatureCard("Summarize")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type message", color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true
                    )

                    IconButton(onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFFC2A8FF))
                    }
                    IconButton(onClick = {
                        if (!hasMicPermission.value) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            if (isListening) recognizer.stopListening() else recognizer.startListening()
                        }
                    }) {
                        Icon(Icons.Default.Mic, contentDescription = "Mic", tint = if (isListening) Color(0xFFFF6F91) else Color(0xFFC2A8FF))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        speaker.stop()
                        viewModel.clearConversation()
                    }) {
                        Text("Clear chat", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedOrbHeader() {
    val transition = rememberInfiniteTransition(label = "orb")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(88.dp)
                .scale(scale)
                .alpha(alpha)
                .background(
                    brush = Brush.radialGradient(listOf(Color(0xFFD2BCFF), Color(0xFF8E63FF), Color(0xFF3D1F9C))),
                    shape = CircleShape
                )
        )
        Text("Hi, I am Aria", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Your multi-model AI assistant", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FeatureCard(title: String) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Text(
            text = title,
            color = Color.White,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    current: AppSettings,
    onBack: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var local by remember(current) { mutableStateOf(current) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Provider Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { Button(onClick = { onSave(local) }) { Text("Save") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Choose provider (OpenRouter recommended for testing)")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderType.entries.forEach { provider ->
                    FilterChip(
                        selected = local.providerType == provider,
                        onClick = {
                            val preset = presetFor(provider)
                            local = local.copy(
                                providerType = provider,
                                baseUrl = preset.baseUrl,
                                endpointPath = preset.endpointPath,
                                model = preset.model
                            )
                        },
                        label = { Text(provider.label) }
                    )
                }
            }

            OutlinedTextField(
                value = local.assistantName,
                onValueChange = { local = local.copy(assistantName = it) },
                label = { Text("Assistant Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = local.baseUrl,
                onValueChange = { local = local.copy(baseUrl = it) },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = local.endpointPath,
                onValueChange = { local = local.copy(endpointPath = it) },
                label = { Text("Endpoint Path") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = local.model,
                onValueChange = { local = local.copy(model = it) },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = local.apiKey,
                onValueChange = { local = local.copy(apiKey = it) },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = local.systemPrompt,
                onValueChange = { local = local.copy(systemPrompt = it) },
                label = { Text("System Prompt") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Temperature: ${"%.2f".format(local.temperature)}")
            Slider(value = local.temperature, onValueChange = { local = local.copy(temperature = it) }, valueRange = 0f..1f)

            Text(
                text = "OpenRouter example: base=https://openrouter.ai, path=api/v1/chat/completions, model=openai/gpt-4.1-mini",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
