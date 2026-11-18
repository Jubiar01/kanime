package com.kanime.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanime.app.data.api.CatalogConfig
import com.kanime.app.data.plugin.PluginManager
import com.kanime.app.ui.player.anime.AnimeSharpenStrength
import com.kanime.app.ui.player.anime.AnimeUpscaleQuality
import com.kanime.app.ui.viewmodel.AnimeUiState
import com.kanime.app.ui.viewmodel.AnimeViewModel

@Composable
fun SettingsScreen(
    viewModel: AnimeViewModel,
    uiState: AnimeUiState
) {
    val context = LocalContext.current
    val plugins by PluginManager.plugins.collectAsState()
    val tmdbApiKey by CatalogConfig.tmdbApiKeyFlow.collectAsState()
    val autoPlayNext by CatalogConfig.autoPlayNextFlow.collectAsState()
    val animeUpscaleEnabled by CatalogConfig.animeUpscaleEnabledFlow.collectAsState()
    val lineSharpeningEnabled by CatalogConfig.animeLineSharpeningFlow.collectAsState()
    val animeUpscaleQuality by CatalogConfig.animeUpscaleQualityFlow.collectAsState()
    val animeSharpenStrength by CatalogConfig.animeSharpenStrengthFlow.collectAsState()
    val activePluginCount = plugins.count { it.isInstalled && it.isEnabled }
    val pluginUpdateCount = plugins.count { it.hasUpdate }

    LaunchedEffect(Unit) {
        PluginManager.checkPluginStatus(context)
    }

    var showPluginsView by remember { mutableStateOf(false) }
    var showTmdbDialog by remember { mutableStateOf(false) }
    var tmdbInput by remember(tmdbApiKey) { mutableStateOf(tmdbApiKey) }
    var cacheClearedBanner by remember { mutableStateOf(false) }

    if (showPluginsView) {
        BackHandler {
            showPluginsView = false
        }
        PluginsScreen(
            onBackClick = { showPluginsView = false }
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & About",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Video Source Plugins
        item {
            Text(
                text = "Streaming Sources & Plugins",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                onClick = { showPluginsView = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Video Source Plugins",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (activePluginCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (activePluginCount > 0) "$activePluginCount Active" else "Inactive",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (activePluginCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (pluginUpdateCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "$pluginUpdateCount Update${if (pluginUpdateCount > 1) "s" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "KAnikoto Android library, sources & APK downloads",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open Plugins",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Catalog & Metadata Sources (MAL + AniList + TMDB + Kitsu)
        item {
            Text(
                text = "Catalog & Art Sources",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("TMDB Primary Art API Key", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (tmdbApiKey.isNotBlank()) {
                                        "Active (Posters, Logos & Episode Stills)"
                                    } else {
                                        "Not set — using Kitsu & AniList fallback"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                tmdbInput = tmdbApiKey
                                showTmdbDialog = true
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (tmdbApiKey.isNotBlank()) "Edit" else "Set Key")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Cached, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Metadata & Art Cache", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (cacheClearedBanner) "Cache cleared" else "Cached MAL, AniList, TMDB & Kitsu responses",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                viewModel.clearCatalogCache()
                                cacheClearedBanner = true
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        // Appearance
        item {
            Text(
                text = "Appearance & Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Dark Theme", fontWeight = FontWeight.SemiBold)
                                Text("Anime dark aesthetic", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = uiState.isDarkTheme,
                            onCheckedChange = { viewModel.toggleTheme() }
                        )
                    }
                }
            }
        }

        // Playback
        item {
            Text(
                text = "Playback Preferences",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Auto-Play Next Episode", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (autoPlayNext) {
                                        "Automatically plays the next episode when finished"
                                    } else {
                                        "Stops playback at the end of each episode"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = autoPlayNext,
                            onCheckedChange = { viewModel.setAutoPlayNext(it) }
                        )
                    }
                }
            }
        }

        // Video Enhancement (Anime4K 2x Upscaler & Line Sharpening)
        item {
            Text(
                text = "Video Enhancement (Anime4K)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Anime4K 2x CNN Upscaler
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Anime4K 2x Upscaler", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (animeUpscaleEnabled) {
                                        "Active — ${animeUpscaleQuality.label} + Catmull-Rom supersampling"
                                    } else {
                                        "Real-time 2x CNN GPU upscaler with anti-ringing"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = animeUpscaleEnabled,
                            onCheckedChange = { viewModel.setAnimeUpscaleEnabled(it) }
                        )
                    }

                    // Quality preset chips (applies to Upscaler & CNN Restore model)
                    if (animeUpscaleEnabled || lineSharpeningEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "CNN Quality Model",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnimeUpscaleQuality.entries.forEach { preset ->
                                    FilterChip(
                                        selected = animeUpscaleQuality == preset,
                                        onClick = { viewModel.setAnimeUpscaleQuality(preset) },
                                        label = { Text(preset.label) }
                                    )
                                }
                            }
                            Text(
                                text = animeUpscaleQuality.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Anime Line Sharpening (Clamp + Restore CNN + Darken HQ + Thin HQ + Adaptive Line Sharpen)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Anime Line Sharpening", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (lineSharpeningEnabled) {
                                        "Active — ${animeSharpenStrength.label} edge-aware ink sharpening + HQ restore"
                                    } else {
                                        "Restores & sharpens anime ink lines without white halos"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = lineSharpeningEnabled,
                            onCheckedChange = { viewModel.setAnimeLineSharpening(it) }
                        )
                    }

                    if (lineSharpeningEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Line Sharpening Intensity",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnimeSharpenStrength.entries.forEach { level ->
                                    FilterChip(
                                        selected = animeSharpenStrength == level,
                                        onClick = { viewModel.setAnimeSharpenStrength(level) },
                                        label = { Text(level.label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // About & Data Source Card
        item {
            Text(
                text = "About KAnime",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SpecRow(icon = Icons.Default.Info, title = "Application", value = "KAnime v1.0.0")
                    SpecRow(icon = Icons.Default.CloudSync, title = "Metadata Engine", value = "MAL Direct + AniList")
                    SpecRow(icon = Icons.Default.Image, title = "Art & Stills", value = "TMDB → Kitsu → AniList")
                    SpecRow(icon = Icons.Default.AutoAwesome, title = "Video Shaders", value = "Anime4K v4 (GLES 3.0)")
                }
            }
        }
    }

    if (showTmdbDialog) {
        AlertDialog(
            onDismissRequest = { showTmdbDialog = false },
            title = { Text("TMDB API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a TMDB v3 API key to enable TMDB posters, transparent logos, backdrops, and episode stills as the primary art source. Leave blank to fall back to Kitsu and AniList automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tmdbInput,
                        onValueChange = { tmdbInput = it },
                        placeholder = { Text("TMDB v3 API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveTmdbApiKey(tmdbInput)
                    showTmdbDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTmdbDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SpecRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
