package com.ani.nightwave

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PlayerScreen(
    title: String,
    cover: Bitmap?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    waveform: FloatArray,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val barCount = ThemeColors.barCount
    val motionOn = ThemeColors.motionEnabled

    // Single lightweight smoothing loop instead of many separate Animatables.
    // This is the main perf fix: no animation restarts on every visualizer
    // callback, and no per-frame background effect behind the cover anymore
    // (the glow ring was removed entirely - see below).
    var barLevels by remember(barCount) { mutableStateOf(FloatArray(barCount) { 0.08f }) }
    var beatScale by remember { mutableFloatStateOf(1f) }
    val latestWaveform by rememberUpdatedState(waveform)

    LaunchedEffect(barCount) {
        while (isActive) {
            val wf = latestWaveform
            if (wf.isNotEmpty()) {
                val step = (wf.size / barCount).coerceAtLeast(1)
                val next = FloatArray(barCount)
                for (i in 0 until barCount) {
                    val idx = (i * step).coerceIn(0, wf.size - 1)
                    val raw = (wf[idx] / 128f).coerceIn(0.05f, 1f)
                    val current = barLevels.getOrElse(i) { 0.08f }
                    next[i] = current + (raw - current) * 0.4f
                }
                barLevels = next
                if (motionOn) {
                    val avg = wf.average().toFloat() / 128f
                    val targetScale = 1f + avg.coerceIn(0f, 1f) * 0.10f
                    beatScale += (targetScale - beatScale) * 0.35f
                } else {
                    beatScale = 1f
                }
            } else if (motionOn) {
                beatScale += (1f - beatScale) * 0.1f
            } else {
                beatScale = 1f
            }
            delay(33) // ~30fps, plenty smooth, much cheaper than per-bar animations
        }
    }

    val backgroundBrush = remember(ThemeColors.bgTop, ThemeColors.bgBottom) {
        Brush.verticalGradient(listOf(ThemeColors.bgTop, Color.Black, ThemeColors.bgBottom))
    }
    val coverShape = remember(ThemeColors.coverCornerRadius) {
        RoundedCornerShape(ThemeColors.coverCornerRadius.dp)
    }
    val coverGradient = remember(ThemeColors.indigo, ThemeColors.violet) {
        Brush.linearGradient(listOf(ThemeColors.indigo, ThemeColors.violet))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Album cover - no background circle/glow effect behind it anymore,
        // just the cover itself with a subtle beat-driven scale.
        Box(
            modifier = Modifier
                .size(230.dp)
                .scale(beatScale)
                .clip(coverShape)
                .background(coverGradient),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = "Cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            "${formatTime(positionMs)} - ${formatTime((durationMs - positionMs).coerceAtLeast(0))}",
            color = NeonGreen,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = Violet,
            trackColor = Color.White.copy(alpha = 0.12f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        VisualizerBars(barLevels = barLevels, barCount = barCount, modifier = Modifier.fillMaxWidth().height(80.dp))

        // Mirrored reflection of the same bars, fading out downward.
        VisualizerReflection(barLevels = barLevels, barCount = barCount, modifier = Modifier.fillMaxWidth().height(28.dp))

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(30.dp)
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Violet, Indigo)))
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

// Extracted into their own composables (rather than inline in PlayerScreen)
// so recomposition on every ~33ms tick is scoped to just these two Canvas
// draws instead of the whole screen.
@Composable
private fun VisualizerBars(barLevels: FloatArray, barCount: Int, modifier: Modifier = Modifier) {
    val neon = NeonGreen
    val violet = Violet
    val indigo = Indigo
    Canvas(modifier = modifier) {
        val barWidth = size.width / barCount
        for (i in 0 until barCount) {
            val h = (size.height * barLevels.getOrElse(i) { 0.08f }).coerceAtLeast(4f)
            val color = when {
                i % 3 == 0 -> neon
                i % 3 == 1 -> violet
                else -> indigo
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(i * barWidth + barWidth * 0.18f, size.height - h),
                size = Size(barWidth * 0.64f, h),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

@Composable
private fun VisualizerReflection(barLevels: FloatArray, barCount: Int, modifier: Modifier = Modifier) {
    val neon = NeonGreen
    val violet = Violet
    val indigo = Indigo
    Canvas(modifier = modifier) {
        val barWidth = size.width / barCount
        for (i in 0 until barCount) {
            val h = (size.height * barLevels.getOrElse(i) { 0.08f } * 0.9f).coerceAtLeast(2f)
            val color = when {
                i % 3 == 0 -> neon
                i % 3 == 1 -> violet
                else -> indigo
            }
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)),
                topLeft = Offset(i * barWidth + barWidth * 0.18f, 0f),
                size = Size(barWidth * 0.64f, h),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}
