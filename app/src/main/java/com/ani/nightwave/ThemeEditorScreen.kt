package com.ani.nightwave

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Theme edit screen, rebuilt around per-part editing:
 *  - "Schnellauswahl" row of presets at the top - a fast starting point, no
 *    longer the only option.
 *  - Below that, every accent role (Neon, Violet, Indigo, Yellow) gets its
 *    own Hue/Sättigung/Helligkeit sliders, editable completely independently
 *    of the others. Changing any slider flips the theme into "custom" mode
 *    and persists immediately.
 */
@Composable
fun ThemeEditorScreen(context: Context, onDone: () -> Unit) {
    var selectedRole by remember { mutableStateOf("neon") }

    var neon by remember { mutableStateOf(ThemeColors.neonGreen) }
    var violet by remember { mutableStateOf(ThemeColors.violet) }
    var indigo by remember { mutableStateOf(ThemeColors.indigo) }
    var yellow by remember { mutableStateOf(ThemeColors.yellow) }
    var bgTop by remember { mutableStateOf(ThemeColors.bgTop) }
    var bgBottom by remember { mutableStateOf(ThemeColors.bgBottom) }

    var coverRadius by remember { mutableFloatStateOf(ThemeColors.coverCornerRadius) }
    var barCount by remember { mutableFloatStateOf(ThemeColors.barCount.toFloat()) }

    fun colorFor(role: String) = when (role) {
        "neon" -> neon
        "violet" -> violet
        "indigo" -> indigo
        "yellow" -> yellow
        "bgTop" -> bgTop
        else -> bgBottom
    }

    fun applyRole(role: String, color: Color) {
        when (role) {
            "neon" -> neon = color
            "violet" -> violet = color
            "indigo" -> indigo = color
            "yellow" -> yellow = color
            "bgTop" -> bgTop = color
            "bgBottom" -> bgBottom = color
        }
        ThemeColors.setRole(role, color)
        if (role == "bgTop" || role == "bgBottom") {
            Prefs.setBgColor(context, if (role == "bgTop") "top" else "bottom", color.toArgb())
        } else {
            ThemeColors.isCustom = true
            Prefs.setThemeCustom(context, true)
            Prefs.setCustomColor(context, role, color.toArgb())
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp, 24.dp, 20.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Theme bearbeiten", color = Yellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDone) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Violet)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Fertig", color = Violet, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            LivePreview(neon = neon, violet = violet, indigo = indigo, yellow = yellow)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Schnellauswahl",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(20.dp, 4.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ThemeVariants) { variant ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            neon = variant.neonGreen
                            violet = variant.violet
                            indigo = variant.indigo
                            yellow = variant.yellow
                            ThemeColors.apply(variant)
                            ThemeColors.isCustom = false
                            Prefs.setThemeCustom(context, false)
                            Prefs.setThemeIndex(context, ThemeVariants.indexOf(variant))
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(variant.indigo, variant.violet, variant.neonGreen)))
                                .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(variant.name, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Fein-Tuning - jeder Teil einzeln",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(20.dp, 4.dp)
            )

            val roles = listOf(
                "neon" to "Neon", "violet" to "Violet", "indigo" to "Indigo", "yellow" to "Yellow",
                "bgTop" to "BG oben", "bgBottom" to "BG unten"
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(roles) { (role, label) ->
                    val active = role == selectedRole
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) colorFor(role).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                            .border(
                                1.dp,
                                if (active) colorFor(role) else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedRole = role }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ColorRoleEditor(
                color = colorFor(selectedRole),
                onColorChange = { applyRole(selectedRole, it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = {
                    val default = ThemeVariants[0]
                    val resetColor = when (selectedRole) {
                        "neon" -> default.neonGreen
                        "violet" -> default.violet
                        "indigo" -> default.indigo
                        "yellow" -> default.yellow
                        "bgTop" -> DefaultBgTop
                        else -> DefaultBgBottom
                    }
                    applyRole(selectedRole, resetColor)
                }) {
                    Icon(Icons.Filled.Restore, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Diesen Teil zurücksetzen", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Layout",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(20.dp, 4.dp)
            )

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Cover-Ecken-Radius: ${coverRadius.toInt()}dp", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Slider(
                    value = coverRadius,
                    onValueChange = {
                        coverRadius = it
                        ThemeColors.coverCornerRadius = it
                        Prefs.setCoverRadius(context, it)
                    },
                    valueRange = 0f..115f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Violet,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Visualizer-Balken: ${barCount.toInt()}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Slider(
                    value = barCount,
                    onValueChange = {
                        barCount = it
                        ThemeColors.barCount = it.toInt()
                        Prefs.setBarCount(context, it.toInt())
                    },
                    valueRange = 12f..40f,
                    steps = 27,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Violet,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** Hue / Sättigung / Helligkeit sliders, plus a direct hex input, for a
 *  single color role. */
@Composable
private fun ColorRoleEditor(color: Color, onColorChange: (Color) -> Unit) {
    val hsv = remember(color) { FloatArray(3) }
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    var hue by remember(color) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(color) { mutableFloatStateOf(hsv[1]) }
    var value by remember(color) { mutableFloatStateOf(hsv[2]) }

    val currentHex = remember(color) { Integer.toHexString(color.toArgb()).uppercase().takeLast(6) }
    var hexText by remember(color) { mutableStateOf(currentHex) }
    var hexError by remember(color) { mutableStateOf(false) }

    fun emit(h: Float, s: Float, v: Float) {
        onColorChange(Color(AndroidColor.HSVToColor(floatArrayOf(h, s, v))))
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = hexText,
                onValueChange = { input ->
                    val cleaned = input.trimStart('#').uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(6)
                    hexText = cleaned
                    if (cleaned.length == 6) {
                        try {
                            onColorChange(Color(AndroidColor.parseColor("#$cleaned")))
                            hexError = false
                        } catch (e: Exception) {
                            hexError = true
                        }
                    } else {
                        hexError = cleaned.isNotEmpty()
                    }
                },
                prefix = { Text("#", color = Color.White.copy(alpha = 0.6f)) },
                singleLine = true,
                isError = hexError,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.width(140.dp).height(52.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Violet,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = Violet
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SliderRow(
            label = "Farbton",
            value = hue,
            range = 0f..360f,
            trackColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        ) {
            hue = it
            emit(hue, sat, value)
        }

        SliderRow(
            label = "Sättigung",
            value = sat,
            range = 0f..1f,
            trackColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))
        ) {
            sat = it
            emit(hue, sat, value)
        }

        SliderRow(
            label = "Helligkeit",
            value = value,
            range = 0f..1f,
            trackColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))
        ) {
            value = it
            emit(hue, sat, value)
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, trackColor: Color, onChange: (Float) -> Unit) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = trackColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
private fun LivePreview(neon: Color, violet: Color, indigo: Color, yellow: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(28.dp, 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(indigo, violet))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("0:41 - 4:49", color = neon, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { 0.35f },
            modifier = Modifier.fillMaxWidth(0.7f).height(4.dp).clip(RoundedCornerShape(4.dp)),
            color = violet,
            trackColor = Color.White.copy(alpha = 0.12f)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(30.dp)) {
            val heights = listOf(0.4f, 0.7f, 1f, 0.5f, 0.8f, 0.3f, 0.6f, 0.9f, 0.45f, 0.65f)
            heights.forEachIndexed { i, h ->
                val c = when (i % 3) { 0 -> neon; 1 -> violet; else -> indigo }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(5.dp)
                        .height((30 * h).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(c)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(violet, indigo))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
