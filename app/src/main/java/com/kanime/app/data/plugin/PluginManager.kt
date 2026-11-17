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
    val plugins: List<AnimeSourcePlugin> = emptyList()
)

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

                list.add(
                    AnimeSourcePlugin(
                        id = obj.optString("id", "plugin_$i"),
                        name = obj.optString("name", "Unknown Plugin"),
                        packageName = obj.optString("packageName", ""),
                        providerAuthority = obj.optString("providerAuthority", "com.kanime.plugin.kanikoto.provider"),
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

    /**
     * Checks online for an updated plugins.json list using plugin_config_version.
     * Returns a CatalogUpdateInfo containing version, changelog/release notes, and preview plugins.
     */
    suspend fun checkForCatalogUpdate(context: Context, customUrl: String? = null): Result<CatalogUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = customUrl ?: DEFAULT_CATALOG_UPDATE_URL
            Log.d(TAG, "checkForCatalogUpdate from URL: $targetUrl")
            if (targetUrl.isBlank()) {
                return@withContext Result.failure(Exception("No update URL configured"))
            }

            val url = URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; KAnime)")
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP ${conn.responseCode} ${conn.responseMessage}"))
            }

            val remoteJson = conn.inputStream.bufferedReader().use { it.readText() }
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
                remotePlugins.add(
                    AnimeSourcePlugin(
                        id = obj.optString("id", "plugin_$i"),
                        name = obj.optString("name", "Unknown Plugin"),
                        packageName = obj.optString("packageName", ""),
                        providerAuthority = obj.optString("providerAuthority", "com.kanime.plugin.kanikoto.provider"),
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

            Log.d(TAG, "Remote config version: $remoteVer, current version: $_pluginConfigVersion")
            val hasUpdate = remoteVer > _pluginConfigVersion

            val updateInfo = CatalogUpdateInfo(
                hasUpdate = hasUpdate,
                currentVersion = _pluginConfigVersion,
                newVersion = remoteVer,
                releaseNotes = notes,
                remoteJson = remoteJson,
                plugins = remotePlugins
            )

            Result.success(updateInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for catalog update", e)
            Result.failure(e)
        }
    }

    /**
     * Applies a catalog update by writing remoteJson to storage and refreshing plugins.
     */
    fun applyCatalogUpdate(context: Context, updateInfo: CatalogUpdateInfo) {
        try {
            val file = File(context.filesDir, PLUGINS_JSON_FILE)
            file.writeText(updateInfo.remoteJson)
            _pluginConfigVersion = updateInfo.newVersion
            _catalogReleaseNotes = updateInfo.releaseNotes
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

            val newPlugin = AnimeSourcePlugin(
                id = obj.optString("id", "plugin_${System.currentTimeMillis()}"),
                name = obj.optString("name", "New Source Plugin"),
                packageName = obj.optString("packageName", ""),
                providerAuthority = obj.optString("providerAuthority", "com.kanime.plugin.kanikoto.provider"),
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
     * Compares remote vs local semantic versions (e.g. "1.2.0" vs "1.1.0").
     */
    fun isNewerVersion(remote: String, local: String): Boolean {
        return try {
            val rParts = remote.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val lParts = local.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(rParts.size, lParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val l = lParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            false
        } catch (_: Exception) {
            false
        }
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

            var installedVer: String? = null
            var updateAvailable = false

            if (isPkgInstalled) {
                try {
                    val pInfo = context.packageManager.getPackageInfo(plugin.packageName, 0)
                    installedVer = pInfo.versionName ?: "1.0.0"
                    updateAvailable = isNewerVersion(plugin.version, installedVer)
                } catch (e: Exception) {
                    installedVer = null
                }
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

            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
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

            val totalBytes = conn.contentLength.toLong().takeIf { it > 0 } ?: (3L * 1024 * 1024)
            val inputStream: InputStream = conn.inputStream

            FileOutputStream(targetApk).use { output ->
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
