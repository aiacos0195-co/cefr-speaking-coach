package com.cefrspeakingcoach.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cefrspeakingcoach.app.ui.theme.A1_GradientEnd
import com.cefrspeakingcoach.app.ui.theme.A1_GradientStart
import com.cefrspeakingcoach.app.ui.theme.A2_GradientEnd
import com.cefrspeakingcoach.app.ui.theme.A2_GradientStart
import com.cefrspeakingcoach.app.ui.theme.B1_GradientEnd
import com.cefrspeakingcoach.app.ui.theme.B1_GradientStart
import com.cefrspeakingcoach.app.ui.theme.B2_GradientEnd
import com.cefrspeakingcoach.app.ui.theme.B2_GradientStart
import com.cefrspeakingcoach.app.ui.theme.BrandGreen
import com.cefrspeakingcoach.app.ui.theme.BrandPurple
import com.cefrspeakingcoach.app.ui.theme.C1_GradientEnd
import com.cefrspeakingcoach.app.ui.theme.C1_GradientStart
import com.cefrspeakingcoach.app.ui.theme.C2_GradientEnd
import com.cefrspeakingcoach.app.ui.theme.C2_GradientStart
import kotlinx.coroutines.delay

data class LevelUi(
    val code: String,
    val title: String,
    val desc: String,
    val gradient: List<Color>
)

@Composable
fun HomeScreenV2(
    selectedLevel: String,
    onLevelChange: (String) -> Unit,
    onStart: () -> Unit
) {
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    val bgResId = remember {
        context.resources.getIdentifier("home_bg", "drawable", context.packageName)
    }

    val levels = remember {
        listOf(
            LevelUi("A1", "Beginner", "Basic phrases and expressions", listOf(A1_GradientStart, A1_GradientEnd)),
            LevelUi("A2", "Elementary", "Simple everyday situations", listOf(A2_GradientStart, A2_GradientEnd)),
            LevelUi("B1", "Intermediate", "Main points of clear standard speech", listOf(B1_GradientStart, B1_GradientEnd)),
            LevelUi("B2", "Upper Intermediate", "Complex texts and abstract topics", listOf(B2_GradientStart, B2_GradientEnd)),
            LevelUi("C1", "Advanced", "Demanding texts with implicit meaning", listOf(C1_GradientStart, C1_GradientEnd)),
            LevelUi("C2", "Proficient", "Near-native fluency", listOf(C2_GradientStart, C2_GradientEnd)),
        )
    }

    var showHero by remember { mutableStateOf(false) }
    var showChips by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showHero = true
        delay(80)
        showChips = true
        delay(80)
        showGrid = true
        delay(80)
        showButtons = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (bgResId != 0) {
            Image(
                painter = painterResource(bgResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(22.dp)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(cs.background.copy(alpha = 0.82f))
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                cs.surfaceVariant.copy(alpha = 0.60f),
                                cs.background.copy(alpha = 0.98f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(
                visible = showHero,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(150))
            ) {
                HeroSectionPremium()
            }

            AnimatedVisibility(
                visible = showChips,
                enter = fadeIn(tween(260)),
                exit = fadeOut(tween(150))
            ) {
                FeatureChipsRowPremium()
            }

            AnimatedVisibility(
                visible = showGrid,
                enter = fadeIn(tween(280)),
                exit = fadeOut(tween(150))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Choose Your Level",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    LevelsGridPremium(
                        levels = levels,
                        selected = selectedLevel,
                        onSelect = onLevelChange
                    )
                }
            }

            AnimatedVisibility(
                visible = showButtons,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(150))
            ) {
                GradientPrimaryButton(
                    text = "Start Practice Session",
                    gradient = levels.firstOrNull { it.code == selectedLevel }?.gradient ?: listOf(cs.primary, cs.secondary),
                    onClick = onStart,
                    leading = {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HeroSectionPremium() {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            cs.surface.copy(alpha = 0.94f),
                            cs.surfaceVariant.copy(alpha = 0.60f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CeFRBadgePremium()

                Text(
                    buildAnnotatedString {
                        pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold))
                        append("Master the ")
                        pop()
                        pushStyle(
                            SpanStyle(
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandPurple
                            )
                        )
                        append("CEFR")
                        pop()
                        pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold))
                        append("\nLevels with AI")
                        pop()
                    },
                    style = MaterialTheme.typography.displaySmall.copy(lineHeight = 40.sp)
                )

                Text(
                    "Personalized feedback for every session to help you reach fluency faster.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CeFRBadgePremium() {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pulseAlpha by rememberInfiniteTransition(label = "pulse")
                .animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )

            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BrandGreen.copy(alpha = pulseAlpha))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "PREMIUM ACCESS",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
private fun FeatureChipsRowPremium() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PremiumChip(
                text = "AI Coaching",
                icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )
            PremiumChip(
                text = "Real-time Feedback",
                icon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
                modifier = Modifier.weight(1f)
            )
        }

        PremiumChip(
            text = "CEFR Aligned",
            icon = { Icon(Icons.Outlined.MenuBook, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PremiumChip(
    text: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(18.dp)

    val glassBg = if (dark) Color.White.copy(alpha = 0.06f) else cs.surface.copy(alpha = 0.92f)
    val border = if (dark) Color.White.copy(alpha = 0.18f) else cs.outline.copy(alpha = 0.22f)
    val content = if (dark) Color.White else cs.onSurface

    Surface(
        modifier = modifier,
        shape = shape,
        tonalElevation = 0.dp,
        color = glassBg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LevelsGridPremium(
    levels: List<LevelUi>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in levels.indices step 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LevelCardPremium(
                    item = levels[i],
                    selected = (levels[i].code == selected),
                    onClick = { onSelect(levels[i].code) },
                    modifier = Modifier.weight(1f)
                )
                if (i + 1 < levels.size) {
                    LevelCardPremium(
                        item = levels[i + 1],
                        selected = (levels[i + 1].code == selected),
                        onClick = { onSelect(levels[i + 1].code) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LevelCardPremium(
    item: LevelUi,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val targetScale = when {
        pressed -> 0.98f
        selected -> 1.02f
        else -> 1f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
        label = "cardScale"
    )

    val elevation by animateDpAsState(
        targetValue = when {
            pressed -> 2.dp
            selected -> 12.dp
            else -> 7.dp
        },
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f),
        label = "cardElevation"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) 0.90f else 0.22f,
        animationSpec = tween(200),
        label = "borderAlpha"
    )

    val shape = RoundedCornerShape(18.dp)
    val glowColor = if (selected) cs.primary.copy(alpha = 0.26f) else Color.Transparent

    Card(
        modifier = modifier
            .height(152.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = glowColor,
                spotColor = glowColor
            )
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick
            ),
        shape = shape,
        border = BorderStroke(1.dp, cs.primary.copy(alpha = borderAlpha))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(item.gradient))
                .padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    item.code,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
                Text(
                    item.desc,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.92f)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (selected) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    Surface(
                        color = cs.surface.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(999.dp),
                        tonalElevation = 2.dp,
                        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.22f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = cs.secondary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Selected",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.secondary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientPrimaryButton(
    text: String,
    gradient: List<Color>,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.99f else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "btnScale"
    )

    val elevation by animateDpAsState(
        targetValue = if (pressed) 6.dp else 10.dp,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 260f),
        label = "btnElev"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = elevation, shape = shape),
        shape = shape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Brush.linearGradient(gradient))
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClick = onClick
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
