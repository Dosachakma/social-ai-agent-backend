package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.PlatformType
import com.example.data.model.PostStatus
import com.example.data.model.SocialPost

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatePostDialog(
    onDismiss: () -> Unit,
    onSubmitPost: (SocialPost) -> Unit,
    onGenerateAiCopy: (topic: String, platform: PlatformType) -> Unit,
    aiGeneratedCopy: String? = null
) {
    var title by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }
    var topicInput by remember { mutableStateOf("") }
    val selectedPlatforms = remember { mutableStateListOf(PlatformType.TWITTER, PlatformType.LINKEDIN) }

    LaunchedEffect(aiGeneratedCopy) {
        if (!aiGeneratedCopy.isNullOrBlank()) {
            contentText = aiGeneratedCopy
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Create Social Post",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Post Title / Campaign Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // AI Copy generator trigger box
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "AI Copilot Generator",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = topicInput,
                                onValueChange = { topicInput = it },
                                placeholder = { Text("e.g. Launching AI roadmap Q3") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (topicInput.isNotBlank()) {
                                        onGenerateAiCopy(topicInput, selectedPlatforms.firstOrNull() ?: PlatformType.TWITTER)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = { Text("Post Content") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Target Platforms",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlatformType.values().forEach { platform ->
                        val isSelected = selectedPlatforms.contains(platform)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedPlatforms.remove(platform)
                                else selectedPlatforms.add(platform)
                            },
                            label = { Text(platform.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && contentText.isNotBlank()) {
                                val post = SocialPost(
                                    title = title,
                                    content = contentText,
                                    targetPlatforms = selectedPlatforms.toList(),
                                    scheduledTime = "Today at 5:00 PM",
                                    status = PostStatus.SCHEDULED,
                                    isAiGenerated = true
                                )
                                onSubmitPost(post)
                                onDismiss()
                            }
                        },
                        enabled = title.isNotBlank() && contentText.isNotBlank()
                    ) {
                        Text("Queue & Schedule")
                    }
                }
            }
        }
    }
}
