package com.cefrspeakingcoach.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedPromptsScreen(
    prompts: List<PromptItem>,
    selectedLevelFilter: String,
    onLevelFilterChange: (String) -> Unit,
    onUsePrompt: (PromptItem) -> Unit,
    onRemovePrompt: (PromptItem) -> Unit
) {
    val levels = listOf("All", "A1", "A2", "B1", "B2", "C1", "C2")
    val filtered = if (selectedLevelFilter == "All") {
        prompts
    } else {
        prompts.filter { it.level == selectedLevelFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Saved Prompts",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                levels.forEach { level ->
                    FilterChip(
                        selected = selectedLevelFilter == level,
                        onClick = { onLevelFilterChange(level) },
                        label = { Text(level) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "No saved prompts yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Mark a prompt as Favorite from Practice Session and it will appear here.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { prompt ->
                SavedPromptCard(
                    prompt = prompt,
                    onUsePrompt = { onUsePrompt(prompt) },
                    onRemovePrompt = { onRemovePrompt(prompt) }
                )
            }
        }
    }
}

@Composable
private fun SavedPromptCard(
    prompt: PromptItem,
    onUsePrompt: () -> Unit,
    onRemovePrompt: () -> Unit
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(prompt.level) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        disabledLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(prompt.category) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        disabledLabelColor = MaterialTheme.colorScheme.secondary
                    )
                )

                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (prompt.source == PromptSource.AI) "AI" else "Local") }
                )
            }

            Text(
                prompt.text,
                style = MaterialTheme.typography.titleMedium
            )

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onUsePrompt,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Use Prompt")
                }

                OutlinedButton(
                    onClick = onRemovePrompt,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            }
        }
    }
}
