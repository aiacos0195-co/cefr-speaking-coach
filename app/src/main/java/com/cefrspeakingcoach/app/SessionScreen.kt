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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cefrspeakingcoach.app.ui.theme.BrandBlue
import com.cefrspeakingcoach.app.ui.theme.BrandGreen
import com.cefrspeakingcoach.app.ui.theme.BrandPink
import com.cefrspeakingcoach.app.ui.theme.BrandPurple
import com.cefrspeakingcoach.app.ui.theme.BrandTeal

data class SessionScreenState(
    val level: String,
    val promptCategory: String,
    val promptText: String,
    val totalSeconds: Int,
    val timeLeft: Int,
    val isRecording: Boolean,
    val statusText: String,
    val liveTranscript: String,
    val finalTranscript: String,
    val spokenSeconds: Int,
    val wordCount: Int,
    val wpm: Int,
    val fillerCount: Int,
    val aiLoading: Boolean,
    val promptRefreshLoading: Boolean,
    val aiError: String?,
    val aiResult: AiFeedback?,
    val canEvaluate: Boolean,
    val engineLabel: String,
    val speechDetected: Boolean,
    val availableCategories: List<String>,
    val selectedCategory: String,
    val isFavoritePrompt: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(
    state: SessionScreenState,
    onToggleRecording: () -> Unit,
    onEvaluate: () -> Unit,
    onNewPrompt: () -> Unit,
    onRefreshPromptAi: () -> Unit,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProgress: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleFavorite: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Back")
                }

                OutlinedButton(onClick = onToggleFavorite) {
                    Text(if (state.isFavoritePrompt) "Unfavorite" else "Favorite")
                }
            }
        }

        item {
            Text(
                "${state.level} Speaking Task",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                state.promptCategory,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.availableCategories.forEach { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                        label = { Text(category) }
                    )
                }
            }
        }

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
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text("${state.level} Prompt") },
                        enabled = false
                    )

                    Text(
                        state.promptText,
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(
                        "Speak naturally. Focus on clarity, grammar, and fluency.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Time left: ${state.timeLeft}s",
                        style = MaterialTheme.typography.titleLarge
                    )

                    LinearProgressIndicator(
                        progress = {
                            val total = state.totalSeconds.coerceAtLeast(1)
                            (state.timeLeft.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = BrandGreen,
                        trackColor = BrandGreen.copy(alpha = 0.15f)
                    )

                    Button(
                        onClick = onToggleRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isRecording) "Stop Recording" else "Start Recording")
                    }

                    Text(
                        state.statusText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = false,
                            onClick = {},
                            enabled = false,
                            label = { Text("Engine: ${state.engineLabel}") }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {},
                            enabled = false,
                            label = { Text(if (state.speechDetected) "Speech detected" else "Waiting for speech") }
                        )
                    }

                    FilterChip(
                        selected = false,
                        onClick = {},
                        enabled = false,
                        label = { Text("Spoken: ${state.spokenSeconds}s") }
                    )
                }
            }
        }

        item {
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
                    Text("Live transcript", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Text(
                        state.liveTranscript.ifBlank { "..." },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        item {
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
                    Text("Final transcript", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Text(
                        state.finalTranscript.ifBlank { "..." },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

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
                    Text("Session metrics", style = MaterialTheme.typography.titleMedium)
                    Text("Words: ${state.wordCount}")
                    Text("Spoken time: ${state.spokenSeconds}s")
                    Text("Approx WPM: ${state.wpm}")
                    Text("Fillers: ${state.fillerCount}")
                }
            }
        }

        item {
            Button(
                onClick = onEvaluate,
                enabled = state.canEvaluate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.aiLoading) "Evaluating..." else "Evaluate with AI")
            }
        }

        state.aiError?.let { error ->
            item {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        state.aiResult?.let { ai ->
            item {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "AI Feedback",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Text(
                            "Estimated: ${ai.cefr_level_estimate}",
                            style = MaterialTheme.typography.titleLarge
                        )

                        SkillBar("Fluency", ai.scores.fluency / 5f, "${ai.scores.fluency}/5", 0)
                        SkillBar("Pronunciation", ai.scores.pronunciation / 5f, "${ai.scores.pronunciation}/5", 1)
                        SkillBar("Grammar", ai.scores.grammar / 5f, "${ai.scores.grammar}/5", 2)
                        SkillBar("Vocabulary", ai.scores.vocabulary / 5f, "${ai.scores.vocabulary}/5", 3)
                        SkillBar("Coherence", ai.scores.coherence / 5f, "${ai.scores.coherence}/5", 4)

                        HorizontalDivider()

                        Text("Overall: ${ai.overall}/5", style = MaterialTheme.typography.titleLarge)

                        if (ai.strengths.isNotEmpty()) {
                            Text("Strengths", style = MaterialTheme.typography.titleMedium)
                            ai.strengths.forEach { Text("• $it") }
                        }

                        if (ai.improvements.isNotEmpty()) {
                            Text("Areas to Improve", style = MaterialTheme.typography.titleMedium)
                            ai.improvements.forEach { Text("• $it") }
                        }

                        if (ai.corrected_version.isNotBlank()) {
                            Text("Corrected Version", style = MaterialTheme.typography.titleMedium)
                            Text(ai.corrected_version)
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onNewPrompt,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("New Prompt")
                }

                OutlinedButton(
                    onClick = onRefreshPromptAi,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.promptRefreshLoading) "Refreshing..." else "Refresh Prompt (AI)")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("History")
                }

                OutlinedButton(
                    onClick = onOpenProgress,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Speed, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Progress")
                }
            }
        }
    }
}

@Composable
private fun SkillBar(
    title: String,
    progress: Float,
    valueText: String,
    colorIndex: Int
) {
    val color = when (colorIndex) {
        0 -> BrandGreen
        1 -> BrandTeal
        2 -> BrandBlue
        3 -> BrandPurple
        else -> BrandPink
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$title • $valueText")
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}
