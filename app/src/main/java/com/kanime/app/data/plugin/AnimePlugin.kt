package com.kanime.app.data.plugin

import androidx.compose.runtime.Immutable

@Immutable
data class AnimeSourcePlugin(
    val id: String,
    val name: String,
    val packageName: String,
    val version: String,
    val versionCode: Int = 1,
    val installedVersion: String? = null,
    val hasUpdate: Boolean = false,
    val changelog: String? = null,
    val changelogItems: List<String> = emptyList(),
    val author: String,
    val description: String,
    val isInstalled: Boolean,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val capabilities: List<String> = emptyList(),
    val sourceUrl: String,
    val apkFileName: String,
    val apkSize: String,
    val isApkDownloaded: Boolean = false,
    val providerAuthority: String = "com.kanime.plugin.kanikoto.provider",
    val apkDownloadUrl: String? = null,
    val pluginType: String = "video" // "video" | "subtitle"
) {
    val isSubtitlePlugin: Boolean
        get() = pluginType.equals("subtitle", ignoreCase = true)

    val isVideoPlugin: Boolean
        get() = !isSubtitlePlugin

    val resolvedChangelogItems: List<String>
        get() = when {
            changelogItems.isNotEmpty() -> changelogItems
            !changelog.isNullOrBlank() -> PluginManager.splitNotesText(changelog)
            else -> emptyList()
        }
}
