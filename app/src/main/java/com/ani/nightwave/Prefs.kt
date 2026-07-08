package com.ani.nightwave

import android.content.Context
import android.net.Uri

object Prefs {
    private const val NAME = "nightwave_prefs"
    private const val KEY_FOLDER = "music_folder_uri"
    private const val KEY_LAST_TRACK = "last_track_uri"
    private const val KEY_ORDER = "track_order"
    private const val KEY_THEME_INDEX = "theme_index"
    private const val KEY_MOTION_ENABLED = "motion_enabled"
    private const val KEY_THEME_IS_CUSTOM = "theme_is_custom"
    private const val KEY_CUSTOM_NEON = "custom_neon"
    private const val KEY_CUSTOM_VIOLET = "custom_violet"
    private const val KEY_CUSTOM_INDIGO = "custom_indigo"
    private const val KEY_CUSTOM_YELLOW = "custom_yellow"
    private const val KEY_BG_TOP = "bg_top"
    private const val KEY_BG_BOTTOM = "bg_bottom"
    private const val KEY_COVER_RADIUS = "cover_radius"
    private const val KEY_BAR_COUNT = "bar_count"

    fun getMusicFolder(context: Context): Uri? {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_FOLDER, null)
        return raw?.let { Uri.parse(it) }
    }

    fun setMusicFolder(context: Context, uri: Uri) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDER, uri.toString())
            .apply()
    }

    fun getLastTrackUri(context: Context): String? {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_LAST_TRACK, null)
    }

    fun setLastTrackUri(context: Context, uriString: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_TRACK, uriString)
            .apply()
    }

    fun getTrackOrder(context: Context): List<String> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_ORDER, null) ?: return emptyList()
        return raw.split("||").filter { it.isNotBlank() }
    }

    fun setTrackOrder(context: Context, uris: List<String>) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORDER, uris.joinToString("||"))
            .apply()
    }

    fun getThemeIndex(context: Context): Int {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_THEME_INDEX, 0)
    }

    fun setThemeIndex(context: Context, index: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_INDEX, index)
            .apply()
    }

    fun isMotionEnabled(context: Context): Boolean {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_MOTION_ENABLED, true)
    }

    fun setMotionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MOTION_ENABLED, enabled)
            .apply()
    }

    fun isThemeCustom(context: Context): Boolean {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_THEME_IS_CUSTOM, false)
    }

    fun setThemeCustom(context: Context, custom: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_THEME_IS_CUSTOM, custom)
            .apply()
    }

    /** Custom per-role colors as ARGB ints, saved individually so each part
     *  of the theme can be tweaked on its own. Falls back to the given
     *  default (usually the current preset value) if never set. */
    fun getCustomColor(context: Context, role: String, default: Int): Int {
        val key = when (role) {
            "neon" -> KEY_CUSTOM_NEON
            "violet" -> KEY_CUSTOM_VIOLET
            "indigo" -> KEY_CUSTOM_INDIGO
            else -> KEY_CUSTOM_YELLOW
        }
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(key, default)
    }

    fun setCustomColor(context: Context, role: String, colorArgb: Int) {
        val key = when (role) {
            "neon" -> KEY_CUSTOM_NEON
            "violet" -> KEY_CUSTOM_VIOLET
            "indigo" -> KEY_CUSTOM_INDIGO
            else -> KEY_CUSTOM_YELLOW
        }
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(key, colorArgb)
            .apply()
    }

    /** Background gradient colors - independent of the accent presets, so
     *  picking a Schnellauswahl-preset doesn't wipe out a custom background. */
    fun getBgColor(context: Context, which: String, default: Int): Int {
        val key = if (which == "top") KEY_BG_TOP else KEY_BG_BOTTOM
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(key, default)
    }

    fun setBgColor(context: Context, which: String, colorArgb: Int) {
        val key = if (which == "top") KEY_BG_TOP else KEY_BG_BOTTOM
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(key, colorArgb)
            .apply()
    }

    fun getCoverRadius(context: Context, default: Float): Float {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(KEY_COVER_RADIUS, default)
    }

    fun setCoverRadius(context: Context, radius: Float) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_COVER_RADIUS, radius)
            .apply()
    }

    fun getBarCount(context: Context, default: Int): Int {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_BAR_COUNT, default)
    }

    fun setBarCount(context: Context, count: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BAR_COUNT, count)
            .apply()
    }

}
