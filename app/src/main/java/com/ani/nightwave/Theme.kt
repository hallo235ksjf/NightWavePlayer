package com.ani.nightwave

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** One selectable color scheme for the theme editor. */
data class ThemeVariant(
    val name: String,
    val neonGreen: Color,
    val violet: Color,
    val indigo: Color,
    val yellow: Color
)

val ThemeVariants = listOf(
    ThemeVariant(
        name = "Night Cyan",
        neonGreen = Color(0xFF00E5A0),
        violet = Color(0xFF8E7CFF),
        indigo = Color(0xFF6C5CE7),
        yellow = Color(0xFFFFD54A)
    ),
    ThemeVariant(
        name = "Crimson Pulse",
        neonGreen = Color(0xFFFF5C7A),
        violet = Color(0xFFFF8A5B),
        indigo = Color(0xFFB83280),
        yellow = Color(0xFFFFD54A)
    ),
    ThemeVariant(
        name = "Deep Ocean",
        neonGreen = Color(0xFF29D6E0),
        violet = Color(0xFF3A7BD5),
        indigo = Color(0xFF1E3A8A),
        yellow = Color(0xFF7FE8C4)
    ),
    ThemeVariant(
        name = "Toxic Lime",
        neonGreen = Color(0xFFB6FF3B),
        violet = Color(0xFF6DD400),
        indigo = Color(0xFF2E4600),
        yellow = Color(0xFFEFFF7A)
    ),
    ThemeVariant(
        name = "Mono Violet",
        neonGreen = Color(0xFFC9A6FF),
        violet = Color(0xFF9B5DE5),
        indigo = Color(0xFF5A2A9E),
        yellow = Color(0xFFE0C3FC)
    )
)

/** Default background gradient, matches the original hardcoded values so
 *  existing installs look the same until someone edits it. */
val DefaultBgTop = Color(0xFF0B0B12)
val DefaultBgBottom = Color(0xFF0A0A14)
const val DefaultCoverRadius = 28f
const val DefaultBarCount = 24

/** Holds the currently active theme colors as Compose state, so every screen
 *  that reads NeonGreen/Violet/Indigo/Yellow recomposes automatically when
 *  the theme is changed - no need to thread a color scheme through every
 *  composable's parameters. */
object ThemeColors {
    var neonGreen by mutableStateOf(ThemeVariants[0].neonGreen)
    var violet by mutableStateOf(ThemeVariants[0].violet)
    var indigo by mutableStateOf(ThemeVariants[0].indigo)
    var yellow by mutableStateOf(ThemeVariants[0].yellow)

    /** Background gradient - edited independently of the accent presets. */
    var bgTop by mutableStateOf(DefaultBgTop)
    var bgBottom by mutableStateOf(DefaultBgBottom)

    /** Extra layout customization, also independent of the accent presets. */
    var coverCornerRadius by mutableFloatStateOf(DefaultCoverRadius)
    var barCount by mutableIntStateOf(DefaultBarCount)

    /** True once the person has hand-tweaked at least one accent color
     *  individually. Presets are just a fast starting point now - this is
     *  what makes the "Fein-Tuning" section in the editor the primary way
     *  to shape a theme. */
    var isCustom by mutableStateOf(false)

    /** Global on/off for the pulsing cover and bar-animation smoothing.
     *  Off = everything still shows, just no motion. */
    var motionEnabled by mutableStateOf(true)

    fun apply(variant: ThemeVariant) {
        neonGreen = variant.neonGreen
        violet = variant.violet
        indigo = variant.indigo
        yellow = variant.yellow
    }

    fun applyIndex(index: Int) {
        val v = ThemeVariants.getOrNull(index) ?: return
        apply(v)
    }

    /** Sets a single accent or background color by role, independent of the
     *  others - this is the "jedes Teil einzeln bearbeiten" entry point. */
    fun setRole(role: String, color: Color) {
        when (role) {
            "neon" -> neonGreen = color
            "violet" -> violet = color
            "indigo" -> indigo = color
            "yellow" -> yellow = color
            "bgTop" -> bgTop = color
            "bgBottom" -> bgBottom = color
        }
    }

    fun getRole(role: String): Color = when (role) {
        "neon" -> neonGreen
        "violet" -> violet
        "indigo" -> indigo
        "yellow" -> yellow
        "bgTop" -> bgTop
        "bgBottom" -> bgBottom
        else -> yellow
    }

    /** Loads the saved theme (or the default) on app launch: presets first,
     *  then any custom per-role overrides on top if custom mode is active,
     *  then the independent background/layout customizations. */
    fun loadSaved(context: Context) {
        applyIndex(Prefs.getThemeIndex(context))
        motionEnabled = Prefs.isMotionEnabled(context)
        isCustom = Prefs.isThemeCustom(context)
        if (isCustom) {
            neonGreen = Color(Prefs.getCustomColor(context, "neon", neonGreen.toArgb()))
            violet = Color(Prefs.getCustomColor(context, "violet", violet.toArgb()))
            indigo = Color(Prefs.getCustomColor(context, "indigo", indigo.toArgb()))
            yellow = Color(Prefs.getCustomColor(context, "yellow", yellow.toArgb()))
        }
        bgTop = Color(Prefs.getBgColor(context, "top", DefaultBgTop.toArgb()))
        bgBottom = Color(Prefs.getBgColor(context, "bottom", DefaultBgBottom.toArgb()))
        coverCornerRadius = Prefs.getCoverRadius(context, DefaultCoverRadius)
        barCount = Prefs.getBarCount(context, DefaultBarCount)
    }
}
