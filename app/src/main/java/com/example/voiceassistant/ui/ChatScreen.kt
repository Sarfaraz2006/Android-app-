package com.example.voiceassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.voiceassistant.domain.Role

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val speaker = remember(context) {
        VoiceSpeaker(
            context = context,
            onError = viewModel::onSpeechError
        )
    }

    val recognizer = remember(context) {
        VoiceRecognizer(
            context = context,
            onResult = { text -> viewModel.sendMessage(text) },
            onPartialResult = viewModel::onPartialTranscript,
            onError = viewModel::onSpeechError,
            onListeningStateChanged = viewModel::onListeningChanged
        )
    }

    LaunchedEffect(uiState.messages.size) {
        val last = uiState.messages.lastOrNull()
        if (last?.role == Role.ASSISTANT) speaker.speak(last.text)
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.release()
            speaker.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Voice Assistant",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (uiState.isListening) "Listening…" else "Tap the mic or type a message",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        uiState.partialTranscript.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "Heard: $it",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        uiState.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.messages) { message ->
                val isUser = message.role == Role.USER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        OutlinedTextField(
            value = uiState.inputText,
            onValueChange = viewModel::onInputChanged,
            label = { Text("Type a message") },
            singleLine = false,
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalIconButton(
                onClick = {
                    if (uiState.isListening) {
                        recognizer.stopListening()
                    } else {
                        viewModel.clearError()
                        recognizer.startListening()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = if (uiState.isListening) "Stop listening" else "Start listening",
                    tint = if (uiState.isListening) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }

            FilledTonalIconButton(
                onClick = { viewModel.sendMessage(uiState.inputText) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
            }

            FilledTonalIconButton(
                onClick = {
                    speaker.stop()
                    viewModel.clearConversation()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Clear chat")
            }
        }
    }
}
