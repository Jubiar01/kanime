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
}
