package com.example.voiceassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.voiceassistant.data.AppSettings
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${uiState.settings.assistantName} • AI Assistant",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            uiState.error?.let {
                Text(
                    text = it,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages) { message ->
                    val isUser = message.role == Role.USER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isUser) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(12.dp)
                                .fillMaxWidth(0.82f)
                        ) {
                            Text(text = message.text)
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message ${uiState.settings.assistantName}") }
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }

                IconButton(
                    onClick = {
                        if (!hasMicPermission.value) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            if (isListening) recognizer.stopListening() else recognizer.startListening()
                        }
                    }
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Talk")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    speaker.stop()
                    viewModel.clearConversation()
                }) {
                    Text("Clear chat")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    current: AppSettings,
    onBack: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var assistantName by remember(current) { mutableStateOf(current.assistantName) }
    var baseUrl by remember(current) { mutableStateOf(current.baseUrl) }
    var endpointPath by remember(current) { mutableStateOf(current.endpointPath) }
    var model by remember(current) { mutableStateOf(current.model) }
    var apiKey by remember(current) { mutableStateOf(current.apiKey) }
    var systemPrompt by remember(current) { mutableStateOf(current.systemPrompt) }
    var temperature by remember(current) { mutableStateOf(current.temperature) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    Button(onClick = {
                        onSave(
                            AppSettings(
                                assistantName = assistantName.trim().ifBlank { "Aria" },
                                baseUrl = baseUrl.trim().ifBlank { "https://api.openai.com" },
                                endpointPath = endpointPath.trim().ifBlank { "v1/chat/completions" },
                                model = model.trim().ifBlank { "gpt-4.1-mini" },
                                apiKey = apiKey.trim(),
                                temperature = temperature,
                                systemPrompt = systemPrompt.trim()
                            )
                        )
                    }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Connect any OpenAI-compatible model provider.")
            HorizontalDivider()

            OutlinedTextField(value = assistantName, onValueChange = { assistantName = it }, label = { Text("Assistant name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = endpointPath, onValueChange = { endpointPath = it }, label = { Text("Endpoint path") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = systemPrompt, onValueChange = { systemPrompt = it }, label = { Text("System prompt") }, modifier = Modifier.fillMaxWidth())

            Text("Temperature: ${"%.2f".format(temperature)}")
            Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..1f)

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Examples: OpenAI, OpenRouter, Groq, Together, local vLLM, Ollama gateway.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
