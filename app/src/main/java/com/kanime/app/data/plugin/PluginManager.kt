package com.kanime.app.data.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import com.kanime.app.R
import kotlinx.coroutines.Dispatchers
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

@Immutable
data class CatalogUpdateInfo(
    val catalogUpdateAvailable: Boolean,
    val currentVersion: Int,
    val newVersion: Int,
    val releaseNotes: List<String> = emptyList(),
    val releaseDate: String? = null,
    val minAppVersion: String? = null,
    val remoteJson: String = "",
    val plugins: List<AnimeSourcePlugin> = emptyList(),
    val newPlugins: List<AnimeSourcePlugin> = emptyList(),
    val updatedPlugins: List<AnimeSourcePlugin> = emptyList(),
    val metadataUpdatedCount: Int = 0
) {
    val pluginUpdatesAvailable: Boolean get() = updatedPlugins.isNotEmpty()
    val hasAnyUpdate: Boolean get() = catalogUpdateAvailable || pluginUpdatesAvailable
}

internal data class ParsedCatalogDocument(
    val configVersion: Int,
    val releaseNotes: List<String>,
    val releaseDate: String?,
    val minAppVersion: String?,
    val plugins: List<AnimeSourcePlugin>
)

object PluginManager {

    private const val TAG = "PluginManager"
    private const val PLUGINS_JSON_FILE = "plugins.json"
    private const val PREFS_NAME = "kanime_plugin_prefs"
    private const val KEY_DISMISSED_CATALOG_VERSION = "dismissed_catalog_version"
    private const val KEY_LAST_FETCHED_NOTES = "last_fetched_catalog_notes"
    private const val KEY_LAST_FETCHED_VERSION = "last_fetched_catalog_version"
    private const val KEY_LAST_FETCHED_DATE = "last_fetched_catalog_date"
    private const val KEY_LAST_CHECK_MS = "last_catalog_check_ms"
    private const val KEY_PLUGIN_FOLDER_URI = "plugin_download_tree_uri"
    private const val KEY_USE_OPENSUBTITLE_IN_PLAYER = "use_opensubtitle_in_player"

    const val CATALOG_CHECK_TTL_MS = 15L * 60L * 1000L // 15 minutes
    private const val STATUS_CHECK_DEBOUNCE_MS = 400L

    const val DEFAULT_CATALOG_UPDATE_URL =
        "https://raw.githubusercontent.com/Jubiar01/kanime-plugin/refs/heads/main/plugins.json"

    private val _pluginConfigVersionFlow = MutableStateFlow(1)
    val pluginConfigVersionFlow: StateFlow<Int> = _pluginConfigVersionFlow.asStateFlow()

    val pluginConfigVersion: Int get() = _pluginConfigVersionFlow.value

    private val _appliedReleaseNotesFlow = MutableStateFlow<List<String>>(emptyList())
    val appliedReleaseNotesFlow: StateFlow<List<String>> = _appliedReleaseNotesFlow.asStateFlow()

    private var _appliedReleaseDate: String? = null
    val appliedReleaseDate: String? get() = _appliedReleaseDate

    private var _appliedMinAppVersion: String? = null
    val appliedMinAppVersion: String? get() = _appliedMinAppVersion

    private val _lastFetchedCatalogFlow = MutableStateFlow<CatalogUpdateInfo?>(null)
    val lastFetchedCatalogFlow: StateFlow<CatalogUpdateInfo?> = _lastFetchedCatalogFlow.asStateFlow()

    private val _dismissedCatalogVersionFlow = MutableStateFlow(0)
    val dismissedCatalogVersionFlow: StateFlow<Int> = _dismissedCatalogVersionFlow.asStateFlow()

    private val _pluginFolderUriFlow = MutableStateFlow<String?>(null)
    val pluginFolderUriFlow: StateFlow<String?> = _pluginFolderUriFlow.asStateFlow()

    @Volatile
    private var lastStatusCheckMs: Long = 0L

