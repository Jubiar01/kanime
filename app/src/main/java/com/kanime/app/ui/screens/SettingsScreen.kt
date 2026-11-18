package com.kanime.app.ui.screens

import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanime.app.R
import com.kanime.app.data.api.CatalogConfig
import com.kanime.app.data.plugin.PluginManager
import com.kanime.app.ui.components.GlassChip
import com.kanime.app.ui.player.anime.AnimeSharpenStrength
import com.kanime.app.ui.player.anime.AnimeUpscaleQuality
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeAmbientBackdrop
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.KanimeWindowWidthSizeClass
import com.kanime.app.ui.theme.LocalWindowSizeClass
import com.kanime.app.ui.theme.OnboardingPreferences
import com.kanime.app.ui.theme.PillShape
import com.kanime.app.ui.theme.ThemeMode
import com.kanime.app.ui.theme.ThemePreferences
import com.kanime.app.ui.viewmodel.AnimeUiState
import com.kanime.app.ui.viewmodel.AnimeViewModel

@Composable
fun SettingsScreen(
    viewModel: AnimeViewModel,
    uiState: AnimeUiState,
    topContentPadding: Dp = 12.dp,
    bottomContentPadding: Dp = KanimeSpacing.floatingBarClearance
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val plugins by PluginManager.plugins.collectAsStateWithLifecycle()
    val pluginFolderUri by PluginManager.pluginFolderUriFlow.collectAsStateWithLifecycle()
    val pluginFolderName = remember(pluginFolderUri) {
        PluginManager.getPluginFolderDisplayName(context, pluginFolderUri)
    }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            PluginManager.setPluginFolderUri(context, treeUri)
            coroutineScope.launch {
                PluginManager.checkPluginStatusAsync(context, force = true)
            }
        }
    }
    val themeMode by ThemePreferences.themeModeFlow.collectAsStateWithLifecycle()
    val dynamicColor by ThemePreferences.dynamicColorFlow.collectAsStateWithLifecycle()
    val tmdbApiKey by CatalogConfig.tmdbApiKeyFlow.collectAsStateWithLifecycle()
    val autoPlayNext by CatalogConfig.autoPlayNextFlow.collectAsStateWithLifecycle()
    val animeUpscaleEnabled by CatalogConfig.animeUpscaleEnabledFlow.collectAsStateWithLifecycle()
    val lineSharpeningEnabled by CatalogConfig.animeLineSharpeningFlow.collectAsStateWithLifecycle()
    val animeUpscaleQuality by CatalogConfig.animeUpscaleQualityFlow.collectAsStateWithLifecycle()
    val animeSharpenStrength by CatalogConfig.animeSharpenStrengthFlow.collectAsStateWithLifecycle()

    val activePluginCount = remember(plugins) { plugins.count { it.isInstalled && it.isEnabled } }
    val pluginUpdateCount = remember(plugins) { plugins.count { it.hasUpdate } }

    LaunchedEffect(Unit) {
        PluginManager.checkPluginStatusAsync(context)
    }

    var showPluginsView by remember { mutableStateOf(false) }

    if (showPluginsView) {
        BackHandler { showPluginsView = false }
        PluginsScreen(onBackClick = { showPluginsView = false })
        return
    }

    SettingsScreenContent(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        activePluginCount = activePluginCount,
        pluginUpdateCount = pluginUpdateCount,
        pluginFolderName = pluginFolderName,
        autoPlayNext = autoPlayNext,
        animeUpscaleEnabled = animeUpscaleEnabled,
        animeUpscaleQuality = animeUpscaleQuality,
        lineSharpeningEnabled = lineSharpeningEnabled,
        animeSharpenStrength = animeSharpenStrength,
        tmdbApiKey = tmdbApiKey,
        onThemeModeChange = { ThemePreferences.setThemeMode(context, it) },
        onDynamicColorChange = { ThemePreferences.setDynamicColor(context, it) },
        onOpenPlugins = { showPluginsView = true },
        onChoosePluginFolder = { folderPickerLauncher.launch(null) },
        onOpenSetup = { OnboardingPreferences.reset(context) },
        onAutoPlayNextChange = { viewModel.setAutoPlayNext(it) },
        onUpscaleEnabledChange = { viewModel.setAnimeUpscaleEnabled(it) },
        onUpscaleQualityChange = { viewModel.setAnimeUpscaleQuality(it) },
        onLineSharpeningChange = { viewModel.setAnimeLineSharpening(it) },
        onSharpenStrengthChange = { viewModel.setAnimeSharpenStrength(it) },
        onSaveTmdbKey = { viewModel.saveTmdbApiKey(it) },
        onClearCache = { viewModel.clearCatalogCache() },
        topContentPadding = topContentPadding,
        bottomContentPadding = bottomContentPadding
    )
}

