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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cefrspeakingcoach.app.ui.theme.BrandBlue
import com.cefrspeakingcoach.app.ui.theme.BrandGreen
import com.cefrspeakingcoach.app.ui.theme.BrandTeal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class PracticeTaskUi(
    val title: String,
    val subtitle: String,
    val done: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticePlanScreen(
    sessions: List<PracticeSession>,
    selectedLevel: String,
    onStartNextSession: () -> Unit
) {
    val todayStart = remember { startOfToday() }
    val tomorrowStart = remember { todayStart + 24L * 60L * 60L * 1000L }
    val last7DaysStart = remember { System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L }

    val todaySessions = remember(sessions) {
        sessions.filter { it.createdAt in todayStart until tomorrowStart }
    }

    val recentSessions = remember(sessions) {
        sessions.filter { it.createdAt >= last7DaysStart && it.ai != null }
    }

    val focusSkill = remember(recentSessions) {
        computeWeakestSkill(recentSessions)
    }

    val todayCount = todaySessions.size
    val progress = (todayCount.coerceIn(0, 3)) / 3f

    val tasks = remember(todayCount, focusSkill) {
        listOf(
            PracticeTaskUi(
                title = "Step 1 · Warm-up session",
                subtitle = "Complete 1 speaking session focusing on $focusSkill.",
                done = todayCount >= 1
            ),
            PracticeTaskUi(
                title = "Step 2 · Improve your weak point",
                subtitle = "Complete a 2nd session and pay extra attention to $focusSkill.",
                done = todayCount >= 2
            ),
            PracticeTaskUi(
                title = "Step 3 · Stretch session",
                subtitle = "Complete a 3rd session with a new prompt at level $selectedLevel.",
                done = todayCount >= 3
            )
        )
    }

    val nextActionLabel = when {
        todayCount <= 0 -> "Start Session 1"
        todayCount == 1 -> "Start Session 2"
        todayCount == 2 -> "Start Session 3"
        else -> "Start Extra Session"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                "Practice Plan",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            TodayOverviewCard(
                selectedLevel = selectedLevel,
                focusSkill = focusSkill,
                todayCount = todayCount,
                progress = progress
            )
        }

        item {
            TodayFocusCard(focusSkill = focusSkill)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Today's Steps",
                    style = MaterialTheme.typography.titleMedium
                )

                tasks.forEach { task ->
                    PracticeTaskCard(task = task)
                }
            }
        }

        item {
            Button(
                onClick = onStartNextSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(nextActionLabel)
            }
        }

        item {
            OutlinedButton(
                onClick = onStartNextSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Practice with current level ($selectedLevel)")
            }
        }

        if (todaySessions.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Today's Completed Sessions",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            items(todaySessions.take(5)) { session ->
                TodaySessionMiniCard(session = session)
            }
        }
    }
}

@Composable
private fun TodayOverviewCard(
    selectedLevel: String,
    focusSkill: String,
    todayCount: Int,
    progress: Float
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Today",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            )

            Text(
                "Level $selectedLevel · $todayCount/3 sessions completed",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
            )

            Text(
                "Focus today: $focusSkill",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayFocusCard(
    focusSkill: String
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Recommended Focus",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                focusSkill,
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                focusHint(focusSkill),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                focusTips(focusSkill).forEach { tip ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(tip) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            disabledLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeTaskCard(
    task: PracticeTaskUi
) {
    val bg = if (task.done) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    }

    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (task.done) Icons.Outlined.CheckCircle else Icons.Outlined.Flag,
                contentDescription = null,
                tint = if (task.done) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    task.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TodaySessionMiniCard(
    session: PracticeSession
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(session.level) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = BrandBlue.copy(alpha = 0.10f),
                        disabledLabelColor = BrandBlue
                    )
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    formatMiniDate(session.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                session.prompt,
                style = MaterialTheme.typography.titleSmall
            )

            session.ai?.let { ai ->
                HorizontalDivider()
                Text(
                    "Score ${ai.overall}/5 • ${ai.cefr_level_estimate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandTeal
                )
            }
        }
    }
}

private fun computeWeakestSkill(
    sessions: List<PracticeSession>
): String {
    if (sessions.isEmpty()) return "Fluency"

    val scores = sessions.mapNotNull { it.ai?.scores }
    if (scores.isEmpty()) return "Fluency"

    val metrics = listOf(
        "Fluency" to scores.map { it.fluency }.average(),
        "Pronunciation" to scores.map { it.pronunciation }.average(),
        "Grammar" to scores.map { it.grammar }.average(),
        "Vocabulary" to scores.map { it.vocabulary }.average(),
        "Coherence" to scores.map { it.coherence }.average()
    )

    return metrics.minByOrNull { it.second }?.first ?: "Fluency"
}

private fun focusHint(skill: String): String {
    return when (skill) {
        "Fluency" -> "Try to speak in longer chunks without stopping too often."
        "Pronunciation" -> "Slow down a little and pronounce key words more clearly."
        "Grammar" -> "Keep your sentence structures simple and accurate."
        "Vocabulary" -> "Use slightly more varied words and avoid repeating the same ones."
        "Coherence" -> "Connect your ideas with clearer transitions and examples."
        else -> "Focus on giving a clear and confident answer."
    }
}

private fun focusTips(skill: String): List<String> {
    return when (skill) {
        "Fluency" -> listOf("Keep speaking", "Avoid long pauses", "Use chunks")
        "Pronunciation" -> listOf("Speak clearly", "Stress keywords", "Slow down slightly")
        "Grammar" -> listOf("Short correct sentences", "Check verb tenses", "Use simple patterns")
        "Vocabulary" -> listOf("Use richer words", "Avoid repetition", "Add examples")
        "Coherence" -> listOf("Use connectors", "Follow a structure", "Stay on topic")
        else -> listOf("Be clear", "Be consistent", "Keep practicing")
    }
}

private fun formatMiniDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun startOfToday(): Long {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}
