package com.kanime.app.data.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class CatalogUpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: Int,
    val newVersion: Int,
    val releaseNotes: String,
    val remoteJson: String,
    val plugins: List<AnimeSourcePlugin> = emptyList(),
    val newPlugins: List<AnimeSourcePlugin> = emptyList(),
    val updatedPlugins: List<AnimeSourcePlugin> = emptyList(),
    val metadataUpdatedCount: Int = 0
) {
    val hasCatalogTemplateUpdate: Boolean get() = hasUpdate
    val hasPluginUpdates: Boolean get() = updatedPlugins.isNotEmpty()
}

object PluginManager {

    private const val TAG = "PluginManager"
    private const val PLUGINS_JSON_FILE = "plugins.json"
    private const val PREFS_NAME = "kanime_plugin_prefs"

    const val DEFAULT_CATALOG_UPDATE_URL = "https://raw.githubusercontent.com/Jubiar01/kanime-plugin/refs/heads/main/plugins.json"

    private val _pluginConfigVersionFlow = MutableStateFlow(1)
    val pluginConfigVersionFlow: StateFlow<Int> = _pluginConfigVersionFlow.asStateFlow()

    private var _pluginConfigVersion: Int
        get() = _pluginConfigVersionFlow.value
        set(value) {
            _pluginConfigVersionFlow.value = value
        }
    val pluginConfigVersion: Int get() = _pluginConfigVersion

    private var _catalogReleaseNotes: String = "Official KAnikoto signed release v1.2.0 (Marvin-J key). Featuring high-speed multi-server HLS stream extraction, MegaPlay AES-256 decryption, and synchronized subtitles."
    val catalogReleaseNotes: String get() = _catalogReleaseNotes

    private var _configUpdateUrl: String = DEFAULT_CATALOG_UPDATE_URL
    val configUpdateUrl: String get() = _configUpdateUrl

    val defaultKanikotoPlugin = AnimeSourcePlugin(
        id = "kanikoto",
        name = "KAnikoto Extractor",
        packageName = "com.kanime.plugin.kanikoto",
        providerAuthority = "com.kanime.plugin.kanikoto.provider",
        version = "1.2.0",
        versionCode = 12,
        author = "KAnime Official",
        description = "Official online streaming plugin for Anikoto with MegaPlay AES decryption and multi-server HLS streams.",
        isInstalled = false,
        isEnabled = true,
        isDefault = true,
        capabilities = listOf("HLS 1080p FHD", "MegaPlay AES-256", "Sub & Dub"),
        sourceUrl = "https://anikototv.bz",
        apkFileName = "kanikoto-plugin.apk",
        apkDownloadUrl = "https://github.com/kanime-app/plugins/releases/download/v1.2.0/kanikoto-plugin.apk",
        apkSize = "3.3 MB",
        isApkDownloaded = false,
        changelog = "Added MegaPlay AES decryption and high-speed multi-server HLS extraction."
    )

    private val _plugins = MutableStateFlow<List<AnimeSourcePlugin>>(listOf(defaultKanikotoPlugin))
    val plugins: StateFlow<List<AnimeSourcePlugin>> = _plugins.asStateFlow()

    // Selected source plugin ID (defaults to "kanikoto")
    private val _activeSourceId = MutableStateFlow("kanikoto")
    val activeSourceId: StateFlow<String> = _activeSourceId.asStateFlow()

    fun setActiveSource(id: String) {
        _activeSourceId.value = id
    }

