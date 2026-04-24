package com.example.voiceassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val listState = rememberLazyListState()

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
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
        uiState.messages.lastOrNull()?.let { message ->
            if (message.role == Role.ASSISTANT) speaker.speak(message.text)
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
            models = uiState.availableModels,
            isLoadingModels = uiState.isLoadingModels,
            onRefreshModels = { viewModel.refreshFreeModels() },
            onBack = { showSettings = false },
            onSave = {
                viewModel.updateSettings(it)
                Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                showSettings = false
            }
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Elix") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF060A18), Color(0xFF0D1530))))
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Model: ${uiState.settings.model}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFAFC2FF),
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (uiState.messages.isEmpty()) {
                    QuickPromptRow(
                        onPromptSend = { prompt ->
                            viewModel.sendMessage(prompt)
                        },
                        onPromptFill = { prompt ->
                            inputText = prompt
                        }
                    )
                }

                uiState.error?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF5B1E2D))) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = it, color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("Dismiss", color = Color.White)
                            }
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.messages) { _, message ->
                        val isUser = message.role == Role.USER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.86f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUser) Color(0xFF3A57D8) else Color(0xFF2A2F3C)
                                )
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
                    TypingIndicator()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message") },
                        maxLines = 4
                    )

                    IconButton(onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }

                    IconButton(onClick = {
                        if (!hasMicPermission.value) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            if (isListening) recognizer.stopListening() else recognizer.startListening()
                        }
                    }) {
                        Icon(Icons.Default.Mic, contentDescription = "Mic")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        speaker.stop()
                        viewModel.clearConversation()
                    }) { Text("Clear chat") }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val a1 by transition.animateFloat(0.2f, 1f, animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a1")
    val a2 by transition.animateFloat(0.2f, 1f, animationSpec = infiniteRepeatable(tween(500, delayMillis = 140), RepeatMode.Reverse), label = "a2")
    val a3 by transition.animateFloat(0.2f, 1f, animationSpec = infiniteRepeatable(tween(500, delayMillis = 280), RepeatMode.Reverse), label = "a3")

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 6.dp)
    ) {
        Dot(alpha = a1)
        Dot(alpha = a2)
        Dot(alpha = a3)
        Spacer(modifier = Modifier.height(1.dp))
        Text("Elix is thinking…", style = MaterialTheme.typography.labelSmall, color = Color(0xFFAFC2FF))
    }
}

@Composable
private fun Dot(alpha: Float) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(Color(0xFFAFC2FF), CircleShape)
    )
}

@Composable
private fun QuickPromptRow(
    onPromptSend: (String) -> Unit,
    onPromptFill: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = {
                onPromptSend(
                    "Mere liye aaj ka practical day plan banao: top 3 priorities, time blocks, and one quick win."
                )
            },
            label = { Text("Plan") }
        )
        AssistChip(
            onClick = {
                onPromptFill("Is text ko concise summary me convert karo with bullets and key takeaways:")
            },
            label = { Text("Summarize") }
        )
        AssistChip(
            onClick = {
                onPromptSend(
                    "Mujhe short motivation do: 3 lines, confident tone, and one immediate action step."
                )
            },
            label = { Text("Motivate") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    current: AppSettings,
    models: List<String>,
    isLoadingModels: Boolean,
    onRefreshModels: () -> Unit,
    onBack: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var apiKey by remember(current) { mutableStateOf(current.apiKey) }
    var selectedModel by remember(current) { mutableStateOf(current.model) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    Button(
                        onClick = { onSave(AppSettings(apiKey = apiKey.trim(), model = selectedModel)) }
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("OpenRouter Configuration", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Base URL and endpoint are fixed internally for stability.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Free OpenRouter models", style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = onRefreshModels) {
                                Text(if (isLoadingModels) "Loading..." else "Refresh")
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selectedModel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Model") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                models.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            selectedModel = model
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
