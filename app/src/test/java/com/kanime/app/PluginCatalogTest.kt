package com.kanime.app

import com.kanime.app.data.plugin.AnimeSourcePlugin
import com.kanime.app.data.plugin.PluginManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCatalogTest {

    private fun samplePlugin(
        id: String = "kanikoto",
        version: String = "1.0.0",
        versionCode: Int = 1
    ) = AnimeSourcePlugin(
        id = id,
        name = "KAnikoto Extractor",
        packageName = "com.kanime.plugin.$id",
        providerAuthority = "com.kanime.plugin.$id.provider",
        version = version,
        versionCode = versionCode,
        author = "Kanime",
        description = "Test source",
        isInstalled = true,
        installedVersion = version,
        isEnabled = true,
        isDefault = true,
        capabilities = listOf("HLS"),
        sourceUrl = "https://example.com",
        apkFileName = "$id.apk",
        apkSize = "2.4 MB"
    )

    @Test
    fun versionComparison_handlesSemverPrefixesAndLengths() {
        assertTrue(PluginManager.compareVersions("1.0.1", "1.0.0") > 0)
        assertTrue(PluginManager.compareVersions("1.2.0", "1.10.0") < 0)
        assertEquals(0, PluginManager.compareVersions("v1.2.0", "1.2"))
        assertEquals(0, PluginManager.compareVersions("V2.0.0-beta1", "2.0.0"))
        assertTrue(PluginManager.isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(PluginManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(PluginManager.isNewerVersion("0.9.5", "1.0.0"))
    }

    @Test
    fun notesParsing_supportsArrayStringBulletsFallbackKeysAndMissing() {
        val arrayJson = JSONObject(
            """
            {
              "release_notes": ["- First fix", "Second fix", "  "]
            }
            """.trimIndent()
        )
        assertEquals(
            listOf("First fix", "Second fix"),
            PluginManager.parseNotesList(arrayJson, "release_notes", "changelog")
        )

        val multiLineString = JSONObject(
            """
            {
              "catalog_changelog": "- Bullet one\n• Bullet two\n* Bullet three"
            }
            """.trimIndent()
        )
        assertEquals(
            listOf("Bullet one", "Bullet two", "Bullet three"),
            PluginManager.parseNotesList(multiLineString, "release_notes", "changelog", "catalog_changelog")
        )

        val missing = JSONObject("""{"plugin_config_version": 2}""")
        assertTrue(PluginManager.parseNotesList(missing, "release_notes", "changelog").isEmpty())
    }

    @Test
    fun evaluateCatalogUpdate_detectsCatalogAndPluginUpdatesWithoutSideEffects() {
        val local = listOf(samplePlugin(version = "1.0.0", versionCode = 1))
        val versionBefore = PluginManager.pluginConfigVersion

        val remoteJson = """
            {
              "plugin_config_version": 2,
              "release_date": "2026-11-18",
              "min_app_version": "1.0.0",
              "release_notes": ["Added faster mirror", "Updated extractor schema"],
              "plugins": [
                {
                  "id": "kanikoto",
                  "name": "KAnikoto Extractor",
                  "packageName": "com.kanime.plugin.kanikoto",
                  "version": "1.1.0",
                  "versionCode": 2,
                  "apkSize": "2.6 MB",
                  "changelog": ["Fixed HLS header parsing", "Reduced latency"]
                }
              ]
            }
        """.trimIndent()

        val result = PluginManager.evaluateCatalogUpdate(
            localPlugins = local,
            localVersion = 1,
            remoteJson = remoteJson,
            installedInspector = { plugin ->
                val hasUpd = PluginManager.isNewerVersion(plugin.version, "1.0.0")
                "1.0.0" to hasUpd
            }
        )

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertTrue(info.catalogUpdateAvailable)
        assertTrue(info.pluginUpdatesAvailable)
        assertEquals(1, info.currentVersion)
        assertEquals(2, info.newVersion)
        assertEquals("2026-11-18", info.releaseDate)
        assertEquals("1.0.0", info.minAppVersion)
        assertEquals(listOf("Added faster mirror", "Updated extractor schema"), info.releaseNotes)
        assertEquals(1, info.updatedPlugins.size)
        assertEquals(
            listOf("Fixed HLS header parsing", "Reduced latency"),
            info.updatedPlugins.first().resolvedChangelogItems
        )

        // Verify side-effect free: PluginManager state did not mutate during evaluation
        assertEquals(versionBefore, PluginManager.pluginConfigVersion)
        assertEquals("1.0.0", local.first().version)
    }

    @Test
    fun evaluateCatalogUpdate_ignoresDowngradeAndHandlesMalformedJson() {
        val local = listOf(samplePlugin(version = "1.0.0"))
        val downgradeJson = """
            {
              "plugin_config_version": 1,
              "release_notes": ["Old catalog"],
              "plugins": []
            }
        """.trimIndent()

        val downgradeResult = PluginManager.evaluateCatalogUpdate(
            localPlugins = local,
            localVersion = 3,
            remoteJson = downgradeJson,
            installedInspector = { "1.0.0" to false }
        ).getOrThrow()

        assertFalse(downgradeResult.catalogUpdateAvailable)
        assertEquals(3, downgradeResult.currentVersion)
        assertEquals(3, downgradeResult.newVersion)
        assertTrue(downgradeResult.releaseNotes.isEmpty())

        val malformed = PluginManager.evaluateCatalogUpdate(
            localPlugins = local,
            localVersion = 1,
            remoteJson = "{not valid json",
            installedInspector = { null to false }
        )
        assertTrue(malformed.isFailure)
    }

    @Test
    fun evaluateCatalogUpdate_detectsPluginOnlyUpdateWithoutCatalogBump() {
        val local = listOf(samplePlugin(version = "1.0.0"))
        val remoteJson = """
            {
              "plugin_config_version": 1,
              "plugins": [
                {
                  "id": "kanikoto",
                  "name": "KAnikoto Extractor",
                  "packageName": "com.kanime.plugin.kanikoto",
                  "version": "1.0.1",
                  "versionCode": 2,
                  "changelog": "Patched token decoder"
                }
              ]
            }
        """.trimIndent()

        val info = PluginManager.evaluateCatalogUpdate(
            localPlugins = local,
            localVersion = 1,
            remoteJson = remoteJson,
            installedInspector = { plugin ->
                "1.0.0" to PluginManager.isNewerVersion(plugin.version, "1.0.0")
            }
        ).getOrThrow()

        assertFalse(info.catalogUpdateAvailable)
        assertTrue(info.pluginUpdatesAvailable)
        assertTrue(info.releaseNotes.isEmpty())
        assertNull(info.releaseDate)
        assertEquals(listOf("Patched token decoder"), info.updatedPlugins.first().resolvedChangelogItems)
    }

    @Test
    fun parseCatalogDocument_distinguishesVideoAndSubtitlePlugins() {
        val json = """
            {
              "plugin_config_version": 5,
              "plugins": [
                {
                  "id": "kanikoto",
                  "name": "KAnikoto Extractor",
                  "packageName": "com.kanime.plugin.kanikoto",
                  "version": "1.0.1",
                  "versionCode": 2
                },
                {
                  "id": "kopensubtitle",
                  "name": "KOpenSubtitle",
                  "packageName": "com.kanime.plugin.kopensubtitle",
                  "providerAuthority": "com.kanime.plugin.kopensubtitle.provider",
                  "pluginType": "subtitle",
                  "version": "1.0.0",
                  "versionCode": 1
                }
              ]
            }
        """.trimIndent()

        val doc = PluginManager.parseCatalogDocument(json).getOrThrow()
        assertEquals(2, doc.plugins.size)
        val video = doc.plugins[0]
        val sub = doc.plugins[1]
        assertTrue(video.isVideoPlugin)
        assertFalse(video.isSubtitlePlugin)
        assertTrue(sub.isSubtitlePlugin)
        assertFalse(sub.isVideoPlugin)
        assertEquals("com.kanime.plugin.kopensubtitle.provider", sub.providerAuthority)
    }
}
