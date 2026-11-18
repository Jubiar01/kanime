package com.kanime.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kanime.app.data.plugin.AnimeSourcePlugin
import com.kanime.app.data.plugin.CatalogUpdateInfo
import com.kanime.app.data.plugin.PluginManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    // Automatically re-check plugin install/uninstall status whenever screen resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PluginManager.checkPluginStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Sync plugin install status and check online updates (Catalog Template + Plugin Updates) on launch
    LaunchedEffect(Unit) {
        PluginManager.checkPluginStatus(context)
        val res = PluginManager.checkForCatalogUpdate(context)
        res.onSuccess { info ->
            if (info.hasCatalogTemplateUpdate) {
                catalogUpdateInfo = info
            }
        }
    }

    val pluginUpdatesAvailable = remember(plugins) { plugins.filter { it.hasUpdate } }

    var downloadingPluginId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showAddJsonDialog by remember { mutableStateOf(false) }
    var jsonInputText by remember { mutableStateOf("") }
    var pluginToUpdate by remember { mutableStateOf<AnimeSourcePlugin?>(null) }

    // Plugin Configuration state
    var preferredAudio by remember { mutableStateOf("Sub") }
    var preferredServer by remember { mutableStateOf("MegaCloud") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Source Plugins", fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                catalogUpdateInfo = CatalogUpdateInfo(
                                    hasUpdate = false,
                                    currentVersion = configVersion,
                                    newVersion = configVersion,
                                    releaseNotes = PluginManager.catalogReleaseNotes,
                                    remoteJson = "",
                                    plugins = plugins
                                )
                            }
                        ) {
                            Text(
                                text = if (pluginUpdatesAvailable.isNotEmpty()) {
                                    "Template v$configVersion • ${pluginUpdatesAvailable.size} plugin update${if (pluginUpdatesAvailable.size > 1) "s" else ""}"
                                } else {
                                    "Template v$configVersion • Online Delivery"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pluginUpdatesAvailable.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Release Notes",
                                tint = if (pluginUpdatesAvailable.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Check for online updates: both Catalog Template (new plugins) & Plugin Updates (new APK versions)
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isCheckingCatalog = true
                                Toast.makeText(context, "Checking catalog template & plugin updates...", Toast.LENGTH_SHORT).show()
                                val res = PluginManager.checkForCatalogUpdate(context)
                                isCheckingCatalog = false
                                res.onSuccess { info ->
                                    when {
                                        info.hasCatalogTemplateUpdate -> {
                                            catalogUpdateInfo = info
                                        }
                                        info.hasPluginUpdates -> {
                                            val summary = info.updatedPlugins.joinToString(", ") {
                                                "${it.name} v${it.version}"
                                            }
                                            Toast.makeText(
                                                context,
                                                "Plugin update available: $summary",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        info.metadataUpdatedCount > 0 -> {
                                            Toast.makeText(
                                                context,
                                                "Synced latest plugin info (${info.metadataUpdatedCount} updated).",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        else -> {
                                            Toast.makeText(
                                                context,
                                                "Catalog template (v${info.currentVersion}) and all plugins are up to date.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }.onFailure { err ->
                                    Toast.makeText(context, "Update check failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isCheckingCatalog
                    ) {
                        if (isCheckingCatalog) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Check for Updates")
                        }
                    }

                    // Add plugin from JSON
                    IconButton(onClick = { showAddJsonDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Plugin JSON")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dedicated Plugin Updates Banner (separate from Catalog Template updates)
            if (pluginUpdatesAvailable.isNotEmpty()) {
                item(key = "plugin_updates_banner") {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "${pluginUpdatesAvailable.size} Plugin Update${if (pluginUpdatesAvailable.size > 1) "s" else ""} Available",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            pluginUpdatesAvailable.forEach { updatable ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = updatable.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "v${updatable.installedVersion ?: "?"} ➜ v${updatable.version} • ${updatable.apkSize}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { pluginToUpdate = updatable },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                            contentColor = MaterialTheme.colorScheme.onTertiary
                                        )
                                    ) {
                                        Text("Update", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(plugins, key = { it.id }) { plugin ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Title row with Material 3 Switch
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
                                    color = when {
                                        plugin.isInstalled && plugin.isEnabled -> MaterialTheme.colorScheme.primary
                                        plugin.isInstalled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (plugin.isInstalled && plugin.isEnabled) Icons.Default.PlayCircle else Icons.Default.Extension,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = plugin.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = if (plugin.hasUpdate) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (plugin.hasUpdate && plugin.installedVersion != null) {
                                                    "v${plugin.installedVersion} ➜ v${plugin.version}"
                                                } else {
                                                    "v${plugin.version}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (plugin.hasUpdate) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${plugin.packageName} • ${plugin.apkSize}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Material 3 Switch to toggle plugin ON/OFF
                            Switch(
                                checked = plugin.isInstalled && plugin.isEnabled,
                                onCheckedChange = { enabled ->
                                    PluginManager.setPluginEnabled(context, plugin.id, enabled)
                                    Toast.makeText(
                                        context,
                                        if (enabled) "${plugin.name} turned ON" else "${plugin.name} turned OFF",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
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

                        // Live Status Badge Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = when {
                                    plugin.hasUpdate -> MaterialTheme.colorScheme.tertiaryContainer
                                    plugin.isInstalled && plugin.isEnabled -> Color(0xFF4CAF50).copy(alpha = 0.18f)
                                    plugin.isInstalled && !plugin.isEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                                    plugin.isApkDownloaded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    when {
                                        plugin.hasUpdate -> {
                                            Icon(
                                                Icons.Default.NewReleases,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "UPDATE AVAILABLE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        plugin.isInstalled && plugin.isEnabled -> {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "INSTALLED • ACTIVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF4CAF50),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        plugin.isInstalled && !plugin.isEnabled -> {
                                            Icon(
                                                Icons.Default.PauseCircleOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "INSTALLED • DISABLED",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        else -> {
                                            Text(
                                                text = if (plugin.isApkDownloaded) "READY TO INSTALL" else "NOT INSTALLED",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (plugin.isApkDownloaded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            if (plugin.isInstalled) {
                                Text(
                                    text = when {
                                        plugin.hasUpdate -> "v${plugin.installedVersion ?: "?"} ➜ v${plugin.version}"
                                        plugin.isEnabled -> "Enabled for streaming"
                                        else -> "Turned off"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        plugin.hasUpdate -> MaterialTheme.colorScheme.tertiary
                                        plugin.isEnabled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Description
                        Text(
                            text = plugin.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                        )

                        // Capabilities FlowRow
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            plugin.capabilities.forEach { cap ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = cap,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Dynamic Action Buttons with UPDATE & UNINSTALL support
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when {
                                    // Case 1: Plugin has an online update available
                                    plugin.hasUpdate -> {
                                        Button(
                                            onClick = { pluginToUpdate = plugin },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.tertiary
                                            )
                                        ) {
                                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Update v${plugin.version}")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                PluginManager.uninstallPlugin(context, plugin)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Uninstall")
                                        }
                                    }

                                    // Case 2: Plugin is installed and up-to-date
                                    plugin.isInstalled -> {
                                        FilledTonalButton(
                                            onClick = { pluginToUpdate = plugin },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Installed (v${plugin.installedVersion ?: plugin.version})")
                                        }

                                        // Uninstall button (Package & APK)
                                        OutlinedButton(
                                            onClick = {
                                                PluginManager.uninstallPlugin(context, plugin)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Uninstall")
                                        }
                                    }

                                    // Case 3: APK is downloaded in storage, ready to install
                                    plugin.isApkDownloaded -> {
                                        Button(
                                            onClick = {
                                                PluginManager.installPluginApk(context, plugin)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Install Plugin")
                                        }

                                        // Delete downloaded APK
                                        OutlinedButton(
                                            onClick = {
                                                PluginManager.uninstallPlugin(context, plugin)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Delete APK")
                                        }
                                    }

                                    // Case 4: Not installed, download purely online
                                    else -> {
                                        Button(
                                            onClick = {
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
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = downloadingPluginId == null
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (downloadingPluginId == plugin.id) "Downloading..." else "Download APK")
                                        }
                                    }
                                }
                            }

                            // Configure Button row
                            OutlinedButton(
                                onClick = { showConfigDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Configure Plugin")
                            }
                        }

                        // Download Progress bar
                        AnimatedVisibility(visible = downloadingPluginId == plugin.id) {
                            Column {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Downloading ${plugin.apkFileName} (${(downloadProgress * 100).toInt()}%)...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PLUGIN UPDATE & DETAILS DIALOG
    // ══════════════════════════════════════════════════════════════
    if (pluginToUpdate != null) {
        val targetPlugin = pluginToUpdate!!
        AlertDialog(
            onDismissRequest = { pluginToUpdate = null },
            icon = {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (targetPlugin.hasUpdate) "Update Available" else "Plugin Details & Reinstall",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(targetPlugin.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Installed: v${targetPlugin.installedVersion ?: targetPlugin.version} ➜ Available: v${targetPlugin.version}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Text("What's New in v${targetPlugin.version}:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = targetPlugin.changelog ?: "• Latest streaming stability updates and decryption optimizations.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Text(
                        text = "File: ${targetPlugin.apkFileName} (${targetPlugin.apkSize})\nSource: ${targetPlugin.apkDownloadUrl ?: "Online"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = targetPlugin
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
                    }
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (targetPlugin.hasUpdate) "Update Now" else "Re-download & Install")
                }
            },
            dismissButton = {
                TextButton(onClick = { pluginToUpdate = null }) {
                    Text("Later")
                }
            }
        )
    }

    // Add / Edit Plugin JSON Dialog
    if (showAddJsonDialog) {
        AlertDialog(
            onDismissRequest = { showAddJsonDialog = false },
            title = { Text("Add / Edit Plugin (.json)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste plugin JSON configuration below to dynamically register or update an online library plugin:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = jsonInputText,
                        onValueChange = { jsonInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = {
                            Text(
                                "{\n  \"id\": \"kanikoto\",\n  \"name\": \"KAnikoto Extractor\",\n  \"packageName\": \"com.kanime.plugin.kanikoto\",\n  \"version\": \"1.2.0\",\n  \"apkDownloadUrl\": \"https://...\"\n}",
                                fontSize = 11.sp
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (jsonInputText.isNotBlank()) {
                            val res = PluginManager.addPluginFromJson(context, jsonInputText)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Plugin '${res.getOrNull()?.name}' saved successfully!", Toast.LENGTH_SHORT).show()
                                showAddJsonDialog = false
                                jsonInputText = ""
                            } else {
                                Toast.makeText(context, "JSON error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Save Plugin")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddJsonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Plugin Configuration Dialog
    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("KAnikoto Plugin Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Preferred Audio Track", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Sub", "Dub").forEach { audio ->
                            FilterChip(
                                selected = preferredAudio == audio,
                                onClick = { preferredAudio = audio },
                                label = { Text(audio) }
                            )
                        }
                    }

                    Text("Preferred Video Server", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("MegaCloud", "Vidstream", "Kiwi CDN").forEach { server ->
                            FilterChip(
                                selected = preferredServer == server,
                                onClick = { preferredServer = server },
                                label = { Text(server) }
                            )
                        }
                    }

                    Text("Active Source: KAnikoto Multi-Node", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Save & Close")
                }
            }
        )
    }

    // ══════════════════════════════════════════════════════════════
    // CATALOG UPDATE & RELEASE NOTES BOTTOM SHEET
    // ══════════════════════════════════════════════════════════════
    if (catalogUpdateInfo != null) {
        val info = catalogUpdateInfo!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { catalogUpdateInfo = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with icon and version status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = if (info.hasUpdate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (info.hasUpdate) Icons.Default.NewReleases else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (info.hasUpdate) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (info.hasCatalogTemplateUpdate) "Catalog Template Update" else "Catalog Template Info",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    info.newVersion > info.currentVersion -> "Template v${info.currentVersion} ➜ Template v${info.newVersion}"
                                    info.newPlugins.isNotEmpty() -> "${info.newPlugins.size} new plugin${if (info.newPlugins.size > 1) "s" else ""} added • Template v${info.newVersion}"
                                    else -> "Current Template v${info.currentVersion}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        color = if (info.hasUpdate) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (info.hasUpdate) "UPDATE" else "CURRENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (info.hasUpdate) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Release Notes Section
                Text(
                    text = "Release Notes & Changelog",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "What's New in Config v${info.newVersion}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = info.releaseNotes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f),
                            lineHeight = 22.sp
                        )
                    }
                }

                // Included plugins preview
                if (info.plugins.isNotEmpty()) {
                    Text(
                        text = "Available Sources (${info.plugins.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    info.plugins.forEach { plugin ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Extension,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val isNewPlugin = info.newPlugins.any { it.id == plugin.id }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = plugin.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (plugin.hasUpdate && plugin.installedVersion != null) {
                                                "v${plugin.installedVersion} ➜ v${plugin.version}"
                                            } else {
                                                "v${plugin.version}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (plugin.hasUpdate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (isNewPlugin) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "NEW",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else if (plugin.hasUpdate) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "UPDATE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${plugin.packageName} • ${plugin.apkSize}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    if (!plugin.changelog.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "• ${plugin.changelog}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (info.hasCatalogTemplateUpdate) {
                        Button(
                            onClick = {
                                PluginManager.applyCatalogUpdate(context, info)
                                catalogUpdateInfo = null
                                Toast.makeText(context, "Catalog template updated to v${info.newVersion}!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Apply Template")
                        }

                        OutlinedButton(
                            onClick = { catalogUpdateInfo = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Later")
                        }
                    } else {
                        Button(
                            onClick = { catalogUpdateInfo = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Got it")
                        }
                    }
                }
            }
        }
    }
}
