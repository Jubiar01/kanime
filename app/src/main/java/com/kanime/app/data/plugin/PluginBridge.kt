package com.kanime.app.data.plugin

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PluginEpisode(
    val num: Double,
    val id: String,
    val title: String
)

data class PluginServer(
    val name: String,
    val sourceId: String,
    val type: String // "sub", "dub", "raw"
)

data class PluginSubtitle(
    val lang: String,
    val url: String,
    val isDefault: Boolean = false
)

data class SubtitleSyncResult(
    val ok: Boolean,
    val offsetMs: Long,
    val scale: Double,
    val confidence: String, // "high" | "medium" | "low"
    val reason: String,
    val uncertaintyMs: Long = 0L // width of the interval the real offset lies in (KOpenSubtitle 1.0.4+)
)

data class PluginStream(
    val embedUrl: String,
    val m3u8: String?,
    val referer: String? = null,
    val subtitles: List<PluginSubtitle> = emptyList(),
    val serverName: String,
    val type: String = "hls" // "hls", "iframe"
)

object PluginBridge {

    private const val TAG = "PluginBridge"
    const val KANIKOTO_AUTHORITY = "com.kanime.plugin.kanikoto.provider"
    const val KANIKOTO_PACKAGE = "com.kanime.plugin.kanikoto"
    const val KANIMAREVIVAL_AUTHORITY = "com.kanime.plugin.kanimarevival.provider"
    const val KANIMAREVIVAL_PACKAGE = "com.kanime.plugin.kanimarevival"
    const val KOPENSUBTITLE_AUTHORITY = "com.kanime.plugin.kopensubtitle.provider"
    const val KOPENSUBTITLE_PACKAGE = "com.kanime.plugin.kopensubtitle"

    fun isPluginInstalled(context: Context, packageName: String = KANIKOTO_PACKAGE): Boolean {
        if (packageName.isBlank()) return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun callProvider(context: Context, authority: String, method: String, arg: String? = null): Bundle? {
        if (authority.isBlank()) return null
        return try {
            val uri = Uri.parse("content://$authority")
            context.contentResolver.call(uri, method, arg, null)
        } catch (e: Exception) {
            Log.w(TAG, "callProvider error for method=$method on authority=$authority: ${e.message}")
            null
        }
    }

    suspend fun ping(context: Context, authority: String = KANIKOTO_AUTHORITY): Boolean = withContext(Dispatchers.IO) {
        val bundle = callProvider(context, authority, "ping")
        bundle?.getBoolean("active", false) ?: false
    }

    suspend fun testConnection(context: Context, authority: String = KANIKOTO_AUTHORITY): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val bundle = callProvider(context, authority, "testConnection")
        if (bundle != null) {
            val ok = bundle.getBoolean("success", false)
            val msg = bundle.getString("message", "Unknown status")
            Pair(ok, msg)
        } else {
            Pair(false, "Plugin not installed or unresponsive")
        }
    }

    suspend fun findSlug(context: Context, title: String, authority: String = KANIKOTO_AUTHORITY): String? = withContext(Dispatchers.IO) {
        val bundle = callProvider(context, authority, "findSlug", title)
        bundle?.getString("slug")
    }

