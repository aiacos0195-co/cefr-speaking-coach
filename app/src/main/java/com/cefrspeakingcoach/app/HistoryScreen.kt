package com.cefrspeakingcoach.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    sessions: List<PracticeSession>,
    onOpenSession: (PracticeSession) -> Unit
) {
    val availableLevels = remember(sessions) {
        listOf("All") + sessions.map { it.level }.distinct().sorted()
    }
    val selectedFilterState = remember { mutableStateOf("All") }
    val selectedFilter by selectedFilterState

    val filteredSessions = remember(sessions, selectedFilter) {
        if (selectedFilter == "All") sessions else sessions.filter { it.level == selectedFilter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Practice History",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (availableLevels.size > 1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableLevels.forEach { level ->
                        FilterChip(
                            selected = selectedFilter == level,
                            onClick = { selectedFilterState.value = level },
                            label = { Text(level) }
                        )
                    }
                }
            }
        }

        if (filteredSessions.isEmpty()) {
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
                            "No sessions yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "Complete your first speaking session and your history will appear here.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredSessions, key = { it.id }) { session ->
                HistorySessionCard(
                    session = session,
                    onClick = { onOpenSession(session) }
                )
            }
        }
    }
}

@Composable
private fun HistorySessionCard(
    session: PracticeSession,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.clickable { onClick() },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(session.level) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        disabledLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = formatDate(session.createdAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Text(
                session.prompt,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            session.ai?.let {
                Text(
                    "Score: ${it.overall}/5 • Estimated: ${it.cefr_level_estimate}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Text(
                session.transcript,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Icon(Icons.Outlined.Schedule, contentDescription = null)
                    Text("${session.spokenSeconds}s")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Icon(Icons.Outlined.Speed, contentDescription = null)
                    Text("${session.wpm} WPM")
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
}
