package com.kanime.app.data.notify

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings + bookkeeping for new-episode alerts: the on/off switch, when the background check last ran
 * and which episodes were already notified (so nothing is announced twice).
 */
object EpisodeAlertPreferences {
    private const val PREFS_NAME = "kanime_episode_alert_prefs"
    private const val KEY_ENABLED = "alerts_enabled_v1"
    private const val KEY_LAST_CHECK_SEC = "last_check_sec_v1"
    private const val KEY_NOTIFIED = "notified_keys_v1"
    private const val MAX_NOTIFIED_KEYS = 200

    private var prefs: SharedPreferences? = null
    private val _enabled = MutableStateFlow(false)
    val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        _enabled.value = sp.getBoolean(KEY_ENABLED, false)
    }

    fun isEnabled(): Boolean = _enabled.value

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        // Start fresh when switched on, so turning alerts on never floods the phone with old episodes.
        if (enabled) prefs?.edit()?.putLong(KEY_LAST_CHECK_SEC, System.currentTimeMillis() / 1000L)?.apply()
    }

    fun lastCheckSec(): Long = prefs?.getLong(KEY_LAST_CHECK_SEC, 0L) ?: 0L

    fun setLastCheckSec(value: Long) {
        prefs?.edit()?.putLong(KEY_LAST_CHECK_SEC, value)?.apply()
    }

    /** True the first time this (malId, episode) is seen; remembers it. */
    fun markNotified(malId: Int, episode: Int): Boolean {
        val sp = prefs ?: return true
        val key = "$malId:$episode"
        val current = sp.getString(KEY_NOTIFIED, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        if (key in current) return false
        val updated = (current + key).takeLast(MAX_NOTIFIED_KEYS)
        sp.edit().putString(KEY_NOTIFIED, updated.joinToString(",")).apply()
        return true
    }
}