    suspend fun getEpisodes(context: Context, slug: String, authority: String = KANIKOTO_AUTHORITY): List<PluginEpisode> = withContext(Dispatchers.IO) {
        val bundle = callProvider(context, authority, "getEpisodes", slug)
        val jsonStr = bundle?.getString("result") ?: return@withContext emptyList()
        val list = mutableListOf<PluginEpisode>()
        try {
            val ja = JSONArray(jsonStr)
            for (i in 0 until ja.length()) {
                val jo = ja.getJSONObject(i)
                list.add(
                    PluginEpisode(
                        num = jo.optDouble("num", 1.0),
                        id = jo.optString("id"),
                        title = jo.optString("title")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse episodes error", e)
        }
        list
    }

    suspend fun getServers(context: Context, episodeId: String, authority: String = KANIKOTO_AUTHORITY): List<PluginServer> = withContext(Dispatchers.IO) {
        val bundle = callProvider(context, authority, "getServers", episodeId)
        val jsonStr = bundle?.getString("result") ?: return@withContext emptyList()
        val list = mutableListOf<PluginServer>()
        try {
            val ja = JSONArray(jsonStr)
            for (i in 0 until ja.length()) {
                val jo = ja.getJSONObject(i)
                list.add(
                    PluginServer(
                        name = jo.optString("name"),
                        sourceId = jo.optString("sourceId"),
                        type = jo.optString("type", "sub")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse servers error", e)
        }
        list
    }

    suspend fun getStream(context: Context, sourceId: String, authority: String = KANIKOTO_AUTHORITY): PluginStream? = withContext(Dispatchers.IO) {
        val bundle = callProvider(context, authority, "getStream", sourceId)
        val jsonStr = bundle?.getString("result") ?: return@withContext null
        try {
            val jo = JSONObject(jsonStr)
            val embedUrl = jo.optString("embedUrl")
            val m3u8 = jo.optString("m3u8").takeIf { it.isNotBlank() }
            val referer = jo.optString("referer").takeIf { it.isNotBlank() }
            val serverName = jo.optString("serverName", "Anikoto")
            val type = jo.optString("type", "hls")

            val subs = mutableListOf<PluginSubtitle>()
            val ja = jo.optJSONArray("subtitles")
            if (ja != null) {
                for (i in 0 until ja.length()) {
                    val so = ja.getJSONObject(i)
                    subs.add(
                        PluginSubtitle(
                            lang = so.optString("lang"),
                            url = so.optString("url"),
                            isDefault = so.optBoolean("default", false)
                        )
                    )
                }
            }

            PluginStream(
                embedUrl = embedUrl,
                m3u8 = m3u8,
                referer = referer,
                subtitles = subs,
                serverName = serverName,
                type = type
            )
        } catch (e: Exception) {
            Log.e(TAG, "parse stream error", e)
            null
        }
    }

    /**
     * Asks the subtitle plugin to work out how early/late [url] is for the video being watched,
     * using the AniSkip intro/outro positions (0 = unknown). Needs KOpenSubtitle 1.0.3+.
     */
    suspend fun analyzeSync(
        context: Context,
        url: String,
        opStartMs: Long,
        opEndMs: Long,
        edStartMs: Long,
        edEndMs: Long,
        authority: String = KOPENSUBTITLE_AUTHORITY,
        durationMs: Long = 0L
    ): SubtitleSyncResult? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("url", url)
                put("opStartMs", opStartMs)
                put("opEndMs", opEndMs)
                put("edStartMs", edStartMs)
                put("edEndMs", edEndMs)
                put("durationMs", durationMs)
            }
            val bundle = callProvider(context, authority, "analyzeSync", payload.toString()) ?: return@withContext null
            if (!bundle.containsKey("confidence")) return@withContext null // older plugin without sync support
            SubtitleSyncResult(
                ok = bundle.getBoolean("ok", false),
                offsetMs = bundle.getLong("offsetMs", 0L),
                scale = bundle.getDouble("scale", 1.0),
                confidence = bundle.getString("confidence") ?: "low",
                reason = bundle.getString("reason") ?: "",
                uncertaintyMs = bundle.getLong("uncertaintyMs", 0L)
            )
        } catch (e: Exception) {
            Log.w(TAG, "analyzeSync error on authority=$authority: ${e.message}")
            null
        }
    }

    /**
     * Queries an installed subtitle plugin (e.g. KOpenSubtitle) for online subtitle tracks
     * when the active video source plugin does not provide subtitles.
     */
    suspend fun getOnlineSubtitles(
        context: Context,
        title: String,
        episode: Int,
        season: Int = 1,
        englishTitle: String? = null,
        romajiTitle: String? = null,
        year: Int = 0,
        malId: Int? = null,
        anilistId: Int? = null,
        kitsuId: Int? = null,
        mediaType: String? = null,
        episodeTitle: String? = null,
        episodeAired: String? = null,
        runtimeMin: Int = 0,
        authority: String = KOPENSUBTITLE_AUTHORITY
    ): List<PluginSubtitle> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("title", title)
                put("episode", episode)
                put("season", season)
                if (!englishTitle.isNullOrBlank()) put("englishTitle", englishTitle)
                if (!romajiTitle.isNullOrBlank()) put("romajiTitle", romajiTitle)
                if (year > 0) put("year", year)
                if (malId != null && malId > 0) put("malId", malId)
                if (anilistId != null && anilistId > 0) put("anilistId", anilistId)
                if (kitsuId != null && kitsuId > 0) put("kitsuId", kitsuId)
                if (!mediaType.isNullOrBlank()) put("mediaType", mediaType)
                if (!episodeTitle.isNullOrBlank()) put("episodeTitle", episodeTitle)
                if (!episodeAired.isNullOrBlank()) put("episodeAired", episodeAired)
                if (runtimeMin > 0) put("runtimeMin", runtimeMin)
            }
            val bundle = callProvider(context, authority, "getSubtitles", payload.toString())
            val jsonStr = bundle?.getString("result") ?: return@withContext emptyList()
            val ja = JSONArray(jsonStr)
            val list = mutableListOf<PluginSubtitle>()
            for (i in 0 until ja.length()) {
                val so = ja.optJSONObject(i) ?: continue
                val url = so.optString("url").trim()
                if (url.isBlank()) continue
                list.add(
                    PluginSubtitle(
                        lang = so.optString("lang", "English"),
                        url = url,
                        isDefault = so.optBoolean("default", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "getOnlineSubtitles error on authority=$authority: ${e.message}")
            emptyList()
        }
    }
}
