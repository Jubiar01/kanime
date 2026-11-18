package com.kanime.app.data.api

import android.content.Context
import android.content.SharedPreferences
import com.kanime.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class AnimeUpscalerMode(
    val id: String,
    val label: String,
    val description: String,
    val scaleFactor: Float,
    val maxWidth: Int,
    val maxHeight: Int,
    val upscaleStrength: Float
) {
    OFF(
        id = "off",
        label = "Off (Original)",
        description = "Standard video output with no GPU upscaling",
        scaleFactor = 1.0f,
        maxWidth = 1920,
        maxHeight = 1080,
        upscaleStrength = 0.0f
    ),
    FAST(
        id = "fast",
        label = "Anime4K Fast (1.5x)",
        description = "Balanced GPU upscaling + adaptive sharpening (battery friendly)",
        scaleFactor = 1.5f,
        maxWidth = 2560,
        maxHeight = 1440,
        upscaleStrength = 0.65f
    ),
    ULTRA_4K(
        id = "ultra_4k",
        label = "Anime4K Ultra (4K / 2x)",
        description = "2x 4K supersampling + crisp edge reconstruction",
        scaleFactor = 2.0f,
        maxWidth = 3840,
        maxHeight = 2160,
        upscaleStrength = 1.0f
    );

    companion object {
        fun fromId(id: String?): AnimeUpscalerMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OFF
    }
}

/**
 * Shared HTTP client & runtime catalog / playback configuration.
 */
object CatalogConfig {

    private const val PREFS_NAME = "kanime_catalog_prefs"
    private const val KEY_TMDB_API_KEY = "tmdb_api_key"
    private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
    private const val KEY_ANIME4K_MODE = "anime4k_upscaler_mode"
    private const val KEY_LINE_SHARPENING = "anime_line_sharpening"
    private const val KEY_LINE_SHARPENING_STRENGTH = "anime_line_sharpening_strength"

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

    private val _animeUpscalerMode = MutableStateFlow(AnimeUpscalerMode.OFF)
    val animeUpscalerModeFlow: StateFlow<AnimeUpscalerMode> = _animeUpscalerMode.asStateFlow()

    val animeUpscalerMode: AnimeUpscalerMode
        get() = _animeUpscalerMode.value

    private val _animeLineSharpening = MutableStateFlow(false)
    val animeLineSharpeningFlow: StateFlow<Boolean> = _animeLineSharpening.asStateFlow()

    val animeLineSharpening: Boolean
        get() = _animeLineSharpening.value

    private val _animeLineSharpeningStrength = MutableStateFlow(0.65f)
    val animeLineSharpeningStrengthFlow: StateFlow<Float> = _animeLineSharpeningStrength.asStateFlow()

    val animeLineSharpeningStrength: Float
        get() = _animeLineSharpeningStrength.value

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
        _animeUpscalerMode.value = AnimeUpscalerMode.fromId(sp.getString(KEY_ANIME4K_MODE, AnimeUpscalerMode.OFF.id))
        _animeLineSharpening.value = sp.getBoolean(KEY_LINE_SHARPENING, false)
        _animeLineSharpeningStrength.value = sp.getFloat(KEY_LINE_SHARPENING_STRENGTH, 0.65f).coerceIn(0.1f, 1.0f)
    }

    fun setAutoPlayNext(enabled: Boolean) {
        _autoPlayNext.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_PLAY_NEXT, enabled)?.apply()
    }

    fun setAnimeUpscalerMode(mode: AnimeUpscalerMode) {
        _animeUpscalerMode.value = mode
        prefs?.edit()?.putString(KEY_ANIME4K_MODE, mode.id)?.apply()
    }

    fun setAnimeLineSharpening(enabled: Boolean) {
        _animeLineSharpening.value = enabled
        prefs?.edit()?.putBoolean(KEY_LINE_SHARPENING, enabled)?.apply()
    }

    fun setAnimeLineSharpeningStrength(strength: Float) {
        val clamped = strength.coerceIn(0.1f, 1.0f)
        _animeLineSharpeningStrength.value = clamped
        prefs?.edit()?.putFloat(KEY_LINE_SHARPENING_STRENGTH, clamped)?.apply()
    }

    fun setTmdbApiKey(key: String) {
        val trimmed = key.trim()
        _tmdbApiKey.value = trimmed
        prefs?.edit()?.putString(KEY_TMDB_API_KEY, trimmed)?.apply()
        // Clear cached TMDB misses so newly configured key takes effect immediately
        CatalogCache.clear()
    }
}