    /**
     * Neutral fallback matching bundled assets/plugins.json — used only if asset loading fails.
     */
    val defaultKanikotoPlugin = AnimeSourcePlugin(
        id = "kanikoto",
        name = "KAnikoto Extractor",
        packageName = "com.kanime.plugin.kanikoto",
        providerAuthority = "com.kanime.plugin.kanikoto.provider",
        version = "1.0.1",
        versionCode = 2,
        author = "KAnime Official",
        description = "Official online streaming plugin for Anikoto with MegaPlay AES decryption and multi-server HLS streams.",
        isInstalled = false,
        isEnabled = true,
        isDefault = true,
        capabilities = listOf("HLS 1080p FHD", "MegaPlay AES-256", "Sub & Dub"),
        sourceUrl = "https://anikototv.bz",
        apkFileName = "kanikoto-plugin.apk",
        apkDownloadUrl = "https://github.com/Jubiar01/kanime-plugin/raw/refs/heads/main/kanikoto-plugin-release.apk",
        apkSize = "127 KB",
        isApkDownloaded = false,
        changelog = null,
        changelogItems = emptyList()
    )

    private val _plugins = MutableStateFlow<List<AnimeSourcePlugin>>(listOf(defaultKanikotoPlugin))
    val plugins: StateFlow<List<AnimeSourcePlugin>> = _plugins.asStateFlow()

    private val _activeSourceId = MutableStateFlow("kanikoto")
    val activeSourceId: StateFlow<String> = _activeSourceId.asStateFlow()

    fun setActiveSource(id: String) {
        _activeSourceId.value = id
    }

    fun getEnabledSubtitlePlugins(): List<AnimeSourcePlugin> {
        return _plugins.value.filter { it.isSubtitlePlugin && it.isInstalled && it.isEnabled }
    }

