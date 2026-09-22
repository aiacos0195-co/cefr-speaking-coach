package com.cefrspeakingcoach.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cefrspeakingcoach.app.ui.theme.BrandBlue
import com.cefrspeakingcoach.app.ui.theme.BrandGreen
import com.cefrspeakingcoach.app.ui.theme.BrandPink
import com.cefrspeakingcoach.app.ui.theme.BrandPurple
import com.cefrspeakingcoach.app.ui.theme.BrandTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryDetailScreen(
    session: PracticeSession?
) {
    if (session == null) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(20.dp)
        ) {
            item {
                Text(
                    "Session not found.",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Session Detail",
                style = MaterialTheme.typography.headlineMedium
            )
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
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(session.level) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            disabledLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Text(
                        formatSessionDate(session.createdAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        session.prompt,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        "Category: ${session.category}",
                        style = MaterialTheme.typography.bodyMedium,
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Transcript",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        session.transcript.ifBlank { "No transcript available." },
                        style = MaterialTheme.typography.bodyLarge
                    )

                    HorizontalDivider()

                    Text(
                        "Metrics",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text("Spoken time: ${session.spokenSeconds}s")
                    Text("Words: ${session.wordCount}")
                    Text("WPM: ${session.wpm}")
                }
            }
        }

        session.ai?.let { ai ->
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
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "Overall: ${ai.overall}/5 • Estimated: ${ai.cefr_level_estimate}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        SkillProgress("Fluency", ai.scores.fluency / 5f, "${ai.scores.fluency}/5", 0)
                        SkillProgress("Grammar", ai.scores.grammar / 5f, "${ai.scores.grammar}/5", 1)
                        SkillProgress("Vocabulary", ai.scores.vocabulary / 5f, "${ai.scores.vocabulary}/5", 2)
                        SkillProgress("Coherence", ai.scores.coherence / 5f, "${ai.scores.coherence}/5", 3)

                        if (ai.strengths.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                "Strengths",
                                style = MaterialTheme.typography.titleMedium
                            )
                            ai.strengths.forEach {
                                Text("• $it")
                            }
                        }

                        if (ai.improvements.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                "Areas to Improve",
                                style = MaterialTheme.typography.titleMedium
                            )
                            ai.improvements.forEach {
                                Text("• $it")
                            }
                        }

                        if (ai.corrected_version.isNotBlank()) {
                            HorizontalDivider()
                            Text(
                                "Corrected Version",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(ai.corrected_version)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillProgress(
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
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}

private fun formatSessionDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(timestamp))
}
