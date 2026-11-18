package com.kanime.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
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
import com.kanime.app.ui.components.GlassSurface
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeAmbientBackdrop
import com.kanime.app.ui.theme.KanimeMotion
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.LocalWindowSizeClass
import com.kanime.app.ui.theme.PillShape
import com.kanime.app.ui.theme.ThemeMode
import com.kanime.app.ui.theme.ThemePreferences
import com.kanime.app.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val STEP_WELCOME = 0
private const val STEP_APPEARANCE = 1
private const val STEP_PERMISSIONS = 2
private const val STEP_PLUGIN = 3
private const val STEP_DONE = 4
private const val TOTAL_STEPS = 5

internal fun canInstallUnknownApps(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true // API 24–25 uses a global Security setting; no runtime per-app toggle
    }
}

internal fun isNetworkAvailable(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        true
    }
}

private fun openUnknownSourcesSettings(context: Context): Boolean {
    return try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        openAppDetailsSettings(context)
    }
}

private fun openAppDetailsSettings(context: Context): Boolean {
    return try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val themeMode by ThemePreferences.themeModeFlow.collectAsStateWithLifecycle()
    val dynamicColor by ThemePreferences.dynamicColorFlow.collectAsStateWithLifecycle()
    val plugins by PluginManager.plugins.collectAsStateWithLifecycle()
    val catalogVersion by PluginManager.pluginConfigVersionFlow.collectAsStateWithLifecycle()
    val pluginFolderUri by PluginManager.pluginFolderUriFlow.collectAsStateWithLifecycle()
    val pluginFolderName = remember(pluginFolderUri) {
        PluginManager.getPluginFolderDisplayName(context, pluginFolderUri)
    }

    var pendingDownloadAfterFolder by remember { mutableStateOf<List<AnimeSourcePlugin>?>(null) }

    var installPermGranted by remember { mutableStateOf(canInstallUnknownApps(context)) }
    var attemptedInstallPerm by remember { mutableStateOf(false) }
    var isOnline by remember { mutableStateOf(isNetworkAvailable(context)) }
    var isCheckingCatalog by remember { mutableStateOf(false) }
    var catalogCheckFailed by remember { mutableStateOf(false) }
    var pendingCatalogUpdate by remember { mutableStateOf<CatalogUpdateInfo?>(null) }
    var downloadingPluginId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadErrorPluginId by remember { mutableStateOf<String?>(null) }
    var routedForPermission by remember { mutableStateOf(false) }

    fun runSequentialDownloads(targets: List<AnimeSourcePlugin>) {
        coroutineScope.launch {
            for (target in targets) {
                if (target.isApkDownloaded && !target.hasUpdate) {
                    PluginManager.installPluginApk(context, target)
                    continue
                }
                downloadingPluginId = target.id
                downloadErrorPluginId = null
                downloadProgress = 0f
                val result = PluginManager.downloadPluginApk(context, target) { p ->
                    downloadProgress = p
                }
                downloadingPluginId = null
                if (result.isFailure) {
                    downloadErrorPluginId = target.id
                    break
                }
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val queued = pendingDownloadAfterFolder
        pendingDownloadAfterFolder = null
        if (treeUri != null) {
            PluginManager.setPluginFolderUri(context, treeUri)
            coroutineScope.launch {
                PluginManager.checkPluginStatusAsync(context, force = true)
                if (!queued.isNullOrEmpty()) {
                    runSequentialDownloads(queued)
                }
            }
        }
    }

    fun checkLatestCatalog() {
        if (!isNetworkAvailable(context)) {
            isOnline = false
            catalogCheckFailed = true
            return
        }
        coroutineScope.launch {
            isCheckingCatalog = true
            catalogCheckFailed = false
            val res = PluginManager.checkForCatalogUpdate(context, force = true)
            isCheckingCatalog = false
            res.onSuccess { info ->
                pendingCatalogUpdate = if (info.catalogUpdateAvailable || info.metadataUpdatedCount > 0) info else null
            }.onFailure {
                catalogCheckFailed = true
            }
        }
    }

    // Automatically check for the latest catalog template once during onboarding
    LaunchedEffect(Unit) {
        PluginManager.checkPluginStatusAsync(context)
        if (isNetworkAvailable(context)) {
            checkLatestCatalog()
        }
    }

    // Re-check permissions, connectivity, and plugin install status whenever returning from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                installPermGranted = canInstallUnknownApps(context)
                isOnline = isNetworkAvailable(context)
                PluginManager.getValidPluginFolderUri(context)
                if (installPermGranted) routedForPermission = false
                coroutineScope.launch {
                    PluginManager.checkPluginStatusAsync(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun ensureFolderThenDownload(targets: List<AnimeSourcePlugin>, goToPermissions: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallUnknownApps(context)) {
            routedForPermission = true
            goToPermissions()
            return
        }
        val needsDownload = targets.any { !it.isApkDownloaded || it.hasUpdate }
        if (needsDownload && PluginManager.getValidPluginFolderUri(context) == null) {
            pendingDownloadAfterFolder = targets
            folderPickerLauncher.launch(null)
            return
        }
        runSequentialDownloads(targets)
    }

    OnboardingContent(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        supportsPerAppInstallPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
        installPermGranted = installPermGranted,
        showOpenSettingsFallback = attemptedInstallPerm && !installPermGranted,
        routedForPermission = routedForPermission,
        isOnline = isOnline,
        pluginFolderName = pluginFolderName,
        plugins = plugins,
        catalogVersion = catalogVersion,
        isCheckingCatalog = isCheckingCatalog,
        catalogCheckFailed = catalogCheckFailed,
        pendingCatalogUpdate = pendingCatalogUpdate,
        downloadingPluginId = downloadingPluginId,
        downloadProgress = downloadProgress,
        downloadErrorPluginId = downloadErrorPluginId,
        onThemeModeChange = { ThemePreferences.setThemeMode(context, it) },
        onDynamicColorChange = { ThemePreferences.setDynamicColor(context, it) },
        onRequestInstallPermission = {
            attemptedInstallPerm = true
            openUnknownSourcesSettings(context)
        },
        onOpenAppSettings = { openAppDetailsSettings(context) },
        onChoosePluginFolder = { folderPickerLauncher.launch(null) },
        onCheckCatalog = { checkLatestCatalog() },
        onApplyCatalogUpdate = { info ->
            PluginManager.applyCatalogUpdate(context, info)
            pendingCatalogUpdate = null
        },
        onDownloadOrInstallPlugin = { target, goToPermissions ->
            ensureFolderThenDownload(listOf(target), goToPermissions)
        },
        onInstallAllPending = { pendingList, goToPermissions ->
            ensureFolderThenDownload(pendingList, goToPermissions)
        },
        onFinish = onFinished
    )
}

@Composable
internal fun OnboardingContent(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    supportsDynamicColor: Boolean,
    supportsPerAppInstallPerm: Boolean,
    installPermGranted: Boolean,
    showOpenSettingsFallback: Boolean,
    routedForPermission: Boolean,
    isOnline: Boolean,
    pluginFolderName: String?,
    plugins: List<AnimeSourcePlugin>,
    catalogVersion: Int,
    isCheckingCatalog: Boolean,
    catalogCheckFailed: Boolean,
    pendingCatalogUpdate: CatalogUpdateInfo?,
    downloadingPluginId: String?,
    downloadProgress: Float,
    downloadErrorPluginId: String?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onRequestInstallPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onChoosePluginFolder: () -> Unit,
    onCheckCatalog: () -> Unit,
    onApplyCatalogUpdate: (CatalogUpdateInfo) -> Unit,
    onDownloadOrInstallPlugin: (AnimeSourcePlugin, goToPermissions: () -> Unit) -> Unit,
    onInstallAllPending: (List<AnimeSourcePlugin>, goToPermissions: () -> Unit) -> Unit,
    onFinish: () -> Unit,
    initialPage: Int = STEP_WELCOME
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { TOTAL_STEPS })
    val coroutineScope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val windowSize = LocalWindowSizeClass.current

    fun goToStep(step: Int) {
        val target = step.coerceIn(0, TOTAL_STEPS - 1)
        coroutineScope.launch {
            if (reduceMotion) {
                pagerState.scrollToPage(target)
            } else {
                pagerState.animateScrollToPage(
                    page = target,
                    animationSpec = KanimeMotion.pageTween(false)
                )
            }
        }
    }

    KanimeAmbientBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(vertical = KanimeSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: Back (when > 0) + Skip (when < last step)
            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = KanimeSpacing.lg)
                    .heightIn(min = KanimeSpacing.minTouchTarget),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > STEP_WELCOME) {
                    IconButton(onClick = { goToStep(pagerState.currentPage - 1) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(KanimeSpacing.minTouchTarget))
                }

                if (pagerState.currentPage < STEP_DONE) {
                    TextButton(
                        onClick = onFinish,
                        modifier = Modifier.heightIn(min = KanimeSpacing.minTouchTarget)
                    ) {
                        Text(
                            text = stringResource(R.string.action_skip),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(KanimeSpacing.minTouchTarget))
                }
            }

            // Strictly clipped full-bleed HorizontalPager — adjacent steps never peek into current step
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 0,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = KanimeSpacing.lg)
                        .graphicsLayer {
                            if (!reduceMotion) {
                                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                val absOffset = offset.absoluteValue.coerceIn(0f, 1f)
                                alpha = lerp(0f, 1f, 1f - absOffset)
                                val scale = lerp(0.95f, 1f, 1f - absOffset)
                                scaleX = scale
                                scaleY = scale
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val isLandscapeLayout = windowSize.isLandscape && windowSize.screenHeightDp < 560
                    when (page) {
                        STEP_WELCOME -> OnboardingStepFrame(
                            icon = Icons.Default.PlayArrow,
                            isLandscape = isLandscapeLayout
                        ) {
                            WelcomeStepBody(onGetStarted = { goToStep(STEP_APPEARANCE) })
                        }

                        STEP_APPEARANCE -> OnboardingStepFrame(
                            icon = Icons.Default.Palette,
                            isLandscape = isLandscapeLayout
                        ) {
                            AppearanceStepBody(
                                themeMode = themeMode,
                                dynamicColor = dynamicColor,
                                supportsDynamicColor = supportsDynamicColor,
                                onThemeModeChange = onThemeModeChange,
                                onDynamicColorChange = onDynamicColorChange,
                                onNext = { goToStep(STEP_PERMISSIONS) }
                            )
                        }

                        STEP_PERMISSIONS -> OnboardingStepFrame(
                            icon = Icons.Default.Security,
                            isLandscape = isLandscapeLayout
                        ) {
                            PermissionsStepBody(
                                supportsPerAppInstallPerm = supportsPerAppInstallPerm,
                                installPermGranted = installPermGranted,
                                showOpenSettingsFallback = showOpenSettingsFallback,
                                routedForPermission = routedForPermission,
                                isOnline = isOnline,
                                pluginFolderName = pluginFolderName,
                                onRequestInstallPermission = onRequestInstallPermission,
                                onOpenAppSettings = onOpenAppSettings,
                                onChoosePluginFolder = onChoosePluginFolder,
                                onNext = { goToStep(STEP_PLUGIN) }
                            )
                        }

                        STEP_PLUGIN -> OnboardingStepFrame(
                            icon = Icons.Default.Extension,
                            isLandscape = isLandscapeLayout
                        ) {
                            PluginStepBody(
                                plugins = plugins,
                                catalogVersion = catalogVersion,
                                isCheckingCatalog = isCheckingCatalog,
                                catalogCheckFailed = catalogCheckFailed,
                                pendingCatalogUpdate = pendingCatalogUpdate,
                                pluginFolderName = pluginFolderName,
                                downloadingPluginId = downloadingPluginId,
                                downloadProgress = downloadProgress,
                                downloadErrorPluginId = downloadErrorPluginId,
                                onChoosePluginFolder = onChoosePluginFolder,
                                onCheckCatalog = onCheckCatalog,
                                onApplyCatalogUpdate = onApplyCatalogUpdate,
                                onPluginAction = { target ->
                                    onDownloadOrInstallPlugin(target) {
                                        goToStep(STEP_PERMISSIONS)
                                    }
                                },
                                onInstallAllPending = { list ->
                                    onInstallAllPending(list) {
                                        goToStep(STEP_PERMISSIONS)
                                    }
                                },
                                onLater = { goToStep(STEP_DONE) },
                                onContinue = { goToStep(STEP_DONE) }
                            )
                        }

                        STEP_DONE -> OnboardingStepFrame(
                            icon = Icons.Default.CheckCircle,
                            isLandscape = isLandscapeLayout
                        ) {
                            DoneStepBody(onStartWatching = onFinish)
                        }
                    }
                }
            }

            // Minimal dot indicator
            val stepCd = stringResource(R.string.onboarding_step_cd, pagerState.currentPage + 1, TOTAL_STEPS)
            Row(
                modifier = Modifier
                    .padding(vertical = KanimeSpacing.md)
                    .semantics { contentDescription = stepCd },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(TOTAL_STEPS) { idx ->
                    val selected = pagerState.currentPage == idx
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 24.dp else 7.dp,
                        animationSpec = KanimeMotion.dotSpring(),
                        label = "OnboardingDot"
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(dotWidth)
                            .clip(PillShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepFrame(
    icon: ImageVector,
    isLandscape: Boolean,
    content: @Composable () -> Unit
) {
    if (isLandscape) {
        Row(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = KanimeSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(0.36f),
                contentAlignment = Alignment.Center
            ) {
                StepIconHero(icon = icon)
            }
            Column(
                modifier = Modifier
                    .weight(0.64f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
            ) {
                content()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = KanimeSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KanimeSpacing.lg)
        ) {
            StepIconHero(icon = icon)
            content()
        }
    }
}

@Composable
private fun StepIconHero(icon: ImageVector) {
    GlassSurface(
        modifier = Modifier.size(80.dp),
        shape = CircleShape
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun WelcomeStepBody(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)) {
                    append("K")
                }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)) {
                    append("anime")
                }
            },
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(KanimeSpacing.sm))
        GlassButton(
            text = stringResource(R.string.onboarding_get_started),
            icon = Icons.Default.PlayArrow,
            style = GlassButtonStyle.Primary,
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AppearanceStepBody(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    supportsDynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
    ) {
        Text(
            text = stringResource(R.string.onboarding_appearance_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.onboarding_appearance_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(KanimeSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                ) {
                    GlassChip(
                        label = stringResource(R.string.settings_theme_system),
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                    GlassChip(
                        label = stringResource(R.string.settings_theme_light),
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    GlassChip(
                        label = stringResource(R.string.settings_theme_dark),
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChange(ThemeMode.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (supportsDynamicColor) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = KanimeSpacing.minTouchTarget),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_dynamic_color),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_dynamic_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(KanimeSpacing.sm))
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }
                }
            }
        }

        GlassButton(
            text = stringResource(R.string.action_next),
            style = GlassButtonStyle.Primary,
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PermissionsStepBody(
    supportsPerAppInstallPerm: Boolean,
    installPermGranted: Boolean,
    showOpenSettingsFallback: Boolean,
    routedForPermission: Boolean,
    isOnline: Boolean,
    pluginFolderName: String?,
    onRequestInstallPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onChoosePluginFolder: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
    ) {
        Text(
            text = stringResource(R.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(
                if (routedForPermission) R.string.onboarding_perm_install_needed_notice
                else R.string.onboarding_permissions_subtitle
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (routedForPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Install unknown apps permission card
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(KanimeSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Default.InstallMobile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.onboarding_perm_install_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                if (supportsPerAppInstallPerm) R.string.onboarding_perm_install_reason
                                else R.string.onboarding_perm_install_legacy_hint
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = if (installPermGranted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                        shape = PillShape
                    ) {
                        Text(
                            text = stringResource(
                                when {
                                    !supportsPerAppInstallPerm -> R.string.onboarding_perm_status_info
                                    installPermGranted -> R.string.onboarding_perm_status_granted
                                    else -> R.string.onboarding_perm_status_needed
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (installPermGranted) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (supportsPerAppInstallPerm && !installPermGranted) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                    ) {
                        GlassButton(
                            text = stringResource(R.string.onboarding_perm_grant),
                            style = GlassButtonStyle.Tonal,
                            onClick = onRequestInstallPermission,
                            modifier = Modifier.weight(1f)
                        )
                        if (showOpenSettingsFallback) {
                            GlassButton(
                                text = stringResource(R.string.action_open_settings),
                                style = GlassButtonStyle.Glass,
                                onClick = onOpenAppSettings
                            )
                        }
                    }
                }
            }
        }

        // SAF Plugin Folder selector card (ACTION_OPEN_DOCUMENT_TREE)
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(KanimeSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.onboarding_perm_folder_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = pluginFolderName ?: stringResource(R.string.onboarding_perm_folder_reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = if (pluginFolderName != null) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                        shape = PillShape
                    ) {
                        Text(
                            text = stringResource(
                                if (pluginFolderName != null) R.string.onboarding_perm_folder_selected
                                else R.string.onboarding_perm_folder_needed
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pluginFolderName != null) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                GlassButton(
                    text = stringResource(
                        if (pluginFolderName != null) R.string.onboarding_perm_folder_change
                        else R.string.onboarding_perm_folder_choose
                    ),
                    icon = Icons.Default.FolderOpen,
                    style = GlassButtonStyle.Tonal,
                    onClick = onChoosePluginFolder,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Offline info card (shown ONLY if the device is currently offline)
        if (!isOnline) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(KanimeSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.onboarding_perm_offline_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.onboarding_perm_offline_reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        GlassButton(
            text = stringResource(R.string.action_next),
            style = GlassButtonStyle.Primary,
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PluginStepBody(
    plugins: List<AnimeSourcePlugin>,
    catalogVersion: Int,
    isCheckingCatalog: Boolean,
    catalogCheckFailed: Boolean,
    pendingCatalogUpdate: CatalogUpdateInfo?,
    pluginFolderName: String?,
    downloadingPluginId: String?,
    downloadProgress: Float,
    downloadErrorPluginId: String?,
    onChoosePluginFolder: () -> Unit,
    onCheckCatalog: () -> Unit,
    onApplyCatalogUpdate: (CatalogUpdateInfo) -> Unit,
    onPluginAction: (AnimeSourcePlugin) -> Unit,
    onInstallAllPending: (List<AnimeSourcePlugin>) -> Unit,
    onLater: () -> Unit,
    onContinue: () -> Unit
) {
    val pendingInstallPlugins = remember(plugins) {
        plugins.filter { !it.isInstalled || it.hasUpdate }
    }
    val allInstalledAndCurrent = plugins.isNotEmpty() && pendingInstallPlugins.isEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
    ) {
        Text(
            text = stringResource(R.string.onboarding_plugin_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.onboarding_plugin_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Catalog template status & update card
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            accentTint = if (pendingCatalogUpdate != null) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
            } else null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KanimeSpacing.lg, vertical = KanimeSpacing.md),
                verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isCheckingCatalog -> stringResource(R.string.onboarding_catalog_checking)
                                pendingCatalogUpdate != null -> stringResource(
                                    R.string.plugins_catalog_upgrade,
                                    pendingCatalogUpdate.currentVersion,
                                    pendingCatalogUpdate.newVersion
                                )
                                catalogCheckFailed -> stringResource(R.string.onboarding_catalog_check_failed)
                                else -> stringResource(R.string.onboarding_catalog_up_to_date, catalogVersion)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (catalogCheckFailed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = pluginFolderName ?: stringResource(R.string.onboarding_plugin_choose_folder_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pluginFolderName == null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onCheckCatalog,
                        enabled = !isCheckingCatalog && downloadingPluginId == null
                    ) {
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
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (pendingCatalogUpdate != null && pendingCatalogUpdate.releaseNotes.isNotEmpty()) {
                    pendingCatalogUpdate.releaseNotes.take(3).forEach { note ->
                        Text(
                            text = "· $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // All source plugins from active catalog template
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
        ) {
            plugins.forEach { plugin ->
                val isDownloadingThis = downloadingPluginId == plugin.id
                val hasErrorThis = downloadErrorPluginId == plugin.id
                val installedAndLatest = plugin.isInstalled && !plugin.hasUpdate
                val readyToInstall = plugin.isApkDownloaded && !plugin.isInstalled

                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(KanimeSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plugin.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val sub = buildList {
                                    if (plugin.hasUpdate && !plugin.installedVersion.isNullOrBlank()) {
                                        add("v${plugin.installedVersion} → v${plugin.version}")
                                    } else {
                                        add("v${plugin.version}")
                                    }
                                    if (plugin.apkSize.isNotBlank()) add(plugin.apkSize)
                                }.joinToString(" · ")
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(KanimeSpacing.sm))

                            if (installedAndLatest) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = PillShape
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.onboarding_plugin_installed),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { onPluginAction(plugin) },
                                    enabled = downloadingPluginId == null,
                                    shape = PillShape
                                ) {
                                    Text(
                                        text = when {
                                            hasErrorThis -> stringResource(R.string.action_retry)
                                            plugin.hasUpdate -> stringResource(R.string.action_update)
                                            readyToInstall -> stringResource(R.string.action_install)
                                            else -> stringResource(R.string.action_download)
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        if (isDownloadingThis) {
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
                                    text = stringResource(R.string.onboarding_plugin_downloading, (downloadProgress * 100).toInt()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (hasErrorThis) {
                            Text(
                                text = stringResource(R.string.onboarding_plugin_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // Single clear primary action: Update catalog -> Choose folder -> Install all latest -> Next
        when {
            pendingCatalogUpdate != null -> {
                GlassButton(
                    text = stringResource(
                        R.string.onboarding_catalog_update_cta,
                        pendingCatalogUpdate.currentVersion,
                        pendingCatalogUpdate.newVersion
                    ),
                    icon = Icons.Default.SystemUpdate,
                    style = GlassButtonStyle.Primary,
                    enabled = downloadingPluginId == null,
                    onClick = { onApplyCatalogUpdate(pendingCatalogUpdate) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            allInstalledAndCurrent -> {
                GlassButton(
                    text = stringResource(R.string.action_next),
                    style = GlassButtonStyle.Primary,
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            pluginFolderName.isNullOrBlank() -> {
                GlassButton(
                    text = stringResource(R.string.onboarding_perm_folder_choose),
                    icon = Icons.Default.FolderOpen,
                    style = GlassButtonStyle.Primary,
                    enabled = downloadingPluginId == null,
                    onClick = onChoosePluginFolder,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            else -> {
                val single = pendingInstallPlugins.singleOrNull()
                val ctaLabel = if (single != null) {
                    if (single.isApkDownloaded && !single.hasUpdate) {
                        stringResource(R.string.onboarding_plugin_install_ready, single.name)
                    } else {
                        stringResource(R.string.onboarding_plugin_cta, single.name)
                    }
                } else {
                    stringResource(R.string.onboarding_plugin_install_all, pendingInstallPlugins.size)
                }
                GlassButton(
                    text = ctaLabel,
                    icon = if (single != null && single.isApkDownloaded && !single.hasUpdate) {
                        Icons.Default.InstallMobile
                    } else {
                        Icons.Default.CloudDownload
                    },
                    style = GlassButtonStyle.Primary,
                    enabled = downloadingPluginId == null && pendingInstallPlugins.isNotEmpty(),
                    onClick = { onInstallAllPending(pendingInstallPlugins) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (!allInstalledAndCurrent) {
            TextButton(
                onClick = onLater,
                modifier = Modifier.heightIn(min = KanimeSpacing.minTouchTarget)
            ) {
                Text(
                    text = stringResource(R.string.action_later),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DoneStepBody(onStartWatching: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
    ) {
        Text(
            text = stringResource(R.string.onboarding_done_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.onboarding_done_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(KanimeSpacing.sm))
        GlassButton(
            text = stringResource(R.string.onboarding_start_watching),
            icon = Icons.Default.PlayArrow,
            style = GlassButtonStyle.Primary,
            onClick = onStartWatching,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ============================================================================
// Previews — Every Step (Light / Dark, Phone / Tablet)
// ============================================================================

@Composable
private fun OnboardingStepPreview(step: Int, offline: Boolean = false) {
    KAnimeTheme(animateTransition = false) {
        OnboardingContent(
            themeMode = ThemeMode.SYSTEM,
            dynamicColor = false,
            supportsDynamicColor = true,
            supportsPerAppInstallPerm = true,
            installPermGranted = false,
            showOpenSettingsFallback = false,
            routedForPermission = false,
            isOnline = !offline,
            pluginFolderName = "Download/Kanime",
            plugins = listOf(PluginManager.defaultKanikotoPlugin),
            catalogVersion = 1,
            isCheckingCatalog = false,
            catalogCheckFailed = false,
            pendingCatalogUpdate = CatalogUpdateInfo(
                catalogUpdateAvailable = true,
                currentVersion = 1,
                newVersion = 2,
                releaseNotes = listOf("Added latest streaming sources")
            ),
            downloadingPluginId = null,
            downloadProgress = 0f,
            downloadErrorPluginId = null,
            onThemeModeChange = {},
            onDynamicColorChange = {},
            onRequestInstallPermission = {},
            onOpenAppSettings = {},
            onChoosePluginFolder = {},
            onCheckCatalog = {},
            onApplyCatalogUpdate = {},
            onDownloadOrInstallPlugin = { _, _ -> },
            onInstallAllPending = { _, _ -> },
            onFinish = {},
            initialPage = step
        )
    }
}

@Preview(name = "Step 1 Welcome Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Step 1 Welcome Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Composable
private fun PreviewOnboardingWelcome() = OnboardingStepPreview(STEP_WELCOME)

@Preview(name = "Step 2 Appearance Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Step 2 Appearance Dark Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.TABLET)
@Composable
private fun PreviewOnboardingAppearance() = OnboardingStepPreview(STEP_APPEARANCE)

@Preview(name = "Step 3 Permissions Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Step 3 Permissions Dark Offline", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Composable
private fun PreviewOnboardingPermissions() = OnboardingStepPreview(STEP_PERMISSIONS, offline = true)

@Preview(name = "Step 4 Plugin Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Step 4 Plugin Dark Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.TABLET)
@Composable
private fun PreviewOnboardingPlugin() = OnboardingStepPreview(STEP_PLUGIN)

@Preview(name = "Step 5 Done Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Step 5 Done Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Composable
private fun PreviewOnboardingDone() = OnboardingStepPreview(STEP_DONE)
