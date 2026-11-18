package com.kanime.app.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kanime.app.R
import com.kanime.app.data.plugin.AnimeSourcePlugin
import com.kanime.app.data.plugin.CatalogUpdateInfo
import com.kanime.app.data.plugin.PluginManager
import com.kanime.app.ui.components.GlassButton
import com.kanime.app.ui.components.GlassButtonStyle
import com.kanime.app.ui.components.GlassChip
import com.kanime.app.ui.components.GlassSheet
import com.kanime.app.ui.components.GlassTopBar
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeAmbientBackdrop
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.PillShape
import kotlinx.coroutines.launch

@Composable
fun PluginsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val plugins by PluginManager.plugins.collectAsState()
    val configVersion by PluginManager.pluginConfigVersionFlow.collectAsState()

    var catalogUpdateInfo by remember { mutableStateOf<CatalogUpdateInfo?>(null) }
    var isCheckingCatalog by remember { mutableStateOf(false) }
    var downloadingPluginId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showAddJsonDialog by remember { mutableStateOf(false) }
    var jsonInputText by remember { mutableStateOf("") }
    var pluginToUpdate by remember { mutableStateOf<AnimeSourcePlugin?>(null) }
    var preferredAudio by remember { mutableStateOf("Sub") }
    var preferredServer by remember { mutableStateOf("MegaCloud") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PluginManager.checkPluginStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        PluginManager.checkPluginStatus(context)
        PluginManager.checkForCatalogUpdate(context).onSuccess { info ->
            if (info.hasCatalogTemplateUpdate) {
                catalogUpdateInfo = info
            }
        }
    }

    PluginsScreenContent(
        plugins = plugins,
        configVersion = configVersion,
        isCheckingCatalog = isCheckingCatalog,
        downloadingPluginId = downloadingPluginId,
        downloadProgress = downloadProgress,
        onBackClick = onBackClick,
        onShowReleaseNotes = {
            catalogUpdateInfo = CatalogUpdateInfo(
                hasUpdate = false,
                currentVersion = configVersion,
                newVersion = configVersion,
                releaseNotes = PluginManager.catalogReleaseNotes,
                remoteJson = "",
                plugins = plugins
            )
        },
        onCheckUpdates = {
            coroutineScope.launch {
                isCheckingCatalog = true
                val res = PluginManager.checkForCatalogUpdate(context)
                isCheckingCatalog = false
                res.onSuccess { info ->
                    when {
                        info.hasCatalogTemplateUpdate -> catalogUpdateInfo = info
                        info.hasPluginUpdates -> {
                            val summary = info.updatedPlugins.joinToString(", ") { "${it.name} v${it.version}" }
                            Toast.makeText(context, "Update: $summary", Toast.LENGTH_LONG).show()
                        }
                        info.metadataUpdatedCount > 0 -> {
                            Toast.makeText(context, "Synced", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(context, "Up to date (v${info.currentVersion})", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Update check failed", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onAddJsonClick = { showAddJsonDialog = true },
        onTogglePlugin = { plugin, enabled ->
            PluginManager.setPluginEnabled(context, plugin.id, enabled)
        },
        onDownloadPlugin = { plugin ->
            coroutineScope.launch {
                try {
                    downloadingPluginId = plugin.id
                    downloadProgress = 0f
                    PluginManager.downloadPluginApk(context, plugin) { progress ->
                        downloadProgress = progress
                    }
                } finally {
                    downloadingPluginId = null
                }
            }
        },
        onInstallPlugin = { PluginManager.installPluginApk(context, it) },
        onUninstallPlugin = { PluginManager.uninstallPlugin(context, it) },
        onUpdateDetailsClick = { pluginToUpdate = it },
        onConfigureClick = { showConfigDialog = true }
    )

    // Plugin Update / Reinstall Dialog
    pluginToUpdate?.let { target ->
        AlertDialog(
            onDismissRequest = { pluginToUpdate = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = if (target.hasUpdate) stringResource(R.string.plugins_update_available) else target.name
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)) {
                    Text(
                        text = "v${target.installedVersion ?: target.version} → v${target.version} · ${target.apkSize}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = target.changelog ?: "Stability and extractor improvements.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val p = target
                    pluginToUpdate = null
                    coroutineScope.launch {
                        try {
                            downloadingPluginId = p.id
                            downloadProgress = 0f
                            PluginManager.downloadPluginApk(context, p) { progress ->
                                downloadProgress = progress
                            }
                        } finally {
                            downloadingPluginId = null
                        }
                    }
                }) {
                    Text(stringResource(if (target.hasUpdate) R.string.plugins_update_now else R.string.plugins_reinstall))
                }
            },
            dismissButton = {
                TextButton(onClick = { pluginToUpdate = null }) {
                    Text(stringResource(R.string.action_later))
                }
            }
        )
    }

    // Add JSON Dialog
    if (showAddJsonDialog) {
        AlertDialog(
            onDismissRequest = { showAddJsonDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.plugins_add_json)) },
            text = {
                OutlinedTextField(
                    value = jsonInputText,
                    onValueChange = { jsonInputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    placeholder = { Text("{\"id\":\"...\",\"name\":\"...\"}") },
                    shape = MaterialTheme.shapes.small
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (jsonInputText.isNotBlank()) {
                        val res = PluginManager.addPluginFromJson(context, jsonInputText)
                        if (res.isSuccess) {
                            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            showAddJsonDialog = false
                            jsonInputText = ""
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Invalid JSON", Toast.LENGTH_LONG).show()
                        }
                    }
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddJsonDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Plugin Config Dialog
    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.plugins_configure)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)) {
                    Text(stringResource(R.string.player_audio), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)) {
                        listOf("Sub", "Dub").forEach { audio ->
                            GlassChip(
                                label = audio,
                                selected = preferredAudio == audio,
                                onClick = { preferredAudio = audio }
                            )
                        }
                    }
                    Text(stringResource(R.string.player_servers), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)) {
                        listOf("MegaCloud", "Vidstream").forEach { srv ->
                            GlassChip(
                                label = srv,
                                selected = preferredServer == srv,
                                onClick = { preferredServer = srv }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showConfigDialog = false }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }

    // Catalog Update / Release Notes Glass Sheet
    GlassSheet(
        visible = catalogUpdateInfo != null,
        onDismiss = { catalogUpdateInfo = null },
        maxHeightFraction = 0.72f
    ) {
        val info = catalogUpdateInfo ?: return@GlassSheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KanimeSpacing.lg)
                .padding(bottom = KanimeSpacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.plugins_release_notes),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "v${info.newVersion}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { catalogUpdateInfo = null }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = info.releaseNotes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(KanimeSpacing.lg)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
            ) {
                if (info.hasCatalogTemplateUpdate) {
                    GlassButton(
                        text = stringResource(R.string.plugins_apply_template),
                        icon = Icons.Default.SystemUpdate,
                        style = GlassButtonStyle.Primary,
                        onClick = {
                            PluginManager.applyCatalogUpdate(context, info)
                            catalogUpdateInfo = null
                            Toast.makeText(context, "Updated to v${info.newVersion}", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = stringResource(R.string.action_later),
                        style = GlassButtonStyle.Glass,
                        onClick = { catalogUpdateInfo = null }
                    )
                } else {
                    GlassButton(
                        text = stringResource(R.string.action_done),
                        style = GlassButtonStyle.Primary,
                        onClick = { catalogUpdateInfo = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginsScreenContent(
    plugins: List<AnimeSourcePlugin>,
    configVersion: Int,
    isCheckingCatalog: Boolean,
    downloadingPluginId: String?,
    downloadProgress: Float,
    onBackClick: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    onCheckUpdates: () -> Unit,
    onAddJsonClick: () -> Unit,
    onTogglePlugin: (AnimeSourcePlugin, Boolean) -> Unit,
    onDownloadPlugin: (AnimeSourcePlugin) -> Unit,
    onInstallPlugin: (AnimeSourcePlugin) -> Unit,
    onUninstallPlugin: (AnimeSourcePlugin) -> Unit,
    onUpdateDetailsClick: (AnimeSourcePlugin) -> Unit,
    onConfigureClick: () -> Unit
) {
    val updates = remember(plugins) { plugins.filter { it.hasUpdate } }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .padding(horizontal = KanimeSpacing.lg),
                contentPadding = PaddingValues(top = 76.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
            ) {
                if (updates.isNotEmpty()) {
                    item(key = "updates_banner") {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(KanimeSpacing.lg),
                                verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                            ) {
                                Text(
                                    text = stringResource(R.string.plugins_updates_available, updates.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                updates.forEach { p ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${p.name} · v${p.version}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = { onUpdateDetailsClick(p) },
                                            shape = PillShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.tertiary,
                                                contentColor = MaterialTheme.colorScheme.onTertiary
                                            )
                                        ) {
                                            Text(stringResource(R.string.action_update))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                items(plugins, key = { it.id }) { plugin ->
                    PluginCard(
                        plugin = plugin,
                        isDownloading = downloadingPluginId == plugin.id,
                        anyDownloading = downloadingPluginId != null,
                        downloadProgress = downloadProgress,
                        onToggle = { onTogglePlugin(plugin, it) },
                        onDownload = { onDownloadPlugin(plugin) },
                        onInstall = { onInstallPlugin(plugin) },
                        onUninstall = { onUninstallPlugin(plugin) },
                        onUpdateDetails = { onUpdateDetailsClick(plugin) },
                        onConfigure = onConfigureClick
                    )
                }
            }
        }

        GlassTopBar(
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable(onClick = onShowReleaseNotes)
                ) {
                    Text(
                        text = stringResource(R.string.plugins_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = PillShape
                    ) {
                        Text(
                            text = "v$configVersion",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = onCheckUpdates, enabled = !isCheckingCatalog) {
                    if (isCheckingCatalog) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.plugins_check_updates),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(onClick = onAddJsonClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.plugins_add_json),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginCard(
    plugin: AnimeSourcePlugin,
    isDownloading: Boolean,
    anyDownloading: Boolean,
    downloadProgress: Float,
    onToggle: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onUpdateDetails: () -> Unit,
    onConfigure: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(KanimeSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (plugin.isInstalled && plugin.isEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (plugin.isInstalled && plugin.isEnabled) Icons.Default.PlayCircle else Icons.Default.Extension,
                                contentDescription = null,
                                tint = if (plugin.isInstalled && plugin.isEnabled) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(KanimeSpacing.md))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = plugin.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val statusBg = when {
                                plugin.hasUpdate -> MaterialTheme.colorScheme.tertiaryContainer
                                plugin.isInstalled && plugin.isEnabled -> MaterialTheme.colorScheme.primaryContainer
                                plugin.isInstalled -> MaterialTheme.colorScheme.surfaceContainerHighest
                                plugin.isApkDownloaded -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.errorContainer
                            }
                            val statusFg = when {
                                plugin.hasUpdate -> MaterialTheme.colorScheme.onTertiaryContainer
                                plugin.isInstalled && plugin.isEnabled -> MaterialTheme.colorScheme.onPrimaryContainer
                                plugin.isInstalled -> MaterialTheme.colorScheme.onSurfaceVariant
                                plugin.isApkDownloaded -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onErrorContainer
                            }
                            val statusText = when {
                                plugin.hasUpdate -> stringResource(R.string.plugins_update_available)
                                plugin.isInstalled && plugin.isEnabled -> stringResource(R.string.plugins_active)
                                plugin.isInstalled -> stringResource(R.string.plugins_disabled)
                                plugin.isApkDownloaded -> stringResource(R.string.plugins_ready)
                                else -> stringResource(R.string.plugins_not_installed)
                            }
                            Surface(color = statusBg, shape = PillShape) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusFg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "v${plugin.version} · ${plugin.apkSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = plugin.isInstalled && plugin.isEnabled,
                    onCheckedChange = onToggle,
                    enabled = plugin.isInstalled,
                    thumbContent = if (plugin.isInstalled) {
                        {
                            Icon(
                                imageVector = if (plugin.isEnabled) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }

            if (plugin.capabilities.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    plugin.capabilities.forEach { cap ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = PillShape
                        ) {
                            Text(
                                text = cap,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
            ) {
                when {
                    plugin.hasUpdate -> {
                        Button(
                            onClick = onUpdateDetails,
                            shape = PillShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_update))
                        }
                        OutlinedButton(
                            onClick = onUninstall,
                            shape = PillShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_uninstall))
                        }
                    }

                    plugin.isInstalled -> {
                        FilledTonalButton(
                            onClick = onConfigure,
                            shape = PillShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.plugins_configure))
                        }
                        OutlinedButton(
                            onClick = onUninstall,
                            shape = PillShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_uninstall))
                        }
                    }

                    plugin.isApkDownloaded -> {
                        Button(
                            onClick = onInstall,
                            shape = PillShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_install))
                        }
                        OutlinedButton(
                            onClick = onUninstall,
                            shape = PillShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.action_delete))
                        }
                    }

                    else -> {
                        Button(
                            onClick = onDownload,
                            enabled = !anyDownloading,
                            shape = PillShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(if (isDownloading) R.string.plugins_downloading else R.string.action_download))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PillShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Preview(name = "Plugins Light Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Plugins Dark Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Preview(name = "Plugins Light Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.TABLET)
@Composable
private fun PluginsScreenPreview() {
    val samplePlugins = listOf(
        AnimeSourcePlugin(
            id = "kanikoto",
            name = "KAnikoto",
            packageName = "com.kanime.plugin.kanikoto",
            providerAuthority = "com.kanime.plugin.kanikoto.provider",
            version = "1.4.0",
            author = "Kanime",
            description = "Multi-node anime streaming source.",
            sourceUrl = "https://anikoto.to",
            apkFileName = "kanikoto.apk",
            capabilities = listOf("HLS", "Sub", "Dub"),
            isInstalled = true,
            isEnabled = true,
            apkSize = "3.4 MB"
        )
    )
    KAnimeTheme(animateTransition = false) {
        KanimeAmbientBackdrop {
            PluginsScreenContent(
                plugins = samplePlugins,
                configVersion = 4,
                isCheckingCatalog = false,
                downloadingPluginId = null,
                downloadProgress = 0f,
                onBackClick = {},
                onShowReleaseNotes = {},
                onCheckUpdates = {},
                onAddJsonClick = {},
                onTogglePlugin = { _, _ -> },
                onDownloadPlugin = {},
                onInstallPlugin = {},
                onUninstallPlugin = {},
                onUpdateDetailsClick = {},
                onConfigureClick = {}
            )
        }
    }
}