@Composable
internal fun SettingsScreenContent(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    supportsDynamicColor: Boolean,
    activePluginCount: Int,
    pluginUpdateCount: Int,
    pluginFolderName: String?,
    autoPlayNext: Boolean,
    animeUpscaleEnabled: Boolean,
    animeUpscaleQuality: AnimeUpscaleQuality,
    lineSharpeningEnabled: Boolean,
    animeSharpenStrength: AnimeSharpenStrength,
    tmdbApiKey: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenPlugins: () -> Unit,
    onChoosePluginFolder: () -> Unit,
    onOpenSetup: () -> Unit,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onUpscaleEnabledChange: (Boolean) -> Unit,
    onUpscaleQualityChange: (AnimeUpscaleQuality) -> Unit,
    onLineSharpeningChange: (Boolean) -> Unit,
    onSharpenStrengthChange: (AnimeSharpenStrength) -> Unit,
    onSaveTmdbKey: (String) -> Unit,
    onClearCache: () -> Unit,
    topContentPadding: Dp = 12.dp,
    bottomContentPadding: Dp = KanimeSpacing.floatingBarClearance
) {
    val windowSize = LocalWindowSizeClass.current
    val horizontalPad = when (windowSize.widthSizeClass) {
        KanimeWindowWidthSizeClass.Compact -> KanimeSpacing.lg
        KanimeWindowWidthSizeClass.Medium -> KanimeSpacing.xl
        KanimeWindowWidthSizeClass.Expanded -> KanimeSpacing.xxl
    }

    var showAdvanced by remember { mutableStateOf(false) }
    var showTmdbDialog by remember { mutableStateOf(false) }
    var tmdbInput by remember(tmdbApiKey) { mutableStateOf(tmdbApiKey) }
    var cacheCleared by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = horizontalPad),
            contentPadding = PaddingValues(top = topContentPadding, bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(KanimeSpacing.lg)
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Appearance (Theme Mode + optional Dynamic Color)
            item(key = "appearance") {
                SettingsGroup(title = stringResource(R.string.settings_appearance)) {
                    Column(
                        modifier = Modifier.padding(KanimeSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_theme),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            GlassChip(
                                label = stringResource(R.string.settings_theme_system),
                                selected = themeMode == ThemeMode.SYSTEM,
                                onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                            )
                            GlassChip(
                                label = stringResource(R.string.settings_theme_light),
                                selected = themeMode == ThemeMode.LIGHT,
                                onClick = { onThemeModeChange(ThemeMode.LIGHT) }
                            )
                            GlassChip(
                                label = stringResource(R.string.settings_theme_dark),
                                selected = themeMode == ThemeMode.DARK,
                                onClick = { onThemeModeChange(ThemeMode.DARK) }
                            )
                        }

                        if (supportsDynamicColor) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SettingsSwitchRow(
                                icon = Icons.Default.Palette,
                                title = stringResource(R.string.settings_dynamic_color),
                                subtitle = stringResource(R.string.settings_dynamic_color_desc),
                                checked = dynamicColor,
                                onCheckedChange = onDynamicColorChange
                            )
                        }
                    }
                }
            }

            // Sources & Playback
            item(key = "sources_playback") {
                SettingsGroup(title = stringResource(R.string.settings_sources)) {
                    Column(modifier = Modifier.padding(KanimeSpacing.lg), verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = KanimeSpacing.minTouchTarget)
                                .clickable(onClick = onOpenPlugins),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(KanimeSpacing.md))
                                Text(
                                    text = stringResource(R.string.settings_plugins),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (pluginUpdateCount > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = PillShape
                                    ) {
                                        Text(
                                            text = pluralStringResource(R.plurals.plugins_updates_count, pluginUpdateCount, pluginUpdateCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Surface(
                                    color = if (activePluginCount > 0) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    shape = PillShape
                                ) {
                                    Text(
                                        text = if (activePluginCount > 0) {
                                            stringResource(R.string.settings_plugins_active, activePluginCount)
                                        } else {
                                            stringResource(R.string.settings_plugins_inactive)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (activePluginCount > 0) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // SAF Plugin Folder row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = KanimeSpacing.minTouchTarget)
                                .clickable(onClick = onChoosePluginFolder),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(KanimeSpacing.md))
                                Column {
                                    Text(
                                        text = stringResource(R.string.settings_plugin_folder),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = pluginFolderName ?: stringResource(R.string.settings_plugin_folder_default),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        SettingsSwitchRow(
                            icon = Icons.Default.PlayCircleOutline,
                            title = stringResource(R.string.settings_autoplay),
                            checked = autoPlayNext,
                            onCheckedChange = onAutoPlayNextChange
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Re-open Onboarding Setup row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = KanimeSpacing.minTouchTarget)
                                .clickable(onClick = onOpenSetup),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RocketLaunch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(KanimeSpacing.md))
                                Column {
                                    Text(
                                        text = stringResource(R.string.settings_setup),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_setup_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Progressive Disclosure: Advanced (Anime4K shaders, TMDB key, Cache)
            item(key = "advanced") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(KanimeSpacing.lg)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = KanimeSpacing.minTouchTarget)
                                .clickable { showAdvanced = !showAdvanced },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(KanimeSpacing.md))
                                Text(
                                    text = stringResource(R.string.settings_advanced),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = showAdvanced) {
                            Column(
                                modifier = Modifier.padding(top = KanimeSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                SettingsSwitchRow(
                                    icon = Icons.Default.AutoAwesome,
                                    title = stringResource(R.string.settings_upscaler),
                                    checked = animeUpscaleEnabled,
                                    onCheckedChange = onUpscaleEnabledChange
                                )

                                if (animeUpscaleEnabled || lineSharpeningEnabled) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                                    ) {
                                        AnimeUpscaleQuality.entries.forEach { preset ->
                                            GlassChip(
                                                label = preset.label,
                                                selected = animeUpscaleQuality == preset,
                                                onClick = { onUpscaleQualityChange(preset) }
                                            )
                                        }
                                    }
                                }

                                SettingsSwitchRow(
                                    icon = Icons.Default.Brush,
                                    title = stringResource(R.string.settings_sharpening),
                                    checked = lineSharpeningEnabled,
                                    onCheckedChange = onLineSharpeningChange
                                )

                                if (lineSharpeningEnabled) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                                    ) {
                                        AnimeSharpenStrength.entries.forEach { level ->
                                            GlassChip(
                                                label = level.label,
                                                selected = animeSharpenStrength == level,
                                                onClick = { onSharpenStrengthChange(level) }
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // TMDB Key
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = KanimeSpacing.minTouchTarget),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(KanimeSpacing.md))
                                        Column {
                                            Text(
                                                text = stringResource(R.string.settings_tmdb_key),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = stringResource(
                                                    if (tmdbApiKey.isNotBlank()) R.string.settings_tmdb_active else R.string.settings_tmdb_unset
                                                ),
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
                                        shape = PillShape
                                    ) {
                                        Text(stringResource(if (tmdbApiKey.isNotBlank()) R.string.action_edit else R.string.action_set))
                                    }
                                }

                                // Clear Cache
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = KanimeSpacing.minTouchTarget),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cached,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(KanimeSpacing.md))
                                        Column {
                                            Text(
                                                text = stringResource(R.string.settings_cache),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (cacheCleared) {
                                                Text(
                                                    text = stringResource(R.string.settings_cache_cleared),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            onClearCache()
                                            cacheCleared = true
                                        },
                                        shape = PillShape
                                    ) {
                                        Text(stringResource(R.string.action_clear))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Compact About
            item(key = "about") {
                SettingsGroup(title = stringResource(R.string.settings_about)) {
                    Column(
                        modifier = Modifier.padding(KanimeSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AboutRow(stringResource(R.string.settings_version), "1.0.0")
                        AboutRow(stringResource(R.string.settings_engine), "MAL · AniList · TMDB")
                        AboutRow(stringResource(R.string.settings_shaders), "Anime4K v4")
                    }
                }
            }
        }
    }

    if (showTmdbDialog) {
        AlertDialog(
            onDismissRequest = { showTmdbDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.settings_tmdb_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)) {
                    Text(
                        text = stringResource(R.string.settings_tmdb_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tmdbInput,
                        onValueChange = { tmdbInput = it },
                        placeholder = { Text(stringResource(R.string.settings_tmdb_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveTmdbKey(tmdbInput)
                    showTmdbDialog = false
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTmdbDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = KanimeSpacing.minTouchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(KanimeSpacing.md))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(KanimeSpacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(name = "Settings Light Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Settings Dark Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Preview(name = "Settings Light Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.TABLET)
@Preview(name = "Settings Dark Foldable", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.FOLDABLE)
@Composable
private fun SettingsScreenPreview() {
    KAnimeTheme(animateTransition = false) {
        KanimeAmbientBackdrop {
            SettingsScreenContent(
                themeMode = ThemeMode.SYSTEM,
                dynamicColor = false,
                supportsDynamicColor = true,
                activePluginCount = 1,
                pluginUpdateCount = 0,
                pluginFolderName = "Download/Kanime",
                autoPlayNext = true,
                animeUpscaleEnabled = false,
                animeUpscaleQuality = AnimeUpscaleQuality.AUTO,
                lineSharpeningEnabled = false,
                animeSharpenStrength = AnimeSharpenStrength.BALANCED,
                tmdbApiKey = "",
                onThemeModeChange = {},
                onDynamicColorChange = {},
                onOpenPlugins = {},
                onChoosePluginFolder = {},
                onOpenSetup = {},
                onAutoPlayNextChange = {},
                onUpscaleEnabledChange = {},
                onUpscaleQualityChange = {},
                onLineSharpeningChange = {},
                onSharpenStrengthChange = {},
                onSaveTmdbKey = {},
                onClearCache = {}
            )
        }
    }
}