    fun isUseOpenSubtitleInPlayer(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_OPENSUBTITLE_IN_PLAYER, false)
    }

    fun setUseOpenSubtitleInPlayer(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USE_OPENSUBTITLE_IN_PLAYER, enabled).apply()
    }

    /**
     * Persists a Storage Access Framework folder URI (from ACTION_OPEN_DOCUMENT_TREE)
     * with takePersistableUriPermission so downloaded plugin APKs are saved there.
     */
    fun setPluginFolderUri(context: Context, treeUri: Uri?) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (treeUri == null) {
            sp.edit().remove(KEY_PLUGIN_FOLDER_URI).apply()
            _pluginFolderUriFlow.value = null
            return
        }
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: Exception) {
            Log.w(TAG, "takePersistableUriPermission failed for $treeUri", e)
        }
        val uriStr = treeUri.toString()
        sp.edit().putString(KEY_PLUGIN_FOLDER_URI, uriStr).apply()
        _pluginFolderUriFlow.value = uriStr
    }

    /**
     * Returns the saved SAF folder URI only if the app still holds persisted read+write access.
     */
    fun getValidPluginFolderUri(context: Context): Uri? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_PLUGIN_FOLDER_URI, null)?.takeIf { it.isNotBlank() } ?: run {
            _pluginFolderUriFlow.value = null
            return null
        }
        val uri = Uri.parse(raw)
        val hasPerm = try {
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
        } catch (_: Exception) {
            false
        }
        return if (hasPerm) {
            _pluginFolderUriFlow.value = raw
            uri
        } else {
            sp.edit().remove(KEY_PLUGIN_FOLDER_URI).apply()
            _pluginFolderUriFlow.value = null
            null
        }
    }

    /**
     * Extracts a clean human-readable folder name from a SAF tree URI (e.g. "Download/Kanime").
     */
    fun getPluginFolderDisplayName(context: Context, uriString: String? = _pluginFolderUriFlow.value): String? {
        val raw = uriString?.takeIf { it.isNotBlank() }
            ?: getValidPluginFolderUri(context)?.toString()
            ?: return null
        return try {
            val treeUri = Uri.parse(raw)
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val cleaned = docId.substringAfter(':', docId).trim('/')
            cleaned.ifBlank { docId }
        } catch (_: Exception) {
            Uri.parse(raw).lastPathSegment?.substringAfter(':')
        }
    }

    private fun findApkInSafTree(context: Context, treeUri: Uri, plugin: AnimeSourcePlugin): Uri? {
        return try {
            val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val altName = "${plugin.apkFileName.removeSuffix(".apk")}-v${plugin.version}.apk"
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                var altMatch: Uri? = null
                while (cursor.moveToNext()) {
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else continue
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 1L
                    if (size <= 0L) continue
                    if (name.equals(plugin.apkFileName, ignoreCase = true)) {
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    }
                    if (name.equals(altName, ignoreCase = true)) {
                        altMatch = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    }
                }
                altMatch
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying SAF plugin folder", e)
            null
        }
    }

    private fun saveApkToSafTree(context: Context, treeUri: Uri, plugin: AnimeSourcePlugin, sourceFile: File): Uri? {
        return try {
            val resolver = context.contentResolver
            // Delete any existing copy with the same name so SAF doesn't append " (1)"
            findApkInSafTree(context, treeUri, plugin)?.let { existing ->
                try { DocumentsContract.deleteDocument(resolver, existing) } catch (_: Exception) {}
            }
            val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val createdUri = DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                "application/vnd.android.package-archive",
                plugin.apkFileName
            ) ?: return null

            resolver.openOutputStream(createdUri, "w")?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out, bufferSize = 8192)
                }
            } ?: return null

            createdUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving APK into SAF folder", e)
            null
        }
    }

    /**
     * Splits a plain-text release notes string into clean bullet lines.
     */
    fun splitNotesText(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lines()
            .map { line ->
                line.trim()
                    .removePrefix("- ")
                    .removePrefix("* ")
                    .removePrefix("• ")
                    .trim()
            }
            .filter { it.isNotEmpty() }
    }

    /**
     * Parses notes from a JSONObject supporting BOTH plain strings and JSON arrays of strings,
     * checking alternate keys in order. Returns an empty list if absent (never invents text).
     */
    fun parseNotesList(obj: JSONObject, vararg keys: String): List<String> {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.opt(key) ?: continue
            when (value) {
                is JSONArray -> {
                    val items = mutableListOf<String>()
                    for (i in 0 until value.length()) {
                        val entry = value.optString(i, "").trim()
                            .removePrefix("- ")
                            .removePrefix("* ")
                            .removePrefix("• ")
                            .trim()
                        if (entry.isNotEmpty()) items.add(entry)
                    }
                    if (items.isNotEmpty()) return items
                }
                is String -> {
                    val items = splitNotesText(value)
                    if (items.isNotEmpty()) return items
                }
            }
        }
        return emptyList()
    }

    internal fun parseCatalogDocument(jsonStr: String): Result<ParsedCatalogDocument> {
        return try {
            val trimmed = jsonStr.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("Empty catalog JSON"))
            }

            val configVersion: Int
            val releaseNotes: List<String>
            val releaseDate: String?
            val minAppVersion: String?
            val jsonArray: JSONArray

            if (trimmed.startsWith("{")) {
                val rootObj = JSONObject(trimmed)
                configVersion = rootObj.optInt("plugin_config_version", 1)
                releaseNotes = parseNotesList(rootObj, "release_notes", "changelog", "catalog_changelog")
                releaseDate = rootObj.optString("release_date", "").trim().takeIf { it.isNotEmpty() }
                minAppVersion = rootObj.optString("min_app_version", "").trim().takeIf { it.isNotEmpty() }
                jsonArray = rootObj.optJSONArray("plugins") ?: JSONArray()
            } else if (trimmed.startsWith("[")) {
                configVersion = 1
                releaseNotes = emptyList()
                releaseDate = null
                minAppVersion = null
                jsonArray = JSONArray(trimmed)
            } else {
                return Result.failure(IllegalArgumentException("Malformed catalog JSON"))
            }

            val list = mutableListOf<AnimeSourcePlugin>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(parsePluginObject(obj, i))
            }

            Result.success(
                ParsedCatalogDocument(
                    configVersion = configVersion,
                    releaseNotes = releaseNotes,
                    releaseDate = releaseDate,
                    minAppVersion = minAppVersion,
                    plugins = list
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePluginObject(obj: JSONObject, index: Int): AnimeSourcePlugin {
        val caps = mutableListOf<String>()
        val capsArray = obj.optJSONArray("capabilities")
        if (capsArray != null) {
            for (c in 0 until capsArray.length()) {
                val cap = capsArray.optString(c, "").trim()
                if (cap.isNotEmpty()) caps.add(cap)
            }
        }
        val pkgName = obj.optString("packageName", "").trim()
        val authority = obj.optString("providerAuthority", "").trim()
            .ifBlank { if (pkgName.isNotBlank()) "$pkgName.provider" else "" }
        val id = obj.optString("id", "plugin_$index")
        val pluginType = obj.optString("pluginType", "").trim().ifBlank {
            if (id.equals("kopensubtitle", ignoreCase = true) ||
                pkgName.contains("subtitle", ignoreCase = true)
            ) {
                "subtitle"
            } else {
                "video"
            }
        }
        val notes = parseNotesList(obj, "changelog", "release_notes")
        return AnimeSourcePlugin(
            id = id,
            name = obj.optString("name", "Plugin"),
            packageName = pkgName,
            providerAuthority = authority,
            pluginType = pluginType,
            version = obj.optString("version", "1.0.0"),
            versionCode = obj.optInt("versionCode", 1),
            author = obj.optString("author", ""),
            description = obj.optString("description", ""),
            isInstalled = false,
            isEnabled = true,
            isDefault = obj.optBoolean("isDefault", false),
            capabilities = caps,
            sourceUrl = obj.optString("sourceUrl", ""),
            apkFileName = obj.optString("apkFileName", "plugin.apk"),
            apkDownloadUrl = obj.optString("apkDownloadUrl", "").trim().takeIf { it.isNotEmpty() },
            apkSize = obj.optString("apkSize", ""),
            isApkDownloaded = false,
            changelog = notes.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            changelogItems = notes
        )
    }

    /**
     * Pure, side-effect-free evaluation of a remote catalog JSON against local state.
     * Ignores downgrades (remoteVer < localVersion) and never mutates local state.
     */
    internal fun evaluateCatalogUpdate(
        localPlugins: List<AnimeSourcePlugin>,
        localVersion: Int,
        remoteJson: String,
        installedInspector: (AnimeSourcePlugin) -> Pair<String?, Boolean>
    ): Result<CatalogUpdateInfo> {
        val parsedRes = parseCatalogDocument(remoteJson)
        if (parsedRes.isFailure) {
            return Result.failure(parsedRes.exceptionOrNull() ?: IllegalArgumentException("Invalid catalog JSON"))
        }
        val doc = parsedRes.getOrThrow()

        // Ignore catalog downgrades completely
        if (doc.configVersion < localVersion) {
            val inspected = localPlugins.map { local ->
                val (installedVer, hasPkgUpdate) = installedInspector(local)
                local.copy(
                    isInstalled = installedVer != null,
                    installedVersion = installedVer,
                    hasUpdate = hasPkgUpdate
                )
            }
            return Result.success(
                CatalogUpdateInfo(
                    catalogUpdateAvailable = false,
                    currentVersion = localVersion,
                    newVersion = localVersion,
                    releaseNotes = emptyList(),
                    releaseDate = null,
                    minAppVersion = null,
                    remoteJson = "",
                    plugins = inspected,
                    newPlugins = emptyList(),
                    updatedPlugins = inspected.filter { it.isInstalled && it.hasUpdate },
                    metadataUpdatedCount = 0
                )
            )
        }

        val inspectedRemote = doc.plugins.map { parsed ->
            val (installedVer, hasPkgUpdate) = installedInspector(parsed)
            parsed.copy(
                isInstalled = installedVer != null,
                installedVersion = installedVer,
                hasUpdate = hasPkgUpdate
            )
        }

        val newPlugins = inspectedRemote.filter { remote ->
            localPlugins.none { local ->
                local.id == remote.id ||
                    (remote.packageName.isNotBlank() && local.packageName == remote.packageName)
            }
        }

        var metadataChangedCount = 0
        for (local in localPlugins) {
            val remoteMatch = inspectedRemote.firstOrNull { remote ->
                remote.id == local.id ||
                    (local.packageName.isNotBlank() && remote.packageName == local.packageName)
            }
            if (remoteMatch != null && hasPluginMetadataChanged(local, remoteMatch)) {
                metadataChangedCount++
            }
        }

        val catalogUpdateAvailable = (doc.configVersion > localVersion) || newPlugins.isNotEmpty()
        val updatedPlugins = inspectedRemote.filter { it.isInstalled && it.hasUpdate }

        return Result.success(
            CatalogUpdateInfo(
                catalogUpdateAvailable = catalogUpdateAvailable,
                currentVersion = localVersion,
                newVersion = doc.configVersion,
                releaseNotes = doc.releaseNotes,
                releaseDate = doc.releaseDate,
                minAppVersion = doc.minAppVersion,
                remoteJson = remoteJson,
                plugins = inspectedRemote,
                newPlugins = newPlugins,
                updatedPlugins = updatedPlugins,
                metadataUpdatedCount = metadataChangedCount
            )
        )
    }

    private fun parseConfigVersion(jsonStr: String): Int {
        return parseCatalogDocument(jsonStr).getOrNull()?.configVersion ?: 0
    }

    /**
     * Loads plugin definitions from filesDir/plugins.json or bundled assets/plugins.json.
     * Prioritizes newer plugin_config_version from assets if the app was updated.
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

        val jsonContent = if (assetVer > fileVer || fileContent.isNullOrBlank()) {
            if (!assetContent.isNullOrBlank()) {
                writeAtomically(file, assetContent)
            }
            assetContent
        } else {
            fileContent
        }

        if (jsonContent.isNullOrBlank()) {
            _pluginConfigVersionFlow.value = 1
            _appliedReleaseNotesFlow.value = emptyList()
            return listOf(defaultKanikotoPlugin)
        }

        val parsed = parseCatalogDocument(jsonContent).getOrNull()
        if (parsed == null) {
            Log.e(TAG, "Malformed plugins.json; falling back to asset or default")
            val assetParsed = assetContent?.let { parseCatalogDocument(it).getOrNull() }
            if (assetParsed != null && assetParsed.plugins.isNotEmpty()) {
                _pluginConfigVersionFlow.value = assetParsed.configVersion
                _appliedReleaseNotesFlow.value = assetParsed.releaseNotes
                _appliedReleaseDate = assetParsed.releaseDate
                _appliedMinAppVersion = assetParsed.minAppVersion
                return assetParsed.plugins
            }
            _pluginConfigVersionFlow.value = 1
            _appliedReleaseNotesFlow.value = emptyList()
            return listOf(defaultKanikotoPlugin)
        }

        _pluginConfigVersionFlow.value = parsed.configVersion
        _appliedReleaseNotesFlow.value = parsed.releaseNotes
        _appliedReleaseDate = parsed.releaseDate
        _appliedMinAppVersion = parsed.minAppVersion
        return if (parsed.plugins.isEmpty()) listOf(defaultKanikotoPlugin) else parsed.plugins
    }

    private fun writeAtomically(target: File, content: String): Boolean {
        return try {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Atomic write failed for ${target.name}", e)
            false
        }
    }

    private fun savePluginsToJson(
        context: Context,
        pluginList: List<AnimeSourcePlugin>,
        configVersion: Int = _pluginConfigVersionFlow.value,
        releaseNotes: List<String> = _appliedReleaseNotesFlow.value,
        releaseDate: String? = _appliedReleaseDate,
        minAppVersion: String? = _appliedMinAppVersion
    ) {
        val root = JSONObject().apply {
            put("plugin_config_version", configVersion)
            if (!releaseDate.isNullOrBlank()) put("release_date", releaseDate)
            if (!minAppVersion.isNullOrBlank()) put("min_app_version", minAppVersion)
            if (releaseNotes.isNotEmpty()) {
                val notesArr = JSONArray()
                releaseNotes.forEach { notesArr.put(it) }
                put("release_notes", notesArr)
            }
            val jsonArray = JSONArray()
            for (p in pluginList) {
                val jo = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("packageName", p.packageName)
                    put("providerAuthority", p.providerAuthority)
                    if (p.isSubtitlePlugin) {
                        put("pluginType", p.pluginType)
                    }
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
                    val cl = p.resolvedChangelogItems
                    if (cl.isNotEmpty()) {
                        val clArr = JSONArray()
                        cl.forEach { clArr.put(it) }
                        put("changelog", clArr)
                    }
                    val ca = JSONArray()
                    p.capabilities.forEach { ca.put(it) }
                    put("capabilities", ca)
                }
                jsonArray.put(jo)
            }
            put("plugins", jsonArray)
        }
        writeAtomically(File(context.filesDir, PLUGINS_JSON_FILE), root.toString(2))
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
            local.resolvedChangelogItems != remote.resolvedChangelogItems ||
            local.name != remote.name ||
            local.description != remote.description ||
            local.sourceUrl != remote.sourceUrl ||
            local.apkFileName != remote.apkFileName ||
            local.providerAuthority != remote.providerAuthority ||
            local.pluginType != remote.pluginType ||
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

    fun shouldAutoCheckCatalog(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastMs = sp.getLong(KEY_LAST_CHECK_MS, 0L)
        return (nowMs - lastMs) >= CATALOG_CHECK_TTL_MS
    }

    fun dismissCatalogUpdate(context: Context, version: Int) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putInt(KEY_DISMISSED_CATALOG_VERSION, version).apply()
        _dismissedCatalogVersionFlow.value = version
    }

    fun isCatalogUpdateDismissed(context: Context, version: Int): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissed = sp.getInt(KEY_DISMISSED_CATALOG_VERSION, 0)
        _dismissedCatalogVersionFlow.value = dismissed
        return dismissed >= version && version > 0
    }

    /**
     * Builds the most up-to-date "What's new" snapshot for the Release Notes chip:
     * prefers the last fetched remote catalog info if available, otherwise falls back
     * to persisted fetched/applied notes + current plugins.
     */
    fun getReleaseNotesSnapshot(context: Context): CatalogUpdateInfo {
        val cached = _lastFetchedCatalogFlow.value
        if (cached != null) return cached

        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fetchedVer = sp.getInt(KEY_LAST_FETCHED_VERSION, 0)
        val fetchedNotesRaw = sp.getString(KEY_LAST_FETCHED_NOTES, null)
        val fetchedDate = sp.getString(KEY_LAST_FETCHED_DATE, null)

        val currentVer = _pluginConfigVersionFlow.value
        val notes = if (fetchedVer >= currentVer && !fetchedNotesRaw.isNullOrBlank()) {
            splitNotesText(fetchedNotesRaw)
        } else {
            _appliedReleaseNotesFlow.value
        }
        val currentPlugins = _plugins.value
        return CatalogUpdateInfo(
            catalogUpdateAvailable = fetchedVer > currentVer,
            currentVersion = currentVer,
            newVersion = maxOf(currentVer, fetchedVer),
            releaseNotes = notes,
            releaseDate = fetchedDate ?: _appliedReleaseDate,
            minAppVersion = _appliedMinAppVersion,
            plugins = currentPlugins,
            updatedPlugins = currentPlugins.filter { it.isInstalled && it.hasUpdate }
        )
    }

    /**
     * Side-effect-free online check for catalog template & plugin updates.
     * Does NOT overwrite plugins.json or applied version/notes until [applyCatalogUpdate] is called.
     */
    suspend fun checkForCatalogUpdate(
        context: Context,
        customUrl: String? = null,
        force: Boolean = true
    ): Result<CatalogUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            if (!force && !shouldAutoCheckCatalog(context)) {
                val cached = _lastFetchedCatalogFlow.value
                if (cached != null) return@withContext Result.success(cached)
            }

            val targetUrl = customUrl ?: DEFAULT_CATALOG_UPDATE_URL
            if (targetUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Missing update URL"))
            }

            val localPlugins = loadPluginsFromJson(context)
            val localVersion = _pluginConfigVersionFlow.value

            val url = URL(appendCacheBuster(targetUrl))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Kanime)")
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                val code = conn.responseCode
                conn.disconnect()
                return@withContext Result.failure(Exception("HTTP $code"))
            }

            val remoteJson = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val evalResult = evaluateCatalogUpdate(
                localPlugins = localPlugins,
                localVersion = localVersion,
                remoteJson = remoteJson,
                installedInspector = { inspectInstalledPlugin(context, it) }
            )

            evalResult.onSuccess { info ->
                _lastFetchedCatalogFlow.value = info
                val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                sp.edit()
                    .putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis())
                    .putInt(KEY_LAST_FETCHED_VERSION, info.newVersion)
                    .putString(KEY_LAST_FETCHED_NOTES, info.releaseNotes.joinToString("\n"))
                    .putString(KEY_LAST_FETCHED_DATE, info.releaseDate)
                    .apply()
                _dismissedCatalogVersionFlow.value = sp.getInt(KEY_DISMISSED_CATALOG_VERSION, 0)
            }

            evalResult
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for catalog update", e)
            Result.failure(e)
        }
    }

    /**
     * Atomically applies catalog version, release notes, and merged plugin metadata
     * (preserving local custom plugins), then refreshes installed status.
     */
    fun applyCatalogUpdate(context: Context, updateInfo: CatalogUpdateInfo) {
        try {
            val currentLocal = loadPluginsFromJson(context)
            val targetVersion = maxOf(_pluginConfigVersionFlow.value, updateInfo.newVersion)
            val targetNotes = updateInfo.releaseNotes.ifEmpty { _appliedReleaseNotesFlow.value }
            val targetDate = updateInfo.releaseDate ?: _appliedReleaseDate
            val targetMinApp = updateInfo.minAppVersion ?: _appliedMinAppVersion

            val mergedPlugins = if (updateInfo.plugins.isNotEmpty()) {
                val customLocalPlugins = currentLocal.filter { local ->
                    updateInfo.plugins.none { remote ->
                        remote.id == local.id ||
                            (local.packageName.isNotBlank() && remote.packageName == local.packageName)
                    }
                }
                updateInfo.plugins + customLocalPlugins
            } else {
                currentLocal
            }

            savePluginsToJson(
                context = context,
                pluginList = mergedPlugins,
                configVersion = targetVersion,
                releaseNotes = targetNotes,
                releaseDate = targetDate,
                minAppVersion = targetMinApp
            )

            _pluginConfigVersionFlow.value = targetVersion
            _appliedReleaseNotesFlow.value = targetNotes
            _appliedReleaseDate = targetDate
            _appliedMinAppVersion = targetMinApp
            _lastFetchedCatalogFlow.value = updateInfo.copy(
                catalogUpdateAvailable = false,
                currentVersion = targetVersion,
                newVersion = targetVersion
            )

            checkPluginStatus(context, force = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying catalog update", e)
        }
    }

    /**
     * Dynamically adds or updates a library plugin from a JSON string at runtime.
     */
    fun addPluginFromJson(context: Context, jsonStr: String): Result<AnimeSourcePlugin> {
        return try {
            val obj = JSONObject(jsonStr)
            val newPlugin = parsePluginObject(obj, 0).copy(
                id = obj.optString("id", "plugin_${System.currentTimeMillis()}"),
                isDefault = false
            )

            val current = loadPluginsFromJson(context).toMutableList()
            val existingIdx = current.indexOfFirst {
                it.id == newPlugin.id || (newPlugin.packageName.isNotBlank() && it.packageName == newPlugin.packageName)
            }
            if (existingIdx >= 0) {
                current[existingIdx] = newPlugin
            } else {
                current.add(newPlugin)
            }

            savePluginsToJson(context, current)
            checkPluginStatus(context, force = true)
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

    /**
     * Offloads package-manager + filesystem plugin status inspection to [Dispatchers.IO]
     * with short debouncing so rapid startup / ON_RESUME / broadcast calls don't thrash.
     */
    suspend fun checkPluginStatusAsync(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastStatusCheckMs) < STATUS_CHECK_DEBOUNCE_MS) return
        withContext(Dispatchers.IO) {
            checkPluginStatus(context, force = true)
        }
    }

    fun checkPluginStatus(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastStatusCheckMs) < STATUS_CHECK_DEBOUNCE_MS) return
        lastStatusCheckMs = now

        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _dismissedCatalogVersionFlow.value = sp.getInt(KEY_DISMISSED_CATALOG_VERSION, 0)
        val safTreeUri = getValidPluginFolderUri(context)

        val baseList = loadPluginsFromJson(context)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir

        val updated = baseList.map { plugin ->
            val apkFile = File(downloadsDir, plugin.apkFileName)
            val altFile = File(downloadsDir, "${plugin.apkFileName.removeSuffix(".apk")}-v${plugin.version}.apk")

            val isPkgInstalled = PluginBridge.isPluginInstalled(context, plugin.packageName)
            val inSaf = safTreeUri != null && findApkInSafTree(context, safTreeUri, plugin) != null
            val isApkDownloaded = inSaf || (apkFile.exists() && apkFile.length() > 0) || (altFile.exists() && altFile.length() > 0)

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

        val currentActive = updated.firstOrNull { it.id == _activeSourceId.value }
        if (currentActive == null || !currentActive.isVideoPlugin || !currentActive.isInstalled || !currentActive.isEnabled) {
            val fallbackActive = updated.firstOrNull { it.isVideoPlugin && it.isInstalled && it.isEnabled }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.plugins_msg_permission_required),
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

            // Prefer the APK saved inside the user's chosen SAF folder (staged via cache FileProvider for universal OEM compatibility)
            var targetApk: File? = null
            val safTreeUri = getValidPluginFolderUri(context)
            if (safTreeUri != null) {
                val safDocUri = findApkInSafTree(context, safTreeUri, plugin)
                if (safDocUri != null) {
                    val stagingDir = File(context.cacheDir, "plugin_install_staging").apply { mkdirs() }
                    val stagedFile = File(stagingDir, plugin.apkFileName)
                    val copied = try {
                        context.contentResolver.openInputStream(safDocUri)?.use { input ->
                            FileOutputStream(stagedFile).use { output ->
                                input.copyTo(output, bufferSize = 8192)
                            }
                            true
                        } ?: false
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed staging SAF APK for install", e)
                        false
                    }
                    if (copied && stagedFile.exists() && stagedFile.length() > 0L) {
                        targetApk = stagedFile
                    }
                }
            }

            if (targetApk == null) {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.cacheDir
                var localApk = File(downloadsDir, plugin.apkFileName)
                if (!localApk.exists() || localApk.length() == 0L) {
                    val alt = File(downloadsDir, "${plugin.apkFileName.removeSuffix(".apk")}-v${plugin.version}.apk")
                    if (alt.exists() && alt.length() > 0L) {
                        localApk = alt
                    }
                }
                if (localApk.exists() && localApk.length() > 0L) {
                    targetApk = localApk
                }
            }

            if (targetApk == null || !targetApk.exists() || targetApk.length() == 0L) {
                Toast.makeText(context, context.getString(R.string.plugins_msg_apk_missing), Toast.LENGTH_SHORT).show()
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
            Log.e(TAG, "Failed to launch installer", e)
        }
    }

    fun uninstallPlugin(context: Context, plugin: AnimeSourcePlugin) {
        try {
            // Delete from user-selected SAF folder if present
            val safTreeUri = getValidPluginFolderUri(context)
            if (safTreeUri != null) {
                findApkInSafTree(context, safTreeUri, plugin)?.let { docUri ->
                    try {
                        DocumentsContract.deleteDocument(context.contentResolver, docUri)
                    } catch (_: Exception) {}
                }
            }

            // Delete staged cache copy if present
            File(File(context.cacheDir, "plugin_install_staging"), plugin.apkFileName).let {
                if (it.exists()) it.delete()
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir

            val targetApk = File(downloadsDir, plugin.apkFileName)
            if (targetApk.exists()) targetApk.delete()
            val altApk = File(downloadsDir, "${plugin.apkFileName.removeSuffix(".apk")}-v${plugin.version}.apk")
            if (altApk.exists()) altApk.delete()

            if (plugin.isInstalled) {
                val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${plugin.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(uninstallIntent)
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.plugins_msg_apk_removed, plugin.name),
                    Toast.LENGTH_SHORT
                ).show()
            }

            checkPluginStatus(context, force = true)
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall error", e)
        }
    }

    suspend fun downloadPluginApk(
        context: Context,
        plugin: AnimeSourcePlugin,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadUrl = plugin.apkDownloadUrl
            if (downloadUrl.isNullOrBlank() || (!downloadUrl.startsWith("http://", ignoreCase = true) && !downloadUrl.startsWith("https://", ignoreCase = true))) {
                return@withContext Result.failure(IllegalStateException("Missing download URL"))
            }

            val stagingDir = File(context.cacheDir, "plugin_install_staging").apply { mkdirs() }
            val tempApk = File(stagingDir, "${plugin.apkFileName}.tmp")

            val url = URL(appendCacheBuster(downloadUrl))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Kanime)")
            }
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                conn.disconnect()
                return@withContext Result.failure(Exception("HTTP $responseCode"))
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

            // Save into the user-selected SAF folder when configured; otherwise fallback to Downloads
            val safTreeUri = getValidPluginFolderUri(context)
            val savedToSaf = safTreeUri != null && saveApkToSafTree(context, safTreeUri, plugin, tempApk) != null
            val finalResultFile: File
            if (savedToSaf) {
                // Keep a staged copy ready for immediate FileProvider installation
                val stagedApk = File(stagingDir, plugin.apkFileName)
                if (stagedApk.exists()) stagedApk.delete()
                if (!tempApk.renameTo(stagedApk)) {
                    tempApk.copyTo(stagedApk, overwrite = true)
                    tempApk.delete()
                }
                finalResultFile = stagedApk
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.cacheDir
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetApk = File(downloadsDir, plugin.apkFileName)
                if (targetApk.exists()) targetApk.delete()
                if (!tempApk.renameTo(targetApk)) {
                    tempApk.copyTo(targetApk, overwrite = true)
                    tempApk.delete()
                }
                finalResultFile = targetApk
            }

            checkPluginStatus(context, force = true)
            withContext(Dispatchers.Main) {
                installPluginApk(context, plugin)
            }

            Result.success(finalResultFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading plugin APK", e)
            Result.failure(e)
        }
    }
}
