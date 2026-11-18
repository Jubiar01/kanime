package com.kanime.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists first-run onboarding state in SharedPreferences (matching [ThemePreferences]).
 * Initialize in MainActivity.onCreate before setContent so frame 1 never flashes.
 */
object OnboardingPreferences {
    private const val PREFS_NAME = "kanime_onboarding_prefs"
    private const val KEY_COMPLETED = "onboarding_completed"

    private var prefs: SharedPreferences? = null

    private val _isCompleted = MutableStateFlow(false)
    val isCompletedFlow: StateFlow<Boolean> = _isCompleted.asStateFlow()

    val isCompleted: Boolean
        get() = _isCompleted.value

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        _isCompleted.value = sp.getBoolean(KEY_COMPLETED, false)
    }

    fun markCompleted(context: Context) {
        init(context)
        _isCompleted.value = true
        prefs?.edit()?.putBoolean(KEY_COMPLETED, true)?.apply()
    }

    fun reset(context: Context) {
        init(context)
        _isCompleted.value = false
        prefs?.edit()?.putBoolean(KEY_COMPLETED, false)?.apply()
    }
}
