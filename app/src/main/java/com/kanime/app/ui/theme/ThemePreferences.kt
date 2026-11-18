package com.kanime.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): ThemeMode =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Pure UI theme preferences store persisted in SharedPreferences.
 * Dynamic color defaults to OFF so the warm brown/white brand identity always shows first.
 */
object ThemePreferences {
    private const val PREFS_NAME = "kanime_ui_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    private var prefs: SharedPreferences? = null

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeModeFlow: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColorFlow: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        _themeMode.value = ThemeMode.fromId(sp.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.id))
        _dynamicColor.value = sp.getBoolean(KEY_DYNAMIC_COLOR, false)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        init(context)
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.id)?.apply()
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        init(context)
        _dynamicColor.value = enabled
        prefs?.edit()?.putBoolean(KEY_DYNAMIC_COLOR, enabled)?.apply()
    }

    fun cycleThemeMode(context: Context, isCurrentlyDark: Boolean) {
        val next = when (_themeMode.value) {
            ThemeMode.SYSTEM -> if (isCurrentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
        }
        setThemeMode(context, next)
    }
}
