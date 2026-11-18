package com.kanime.app.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Subtitles
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanime.app.R
import com.kanime.app.data.plugin.AnimeSourcePlugin
import com.kanime.app.data.plugin.CatalogUpdateInfo
import com.kanime.app.data.plugin.PluginManager
import com.kanime.app.ui.components.GlassButton
import com.kanime.app.ui.components.GlassButtonStyle
import com.kanime.app.ui.components.GlassChip
import com.kanime.app.ui.components.GlassSheet
import com.kanime.app.ui.components.GlassSurface
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
    val snackbarHostState = remember { SnackbarHostState() }

    val plugins by PluginManager.plugins.collectAsStateWithLifecycle()
    val configVersion by PluginManager.pluginConfigVersionFlow.collectAsStateWithLifecycle()
    val lastFetchedCatalog by PluginManager.lastFetchedCatalogFlow.collectAsStateWithLifecycle()
    val dismissedVersion by PluginManager.dismissedCatalogVersionFlow.collectAsStateWithLifecycle()

    var whatsNewInfo by remember { mutableStateOf<CatalogUpdateInfo?>(null) }
    var isCheckingCatalog by remember { mutableStateOf(false) }
    var downloadingPluginId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var pendingDownloadPlugin by remember { mutableStateOf<AnimeSourcePlugin?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showAddJsonDialog by remember { mutableStateOf(false) }
    var jsonInputText by remember { mutableStateOf("") }
    var pluginToUpdate by remember { mutableStateOf<AnimeSourcePlugin?>(null) }
    var preferredAudio by remember { mutableStateOf("Sub") }
    var preferredServer by remember { mutableStateOf("MegaCloud") }

    val hasPendingCatalogBadge = remember(lastFetchedCatalog, configVersion) {
        val fetched = lastFetchedCatalog
        fetched != null && (fetched.catalogUpdateAvailable || fetched.newVersion > configVersion || fetched.pluginUpdatesAvailable)
    }

    fun showMessage(msg: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun executePluginDownload(plugin: AnimeSourcePlugin) {
        coroutineScope.launch {
            try {
                downloadingPluginId = plugin.id
                downloadProgress = 0f
                val res = PluginManager.downloadPluginApk(context, plugin) { progress ->
                    downloadProgress = progress
                }
                if (res.isFailure) {
                    showMessage(context.getString(R.string.plugins_msg_download_failed))
                }
            } finally {
                downloadingPluginId = null
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val queued = pendingDownloadPlugin
        pendingDownloadPlugin = null
        if (treeUri != null) {
            PluginManager.setPluginFolderUri(context, treeUri)
            if (queued != null) {
                executePluginDownload(queued)
            }
        }
    }

    fun startPluginDownload(plugin: AnimeSourcePlugin) {
        if (PluginManager.getValidPluginFolderUri(context) == null) {
            pendingDownloadPlugin = plugin
            showMessage(context.getString(R.string.plugins_msg_choose_folder))
            folderPickerLauncher.launch(null)
            return
        }
        executePluginDownload(plugin)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    PluginManager.checkPluginStatusAsync(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Throttled background check (skips if checked < 15 min ago; respects dismissed version to prevent nag loops)
    LaunchedEffect(Unit) {
        PluginManager.checkPluginStatusAsync(context)
        if (PluginManager.shouldAutoCheckCatalog(context) && isNetworkAvailable(context)) {
            PluginManager.checkForCatalogUpdate(context, force = false).onSuccess { info ->
                val shouldAutoOpenCatalog = info.catalogUpdateAvailable &&
                    !PluginManager.isCatalogUpdateDismissed(context, info.newVersion)
                if (shouldAutoOpenCatalog) {
                    whatsNewInfo = info
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PluginsScreenContent(
            plugins = plugins,
            configVersion = configVersion,
            hasPendingUpdateBadge = hasPendingCatalogBadge && dismissedVersion > 0 || hasPendingCatalogBadge,
            isCheckingCatalog = isCheckingCatalog,
            downloadingPluginId = downloadingPluginId,
            downloadProgress = downloadProgress,
            onBackClick = onBackClick,
            onShowReleaseNotes = {
                whatsNewInfo = PluginManager.getReleaseNotesSnapshot(context)
            },
            onCheckUpdates = {
                coroutineScope.launch {
                    if (!isNetworkAvailable(context)) {
                        showMessage(context.getString(R.string.plugins_msg_offline))
                        return@launch
                    }
                    isCheckingCatalog = true
                    val res = PluginManager.checkForCatalogUpdate(context, force = true)
                    isCheckingCatalog = false
                    res.onSuccess { info ->
                        if (info.hasAnyUpdate) {
                            whatsNewInfo = info
                        } else {
                            showMessage(context.getString(R.string.plugins_msg_up_to_date, info.currentVersion))
                        }
                    }.onFailure {
                        showMessage(context.getString(R.string.plugins_msg_check_failed))
                    }
                }
            },
            onAddJsonClick = { showAddJsonDialog = true },
            onTogglePlugin = { plugin, enabled ->
                PluginManager.setPluginEnabled(context, plugin.id, enabled)
            },
            onDownloadPlugin = { startPluginDownload(it) },
            onInstallPlugin = { PluginManager.installPluginApk(context, it) },
            onUninstallPlugin = { PluginManager.uninstallPlugin(context, it) },
            onUpdateDetailsClick = { pluginToUpdate = it },
            onConfigureClick = { showConfigDialog = true }
        )

        // Floating liquid glass SnackbarHost
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(KanimeSpacing.lg)
        ) { data ->
            GlassSurface(
                shape = PillShape,
                modifier = Modifier.widthIn(max = 460.dp)
            ) {
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
        }
    }

    // Plugin Update / Reinstall Dialog
    pluginToUpdate?.let { target ->
        val notes = target.resolvedChangelogItems
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
                        text = buildString {
                            append(
                                stringResource(
                                    R.string.plugins_version_upgrade,
                                    target.installedVersion ?: target.version,
                                    target.version
                                )
                            )
                            if (target.apkSize.isNotBlank()) {
                                append(" · ")
                                append(target.apkSize)
                            }
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (notes.isNotEmpty()) {
                        BulletNotesList(notes = notes)
                    } else {
                        Text(
                            text = stringResource(R.string.plugins_no_notes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val p = target
                    pluginToUpdate = null
                    startPluginDownload(p)
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
                            showMessage(context.getString(R.string.plugins_msg_saved))
                            showAddJsonDialog = false
                            jsonInputText = ""
                        } else {
                            showMessage(context.getString(R.string.plugins_msg_invalid_json))
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

    // Unified "What's new" Glass Sheet (Catalog + Plugin updates & changelogs)
    GlassSheet(
        visible = whatsNewInfo != null,
        onDismiss = {
            whatsNewInfo?.let { info ->
                if (info.catalogUpdateAvailable) {
                    PluginManager.dismissCatalogUpdate(context, info.newVersion)
                }
            }
            whatsNewInfo = null
        },
        maxHeightFraction = 0.78f
    ) {
        val info = whatsNewInfo ?: return@GlassSheet
        WhatsNewSheetBody(
            info = info,
            downloadingPluginId = downloadingPluginId,
            onDismissLater = {
                if (info.catalogUpdateAvailable) {
                    PluginManager.dismissCatalogUpdate(context, info.newVersion)
                }
                whatsNewInfo = null
            },
            onApplyCatalog = {
                PluginManager.applyCatalogUpdate(context, info)
                whatsNewInfo = null
                showMessage(context.getString(R.string.plugins_msg_catalog_applied, info.newVersion))
            },
            onUpdatePlugin = { plugin ->
                whatsNewInfo = null
                startPluginDownload(plugin)
            }
        )
    }
}

@Composable
internal fun WhatsNewSheetBody(
    info: CatalogUpdateInfo,
    downloadingPluginId: String?,
    onDismissLater: () -> Unit,
    onApplyCatalog: () -> Unit,
    onUpdatePlugin: (AnimeSourcePlugin) -> Unit
) {
    val showCatalogSection = info.catalogUpdateAvailable || info.releaseNotes.isNotEmpty()
    val pluginsForSheet = remember(info.updatedPlugins, info.plugins) {
        if (info.updatedPlugins.isNotEmpty()) {
            info.updatedPlugins
        } else {
            info.plugins.filter { it.resolvedChangelogItems.isNotEmpty() }
        }
    }
    val showPluginsSection = pluginsForSheet.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KanimeSpacing.lg)
            .padding(bottom = KanimeSpacing.xl)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
    ) {
        // Header: clear "What's new" + explicit "Catalog vN" label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.plugins_release_notes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val catalogSub = buildString {
                    append(
                        if (info.catalogUpdateAvailable && info.newVersion > info.currentVersion) {
                            stringResource(R.string.plugins_catalog_upgrade, info.currentVersion, info.newVersion)
                        } else {
                            stringResource(R.string.plugins_catalog_label, info.newVersion)
                        }
                    )
                    if (!info.releaseDate.isNullOrBlank()) {
                        append(" · ")
                        append(info.releaseDate)
                    }
                }
                Text(
                    text = catalogSub,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDismissLater) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
            }
        }

        // Section (a): Catalog notes & Apply CTA
        if (showCatalogSection) {
            Text(
                text = stringResource(R.string.plugins_section_catalog),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(KanimeSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                ) {
                    if (info.releaseNotes.isNotEmpty()) {
                        BulletNotesList(notes = info.releaseNotes)
                    } else {
                        Text(
                            text = stringResource(R.string.plugins_no_notes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (info.catalogUpdateAvailable) {
                        GlassButton(
                            text = stringResource(R.string.plugins_apply_template),
                            icon = Icons.Default.SystemUpdate,
                            style = GlassButtonStyle.Primary,
                            onClick = onApplyCatalog,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Section (b): Plugin updates / changelogs
        if (showPluginsSection) {
            Text(
                text = stringResource(R.string.plugins_section_plugins),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            pluginsForSheet.forEach { plugin ->
                val notes = plugin.resolvedChangelogItems
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(KanimeSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plugin.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val verText = buildString {
                                    if (plugin.hasUpdate && !plugin.installedVersion.isNullOrBlank()) {
                                        append(
                                            stringResource(
                                                R.string.plugins_version_upgrade,
                                                plugin.installedVersion,
                                                plugin.version
                                            )
                                        )
                                    } else {
                                        append("v${plugin.version}")
                                    }
                                    if (plugin.apkSize.isNotBlank()) {
                                        append(" · ")
                                        append(plugin.apkSize)
                                    }
                                }
                                Text(
                                    text = verText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (plugin.hasUpdate) {
                                Button(
                                    onClick = { onUpdatePlugin(plugin) },
                                    enabled = downloadingPluginId == null,
                                    shape = PillShape
                                ) {
                                    Text(stringResource(R.string.action_update))
                                }
                            }
                        }

                        if (notes.isNotEmpty()) {
                            BulletNotesList(notes = notes)
                        }
                    }
                }
            }
        }

        if (!showCatalogSection && !showPluginsSection) {
            Text(
                text = stringResource(R.string.plugins_no_notes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        GlassButton(
            text = stringResource(if (info.hasAnyUpdate) R.string.action_later else R.string.action_done),
            style = if (info.hasAnyUpdate) GlassButtonStyle.Glass else GlassButtonStyle.Primary,
            onClick = onDismissLater,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BulletNotesList(notes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        notes.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginsScreenContent(
    plugins: List<AnimeSourcePlugin>,
    configVersion: Int,
    hasPendingUpdateBadge: Boolean,
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
                contentPadding = PaddingValues(top = 76.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
            ) {
                if (updates.isNotEmpty()) {
                    item(key = "updates_banner", contentType = "banner") {
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
                                    text = pluralStringResource(R.plurals.plugins_updates_count, updates.size, updates.size),
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

                items(plugins, key = { it.id }, contentType = { "plugin_card" }) { plugin ->
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
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (hasPendingUpdateBadge) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Text(
                                text = stringResource(R.string.plugins_catalog_label, configVersion),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
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
                                imageVector = when {
                                    plugin.isSubtitlePlugin -> Icons.Default.Subtitles
                                    plugin.isInstalled && plugin.isEnabled -> Icons.Default.PlayCircle
                                    else -> Icons.Default.Extension
                                },
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
                        val meta = buildList {
                            add("v${plugin.version}")
                            if (plugin.apkSize.isNotBlank()) add(plugin.apkSize)
                        }.joinToString(" · ")
                        Text(
                            text = meta,
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
        PluginManager.defaultKanikotoPlugin.copy(isInstalled = true, isEnabled = true)
    )
    KAnimeTheme(animateTransition = false) {
        KanimeAmbientBackdrop {
            PluginsScreenContent(
                plugins = samplePlugins,
                configVersion = 1,
                hasPendingUpdateBadge = true,
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