    private fun parseConfigVersion(jsonStr: String): Int {
        return try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).optInt("plugin_config_version", 1)
            } else {
                1
            }
        } catch (_: Exception) {
            1
        }
    }

    /**
     * Loads plugin definitions from plugins.json.
     * Automatically prioritizes newer plugin_config_version from assets or filesDir.
     */
    fun loadPluginsFromJson(context: Context): List<AnimeSourcePlugin> {
        val file = File(context.filesDir, PLUGINS_JSON_FILE)

        val assetContent = try {
            context.assets.open(PLUGINS_JSON_FILE).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }

        val fileContent = if (file.exists() && file.length() > 0) {
            try { file.readText() } catch (_: Exception) { null }
        } else null

        val assetVer = assetContent?.let { parseConfigVersion(it) } ?: 0
        val fileVer = fileContent?.let { parseConfigVersion(it) } ?: 0

        // If asset has a newer plugin_config_version or file contains deprecated config_update_url, refresh filesDir
        val jsonContent = if (assetVer > fileVer || fileContent.isNullOrBlank() || fileContent.contains("config_update_url")) {
            if (!assetContent.isNullOrBlank()) {
                try { file.writeText(assetContent) } catch (_: Exception) {}
            }
            assetContent
        } else {
            fileContent
        }

        if (jsonContent.isNullOrBlank()) {
            _pluginConfigVersion = 1
            return listOf(defaultKanikotoPlugin)
        }

        return try {
            val trimmed = jsonContent.trim()
            val jsonArray: JSONArray
            if (trimmed.startsWith("{")) {
                val rootObj = JSONObject(trimmed)
                _pluginConfigVersion = rootObj.optInt("plugin_config_version", 1)
                _catalogReleaseNotes = rootObj.optString("release_notes").takeIf { it.isNotBlank() }
                    ?: rootObj.optString("changelog").takeIf { it.isNotBlank() }
                    ?: rootObj.optString("catalog_changelog").takeIf { it.isNotBlank() }
                    ?: _catalogReleaseNotes
                jsonArray = rootObj.optJSONArray("plugins") ?: JSONArray()
            } else {
                _pluginConfigVersion = 1
                jsonArray = JSONArray(trimmed)
            }

            val list = mutableListOf<AnimeSourcePlugin>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val caps = mutableListOf<String>()
                val capsArray = obj.optJSONArray("capabilities")
                if (capsArray != null) {
                    for (c in 0 until capsArray.length()) {
                        caps.add(capsArray.getString(c))
                    }
                }

                val pkgName = obj.optString("packageName", "").trim()
                val authority = obj.optString("providerAuthority", "").trim()
                    .ifBlank { if (pkgName.isNotBlank()) "$pkgName.provider" else "" }
                list.add(
                    AnimeSourcePlugin(
                        id = obj.optString("id", "plugin_$i"),
                        name = obj.optString("name", "Unknown Plugin"),
                        packageName = pkgName,
                        providerAuthority = authority,
                        version = obj.optString("version", "1.0.0"),
                        versionCode = obj.optInt("versionCode", 1),
                        author = obj.optString("author", "Community"),
                        description = obj.optString("description", ""),
                        isInstalled = false,
                        isEnabled = true,
                        isDefault = obj.optBoolean("isDefault", false),
                        capabilities = caps,
                        sourceUrl = obj.optString("sourceUrl", ""),
                        apkFileName = obj.optString("apkFileName", "plugin.apk"),
                        apkDownloadUrl = obj.optString("apkDownloadUrl").takeIf { it.isNotBlank() },
                        apkSize = obj.optString("apkSize", "N/A"),
                        isApkDownloaded = false,
                        changelog = obj.optString("changelog").takeIf { it.isNotBlank() }
                    )
                )
            }
            if (list.isEmpty()) listOf(defaultKanikotoPlugin) else list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing plugins.json", e)
            listOf(defaultKanikotoPlugin)
        }
    }

    /**
     * Saves the current plugin list and config version to filesDir/plugins.json
     */
    private fun savePluginsToJson(context: Context, pluginList: List<AnimeSourcePlugin>) {
        try {
            val root = JSONObject().apply {
                put("plugin_config_version", _pluginConfigVersion)
                if (_catalogReleaseNotes.isNotBlank()) {
                    put("release_notes", _catalogReleaseNotes)
                }
                val jsonArray = JSONArray()
                for (p in pluginList) {
                    val jo = JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("packageName", p.packageName)
                        put("providerAuthority", p.providerAuthority)
                        put("version", p.version)
                        put("versionCode", p.versionCode)
                        put("author", p.author)
                        put("description", p.description)
                        put("sourceUrl", p.sourceUrl)
                        put("apkFileName", p.apkFileName)
                        if (!p.apkDownloadUrl.isNullOrBlank()) {
                            put("apkDownloadUrl", p.apkDownloadUrl)
                        }
                        put("apkSize", p.apkSize)
                        put("isDefault", p.isDefault)
                        if (!p.changelog.isNullOrBlank()) {
                            put("changelog", p.changelog)
                        }
                        val ca = JSONArray()
                        p.capabilities.forEach { ca.put(it) }
                        put("capabilities", ca)
                    }
                    jsonArray.put(jo)
                }
                put("plugins", jsonArray)
            }
            File(context.filesDir, PLUGINS_JSON_FILE).writeText(root.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving plugins.json", e)
        }
    }

    private fun appendCacheBuster(rawUrl: String): String {
        val separator = if (rawUrl.contains("?")) "&" else "?"
        return "${rawUrl}${separator}_ts=${System.currentTimeMillis()}"
    }

    private fun hasPluginMetadataChanged(local: AnimeSourcePlugin, remote: AnimeSourcePlugin): Boolean {
        return local.version != remote.version ||
            local.versionCode != remote.versionCode ||
            local.apkDownloadUrl != remote.apkDownloadUrl ||
            local.apkSize != remote.apkSize ||
            local.changelog != remote.changelog ||
            local.name != remote.name ||
            local.description != remote.description ||
            local.sourceUrl != remote.sourceUrl ||
            local.apkFileName != remote.apkFileName ||
            local.providerAuthority != remote.providerAuthority ||
            local.author != remote.author ||
            local.capabilities != remote.capabilities
    }

    private fun inspectInstalledPlugin(context: Context, plugin: AnimeSourcePlugin): Pair<String?, Boolean> {
        if (!PluginBridge.isPluginInstalled(context, plugin.packageName)) {
            return Pair(null, false)
        }
        return try {
            val pInfo = context.packageManager.getPackageInfo(plugin.packageName, 0)
            val installedVer = pInfo.versionName ?: "1.0.0"
            val installedVerCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            val semverDiff = compareVersions(plugin.version, installedVer)
            val updateAvailable = semverDiff > 0 ||
                (semverDiff == 0 && plugin.versionCode > 0 && plugin.versionCode.toLong() > installedVerCode)
            Pair(installedVer, updateAvailable)
        } catch (_: Exception) {
            Pair(null, false)
        }
    }

    /**
     * Checks online for BOTH:
     * 1. Template Catalog updates (new `plugin_config_version` or new plugins added to the catalog).
     * 2. Individual Plugin updates (newer `version`/`versionCode` or updated APK metadata for existing plugins).
     *
     * Existing plugin metadata (e.g. KAnikoto 1.0.0 -> 1.0.1) is synced immediately so plugin update actions
     * appear right away even when the catalog template version hasn't changed.
     */
    suspend fun checkForCatalogUpdate(context: Context, customUrl: String? = null): Result<CatalogUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = customUrl ?: DEFAULT_CATALOG_UPDATE_URL
            Log.d(TAG, "checkForCatalogUpdate from URL: $targetUrl")
            if (targetUrl.isBlank()) {
                return@withContext Result.failure(Exception("No update URL configured"))
            }

            // Ensure local state and _pluginConfigVersion are loaded first
            val localPlugins = loadPluginsFromJson(context)

            val url = URL(appendCacheBuster(targetUrl))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; KAnime)")
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP ${conn.responseCode} ${conn.responseMessage}"))
            }

            val remoteJson = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val rootObj = JSONObject(remoteJson)
            val remoteVer = rootObj.optInt("plugin_config_version", 1)
            val notes = rootObj.optString("release_notes").takeIf { it.isNotBlank() }
                ?: rootObj.optString("changelog").takeIf { it.isNotBlank() }
                ?: rootObj.optString("catalog_changelog").takeIf { it.isNotBlank() }
                ?: "Official release update with high-speed multi-server streaming and stability optimizations."

            val jsonArray = rootObj.optJSONArray("plugins") ?: JSONArray()
            val remotePlugins = mutableListOf<AnimeSourcePlugin>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val caps = mutableListOf<String>()
                val capsArray = obj.optJSONArray("capabilities")
                if (capsArray != null) {
                    for (c in 0 until capsArray.length()) {
                        caps.add(capsArray.getString(c))
                    }
                }
                val pkgName = obj.optString("packageName", "").trim()
                val authority = obj.optString("providerAuthority", "").trim()
                    .ifBlank { if (pkgName.isNotBlank()) "$pkgName.provider" else "" }
                val parsed = AnimeSourcePlugin(
                    id = obj.optString("id", "plugin_$i"),
                    name = obj.optString("name", "Unknown Plugin"),
                    packageName = pkgName,
                    providerAuthority = authority,
                    version = obj.optString("version", "1.0.0"),
                    versionCode = obj.optInt("versionCode", 1),
                    author = obj.optString("author", "Community"),
                    description = obj.optString("description", ""),
                    isInstalled = false,
                    isEnabled = true,
                    isDefault = obj.optBoolean("isDefault", false),
                    capabilities = caps,
                    sourceUrl = obj.optString("sourceUrl", ""),
                    apkFileName = obj.optString("apkFileName", "plugin.apk"),
                    apkDownloadUrl = obj.optString("apkDownloadUrl").takeIf { it.isNotBlank() },
                    apkSize = obj.optString("apkSize", "N/A"),
                    isApkDownloaded = false,
                    changelog = obj.optString("changelog").takeIf { it.isNotBlank() }
                )
                val (installedVer, hasPkgUpdate) = inspectInstalledPlugin(context, parsed)
                remotePlugins.add(
                    parsed.copy(
                        isInstalled = installedVer != null,
                        installedVersion = installedVer,
                        hasUpdate = hasPkgUpdate
                    )
                )
            }

            // 1. Detect Template Catalog Update (new config version or newly added plugins)
            val newPlugins = remotePlugins.filter { remote ->
                localPlugins.none { local ->
                    local.id == remote.id ||
                        (remote.packageName.isNotBlank() && local.packageName == remote.packageName)
                }
            }
            val hasTemplateUpdate = (remoteVer > _pluginConfigVersion) || newPlugins.isNotEmpty()

            // 2. Sync updated metadata for existing plugins immediately (independent of template version)
            var metadataUpdatedCount = 0
            val mergedExisting = localPlugins.map { local ->
                val remoteMatch = remotePlugins.firstOrNull { remote ->
                    remote.id == local.id ||
                        (local.packageName.isNotBlank() && remote.packageName == local.packageName)
                }
                if (remoteMatch != null && hasPluginMetadataChanged(local, remoteMatch)) {
                    metadataUpdatedCount++
                    remoteMatch.copy(
                        isInstalled = local.isInstalled,
                        installedVersion = local.installedVersion,
                        hasUpdate = local.hasUpdate,
                        isEnabled = local.isEnabled,
                        isApkDownloaded = local.isApkDownloaded
                    )
                } else {
                    local
                }
            }

            if (metadataUpdatedCount > 0) {
                savePluginsToJson(context, mergedExisting)
            }

            // Refresh live installed status & UI state flow so "Update vX.Y.Z" buttons appear immediately
            checkPluginStatus(context)

            val updatedPlugins = remotePlugins.filter { it.isInstalled && it.hasUpdate }

            Log.d(
                TAG,
                "Catalog check done: remoteVer=$remoteVer, localVer=$_pluginConfigVersion, " +
                    "templateUpdate=$hasTemplateUpdate, newPlugins=${newPlugins.size}, " +
                    "pluginUpdates=${updatedPlugins.size}, metadataSynced=$metadataUpdatedCount"
            )

            val updateInfo = CatalogUpdateInfo(
                hasUpdate = hasTemplateUpdate,
                currentVersion = _pluginConfigVersion,
                newVersion = remoteVer,
                releaseNotes = notes,
                remoteJson = remoteJson,
                plugins = remotePlugins,
                newPlugins = newPlugins,
                updatedPlugins = updatedPlugins,
                metadataUpdatedCount = metadataUpdatedCount
            )

            Result.success(updateInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for catalog update", e)
            Result.failure(e)
        }
    }

    /**
     * Applies a Template Catalog update by merging remote catalog plugins (preserving custom local plugins),
     * updating config version/release notes, and refreshing installed plugin status.
     */
    fun applyCatalogUpdate(context: Context, updateInfo: CatalogUpdateInfo) {
        try {
            val currentLocal = loadPluginsFromJson(context)
            _pluginConfigVersion = maxOf(_pluginConfigVersion, updateInfo.newVersion)
            if (updateInfo.releaseNotes.isNotBlank()) {
                _catalogReleaseNotes = updateInfo.releaseNotes
            }

            if (updateInfo.plugins.isNotEmpty()) {
                val customLocalPlugins = currentLocal.filter { local ->
                    updateInfo.plugins.none { remote ->
                        remote.id == local.id ||
                            (local.packageName.isNotBlank() && remote.packageName == local.packageName)
                    }
                }
                savePluginsToJson(context, updateInfo.plugins + customLocalPlugins)
            } else if (updateInfo.remoteJson.isNotBlank()) {
                val file = File(context.filesDir, PLUGINS_JSON_FILE)
                file.writeText(updateInfo.remoteJson)
            }

            checkPluginStatus(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying catalog update", e)
        }
    }

    /**
     * Legacy wrapper for checkForCatalogUpdate.
     */
    suspend fun checkForPluginConfigUpdate(context: Context, customUrl: String? = null): Result<Pair<Boolean, Int>> {
        return checkForCatalogUpdate(context, customUrl).map { info ->
            if (info.hasUpdate) {
                applyCatalogUpdate(context, info)
                Pair(true, info.newVersion)
            } else {
                Pair(false, info.currentVersion)
            }
        }
    }

    /**
     * Dynamically adds or updates a library plugin from a JSON string at runtime.
     */
    fun addPluginFromJson(context: Context, jsonStr: String): Result<AnimeSourcePlugin> {
        return try {
            val obj = JSONObject(jsonStr)
            val caps = mutableListOf<String>()
            val capsArray = obj.optJSONArray("capabilities")
            if (capsArray != null) {
                for (c in 0 until capsArray.length()) {
                    caps.add(capsArray.getString(c))
                }
            }

            val pkgName = obj.optString("packageName", "").trim()
            val authority = obj.optString("providerAuthority", "").trim()
                .ifBlank { if (pkgName.isNotBlank()) "$pkgName.provider" else "" }
            val newPlugin = AnimeSourcePlugin(
                id = obj.optString("id", "plugin_${System.currentTimeMillis()}"),
                name = obj.optString("name", "New Source Plugin"),
                packageName = pkgName,
                providerAuthority = authority,
                version = obj.optString("version", "1.0.0"),
                versionCode = obj.optInt("versionCode", 1),
                author = obj.optString("author", "Community"),
                description = obj.optString("description", ""),
                isInstalled = false,
                isEnabled = true,
                isDefault = false,
                capabilities = caps,
                sourceUrl = obj.optString("sourceUrl", ""),
                apkFileName = obj.optString("apkFileName", "plugin.apk"),
                apkDownloadUrl = obj.optString("apkDownloadUrl").takeIf { it.isNotBlank() },
                apkSize = obj.optString("apkSize", "N/A"),
                isApkDownloaded = false,
                changelog = obj.optString("changelog").takeIf { it.isNotBlank() }
            )

            val current = loadPluginsFromJson(context).toMutableList()
            val existingIdx = current.indexOfFirst { it.id == newPlugin.id || it.packageName == newPlugin.packageName }
            if (existingIdx >= 0) {
                current[existingIdx] = newPlugin
            } else {
                current.add(newPlugin)
            }

            savePluginsToJson(context, current)
            checkPluginStatus(context)
            Result.success(newPlugin)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compares two semantic version strings (e.g. "1.0.1" vs "1.0.0").
     * Returns > 0 if remote > local, 0 if equal, < 0 if remote < local.
     */
    fun compareVersions(remote: String, local: String): Int {
        return try {
            val rParts = remote.trim().removePrefix("v").removePrefix("V")
                .substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
            val lParts = local.trim().removePrefix("v").removePrefix("V")
                .substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(rParts.size, lParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val l = lParts.getOrElse(i) { 0 }
                if (r != l) return r.compareTo(l)
            }
            0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Compares remote vs local semantic versions (e.g. "1.2.0" vs "1.1.0").
     */
    fun isNewerVersion(remote: String, local: String): Boolean {
        return compareVersions(remote, local) > 0
    }

    fun isPluginEnabledPref(context: Context, pluginId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("enabled_$pluginId", true)
    }

    fun setPluginEnabled(context: Context, id: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enabled_$id", enabled).apply()
        _plugins.value = _plugins.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
    }

    fun checkPluginStatus(context: Context) {
        val baseList = loadPluginsFromJson(context)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir

        val updated = baseList.map { plugin ->
            val apkFile = File(downloadsDir, plugin.apkFileName)
            val altFile = File(downloadsDir, "${plugin.apkFileName.removeSuffix(".apk")}-v${plugin.version}.apk")

            val isPkgInstalled = PluginBridge.isPluginInstalled(context, plugin.packageName)
            val isApkDownloaded = (apkFile.exists() && apkFile.length() > 0) || (altFile.exists() && altFile.length() > 0)

            val (installedVer, updateAvailable) = if (isPkgInstalled) {
                inspectInstalledPlugin(context, plugin)
            } else {
                Pair(null, false)
            }

            val isEnabledPref = isPluginEnabledPref(context, plugin.id)

            plugin.copy(
                isInstalled = isPkgInstalled,
                installedVersion = installedVer,
                hasUpdate = updateAvailable,
                isApkDownloaded = isApkDownloaded,
                isEnabled = isEnabledPref
            )
        }

        _plugins.value = updated

        // If the currently selected activeSourceId isn't installed & enabled, automatically switch to an active installed plugin
        val currentActive = updated.firstOrNull { it.id == _activeSourceId.value }
        if (currentActive == null || !currentActive.isInstalled || !currentActive.isEnabled) {
            val fallbackActive = updated.firstOrNull { it.isInstalled && it.isEnabled }
            if (fallbackActive != null) {
                _activeSourceId.value = fallbackActive.id
            }
        }
    }

    fun togglePlugin(id: String) {
        _plugins.value = _plugins.value.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
    }

    fun installPluginApk(context: Context, plugin: AnimeSourcePlugin) {
        try {
            // Check unknown sources install permission on Android 8.0+ (Oreo, API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        context,
                        "Please grant permission to install plugins from KAnime",
                        Toast.LENGTH_LONG
                    ).show()
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(permissionIntent)
                    return
                }
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            var targetApk = File(downloadsDir, plugin.apkFileName)

            if (!targetApk.exists() || targetApk.length() == 0L) {
                val alt = File(downloadsDir, "${plugin.apkFileName.removeSuffix(".apk")}-v${plugin.version}.apk")
                if (alt.exists() && alt.length() > 0L) {
                    targetApk = alt
                }
            }

            if (!targetApk.exists() || targetApk.length() == 0L) {
                Toast.makeText(context, "APK file not found. Please tap Download to fetch it online.", Toast.LENGTH_SHORT).show()
                return
            }

            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetApk
                )
            } else {
                Uri.fromFile(targetApk)
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Uninstalls the Android package and deletes the downloaded APK file.
     */
    fun uninstallPlugin(context: Context, plugin: AnimeSourcePlugin) {
        try {
            // Delete downloaded APK from storage
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir

            val targetApk = File(downloadsDir, plugin.apkFileName)
            if (targetApk.exists()) targetApk.delete()
            val altApk = File(downloadsDir, "${plugin.apkFileName.removeSuffix(".apk")}-v${plugin.version}.apk")
            if (altApk.exists()) altApk.delete()

            // Trigger system uninstaller if package is installed
            if (plugin.isInstalled) {
                val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${plugin.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(uninstallIntent)
            } else {
                Toast.makeText(context, "Removed ${plugin.name} APK file.", Toast.LENGTH_SHORT).show()
            }

            checkPluginStatus(context)
        } catch (e: Exception) {
            Toast.makeText(context, "Uninstall error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Downloads plugin APK purely ONLINE over HTTP/HTTPS.
     * Streams directly to the phone's Downloads directory and triggers installation.
     */
    suspend fun downloadPluginApk(
        context: Context,
        plugin: AnimeSourcePlugin,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadUrl = plugin.apkDownloadUrl
            if (downloadUrl.isNullOrBlank() || (!downloadUrl.startsWith("http://", ignoreCase = true) && !downloadUrl.startsWith("https://", ignoreCase = true))) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No valid online download URL configured for ${plugin.name}.", Toast.LENGTH_LONG).show()
                }
                return@withContext Result.failure(Exception("No valid download URL"))
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir

            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val targetApk = File(downloadsDir, plugin.apkFileName)
            val tempApk = File(downloadsDir, "${plugin.apkFileName}.tmp")

            val url = URL(appendCacheBuster(downloadUrl))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; KAnime)")
            }
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val msg = "Server returned HTTP $responseCode: ${conn.responseMessage}"
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: $msg. Check apkDownloadUrl.", Toast.LENGTH_LONG).show()
                }
                return@withContext Result.failure(Exception(msg))
            }

            val totalBytes = conn.contentLength.toLong().takeIf { it > 0 } ?: (256L * 1024)
            val inputStream: InputStream = conn.inputStream

            FileOutputStream(tempApk).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalCopied = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalCopied += bytesRead
                    val progress = (totalCopied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                }
            }
            inputStream.close()
            conn.disconnect()

            if (targetApk.exists()) targetApk.delete()
            if (!tempApk.renameTo(targetApk)) {
                tempApk.copyTo(targetApk, overwrite = true)
                tempApk.delete()
            }

            withContext(Dispatchers.Main) {
                checkPluginStatus(context)
                Toast.makeText(
                    context,
                    "Downloaded ${plugin.apkFileName} (${targetApk.length() / 1024} KB).",
                    Toast.LENGTH_SHORT
                ).show()

                // Prompt installation immediately with permission check
                installPluginApk(context, plugin)
            }

            Result.success(targetApk)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Online download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            Result.failure(e)
        }
    }
}
