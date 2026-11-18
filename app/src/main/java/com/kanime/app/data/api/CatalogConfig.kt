package com.kanime.app.data.api

import android.content.Context
import android.content.SharedPreferences
import com.kanime.app.BuildConfig
import com.kanime.app.ui.player.anime.AnimeSharpenStrength
import com.kanime.app.ui.player.anime.AnimeUpscaleQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client & runtime catalog / playback configuration.
 */
object CatalogConfig {

    private const val PREFS_NAME = "kanime_catalog_prefs"
    private const val KEY_TMDB_API_KEY = "tmdb_api_key"
    private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
    private const val KEY_ANIME4K_UPSCALE = "anime4k_upscale_enabled"
    private const val KEY_LINE_SHARPENING = "anime_line_sharpening"
    private const val KEY_ANIME4K_QUALITY = "anime4k_upscale_quality"
    private const val KEY_SHARPEN_STRENGTH = "anime_sharpen_strength"

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

    private var prefs: SharedPreferences? = null

    private val _tmdbApiKey = MutableStateFlow(BuildConfig.TMDB_API_KEY.trim())
    val tmdbApiKeyFlow: StateFlow<String> = _tmdbApiKey.asStateFlow()

    val tmdbApiKey: String
        get() = _tmdbApiKey.value

    private val _autoPlayNext = MutableStateFlow(true)
    val autoPlayNextFlow: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    val autoPlayNext: Boolean
        get() = _autoPlayNext.value

    private val _animeUpscaleEnabled = MutableStateFlow(false)
    val animeUpscaleEnabledFlow: StateFlow<Boolean> = _animeUpscaleEnabled.asStateFlow()

    val animeUpscaleEnabled: Boolean
        get() = _animeUpscaleEnabled.value

    private val _animeLineSharpening = MutableStateFlow(false)
    val animeLineSharpeningFlow: StateFlow<Boolean> = _animeLineSharpening.asStateFlow()

    val animeLineSharpening: Boolean
        get() = _animeLineSharpening.value

    private val _animeUpscaleQuality = MutableStateFlow(AnimeUpscaleQuality.AUTO)
    val animeUpscaleQualityFlow: StateFlow<AnimeUpscaleQuality> = _animeUpscaleQuality.asStateFlow()

    val animeUpscaleQuality: AnimeUpscaleQuality
        get() = _animeUpscaleQuality.value

    private val _animeSharpenStrength = MutableStateFlow(AnimeSharpenStrength.BALANCED)
    val animeSharpenStrengthFlow: StateFlow<AnimeSharpenStrength> = _animeSharpenStrength.asStateFlow()

    val animeSharpenStrength: AnimeSharpenStrength
        get() = _animeSharpenStrength.value

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        val saved = sp.getString(KEY_TMDB_API_KEY, null)?.trim()
        if (!saved.isNullOrBlank()) {
            _tmdbApiKey.value = saved
        } else if (BuildConfig.TMDB_API_KEY.isNotBlank()) {
            _tmdbApiKey.value = BuildConfig.TMDB_API_KEY.trim()
        }
        _autoPlayNext.value = sp.getBoolean(KEY_AUTO_PLAY_NEXT, true)
        _animeUpscaleEnabled.value = sp.getBoolean(KEY_ANIME4K_UPSCALE, false)
        _animeLineSharpening.value = sp.getBoolean(KEY_LINE_SHARPENING, false)
        _animeUpscaleQuality.value = AnimeUpscaleQuality.fromId(sp.getString(KEY_ANIME4K_QUALITY, null))
        _animeSharpenStrength.value = AnimeSharpenStrength.fromId(sp.getString(KEY_SHARPEN_STRENGTH, null))
    }

    fun setAutoPlayNext(enabled: Boolean) {
        _autoPlayNext.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_PLAY_NEXT, enabled)?.apply()
    }

    fun setAnimeUpscaleEnabled(enabled: Boolean) {
        _animeUpscaleEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_ANIME4K_UPSCALE, enabled)?.apply()
    }

    fun setAnimeLineSharpening(enabled: Boolean) {
        _animeLineSharpening.value = enabled
        prefs?.edit()?.putBoolean(KEY_LINE_SHARPENING, enabled)?.apply()
    }

    fun setAnimeUpscaleQuality(quality: AnimeUpscaleQuality) {
        _animeUpscaleQuality.value = quality
        prefs?.edit()?.putString(KEY_ANIME4K_QUALITY, quality.id)?.apply()
    }

    fun setAnimeSharpenStrength(strength: AnimeSharpenStrength) {
        _animeSharpenStrength.value = strength
        prefs?.edit()?.putString(KEY_SHARPEN_STRENGTH, strength.id)?.apply()
    }

    fun setTmdbApiKey(key: String) {
        val trimmed = key.trim()
        _tmdbApiKey.value = trimmed
        prefs?.edit()?.putString(KEY_TMDB_API_KEY, trimmed)?.apply()
        CatalogCache.clear()
    }
}
