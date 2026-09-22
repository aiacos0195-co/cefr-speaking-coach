package com.cefrspeakingcoach.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProgressScreen(sessions: List<PracticeSession>) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        Text(
            "Your Progress",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No data available yet. Start practicing!", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val sessionsWithAi = sessions.filter { it.ai != null }
            
            if (sessionsWithAi.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No AI evaluations yet. Keep practicing!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val averageScore = sessionsWithAi.map { it.ai!!.overall }.average().toFloat()
                val sessionsStreak = sessions.size // Simple streak logic for now

                val avgFluency = sessionsWithAi.map { it.ai!!.scores.fluency }.average().toFloat() / 5f
                val avgGrammar = sessionsWithAi.map { it.ai!!.scores.grammar }.average().toFloat() / 5f
                val avgVocabulary = sessionsWithAi.map { it.ai!!.scores.vocabulary }.average().toFloat() / 5f
                val avgCoherence = sessionsWithAi.map { it.ai!!.scores.coherence }.average().toFloat() / 5f

                val skills = listOf(
                    "Fluency" to avgFluency,
                    "Grammar" to avgGrammar,
                    "Vocabulary" to avgVocabulary,
                    "Coherence" to avgCoherence
                )

                AverageScoreCard(averageScore)

                Spacer(Modifier.height(24.dp))

                StreakCard(sessionsStreak)

                Spacer(Modifier.height(24.dp))

                SkillsBreakdown(skills)
            }
        }
    }
}

@Composable
fun AverageScoreCard(score: Float) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D3A8C))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Average Overall Score",
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "%.1f".format(score),
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "/ 5.0",
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun StreakCard(streak: Int) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Total Sessions",
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$streak sessions 🔥",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SkillsBreakdown(skills: List<Pair<String, Float>>) {
    Column {
        Text(
            "Skills Breakdown",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        skills.forEach { (name, progress) ->
            SkillBar(
                name = name,
                progress = progress
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun SkillBar(name: String, progress: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name)
            Text("${(progress * 100).toInt()}%")
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Color.DarkGray, RoundedCornerShape(50))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF34D399),
                                Color(0xFF3B82F6)
                            )
                        ),
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}
