package com.klisee.reloaded.gmk

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class Pushed { About, Saves, Mods, Online, Touch, TouchEditor, TouchAppearance, TouchAssets, Fps, Controls, Updates }

@Composable
fun App(
    repo: GameStore,
    settings: SettingsStore,
    onPickZip: ((PickedFile?) -> Unit) -> Unit,
    onLaunch: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
    onShareLogs: ((String) -> Unit)? = null,
    onPickImage: ((PickedFile?) -> Unit) -> Unit = {},
    onSaveFile: ((String, ByteArray) -> Unit)? = null,
    supportedAbis: List<String> = emptyList(),
    connectedPads: List<String> = emptyList(),
    refreshRate: Float = 60f,
    networkGranted: Boolean = true,
    onRequestNetwork: () -> Unit = {},
    appVersion: String = "0.5.0-dev",
    fontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
    colorScheme: ColorScheme? = null,
    shapes: Shapes? = null,
    desktopUi: Boolean = false,
    logo: Painter? = null,
    onlineEngine: OnlineEngineStore? = null,
    updateManager: com.klisee.reloaded.gmk.update.UpdateManager? = null,
) {
    var theme by remember { mutableStateOf(settings.theme) }
    val backdrop = rememberReloadedBackdrop()
    ReloadedTheme(
        theme = theme,
        fontFamily = fontFamily,
        colorSchemeOverride = colorScheme,
        shapesOverride = shapes,
    ) {
        val liquid = LocalLiquidGlass.current
        val tabs = if (liquid) AppTab.entries else MaterialTabs
        CompositionLocalProvider(LocalBackdrop provides if (theme == "liquid") backdrop else null) {
        Box(modifier = Modifier.fillMaxSize()) {
        if (theme == "liquid") {
            BackdropSource(backdrop) { LiquidGlassBackground() }
        }
        BoxWithConstraints {
        val wide = maxWidth >= 900.dp
        var onboarded by remember { mutableStateOf(settings.onboardingDone) }
        var tab by remember { mutableStateOf(AppTab.Home) }
        var pushed by remember { mutableStateOf<Pushed?>(null) }
        var revision by remember { mutableStateOf(0) }
        var launching by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val pagerState = rememberPagerState { tabs.size }

        LaunchedEffect(pagerState.currentPage, wide, tabs) {
            if (!wide) {
                tab = tabs[pagerState.currentPage.coerceIn(tabs.indices)]
                revision++
            }
        }

        fun goTo(target: AppTab) {
            val page = tabs.indexOf(target)
            if (wide) {
                if (page >= 0) {
                    tab = target
                    revision++
                }
            } else if (page >= 0) {
                scope.launch { pagerState.animateScrollToPage(page) }
            }
        }

        LaunchedEffect(launching) {
            if (launching) {
                delay(4000)
                launching = false
            }
        }

        // Comprobación silenciosa al abrir: como mucho una vez por hora, y si falla no molesta.
        LaunchedEffect(updateManager) {
            val manager = updateManager ?: return@LaunchedEffect
            if (!manager.updateSettings.autoCheck) return@LaunchedEffect
            runCatching { manager.check(automatic = true) }
        }

        if (!onboarded) {
            Onboarding(
                repo = repo,
                settings = settings,
                logo = logo,
                desktop = desktopUi,
                theme = theme,
                onThemeChange = { theme = it },
                onPickZip = onPickZip,
                onOpenUrl = onOpenUrl,
                onFinish = {
                    settings.onboardingDone = true
                    onboarded = true
                    revision++
                },
            )
        } else {

        val current = pushed
        if (current != null) {
            when (current) {
                Pushed.About -> AboutScreen(appVersion = appVersion, logo = logo, onOpenUrl = onOpenUrl, onBack = { pushed = null })
                Pushed.Saves -> SavesScreen(
                    repo = repo,
                    onPickZip = onPickZip,
                    onSaveFile = onSaveFile,
                    onBack = { pushed = null },
                    desktopUi = desktopUi,
                )
                Pushed.Mods -> ModsScreen(
                    repo = repo,
                    onPickZip = onPickZip,
                    onBack = { pushed = null },
                )
                Pushed.Online -> OnlineScreen(engine = onlineEngine, repo = repo, onLaunch = onLaunch, onBack = { pushed = null })
                Pushed.Touch -> TouchScreen(
                    settings = settings,
                    onEditor = { pushed = Pushed.TouchEditor },
                    onAppearance = { pushed = Pushed.TouchAppearance },
                    onAssets = { pushed = Pushed.TouchAssets },
                    onBack = { pushed = null },
                )
                Pushed.TouchEditor -> TouchEditorScreen(settings = settings, onBack = { pushed = Pushed.Touch })
                Pushed.TouchAppearance -> TouchAppearanceScreen(settings = settings, onBack = { pushed = Pushed.Touch })
                Pushed.TouchAssets -> TouchAssetsScreen(
                    settings = settings,
                    onPickImage = onPickImage,
                    onPickZip = onPickZip,
                    onBack = { pushed = Pushed.Touch },
                )
                Pushed.Fps -> FpsScreen(settings = settings, refreshRate = refreshRate, onBack = { pushed = null })
                Pushed.Controls -> ControlsScreen(settings = settings, pads = connectedPads, onBack = { pushed = null })
                Pushed.Updates -> if (updateManager != null) {
                    com.klisee.reloaded.gmk.update.UpdatesScreen(
                        manager = updateManager,
                        settings = updateManager.updateSettings,
                        onBack = { pushed = null },
                        onOpenUrl = onOpenUrl,
                    )
                }
            }
        } else {

        val tabContent: @Composable (AppTab) -> Unit = { target ->
            when (target) {
                AppTab.Home -> HomeScreen(
                    repo = repo,
                    onlineEngine = onlineEngine,
                    revision = revision,
                    launching = launching,
                    wide = wide,
                    desktop = desktopUi,
                    onPickZip = onPickZip,
                    onLaunch = {
                        launching = true
                        onLaunch()
                    },
                    onLogs = { goTo(AppTab.Logs) },
                    onSettings = { goTo(AppTab.Settings) },
                    onOnline = { pushed = Pushed.Online },
                    onOpenUrl = onOpenUrl,
                    networkGranted = networkGranted,
                    onRequestNetwork = onRequestNetwork,
                    logo = logo,
                )
                AppTab.Saves -> SavesScreen(
                    repo = repo,
                    onPickZip = onPickZip,
                    onSaveFile = onSaveFile,
                    onBack = null,
                    desktopUi = desktopUi,
                )
                AppTab.Mods -> ModsScreen(
                    repo = repo,
                    onPickZip = onPickZip,
                    onBack = null,
                )
                AppTab.Logs -> LogsScreen(
                    repo = repo,
                    revision = revision,
                    onSettings = { goTo(AppTab.Settings) },
                    onShare = onShareLogs,
                )
                AppTab.Settings -> SettingsScreen(
                    repo = repo,
                    settings = settings,
                    revision = revision,
                    desktop = desktopUi,
                    onLogs = { goTo(AppTab.Logs) },
                    onAbout = { pushed = Pushed.About },
                    onSaves = { pushed = Pushed.Saves },
                    onMods = { pushed = Pushed.Mods },
                    onTouch = { pushed = Pushed.Touch },
                    onControls = { pushed = Pushed.Controls },
                    onFps = { pushed = Pushed.Fps },
                    onUpdates = if (updateManager != null) ({ pushed = Pushed.Updates }) else null,
                    appVersion = appVersion,
                    theme = theme,
                    onThemeChange = {
                        settings.theme = it
                        theme = it
                        revision++
                    },
                    onClose = { goTo(AppTab.Home) },
                    onOpenUrl = onOpenUrl,
                    supportedAbis = supportedAbis,
                    onChanged = { revision++ },
                )
            }
        }

        Scaffold(
            bottomBar = {},
            snackbarHost = {
                SnackbarHost(
                    snackbar,
                    modifier = Modifier.padding(bottom = if (wide) 16.dp else BottomNavSpace),
                )
            },
            contentWindowInsets = WindowInsets(0.dp),
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    if (desktopUi) {
                        PcSidebar(
                            current = tab,
                            tabs = tabs,
                            onSelect = ::goTo,
                            onControls = { pushed = Pushed.Controls },
                            version = appVersion,
                            logo = logo,
                        )
                    } else {
                        DesktopSidebar(current = tab, onSelect = ::goTo, version = appVersion, tabs = tabs, logo = logo)
                    }
                }
                if (wide) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (tab == AppTab.Home || desktopUi) {
                            Box(Modifier.fillMaxSize()) { tabContent(tab) }
                        } else {
                            Box(Modifier.widthIn(max = 1080.dp).fillMaxSize()) { tabContent(tab) }
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            tabContent(tabs[page])
                        }
                        if (!(liquid && tab == AppTab.Settings)) {
                            ReloadedBottomNav(
                                current = tab,
                                onSelect = ::goTo,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
        }
        }
        }
        }
        }
    }
}

private const val BLOG_DOWNLOAD_URL = "https://pokemon-reloaded.blogspot.com/2026/08/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Onboarding(
    repo: GameStore,
    settings: SettingsStore,
    logo: Painter?,
    desktop: Boolean = false,
    theme: String,
    onThemeChange: (String) -> Unit,
    onPickZip: ((PickedFile?) -> Unit) -> Unit,
    onOpenUrl: (String) -> Unit,
    onFinish: () -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var language by remember { mutableStateOf(settings.language) }
    var showLanguage by remember { mutableStateOf(false) }
    var darkTheme by remember(theme) { mutableStateOf(theme != "light") }
    var post by remember { mutableStateOf<BlogPost?>(null) }
    var status by remember { mutableStateOf(repo.status()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var log by remember { mutableStateOf(emptyList<String>()) }
    var keepScreenOn by remember { mutableStateOf(settings.keepScreenOn) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { post = latestBlogPost().getOrNull() }

    fun import() {
        onPickZip { file ->
            if (file == null) return@onPickZip
            busy = true
            progress = 0
            log = emptyList()
            scope.launch {
                val result = repo.importZip(file, onProgress = { progress = it }, onLog = { log = (log + it).takeLast(8) })
                busy = false
                status = repo.status()
                if (result.isFailure) log = log + (result.exceptionOrNull()?.message ?: "No pude importar.")
            }
        }
    }

    val installed = status as? GameStatus.Ready
    val scheme = MaterialTheme.colorScheme

    if (desktop) {
        CompositionLocalProvider(LocalLauncherStrings provides launcherStrings(language)) {
            DesktopOnboarding(
                logo = logo,
                language = language,
                onLanguage = { showLanguage = true },
                theme = theme,
                onToggleTheme = {
                    val next = if (theme == "light") "dark" else "light"
                    settings.theme = next
                    onThemeChange(next)
                },
                post = post,
                busy = busy,
                progress = progress,
                log = log,
                installed = installed,
                onDownload = {
                    (post?.postUrl ?: post?.links?.firstOrNull()?.url)?.let(onOpenUrl)
                },
                onImport = ::import,
                keepScreenOn = keepScreenOn,
                onKeepScreenOn = { keepScreenOn = it },
                onFinish = {
                    settings.keepScreenOn = keepScreenOn
                    onFinish()
                },
            )
            if (showLanguage) {
                AlertDialog(
                    onDismissRequest = { showLanguage = false },
                    title = { Text(launcherStrings(language).languageTitle) },
                    text = { LanguageOptions(language) { mode -> language = mode; settings.language = mode; showLanguage = false } },
                    confirmButton = { TextButton(onClick = { showLanguage = false }) { Text(launcherStrings(language).close) } },
                )
            }
        }
        return
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (step + 1) / 3f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "onboarding-progress",
    )

    CompositionLocalProvider(LocalLauncherStrings provides launcherStrings(language)) {
    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text(LocalLauncherStrings.current.languageTitle) },
            text = {
                Column {
                    val t = LocalLauncherStrings.current
                    listOf(
                        "auto" to (t.languageAuto to t.languageAutoSubtitle),
                        "es" to (t.languageSpanish to t.languageSpanishSubtitle),
                        "en" to (t.languageEnglish to t.languageEnglishSubtitle),
                    ).forEachIndexed { index, (mode, titles) ->
                        ChoiceRow(
                            icon = Icons.Filled.Language,
                            title = titles.first,
                            subtitle = titles.second,
                            selected = language == mode,
                            items = 3,
                            index = index,
                        ) {
                            language = mode
                            settings.language = mode
                            showLanguage = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguage = false }) { Text(LocalLauncherStrings.current.close) } },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(scheme.surface)) {
        OnboardingBackdrop()
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            OnboardingTopBar(
                step = step,
                progress = animatedProgress,
                language = language,
                darkTheme = darkTheme,
                onBack = if (step > 0) ({ step-- }) else null,
                onLanguage = { showLanguage = true },
                onToggleTheme = {
                    darkTheme = !darkTheme
                    val next = if (darkTheme) "dark" else "light"
                    settings.theme = next
                    onThemeChange(next)
                },
            )

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(animationSpec = tween(320)) { if (forward) it / 3 else -it / 3 } +
                        fadeIn(animationSpec = tween(220))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(280)) { if (forward) -it / 4 else it / 4 } +
                            fadeOut(animationSpec = tween(180)))
                },
                modifier = Modifier.weight(1f),
                label = "onboarding-step",
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    when (current) {
                        0 -> OnboardingWelcome(logo = logo)
                        1 -> OnboardingInstall(
                            post = post,
                            busy = busy,
                            progress = progress,
                            log = log,
                            installed = installed,
                            onDownload = { (post?.postUrl ?: post?.links?.firstOrNull()?.url)?.let(onOpenUrl) },
                            onImport = ::import,
                        )
                        else -> OnboardingPreferences(
                            keepScreenOn = keepScreenOn,
                            onChange = { keepScreenOn = it },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            OnboardingFooter(
                step = step,
                enabled = step != 1 || installed != null || busy,
                busy = busy,
                onContinue = {
                    when (step) {
                        0 -> step = 1
                        1 -> step = 2
                        else -> {
                            settings.keepScreenOn = keepScreenOn
                            onFinish()
                        }
                    }
                },
            )
        }
    }
    }
}

@Composable
private fun LanguageOptions(selected: String, onPick: (String) -> Unit) {
    val t = LocalLauncherStrings.current
    Column {
        listOf(
            "auto" to (t.languageAuto to t.languageAutoSubtitle),
            "es" to (t.languageSpanish to t.languageSpanishSubtitle),
            "en" to (t.languageEnglish to t.languageEnglishSubtitle),
        ).forEachIndexed { index, (mode, titles) ->
            ChoiceRow(
                icon = Icons.Filled.Language,
                title = titles.first,
                subtitle = titles.second,
                selected = selected == mode,
                items = 3,
                index = index,
            ) { onPick(mode) }
        }
    }
}

@Composable
private fun DesktopOnboarding(
    logo: Painter?,
    language: String,
    onLanguage: () -> Unit,
    theme: String,
    onToggleTheme: () -> Unit,
    post: BlogPost?,
    busy: Boolean,
    progress: Int,
    log: List<String>,
    installed: GameStatus.Ready?,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOn: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val display = reloadedDisplayFont()
    val t = LocalLauncherStrings.current
    val downloadUrl = post?.postUrl ?: post?.links?.firstOrNull()?.url

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surface),
    ) {
        OnboardingBackdrop()

        Column(Modifier.fillMaxSize()) {
            // Barra superior
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onToggleTheme,
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (theme == "light") Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = t.lightTheme,
                            modifier = Modifier.size(18.dp),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Surface(
                    onClick = onLanguage,
                    shape = RoundedCornerShape(999.dp),
                    color = scheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            when (language) {
                                "es" -> "Español"
                                "en" -> "English"
                                else -> t.language
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Cuerpo a dos columnas
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Columna izquierda: marca
                Column(Modifier.weight(1f)) {
                    if (logo != null) {
                        Image(
                            painter = logo,
                            contentDescription = null,
                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(22.dp))
                    }
                    Text(
                        t.welcomeTo,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = display,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                        ),
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        t.brandTitle,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = display,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            lineHeight = 78.sp,
                        ),
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        t.welcomeSubtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 460.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DesktopFeatureChip(Icons.Filled.Memory, "Motor nativo en Rust")
                        DesktopFeatureChip(Icons.Filled.Speed, "Más de 30 FPS")
                        DesktopFeatureChip(Icons.Filled.Save, "Partidas portables")
                    }
                }

                Spacer(Modifier.width(48.dp))

                // Columna derecha: tarjeta de acción
                Column(Modifier.width(420.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = scheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                "Primeros pasos",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (installed != null) "Todo listo para jugar" else t.installTitle,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = display,
                                    fontWeight = FontWeight.Black,
                                ),
                            )

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = scheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(9.dp)
                                            .background(
                                                if (installed != null) scheme.primary else scheme.error,
                                                CircleShape,
                                            ),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (installed != null) t.gameReady else t.noGame,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            if (installed != null) {
                                                "${installed.version ?: "?"} · ${formatBytes(installed.sizeBytes)}"
                                            } else {
                                                t.importToContinue
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            if (busy) {
                                LinearProgressIndicator(
                                    progress = { progress / 100f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                                    gapSize = 0.dp,
                                    drawStopIndicator = {},
                                )
                                Text(
                                    "${t.importing} $progress %",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }

                            Button(
                                onClick = onDownload,
                                enabled = downloadUrl != null && !busy,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(13.dp),
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (post?.version != null) "${t.download} ${post.version}" else t.downloadFromBlog)
                            }
                            OutlinedButton(
                                onClick = onImport,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(13.dp),
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (busy) t.importing else t.importZip)
                            }

                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LightMode, contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    t.keepScreenOn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOn)
                            }
                        }
                    }
                }
            }

            // Pie con botón
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.canChangeLater,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onFinish,
                    enabled = !busy,
                    modifier = Modifier.width(240.dp).height(52.dp),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text(
                        if (installed != null) t.startPlaying else t.continueLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DesktopFeatureChip(icon: ImageVector, text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = scheme.primary)
            Spacer(Modifier.width(7.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OnboardingTopBar(
    step: Int,
    progress: Float,
    language: String,
    darkTheme: Boolean,
    onBack: (() -> Unit)?,
    onLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val t = LocalLauncherStrings.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = t.back,
                            modifier = Modifier.size(20.dp),
                            tint = scheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    t.stepOf(step + 1, 3),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = onToggleTheme,
                shape = CircleShape,
                color = scheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (darkTheme) t.lightTheme else t.darkTheme,
                        modifier = Modifier.size(19.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = onLanguage,
                shape = RoundedCornerShape(999.dp),
                color = scheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (language) {
                            "es" -> "Español"
                            "en" -> "English"
                            else -> t.language
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurface,
                    )
                }
            }
        }
        if (step > 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = scheme.primary,
                trackColor = scheme.surfaceContainerHighest,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun OnboardingWelcome(logo: Painter?) {
    val scheme = MaterialTheme.colorScheme
    val display = reloadedDisplayFont()
    val t = LocalLauncherStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (logo != null) {
            Image(
                painter = logo,
                contentDescription = null,
                modifier = Modifier.size(164.dp).clip(RoundedCornerShape(38.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Surface(shape = RoundedCornerShape(38.dp), color = scheme.primaryContainer, modifier = Modifier.size(164.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.CatchingPokemon,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(88.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(34.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val longWord = t.brandTitle.split(' ').maxOfOrNull { it.length } ?: 8
            val titleSize = ((maxWidth.value / (longWord * 0.62f)).coerceIn(22f, 34f)).sp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    t.welcomeTo,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = display,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.4.sp,
                    ),
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    t.brandTitle,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = display,
                        fontWeight = FontWeight.Black,
                        fontSize = titleSize * 1.06f,
                        letterSpacing = 0.2.sp,
                        lineHeight = titleSize * 1.06f,
                    ),
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    softWrap = true,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            t.welcomeSubtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun OnboardingFeature(icon: ImageVector, title: String, text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = scheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(21.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OnboardingInstall(
    post: BlogPost?,
    busy: Boolean,
    progress: Int,
    log: List<String>,
    installed: GameStatus.Ready?,
    onDownload: () -> Unit,
    onImport: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val display = reloadedDisplayFont()
    val t = LocalLauncherStrings.current
    val downloadUrl = BLOG_DOWNLOAD_URL
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            t.installNeeded.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = display,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.2.sp,
            ),
            color = scheme.onSurface,
        )
        Text(
            t.installTitle.uppercase(),
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = display,
                fontWeight = FontWeight.Black,
                lineHeight = 42.sp,
            ),
            color = scheme.onSurface,
        )
        Text(
            t.installBody,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = scheme.surfaceContainerLow,
            border = BorderStroke(1.dp, if (installed != null) scheme.primary.copy(alpha = 0.6f) else scheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (installed != null) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceContainerHighest,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (installed != null) Icons.Filled.Save else Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = if (installed != null) scheme.primary else scheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (installed != null) t.gameReady else t.noGame,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            installed?.let { it.exe.substringAfterLast('/').ifBlank { it.exe } } ?: t.importToContinue,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (installed != null) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
                if (busy) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${t.importing} $progress %",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    if (log.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            log.takeLast(3).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = scheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Button(
            onClick = onDownload,
            enabled = downloadUrl != null && !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text(if (post?.version != null) "${t.download} ${post?.version}" else t.downloadFromBlog)
        }
        OutlinedButton(
            onClick = onImport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text(if (busy) t.importing else t.importZip)
        }
    }
}

@Composable
private fun OnboardingPreferences(keepScreenOn: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val display = reloadedDisplayFont()
    val t = LocalLauncherStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            t.almostReady.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = display,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.2.sp,
            ),
            color = scheme.onSurface,
        )
        Text(
            t.howToPlay.uppercase(),
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = display,
                fontWeight = FontWeight.Black,
                lineHeight = 42.sp,
            ),
            color = scheme.onSurface,
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = scheme.surfaceContainerLow,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = scheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.LightMode, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.keepScreenOn, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        t.keepScreenOnSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = keepScreenOn, onCheckedChange = onChange)
            }
        }
        Text(
            t.touchControlsNote,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = scheme.surfaceContainerLow,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    t.changeLater,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun OnboardingFooter(
    step: Int,
    enabled: Boolean,
    busy: Boolean,
    onContinue: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val t = LocalLauncherStrings.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Button(
            onClick = onContinue,
            enabled = enabled && !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                when {
                    busy -> t.importing
                    step == 2 -> t.startPlaying
                    else -> t.continueLabel
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (step == 1 && !enabled && !busy) {
            Text(
                t.installToContinue,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun OnlineStatusPill(enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) scheme.primary.copy(alpha = 0.22f) else scheme.onSurface.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (enabled) scheme.primary else scheme.onSurfaceVariant),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (enabled) "Activado" else "Desactivado",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) scheme.primary else scheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    repo: GameStore,
    revision: Int,
    launching: Boolean,
    wide: Boolean = false,
    desktop: Boolean = false,
    onPickZip: ((PickedFile?) -> Unit) -> Unit,
    onLaunch: () -> Unit,
    onLogs: () -> Unit,
    onSettings: () -> Unit,
    onOnline: () -> Unit,
    onOpenUrl: (String) -> Unit,
    networkGranted: Boolean,
    onRequestNetwork: () -> Unit,
    logo: Painter? = null,
    onlineEngine: OnlineEngineStore? = null,
    updateManager: com.klisee.reloaded.gmk.update.UpdateManager? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val liquid = LocalLiquidGlass.current
    val onlineEnabled = remember(revision, onlineEngine) {
        onlineEngine?.load()?.enabled == true
    }
    var status by remember(revision) { mutableStateOf(repo.status()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var installLog by remember { mutableStateOf(emptyList<String>()) }
    var post by remember { mutableStateOf<BlogPost?>(null) }
    var blogError by remember { mutableStateOf<String?>(null) }
    var loadingPost by remember { mutableStateOf(true) }
    var blogAttempt by remember { mutableStateOf(0) }

    LaunchedEffect(blogAttempt, networkGranted) {
        post = null
        blogError = null
        loadingPost = true
        if (!networkGranted) {
            loadingPost = false
            return@LaunchedEffect
        }
        latestBlogPost().fold(
            onSuccess = { post = it },
            onFailure = { blogError = it.message },
        )
        loadingPost = false
    }

    fun importGame() {
        onPickZip { file ->
            if (file == null) return@onPickZip
            val hadGame = status is GameStatus.Ready
            busy = true
            installLog = emptyList()
            progress = 0
            scope.launch {
                val result = repo.importZip(
                    file,
                    onProgress = { progress = it },
                    onLog = { line -> installLog = (installLog + line).takeLast(40) },
                )
                busy = false
                progress = null
                status = repo.status()
                snackbar.showSnackbar(
                    result.fold(
                        { if (hadGame) "Juego actualizado. Tus partidas se conservaron." else "Juego instalado." },
                        { it.message ?: "No pude importar el juego." },
                    ),
                )
            }
        }
    }

    val installed = status as? GameStatus.Ready
    val downloadUrl = BLOG_DOWNLOAD_URL
    val postVersion = post?.version

    val news: @Composable () -> Unit = {
        if (liquid) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Reloaded OpenGMK",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (liquid) {
            when {
                loadingPost -> NewsPlaceholder()
                post != null -> NewsFeed(post!!, onOpenUrl)
                !networkGranted -> NoticeCard(
                    text = "La app necesita permiso de red para leer las novedades del blog y descargar el juego.",
                    action = "Conceder permiso",
                    onAction = onRequestNetwork,
                )
                blogError != null -> NoticeCard(
                    text = "No pude leer el blog: $blogError",
                    action = "Reintentar",
                    onAction = { blogAttempt++ },
                )
                else -> NoticeCard(
                    text = "El blog no devolvió ninguna entrada.",
                    action = "Reintentar",
                    onAction = { blogAttempt++ },
                )
            }
        } else {
            when {
                loadingPost -> NewsPlaceholder()
                post != null -> MaterialNewsFeed(post!!, onOpenUrl)
                !networkGranted -> NoticeCard(
                    text = "La app necesita permiso de red para leer las novedades del blog y descargar el juego.",
                    action = "Conceder permiso",
                    onAction = onRequestNetwork,
                )
                blogError != null -> NoticeCard(
                    text = "No pude leer el blog: $blogError",
                    action = "Reintentar",
                    onAction = { blogAttempt++ },
                )
                else -> NoticeCard(
                    text = "El blog no devolvió ninguna entrada.",
                    action = "Reintentar",
                    onAction = { blogAttempt++ },
                )
            }
        }
    }

    @Composable
    fun playButton(modifier: Modifier = Modifier) {
        val enabled = installed != null && !busy && !launching
        if (liquid) {
            val shape = RoundedCornerShape(50)
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .alpha(if (enabled) 1f else 0.45f)
                    .clip(shape)
                    .clickable(enabled = enabled, onClick = onLaunch),
            ) {
                GlassPanel(
                    shape = shape,
                    modifier = Modifier.fillMaxSize(),
                    fallbackColor = MaterialTheme.colorScheme.primary,
                    glassTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (launching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (launching) "Cargando..." else "Jugar",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        } else {
            Button(
                onClick = onLaunch,
                enabled = enabled,
                modifier = modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(50),
            ) {
                if (launching) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(if (launching) "Cargando..." else "Jugar", style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    @Composable
    fun importButtons() {
        if (liquid) {
            val shape = RoundedCornerShape(50)
            val tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .alpha(if (downloadUrl != null && !busy) 1f else 0.45f)
                        .clip(shape)
                        .clickable(enabled = downloadUrl != null && !busy) {
                            downloadUrl?.let(onOpenUrl)
                        },
                ) {
                    GlassPanel(
                        shape = shape,
                        modifier = Modifier.fillMaxSize(),
                        fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        glassTint = tint,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                if (postVersion != null) "Descargar $postVersion" else "Descargar",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .alpha(if (!busy) 1f else 0.45f)
                        .clip(shape)
                        .clickable(enabled = !busy, onClick = ::importGame),
                ) {
                    GlassPanel(
                        shape = shape,
                        modifier = Modifier.fillMaxSize(),
                        fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        glassTint = tint,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("Importar", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { downloadUrl?.let(onOpenUrl) },
                    enabled = downloadUrl != null && !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (postVersion != null) "Descargar $postVersion" else "Descargar")
                }
                TextButton(
                    onClick = ::importGame,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Importar")
                }
            }
        }
    }

    if (desktop) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Inicio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Novedades y estado de tu instalación",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PcHeaderAction(Icons.Filled.Article, "Logs", onLogs)
                    PcHeaderAction(Icons.Filled.Settings, "Ajustes", onSettings)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PcNewsPanel(
                        post = post,
                        loading = loadingPost,
                        error = blogError,
                        networkGranted = networkGranted,
                        onOpenUrl = onOpenUrl,
                        onRetry = { blogAttempt++ },
                    )
                }
                Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DesktopHeroCard(
                        installed = installed,
                        launching = launching,
                        busy = busy,
                        downloadUrl = downloadUrl,
                        postVersion = postVersion,
                        onLaunch = onLaunch,
                        onDownload = { downloadUrl?.let(onOpenUrl) },
                        onImport = ::importGame,
                        onOnline = onOnline,
                        onlineEnabled = onlineEnabled,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        ImportPanel(busy = busy, progress = progress, lines = installLog)
        return
    }

    Scaffold(
        topBar = {
            if (liquid) {
                TopAppBar(
                    colors = reloadedTopBarColors(),
                    title = {},
                    actions = {
                        if (!wide) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(end = 6.dp),
                            ) {
                                CircleIconButton(Icons.Filled.Article, "Logs", onLogs)
                                CircleIconButton(Icons.Filled.Settings, "Ajustes", onSettings)
                            }
                        }
                    },
                )
            } else if (wide) {
                LargeTopAppBar(
                    colors = reloadedTopBarColors(),
                    title = { MaterialBrandTitle(logo = logo, wide = true) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = if (wide) 16.dp else BottomNavSpace)) },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 32.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        news()
                        Spacer(Modifier.height(24.dp))
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.width(380.dp).fillMaxHeight(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(28.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text("Tu partida", style = MaterialTheme.typography.headlineSmall)
                            if (installed == null) {
                                Text(
                                    "Todavía no hay una copia del juego instalada.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(installed.exe, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Versión ${installed.version ?: "desconocida"} · ${formatBytes(installed.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            playButton()
                            if (installed == null) {
                                Text(
                                    "Para jugar hace falta el juego: descárgalo del blog o importa el zip que ya tengas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            importButtons()
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Consejo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "Puedes importar una copia nueva del juego sin perder tus partidas: se conservan automáticamente.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .padding(bottom = BottomNavSpace),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    MaterialHomeHero(
                        logo = logo,
                        installed = installed,
                        launching = launching,
                        busy = busy,
                        downloadUrl = downloadUrl,
                        postVersion = postVersion,
                        onLaunch = onLaunch,
                        onDownload = { downloadUrl?.let(onOpenUrl) },
                        onImport = ::importGame,
                        onOnline = onOnline,
                        onlineEnabled = onlineEnabled,
                    )
                    news()
                    Spacer(Modifier.height(12.dp))
                }
            }
            ImportPanel(busy = busy, progress = progress, lines = installLog)
        }
    }
}



@Composable
private fun MaterialBrandTitle(logo: Painter?, wide: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(48.dp),
        ) {
            if (logo != null) {
                Image(
                    painter = logo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    Icons.Filled.CatchingPokemon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Reloaded OpenGMK",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            if (wide) {
                Text(
                    "Inicio",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MaterialHomeHero(
    logo: Painter?,
    installed: GameStatus.Ready?,
    launching: Boolean,
    busy: Boolean,
    downloadUrl: String?,
    postVersion: String?,
    onLaunch: () -> Unit,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onOnline: () -> Unit,
    onlineEnabled: Boolean = false,
) {
    val canPlay = installed != null && !busy && !launching
    val shape = RoundedCornerShape(36.dp)
    val background = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = .84f),
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .08f), shape),
    ) {
        Icon(
            Icons.Filled.CatchingPokemon,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(190.dp)
                .padding(top = 4.dp, end = 2.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .055f),
        )
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .62f),
                    modifier = Modifier.size(62.dp),
                ) {
                    if (logo != null) {
                        Image(
                            painter = logo,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            Icons.Filled.CatchingPokemon,
                            contentDescription = null,
                            modifier = Modifier.padding(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Reloaded OpenGMK",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Cliente no oficial de Pokémon Reloaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .42f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(
                                if (installed != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            if (installed != null) "Listo para jugar" else "Falta instalar el juego",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (installed != null) {
                                "${installed.version ?: "Versión desconocida"} · ${formatBytes(installed.sizeBytes)}"
                            } else {
                                "Descarga o importa tu copia para empezar."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val heroHaptics = rememberInterfaceHaptics()
            Button(
                onClick = { heroHaptics.confirm(); onLaunch() },
                enabled = canPlay,
                modifier = Modifier.fillMaxWidth().height(66.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryFixed,
                    contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
                    disabledContainerColor = MaterialTheme.colorScheme.primaryFixed.copy(alpha = .4f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimaryFixed.copy(alpha = .55f),
                ),
            ) {
                if (launching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimaryFixed,
                    )
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (launching) "Cargando..." else "Jugar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDownload,
                    enabled = downloadUrl != null && !busy,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .4f),
                    ),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (postVersion != null) "Descargar $postVersion" else "Descargar",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onImport,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .4f),
                    ),
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Importar")
                }
            }

            FilledTonalButton(
                onClick = onOnline,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .46f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text("Reloaded Online", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                OnlineStatusPill(onlineEnabled)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .7f),
                )
            }
        }
    }
}

@Composable
private fun MaterialNewsFeed(post: BlogPost, onOpenUrl: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Novedades", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    "BLOG",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (post.images.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(post.images.size) { index ->
                            RemoteImage(
                                url = post.images[index],
                                modifier = Modifier
                                    .height(210.dp)
                                    .width(if (index == 0) 300.dp else 230.dp)
                                    .clip(RoundedCornerShape(22.dp)),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (post.published != null) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    formatDate(post.publishedMillis ?: 0L),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (post.version != null) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                post.version,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Text(
                    post.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (post.summary.isNotBlank()) {
                    Text(
                        post.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (post.postUrl != null) {
                    FilledTonalButton(
                        onClick = { onOpenUrl(post.postUrl) },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Leer entrada completa")
                    }
                }
            }
        }
    }
}

@Composable
private fun PcHeaderAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DesktopHeroCard(
    installed: GameStatus.Ready?,
    launching: Boolean,
    busy: Boolean,
    downloadUrl: String?,
    postVersion: String?,
    onLaunch: () -> Unit,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onOnline: () -> Unit,
    onlineEnabled: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val canPlay = installed != null && !busy && !launching
    val shape = RoundedCornerShape(32.dp)
    val background = Brush.linearGradient(
        listOf(
            scheme.primaryContainer,
            scheme.primaryContainer.copy(alpha = 0.84f),
            scheme.surfaceContainerHigh,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, scheme.onPrimaryContainer.copy(alpha = 0.08f), shape),
    ) {
        Icon(
            Icons.Filled.CatchingPokemon,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(200.dp)
                .padding(top = 2.dp, end = 0.dp),
            tint = scheme.onPrimaryContainer.copy(alpha = 0.07f),
        )

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Tu partida",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onPrimaryContainer,
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = scheme.surface.copy(alpha = 0.42f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(
                                if (installed != null) scheme.primary else scheme.error,
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (installed != null) "Listo para jugar" else "Falta instalar el juego",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (installed != null) {
                                "${installed.version ?: "Versión desconocida"} · ${formatBytes(installed.sizeBytes)}"
                            } else {
                                "Descarga o importa tu copia para empezar."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        if (installed != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                installed.exe,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            val launchHaptics = rememberInterfaceHaptics()
            Button(
                onClick = { launchHaptics.confirm(); onLaunch() },
                enabled = canPlay,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primaryFixed,
                    contentColor = scheme.onPrimaryFixed,
                    disabledContainerColor = scheme.primaryFixed.copy(alpha = 0.4f),
                    disabledContentColor = scheme.onPrimaryFixed.copy(alpha = 0.55f),
                ),
            ) {
                if (launching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = scheme.onPrimaryFixed,
                    )
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (launching) "Cargando..." else "Jugar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDownload,
                    enabled = downloadUrl != null && !busy,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = scheme.onPrimaryContainer,
                        disabledContentColor = scheme.onPrimaryContainer.copy(alpha = 0.4f),
                    ),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (postVersion != null) "Descargar $postVersion" else "Descargar",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onImport,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = scheme.onPrimaryContainer,
                        disabledContentColor = scheme.onPrimaryContainer.copy(alpha = 0.4f),
                    ),
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Importar")
                }
            }

            FilledTonalButton(
                onClick = onOnline,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.surface.copy(alpha = 0.46f),
                    contentColor = scheme.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text("Reloaded Online", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                OnlineStatusPill(onlineEnabled)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = scheme.surfaceContainerHigh.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Consejo",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Puedes importar una copia nueva del juego sin perder tus partidas: se conservan automáticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PcPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun PcNewsPanel(
    post: BlogPost?,
    loading: Boolean,
    error: String?,
    networkGranted: Boolean,
    onOpenUrl: (String) -> Unit,
    onRetry: () -> Unit,
) {
    PcPanel {
        when {
            loading -> {
                Text("Cargando novedades…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                ) {}
            }
            post != null -> {
                Text(post.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                if (post.published != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Publicado el ${formatDate(post.publishedMillis ?: 0L)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                post.images.firstOrNull()?.let { image ->
                    Spacer(Modifier.height(16.dp))
                    RemoteImage(
                        url = image,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
                if (post.summary.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(post.summary, style = MaterialTheme.typography.bodyLarge)
                }
                if (post.postUrl != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onOpenUrl(post.postUrl) }) {
                        Text("Leer la entrada completa")
                    }
                }
            }
            !networkGranted -> {
                Text(
                    "La app necesita permiso de red para leer las novedades del blog y descargar el juego.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
            else -> {
                Text(
                    error?.let { "No pude leer el blog: $it" } ?: "El blog no devolvió ninguna entrada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
        }
    }
}

@Composable
private fun NewsFeed(post: BlogPost, onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(post.title, style = MaterialTheme.typography.headlineSmall)
            if (post.published != null) {
                Text(
                    text = "Publicado el ${formatDate(post.publishedMillis ?: 0L)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (post.images.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(post.images.size) { index ->
                    RemoteImage(
                        url = post.images[index],
                        modifier = Modifier
                            .height(200.dp)
                            .width(if (index == 0) 320.dp else 240.dp)
                            .clip(MaterialTheme.shapes.extraLarge),
                    )
                }
            }
        }
        if (post.summary.isNotBlank()) {
            Text(
                text = post.summary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        if (post.postUrl != null) {
            TextButton(
                onClick = { onOpenUrl(post.postUrl) },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text("Leer la entrada completa")
            }
        }
    }
}

@Composable
private fun NewsPlaceholder() {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(0.7f).height(32.dp)) {}
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().height(200.dp)) {}
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().height(16.dp)) {}
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(0.8f).height(16.dp)) {}
    }
}

@Composable
private fun NoticeCard(text: String, action: String, onAction: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun ImportPanel(busy: Boolean, progress: Int?, lines: List<String>) {
    if (!busy) return
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), modifier = Modifier.fillMaxSize()) {}
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(32.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = if (progress == null) "Preparando el archivo…" else "Instalando el juego…",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                if (progress == null) {
                    CircularProgressIndicator()
                } else {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("$progress %", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ImportConsole(lines)
            }
        }
    }
}

@Composable
private fun ImportConsole(lines: List<String>) {
    if (lines.isEmpty()) return
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size) { scroll.scrollTo(scroll.maxValue) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("Registro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                text = lines.takeLast(6).joinToString("\n"),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 104.dp)
                    .padding(top = 4.dp)
                    .verticalScroll(scroll),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    repo: GameStore,
    settings: SettingsStore,
    revision: Int,
    desktop: Boolean = false,
    onLogs: () -> Unit,
    onAbout: () -> Unit,
    onSaves: () -> Unit,
    onMods: () -> Unit,
    onTouch: () -> Unit,
    onControls: () -> Unit,
    onFps: () -> Unit,
    onUpdates: (() -> Unit)? = null,
    appVersion: String,
    theme: String,
    onThemeChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
    supportedAbis: List<String>,
    onChanged: () -> Unit,
) {
    if (LocalLiquidGlass.current) {
        LiquidSettingsScreen(
            repo = repo,
            settings = settings,
            revision = revision,
            appVersion = appVersion,
            theme = theme,
            onThemeChange = onThemeChange,
            onClose = onClose,
            onLogs = onLogs,
            onAbout = onAbout,
            onSaves = onSaves,
            onMods = onMods,
            onTouch = onTouch,
            onControls = onControls,
            onFps = onFps,
            onUpdates = onUpdates,
            onChanged = onChanged,
        )
    } else {
        MaterialSettingsScreen(
            repo = repo,
            settings = settings,
            revision = revision,
            desktop = desktop,
            onLogs = onLogs,
            onAbout = onAbout,
            onSaves = onSaves,
            onMods = onMods,
            onTouch = onTouch,
            onControls = onControls,
            onFps = onFps,
            onUpdates = onUpdates,
            appVersion = appVersion,
            theme = theme,
            onThemeChange = onThemeChange,
            onOpenUrl = onOpenUrl,
            supportedAbis = supportedAbis,
            onChanged = onChanged,
            onClose = onClose,
        )
    }
}

@Composable
private fun PcSettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun PcSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun PcSettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialSettingsScreen(
    repo: GameStore,
    settings: SettingsStore,
    revision: Int,
    desktop: Boolean = false,
    onLogs: () -> Unit,
    onAbout: () -> Unit,
    onSaves: () -> Unit,
    onMods: () -> Unit,
    onTouch: () -> Unit,
    onControls: () -> Unit,
    onFps: () -> Unit,
    onUpdates: (() -> Unit)? = null,
    appVersion: String,
    theme: String,
    onThemeChange: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    supportedAbis: List<String>,
    onChanged: () -> Unit,
    onClose: () -> Unit,
) {
    var status by remember(revision) { mutableStateOf(repo.status()) }
    val installed = status as? GameStatus.Ready
    var language by remember { mutableStateOf(settings.language) }
    var showLanguage by remember { mutableStateOf(false) }
    var darkTheme by remember(theme) { mutableStateOf(theme != "light") }
    var confirmingDelete by remember { mutableStateOf(false) }
    var keepAspect by remember { mutableStateOf(settings.keepAspect) }
    var renderer by remember { mutableStateOf(settings.renderer) }
    var showRenderer by remember { mutableStateOf(false) }
    var keepScreenOn by remember { mutableStateOf(settings.keepScreenOn) }
    var updateFound by remember { mutableStateOf<UpdateRelease?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text("Seleccionar idioma") },
            text = {
                Column {
                    listOf(
                        "auto" to ("Automático" to "Según el idioma del teléfono"),
                        "es" to ("Español" to "Texto del juego en español"),
                        "en" to ("English" to "Game text in English"),
                    ).forEachIndexed { index, (mode, titleSubtitle) ->
                        val (title, subtitle) = titleSubtitle
                        ChoiceRow(
                            icon = Icons.Filled.Language,
                            title = title,
                            subtitle = subtitle,
                            selected = language == mode,
                            items = 3,
                            index = index,
                        ) {
                            language = mode
                            settings.language = mode
                            showLanguage = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguage = false }) { Text(LocalLauncherStrings.current.close) } },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("¿Borrar el juego?") },
            text = { Text("Se borra la copia extraída (${formatBytes(installed?.sizeBytes ?: 0)}). Podrás volver a importarlo, pero tus partidas guardadas se pierden.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    scope.launch {
                        repo.deleteGame()
                        status = repo.status()
                        onChanged()
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancelar") } },
        )
    }

    updateFound?.let { release ->
        AlertDialog(
            onDismissRequest = { updateFound = null },
            title = { Text("Actualización disponible") },
            text = { Text("Hay una versión nueva: ${release.version}. Puedes abrir la página de la release para descargarla.") },
            confirmButton = {
                TextButton(onClick = {
                    updateFound = null
                    onOpenUrl(release.pageUrl)
                }) { Text("Ver release") }
            },
            dismissButton = { TextButton(onClick = { updateFound = null }) { Text("Cerrar") } },
        )
    }

    if (desktop) {
        var section by remember { mutableStateOf(0) }
        val sections = listOf(
            Triple("Juego", Icons.Filled.VideogameAsset, "Instalación, idioma, mods y partidas"),
            Triple("Apariencia", Icons.Filled.Palette, "Temas, resolución y pantalla"),
            Triple("Controles", Icons.Filled.SportsEsports, "Controles táctiles, mando y FPS"),
            Triple("Sistema", Icons.Filled.Settings, "Actualizaciones y registros"),
            Triple("Ayuda", Icons.Filled.Info, "Acerca de Reloaded OpenGMK"),
        )
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 10.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "AJUSTES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    sections.forEachIndexed { index, (label, icon, _) ->
                        val selected = section == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else Color.Transparent,
                                )
                                .clickable { section = index }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 900.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Column {
                            Text(
                                sections[section].first,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                sections[section].third,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        when (section) {
                            0 -> {
                                PcSettingsCard("Instalación") {
                                    if (installed == null) {
                                        PcSettingRow(Icons.Filled.Info, "Sin juego instalado", "Impórtalo desde Inicio")
                                    } else {
                                        PcSettingRow(Icons.Filled.VideogameAsset, "Versión", installed.version ?: "sin datos")
                                        PcSettingsDivider()
                                        PcSettingRow(Icons.Filled.Storage, "Tamaño en disco", formatBytes(installed.sizeBytes))
                                        PcSettingsDivider()
                                        PcSettingRow(Icons.Filled.CalendarMonth, "Instalado el", formatDate(installed.installedAt))
                                        PcSettingsDivider()
                                        PcSettingRow(
                                            Icons.Filled.Delete,
                                            "Borrar el juego",
                                            "Libera ${formatBytes(installed.sizeBytes)}",
                                            onClick = { confirmingDelete = true },
                                        )
                                    }
                                }
                                PcSettingsCard("Idioma") {
                                    PcSettingRow(
                                        Icons.Filled.Language,
                                        "Idioma del juego",
                                        when (language) {
                                            "es" -> "Español"
                                            "en" -> "English"
                                            else -> "Automático"
                                        },
                                        onClick = { showLanguage = true },
                                    )
                                }
                                PcSettingsCard("Juego y contenido") {
                                    PcSettingRow(
                                        Icons.Filled.Build,
                                        "Gestionar mods",
                                        "Activa o desactiva los mods instalados",
                                        onClick = onMods,
                                    )
                                    PcSettingsDivider()
                                    PcSettingRow(
                                        Icons.Filled.Save,
                                        "Mis partidas",
                                        if (installed == null) "El juego no está instalado" else "Consulta, importa y exporta tus partidas",
                                        onClick = if (installed != null) onSaves else null,
                                    )
                                }
                            }
                            1 -> {
                                PcSettingsCard("Tema") {
                                    PcSettingRow(
                                        Icons.Filled.Palette,
                                        "Material You",
                                        "Adaptado a los colores del sistema",
                                        trailing = { RadioButton(selected = theme == "material", onClick = null) },
                                        onClick = { onThemeChange("material") },
                                    )
                                    PcSettingsDivider()
                                    PcSettingRow(
                                        Icons.Filled.Palette,
                                        "Liquid Glass",
                                        "Cristal translúcido, reflejos y fondo degradado",
                                        trailing = { RadioButton(selected = theme == "liquid", onClick = null) },
                                        onClick = { onThemeChange("liquid") },
                                    )
                                }
                                PcSettingsCard("Resolución") {
                                    PcSettingRow(
                                        Icons.Filled.Fullscreen,
                                        "Rellenar la pantalla",
                                        "La imagen ocupa toda la ventana",
                                        trailing = { RadioButton(selected = !keepAspect, onClick = null) },
                                        onClick = {
                                            keepAspect = false
                                            settings.keepAspect = false
                                        },
                                    )
                                    PcSettingsDivider()
                                    PcSettingRow(
                                        Icons.Filled.AspectRatio,
                                        "Mantener proporción",
                                        "Aspecto clásico con bandas oscuras",
                                        trailing = { RadioButton(selected = keepAspect, onClick = null) },
                                        onClick = {
                                            keepAspect = true
                                            settings.keepAspect = true
                                        },
                                    )
                                }
                                PcSettingsCard("Gráficos") {
                                    PcSettingRow(
                                        Icons.Filled.Memory,
                                        "Renderizador",
                                        when (renderer) {
                                            "vulkan" -> "${gpuBackendLabel()} (wgpu), experimental"
                                            "opengl" -> "OpenGL, forzado"
                                            else -> "Automático (OpenGL)"
                                        },
                                        onClick = { showRenderer = true },
                                    )
                                }
                                PcSettingsCard("Pantalla") {
                                    PcSettingRow(
                                        Icons.Filled.LightMode,
                                        "Mantener la pantalla encendida",
                                        "Evita que se bloquee mientras juegas",
                                        trailing = {
                                            Switch(checked = keepScreenOn, onCheckedChange = {
                                                keepScreenOn = it
                                                settings.keepScreenOn = it
                                            })
                                        },
                                        onClick = {
                                            keepScreenOn = !keepScreenOn
                                            settings.keepScreenOn = keepScreenOn
                                        },
                                    )
                                }
                            }
                            2 -> {
                                PcSettingsCard("Entrada") {
                                    PcSettingRow(
                                        Icons.Filled.Tune,
                                        "Controles táctiles",
                                        "Estilo, movimiento, transparencia y editores",
                                        onClick = onTouch,
                                    )
                                    PcSettingsDivider()
                                    PcSettingRow(
                                        Icons.Filled.SportsEsports,
                                        "Mando",
                                        "Elige qué botón hace cada tecla y pruébalo",
                                        onClick = onControls,
                                    )
                                }
                                PcSettingsCard("Rendimiento") {
                                    PcSettingRow(
                                        Icons.Filled.Speed,
                                        "FPS",
                                        "Modifica el framerate del juego",
                                        onClick = onFps,
                                    )
                                }
                            }
                            3 -> {
                                PcSettingsCard("Aplicación") {
                                    PcSettingRow(
                                        Icons.Filled.SystemUpdate,
                                        "Versión de la app: $appVersion",
                                        "Actualizaciones: busca, descarga e instala",
                                        onClick = {
                                            if (onUpdates != null) onUpdates()
                                            else scope.launch {
                                                val found = checkForUpdate(appVersion, supportedAbis).getOrNull()
                                                if (found != null) updateFound = found
                                                else snackbar.showSnackbar("Ya tienes la última versión ($appVersion).")
                                            }
                                        },
                                    )
                                    PcSettingsDivider()
                                    PcSettingRow(
                                        Icons.Filled.Article,
                                        "Logs",
                                        "Comparte los registros si algo falla",
                                        onClick = onLogs,
                                    )
                                }
                            }
                            else -> {
                                PcSettingsCard("Reloaded OpenGMK") {
                                    PcSettingRow(Icons.Filled.Info, "Versión $appVersion", "Cliente de escritorio para Pokémon Reloaded")
                                    PcSettingsDivider()
                                    PcSettingRow(Icons.Filled.Article, "Acerca de", "Créditos, licencias y aviso", onClick = onAbout)
                                    PcSettingsDivider()
                                    PcSettingRow(Icons.Filled.Language, "Proyecto", "github.com/Klisee/Reloaded-OpenGMK", onClick = {
                                        onOpenUrl("https://github.com/Klisee/Reloaded-OpenGMK")
                                    })
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
        if (showRenderer) {
            AlertDialog(
                onDismissRequest = { showRenderer = false },
                title = { Text("Renderizador") },
                text = {
                    Column {
                        ChoiceRow(
                            icon = Icons.Filled.Memory,
                            title = "Automático",
                            subtitle = "OpenGL, el backend probado. Es lo que se usa por defecto.",
                            selected = renderer == "auto",
                            items = 3,
                            index = 0,
                        ) { renderer = "auto"; settings.renderer = "auto"; showRenderer = false }
                        ChoiceRow(
                            icon = Icons.Filled.Memory,
                            title = "OpenGL",
                            subtitle = "Fuerza el backend clásico en todas las plataformas.",
                            selected = renderer == "opengl",
                            items = 3,
                            index = 1,
                        ) { renderer = "opengl"; settings.renderer = "opengl"; showRenderer = false }
                        ChoiceRow(
                            icon = Icons.Filled.Memory,
                            title = gpuBackendLabel(),
                            subtitle = "Backend nuevo sobre wgpu. Experimental; puede ir más fino en equipos modernos.",
                            selected = renderer == "vulkan",
                            items = 3,
                            index = 2,
                        ) { renderer = "vulkan"; settings.renderer = "vulkan"; showRenderer = false }
                    }
                },
                confirmButton = { TextButton(onClick = { showRenderer = false }) { Text("Cerrar") } },
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = reloadedTopBarColors(),
                navigationIcon = { CircleBackButton(onClose) },
                title = {
                    Column(Modifier.padding(start = 2.dp)) {
                        Text(
                            "Ajustes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            "Personaliza tu experiencia",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = BottomNavSpace)) },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = BottomNavSpace),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            MaterialSettingsSection("Juego") {
                if (installed == null) {
                    MaterialSettingRow(
                        icon = Icons.Filled.Info,
                        title = "Sin juego instalado",
                        subtitle = "Impórtalo desde Inicio",
                    )
                } else {
                    MaterialSettingRow(
                        icon = Icons.Filled.VideogameAsset,
                        title = "Versión",
                        subtitle = installed.version ?: "sin datos",
                    )
                    MaterialSettingDivider()
                    MaterialSettingRow(
                        icon = Icons.Filled.Storage,
                        title = "Tamaño en disco",
                        subtitle = formatBytes(installed.sizeBytes),
                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    MaterialSettingDivider()
                    MaterialSettingRow(
                        icon = Icons.Filled.CalendarMonth,
                        title = "Instalado el",
                        subtitle = formatDate(installed.installedAt),
                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    MaterialSettingDivider()
                    MaterialSettingRow(
                        icon = Icons.Filled.Delete,
                        title = "Borrar el juego",
                        subtitle = "Libera ${formatBytes(installed.sizeBytes)}",
                        destructive = true,
                        onClick = { confirmingDelete = true },
                    )
                }
            }

            MaterialSettingsSection("Preferencias") {
                MaterialSettingRow(
                    icon = Icons.Filled.Language,
                    title = "Idioma del juego",
                    subtitle = when (language) {
                        "es" -> "Español"
                        "en" -> "English"
                        else -> "Automático"
                    },
                    onClick = { showLanguage = true },
                )
                MaterialSettingDivider()
                MaterialSettingRow(
                    icon = Icons.Filled.Build,
                    title = "Gestionar mods",
                    subtitle = "Activa o desactiva los mods instalados",
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onMods,
                )
                MaterialSettingDivider()
                MaterialSettingRow(
                    icon = Icons.Filled.Save,
                    title = "Mis partidas",
                    subtitle = if (installed == null) "El juego no está instalado" else "Consulta, importa y exporta tus partidas",
                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    enabled = installed != null,
                    onClick = onSaves,
                )
            }

            MaterialSettingsSection("Apariencia") {
                MaterialSettingRow(
                    icon = Icons.Filled.Palette,
                    title = "Material You",
                    subtitle = "Colores adaptados al sistema",
                    trailing = { RadioButton(selected = theme == "material", onClick = null) },
                    onClick = { onThemeChange("material") },
                )
                MaterialSettingDivider()
                MaterialSettingRow(
                    icon = Icons.Filled.Brush,
                    title = "Liquid Glass",
                    subtitle = "Cristal translúcido, reflejos y fondo degradado",
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    trailing = { RadioButton(selected = theme == "liquid", onClick = null) },
                    onClick = { onThemeChange("liquid") },
                )
                MaterialSettingDivider()
                MaterialSettingRow(
                    icon = Icons.Filled.Fullscreen,
                    title = "Rellenar la pantalla",
                    subtitle = "La imagen ocupa todo el móvil, aunque se deforme un poco",
                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    trailing = { RadioButton(selected = !keepAspect, onClick = null) },
                    onClick = {
                        keepAspect = false
                        settings.keepAspect = false
                    },
                )
                MaterialSettingDivider()
                MaterialSettingRow(
                    icon = Icons.Filled.AspectRatio,
                    title = "Mantener la proporción",
                    subtitle = "El aspecto clásico del PC, con bandas oscuras a los lados",
                    trailing = { RadioButton(selected = keepAspect, onClick = null) },
                    onClick = {
                        keepAspect = true
                        settings.keepAspect = true
                    },
                )
                MaterialSettingDivider()
                MaterialSettingRow(
                    icon = Icons.Filled.LightMode,
                    title = "Mantener la pantalla encendida",
                    subtitle = "Evita que el móvil se bloquee mientras juegas",
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    trailing = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = {
                                keepScreenOn = it
                                settings.keepScreenOn = it
                            },
                        )
                    },
                    onClick = {
                        keepScreenOn = !keepScreenOn
                        settings.keepScreenOn = keepScreenOn
                    },
                )
            }

            MaterialSettingsSection("Gráficos") {
                MaterialSettingRow(
                    icon = Icons.Filled.Memory,
                    title = "Renderizador",
                    subtitle = when (renderer) {
                        "vulkan" -> "${gpuBackendLabel()} (wgpu), experimental"
                        "opengl" -> "OpenGL, forzado"
                        else -> "Automático (OpenGL)"
                    },
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { showRenderer = true },
                )
            }

            MaterialSettingsSection("Controles") {
                MaterialSettingRow(
                    icon = Icons.Filled.Tune,
                    title = "Controles táctiles",
                    subtitle = "Estilo, movimiento, transparencia y editores",
                    onClick = onTouch,
                )
                MaterialSettingDivider()
                MaterialSettingRow(
                    icon = Icons.Filled.SportsEsports,
                    title = "Mando",
                    subtitle = "Elige qué botón hace cada tecla y pruébalo",
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onControls,
                )
                MaterialSettingDivider()
                MaterialSettingRow(
                    icon = Icons.Filled.Speed,
                    title = "FPS",
                    subtitle = "Modifica el framerate del juego a tu gusto",
                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onFps,
                )
            }

            MaterialSettingsSection("Sistema") {
                MaterialSettingRow(
                    icon = Icons.Filled.SystemUpdate,
                    title = "Versión de la app: $appVersion",
                    subtitle = "Actualizaciones: busca, descarga e instala",
                    enabled = true,
                    onClick = {
                        if (onUpdates != null) onUpdates()
                        else scope.launch {
                            val found = checkForUpdate(appVersion, supportedAbis).getOrNull()
                            if (found != null) updateFound = found
                            else snackbar.showSnackbar("Ya tienes la última versión ($appVersion).")
                        }
                    },
                )
                MaterialSettingDivider()
                MaterialSettingRow(
                    icon = Icons.Filled.Article,
                    title = "Logs",
                    subtitle = "Comparte los registros si algo falla",
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onLogs,
                )
                MaterialSettingDivider()
                MaterialSettingRow(
                    icon = Icons.Filled.Info,
                    title = "Acerca de",
                    subtitle = "Versión, créditos y aviso",
                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onAbout,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
        if (showRenderer) {
            AlertDialog(
                onDismissRequest = { showRenderer = false },
                title = { Text("Renderizador") },
                text = {
                    Column {
                        ChoiceRow(
                            icon = Icons.Filled.Memory,
                            title = "Automático",
                            subtitle = "OpenGL, el backend probado. Es lo que se usa por defecto.",
                            selected = renderer == "auto",
                            items = 3,
                            index = 0,
                        ) { renderer = "auto"; settings.renderer = "auto"; showRenderer = false }
                        ChoiceRow(
                            icon = Icons.Filled.Memory,
                            title = "OpenGL",
                            subtitle = "Fuerza el backend clásico en todas las plataformas.",
                            selected = renderer == "opengl",
                            items = 3,
                            index = 1,
                        ) { renderer = "opengl"; settings.renderer = "opengl"; showRenderer = false }
                        ChoiceRow(
                            icon = Icons.Filled.Memory,
                            title = gpuBackendLabel(),
                            subtitle = "Backend nuevo sobre wgpu. Experimental; puede ir más fino en móviles modernos.",
                            selected = renderer == "vulkan",
                            items = 3,
                            index = 2,
                        ) { renderer = "vulkan"; settings.renderer = "vulkan"; showRenderer = false }
                    }
                },
                confirmButton = { TextButton(onClick = { showRenderer = false }) { Text("Cerrar") } },
            )
        }
    }
}

@Composable
private fun CircleBackButton(onClick: () -> Unit) {
    Box(modifier = Modifier.padding(start = 6.dp, end = 0.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun MaterialSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun MaterialSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    destructive: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val container = if (destructive) MaterialTheme.colorScheme.errorContainer else iconContainer
    val tint = if (destructive) MaterialTheme.colorScheme.onErrorContainer else iconTint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else .5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = container, modifier = Modifier.size(42.dp)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp),
                tint = tint,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MaterialSettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavesScreen(
    repo: GameStore,
    onPickZip: ((PickedFile?) -> Unit) -> Unit,
    onSaveFile: ((String, ByteArray) -> Unit)?,
    onBack: (() -> Unit)?,
    desktopUi: Boolean = false,
) {
    var slots by remember { mutableStateOf<List<SaveSlot>?>(null) }
    var revision by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(revision) {
        slots = withContext(Dispatchers.IO) { repo.saveSlots() }
    }

    fun importInto(index: Int) {
        onPickZip { file ->
            if (file == null) return@onPickZip
            scope.launch {
                val result = withContext(Dispatchers.IO) { repo.importSlot(file, index) }
                revision++
                snackbar.showSnackbar(result.fold({ "Partida importada en la ranura $index." }, { "No pude importar: ${it.message}" }))
            }
        }
    }

    fun exportAll() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { repo.exportSaves() }
            result.onSuccess { file ->
                if (onSaveFile != null) {
                    onSaveFile("partidas-reloaded.zip", file.readBytes())
                    snackbar.showSnackbar("Elige dónde guardar las partidas.")
                } else {
                    snackbar.showSnackbar("Partidas exportadas.")
                }
            }.onFailure { snackbar.showSnackbar("No pude exportar: ${it.message}") }
        }
    }

    fun importAll() {
        onPickZip { file ->
            if (file == null) return@onPickZip
            scope.launch {
                val result = withContext(Dispatchers.IO) { repo.importSaves(file) }
                revision++
                snackbar.showSnackbar(
                    result.fold({ "Partidas, equipos y personajes restaurados." }, { "No pude importar: ${it.message}" }),
                )
            }
        }
    }

    deleting?.let { index ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Borrar la partida") },
            text = { Text("¿Seguro que quieres borrar la ranura $index? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { repo.deleteSlot(index) }
                        revision++
                        snackbar.showSnackbar(result.fold({ "Partida $index borrada." }, { "No pude borrar: ${it.message}" }))
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = reloadedTopBarColors(),
                title = { Text("Mis partidas", modifier = Modifier.padding(start = 2.dp)) },
                navigationIcon = { onBack?.let { ReloadedBackButton(it) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = 16.dp)) },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val list = slots
            if (list == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Leyendo las partidas…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            val used = list.count { it.name != null }
            SavesHero(
                used = used,
                total = list.size,
                onExportAll = { exportAll() },
                onImportAll = { importAll() },
            )

            Spacer(Modifier.height(2.dp))
            Text(
                "RANURAS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            if (desktopUi) {
                BoxWithConstraints {
                    val columns = if (maxWidth >= 1100.dp) 3 else if (maxWidth >= 720.dp) 2 else 1
                    val gap = 14.dp
                    val cardWidth = (maxWidth - gap * (columns - 1)) / columns
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        list.chunked(columns).forEach { rowSlots ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                rowSlots.forEach { slot ->
                                    SaveSlotCard(
                                        slot = slot,
                                        art = remember(slot) { repo.slotArt(slot) },
                                        onExport = {
                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) { repo.exportSlot(slot.index) }
                                                result.onSuccess { file ->
                                                    if (onSaveFile != null) {
                                                        onSaveFile("partida${slot.index}.zip", file.readBytes())
                                                        snackbar.showSnackbar("Elige dónde guardar la partida.")
                                                    } else {
                                                        snackbar.showSnackbar("Partida exportada.")
                                                    }
                                                }.onFailure { snackbar.showSnackbar("No pude exportar: ${it.message}") }
                                            }
                                        },
                                        onImport = { importInto(slot.index) },
                                        onDelete = { deleting = slot.index },
                                        modifier = Modifier.width(cardWidth),
                                        wide = columns > 1,
                                    )
                                }
                                while (rowSlots.size < columns) {
                                    Spacer(Modifier.width(cardWidth))
                                    break
                                }
                            }
                        }
                    }
                }
            } else {
                list.forEach { slot ->
                    SaveSlotCard(
                        slot = slot,
                        art = remember(slot) { repo.slotArt(slot) },
                        onExport = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { repo.exportSlot(slot.index) }
                                result.onSuccess { file ->
                                    if (onSaveFile != null) {
                                        onSaveFile("partida${slot.index}.zip", file.readBytes())
                                        snackbar.showSnackbar("Elige dónde guardar la partida.")
                                    } else {
                                        snackbar.showSnackbar("Partida exportada.")
                                    }
                                }.onFailure { snackbar.showSnackbar("No pude exportar: ${it.message}") }
                            }
                        },
                        onImport = { importInto(slot.index) },
                        onDelete = { deleting = slot.index },
                    )
                }
            }
            Spacer(Modifier.height(BottomNavSpace))
        }
    }
}

@Composable
private fun SavesHero(
    used: Int,
    total: Int,
    onExportAll: () -> Unit,
    onImportAll: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (used == 0) "Sin partidas guardadas" else "$used de $total ranuras en uso",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Las partidas viven en la carpeta del juego. Puedes copiarlas o restaurarlas cuando quieras.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            TextButton(onClick = onImportAll) { Text("Importar todo") }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = onExportAll, shape = RoundedCornerShape(10.dp)) { Text("Exportar todo") }
        }
    }
}

@Composable
private fun SaveSlotCard(
    slot: SaveSlot,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    art: SlotArt = SlotArt(),
    wide: Boolean = false,
) {
    val filled = slot.name != null
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val portraitSize = if (wide) 104.dp else 84.dp
    val pkmnSize = if (wide) 84.dp else 64.dp

    Surface(
        shape = shape,
        color = if (filled) scheme.surfaceContainerLow else scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = if (filled) 0.9f else 0.55f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SpriteFrame(
                    path = art.portraitPath,
                    fallback = Icons.Filled.Person,
                    size = portraitSize,
                    contentDescription = "Protagonista de la ranura ${slot.index}",
                )
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "RANURA ${slot.index}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (filled) slot.name.orEmpty() else "Vacía",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (filled) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (filled) {
                        SlotInfoRow(Icons.Filled.Place, "Ubicación", slot.place.orEmpty().ifBlank { "—" })
                        SlotInfoRow(Icons.Filled.CatchingPokemon, "Vistos", slot.seen ?: "—")
                        SlotInfoRow(Icons.Filled.Favorite, "Capturados", slot.caught ?: "—")
                    } else {
                        Text(
                            "No hay ninguna partida en esta ranura.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    SpriteFrame(
                        path = if (filled) art.pkmnPath else null,
                        fallback = Icons.Filled.CatchingPokemon,
                        size = pkmnSize,
                        contentDescription = "Pokémon actual de la ranura ${slot.index}",
                    )
                    if (filled) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Borrar la ranura ${slot.index}",
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = scheme.outlineVariant.copy(alpha = 0.6f),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onImport, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Importar")
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onExport, enabled = filled, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar")
                }
                Spacer(Modifier.weight(1f))
                if (filled) {
                    Text(
                        "Guardada",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 88.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpriteFrame(
    path: String?,
    fallback: ImageVector,
    size: Dp,
    contentDescription: String?,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.size(size),
    ) {
        Box(Modifier.fillMaxSize().padding(size * 0.09f), contentAlignment = Alignment.Center) {
            if (path != null) {
                AsyncImage(
                    model = path,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    fallback,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(size * 0.42f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModsScreen(
    repo: GameStore,
    onPickZip: ((PickedFile?) -> Unit) -> Unit,
    onBack: (() -> Unit)?,
) {
    var revision by remember { mutableStateOf(0) }
    var enabled by remember(revision) { mutableStateOf(repo.modsEnabled()) }
    val mods = remember(revision) { repo.mods() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = reloadedTopBarColors(),
                title = { Text("Mods", modifier = Modifier.padding(start = 2.dp)) },
                navigationIcon = { onBack?.let { ReloadedBackButton(it) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = 16.dp)) },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup("General") {
                SwitchRow(
                    icon = Icons.Filled.Build,
                    title = "Activar mods",
                    subtitle = if (enabled) "Los mods se cargan al iniciar el juego" else "El juego arranca sin mods",
                    checked = enabled,
                    items = 1,
                    index = 0,
                    onCheckedChange = {
                        enabled = it
                        repo.setModsEnabled(it)
                        revision++
                    },
                )
            }
            Button(
                onClick = {
                    onPickZip { file ->
                        if (file == null) return@onPickZip
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { repo.importMod(file) }
                            revision++
                            snackbar.showSnackbar(result.fold({ "Mod instalado." }, { "No pude importar: ${it.message}" }))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) { Text("Importar mod .zip") }

            if (enabled) {
                if (mods.isEmpty()) {
                    Text("No hay mods instalados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SettingsGroup("Instalados") {
                        mods.forEachIndexed { index, mod ->
                            SwitchRow(
                                icon = Icons.Filled.Build,
                                title = mod.name,
                                subtitle = buildString {
                                    if (mod.author.isNotBlank()) append("De ${mod.author}. ")
                                    append(mod.description.ifBlank { mod.id })
                                },
                                checked = mod.enabled,
                                items = mods.size,
                                index = index,
                                onCheckedChange = {
                                    repo.setModEnabled(mod, it)
                                    revision++
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(BottomNavSpace))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TouchScreen(
    settings: SettingsStore,
    onEditor: () -> Unit,
    onAppearance: () -> Unit,
    onAssets: () -> Unit,
    onBack: () -> Unit,
) {
    var appearance by remember { mutableStateOf(settings.touchAppearance) }
    var move by remember { mutableStateOf(settings.moveMode) }
    var scale by remember { mutableStateOf(settings.touchScale) }
    var opacity by remember { mutableStateOf(settings.touchOpacity) }

    fun saveAppearance(next: TouchAppearance) {
        appearance = next
        settings.touchAppearance = next
        settings.touchStyle = if (next.style == TouchStyle.ORIGINAL) "modern" else "classic"
        if (next.style == TouchStyle.ORIGINAL) {
            move = "joystick"
            settings.moveMode = move
        } else {
            move = "dpad"
            settings.moveMode = move
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = reloadedTopBarColors(),
                title = { Text("Controles tactiles") },
                navigationIcon = { ReloadedBackButton(onBack) },
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (appearance.style != TouchStyle.ORIGINAL) {
                SettingsGroup("Movimiento") {
                    ChoiceRow(
                        icon = Icons.Filled.SportsEsports,
                        title = "Joystick",
                        subtitle = "Palanca analógica: mueve en cualquier dirección",
                        selected = move == "joystick",
                        items = 2,
                        index = 0,
                    ) { move = "joystick"; settings.moveMode = move }
                    ChoiceRow(
                        icon = Icons.Filled.SportsEsports,
                        title = "Cruceta",
                        subtitle = "Cruceta de cuatro direcciones, con vibración al marcar",
                        selected = move == "dpad",
                        items = 2,
                        index = 1,
                    ) { move = "dpad"; settings.moveMode = move }
                }
            }

            SettingsGroup("Estilo visual") {
                TouchStyle.entries.forEachIndexed { index, style ->
                    ChoiceRow(
                        icon = Icons.Filled.VideogameAsset,
                        title = style.title,
                        subtitle = when (style) {
                            TouchStyle.ORIGINAL -> "Sprites originales del juego"
                            TouchStyle.MINIMAL -> "Botones suaves con anillos y etiquetas limpias"
                            TouchStyle.PIXEL -> "Botones con relieve estilo Pixel"
                        },
                        selected = appearance.style == style,
                        items = TouchStyle.entries.size,
                        index = index,
                    ) { saveAppearance(appearance.copy(style = style)) }
                }
            }

            SettingsGroup("Transparencia y tamaño") {
                SliderRow(
                    icon = Icons.Filled.Opacity,
                    title = "Opacidad de los botones",
                    subtitle = "${(opacity * 100).roundToInt()} %",
                    value = opacity,
                    items = 2,
                    index = 0,
                ) { opacity = it; settings.touchOpacity = it }
                SliderRow(
                    icon = Icons.Filled.Fullscreen,
                    title = "Tamaño de los botones",
                    subtitle = "${(scale * 100).roundToInt()} %",
                    value = scale,
                    items = 2,
                    index = 1,
                ) { scale = it; settings.touchScale = it }
            }

            SettingsGroup("Editores") {
                NavRow(
                    icon = Icons.Filled.Tune,
                    title = "Posiciones y tamaño",
                    subtitle = "Arrastra y redimensiona por perfil (Exploración, Combate...)",
                    items = 3,
                    index = 0,
                    onClick = onEditor,
                )
                NavRow(
                    icon = Icons.Filled.VideogameAsset,
                    title = "Sprites y paquetes",
                    subtitle = "Cambia la imagen de un botón o importa un pack",
                    items = 3,
                    index = 1,
                    onClick = onAssets,
                )
                NavRow(
                    icon = Icons.Filled.Settings,
                    title = "Animaciones y vibración",
                    subtitle = "Ajustes finos de la apariencia",
                    items = 3,
                    index = 2,
                    onClick = onAppearance,
                )
            }

            Text(
                "El botón SEL aparece en exploración (movimientos) y en combate (equipo) con el estilo clásico. Los cambios se aplican al volver a entrar al juego.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FpsScreen(settings: SettingsStore, refreshRate: Float, onBack: () -> Unit) {
    var fps by remember { mutableStateOf(settings.fpsMode) }
    var showFps by remember { mutableStateOf(settings.showFps) }
    val supports60 = refreshRate >= 59.5f
    val supports120 = refreshRate >= 119.5f
    val scheme = MaterialTheme.colorScheme
    val detectedHz = refreshRate.roundToInt()

    fun select(mode: String) {
        fps = mode
        settings.fpsMode = mode
    }

    val recommended = when {
        refreshRate >= 119.5f -> "120"
        refreshRate >= 59.5f -> if (refreshRate >= 75f) "uncapped" else "60"
        else -> "original"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = reloadedTopBarColors(),
                title = { Text("FPS", modifier = Modifier.padding(start = 2.dp)) },
                navigationIcon = { ReloadedBackButton(onBack) },
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FpsScreenPanel(
                refreshRate = refreshRate,
                mode = fps,
                supports60 = supports60,
                supports120 = supports120,
                recommended = recommended,
                onAutoAdjust = { select(recommended) },
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = scheme.surfaceContainerLow,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = scheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Mostrar FPS en el juego", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Superpone un contador en la esquina del juego.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = showFps, onCheckedChange = { showFps = it; settings.showFps = it })
                }
            }

            Text(
                "LÍMITE DE FOTOGRAMAS",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FpsModeCard(
                    title = "Original",
                    value = "30 FPS",
                    description = "El ritmo clásico de Pokémon Reloaded. Todo se mueve como en la versión de Game Maker 8.",
                    selected = fps == "original",
                    enabled = true,
                    badge = "Recomendado",
                ) { select("original") }
                FpsModeCard(
                    title = "Fluido",
                    value = "60 FPS",
                    description = "El doble de fotogramas. Animaciones más suaves; el motor lo soporta de forma experimental.",
                    selected = fps == "60",
                    enabled = true,
                    badge = "Experimental",
                ) { select("60") }
                FpsModeCard(
                    title = "Muy fluido",
                    value = "120 FPS",
                    description = "Fuerza 120 fotogramas por segundo. En pantallas de menos Hz puede provocar tirones; pruébalo.",
                    selected = fps == "120",
                    enabled = true,
                    badge = "Experimental",
                ) { select("120") }
                FpsModeCard(
                    title = "Máximo de pantalla",
                    value = "$detectedHz FPS",
                    description = "Corre justo a la frecuencia de tu pantalla ($detectedHz Hz), sin pasarse ni quedarse corto.",
                    selected = fps == "uncapped",
                    enabled = true,
                    badge = "Experimental",
                ) { select("uncapped") }
            }

            if (fps != "original") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Los modos por encima de 30 FPS son experimentales: si notas aceleraciones, velocidad extra o fallos, vuelve a Original y repórtalo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(BottomNavSpace))
        }
    }
}

@Composable
private fun FpsScreenPanel(
    refreshRate: Float,
    mode: String,
    supports60: Boolean,
    supports120: Boolean,
    recommended: String,
    onAutoAdjust: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hz = refreshRate.roundToInt()
    val active = when (mode) {
        "60" -> minOf(60, hz)
        "120" -> minOf(120, hz)
        "uncapped" -> hz
        else -> 30
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "PANTALLA DETECTADA",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$hz",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Hz",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = scheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Jugando a $active FPS",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            FpsGauge(refreshRate = refreshRate)
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ajusta el límite a lo que detectó tu pantalla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = onAutoAdjust, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (mode == recommended) "Ya ajustado a $hz Hz" else "Usar $hz Hz")
                }
            }
        }
    }
}

@Composable
private fun FpsGauge(refreshRate: Float) {
    val detected = refreshRate.roundToInt()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FpsGaugeStep("30", "Hz", true, Modifier.weight(1f))
        FpsGaugeStep("60", "Hz", true, Modifier.weight(1f))
        FpsGaugeStep("120", "Hz", true, Modifier.weight(1f))
        FpsGaugeStep("$detected", "Tu pantalla", true, Modifier.weight(1f))
    }
}

@Composable
private fun FpsGaugeStep(label: String, caption: String, supported: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (supported) scheme.primary.copy(alpha = 0.14f) else scheme.surfaceContainerHighest,
        modifier = modifier.height(58.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (supported) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = if (supported) scheme.onSurfaceVariant else scheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun FpsModeCard(
    title: String,
    value: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    badge: String?,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val border = when {
        selected -> scheme.primary.copy(alpha = 0.7f)
        else -> scheme.outlineVariant.copy(alpha = 0.7f)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onSelect)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = scheme.surfaceContainerHighest,
                        ) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) scheme.onSurfaceVariant else scheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.width(10.dp))
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsScreen(repo: GameStore, revision: Int, onSettings: () -> Unit, onShare: ((String) -> Unit)?) {
    var text by remember { mutableStateOf("Cargando…") }
    var nonce by remember { mutableStateOf(0) }

    LaunchedEffect(revision, nonce) {
        text = withContext(Dispatchers.IO) { readLog(repo) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = reloadedTopBarColors(),
                title = { Text("Logs") },
                actions = {
                    IconButton(onClick = { nonce++ }) { Icon(Icons.Filled.Refresh, "Actualizar") }
                    if (onShare != null) {
                        IconButton(onClick = { onShare(text) }) { Icon(Icons.Filled.Share, "Compartir") }
                    }
                    CircleIconButton(Icons.Filled.Settings, "Ajustes", onSettings)
                    Spacer(Modifier.width(6.dp))
                },
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(
                text,
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).padding(bottom = BottomNavSpace).verticalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineScreen(
    engine: OnlineEngineStore?,
    repo: GameStore,
    onLaunch: () -> Unit,
    onBack: () -> Unit,
) {
    var config by remember(engine) {
        mutableStateOf((engine?.load() ?: OnlineEngineConfig()).normalized().withProtocol(OnlineEngineConfig.PROTOCOL_ZIBELAN))
    }
    var error by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Leyendo el estado del motor…") }
    var statusNonce by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    val installed = remember { repo.status() is GameStatus.Ready }
    val scheme = MaterialTheme.colorScheme
    val display = reloadedDisplayFont()
    val haptics = rememberInterfaceHaptics()

    LaunchedEffect(statusNonce) {
        statusText = withContext(Dispatchers.IO) { onlineEngineEvents(readLog(repo)) }
    }

    val stateLabel = when {
        engine == null -> "No disponible en esta plataforma"
        !installed -> "Juego no instalado"
        !config.enabled -> "Desactivado"
        else -> "Activado"
    }
    val stateColor = when {
        engine == null || !installed -> scheme.error
        config.enabled -> scheme.primary
        else -> scheme.outline
    }

    fun store(update: OnlineEngineConfig, launch: Boolean = false) {
        val store = engine ?: return
        val next = update.normalized()
        if (launch && !installed) {
            error = "Primero instala el juego para jugar en línea."
            return
        }
        store.save(next).fold(
            onSuccess = {
                config = next
                error = null
                saved = true
                if (launch) onLaunch()
            },
            onFailure = { error = it.message ?: "No pude guardar la configuración." },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = reloadedTopBarColors(),
                title = {
                    Text(
                        "Reloaded Online",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                },
                navigationIcon = { CircleBackButton(onBack) },
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = BottomNavSpace),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(4.dp))

            // === Tarjeta principal con estado ===
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (config.enabled) scheme.primaryContainer else scheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    if (config.enabled) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            ) {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (config.enabled) {
                                scheme.primary.copy(alpha = 0.20f)
                            } else {
                                scheme.surfaceContainerHighest
                            },
                            modifier = Modifier.size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp),
                                    tint = if (config.enabled) scheme.primary else scheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Reloaded Online",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = display,
                                    fontWeight = FontWeight.Black,
                                ),
                                color = if (config.enabled) scheme.onPrimaryContainer else scheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Mundo compartido",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (config.enabled) {
                                    scheme.onPrimaryContainer.copy(alpha = 0.7f)
                                } else {
                                    scheme.onSurfaceVariant
                                },
                            )
                        }
                    }

                    Text(
                        "Juega con otras personas en el mismo mapa. El motor dibuja a los demás jugadores y mantiene el mundo sincronizado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (config.enabled) {
                            scheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            scheme.onSurfaceVariant
                        },
                    )

                    HorizontalDivider(
                        color = if (config.enabled) {
                            scheme.onPrimaryContainer.copy(alpha = 0.15f)
                        } else {
                            scheme.outlineVariant.copy(alpha = 0.6f)
                        },
                    )

                    // Estado + interruptor grande
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(stateColor),
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    stateLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (config.enabled) scheme.onPrimaryContainer else scheme.onSurface,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (config.enabled) {
                                    "Se conectará automáticamente al abrir el juego."
                                } else {
                                    "Actívalo para conectar al iniciar el juego."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (config.enabled) {
                                    scheme.onPrimaryContainer.copy(alpha = 0.7f)
                                } else {
                                    scheme.onSurfaceVariant
                                },
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = {
                                haptics.confirm()
                                store(config.copy(enabled = it))
                            },
                            modifier = Modifier.scale(1.15f),
                        )
                    }
                }
            }

            // === Nombre personalizado ===
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "TU NOMBRE EN EL MUNDO",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = scheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(14.dp))
                        BasicTextField(
                            value = config.name,
                            onValueChange = {
                                config = config.copy(name = it.take(16))
                                saved = false
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                            decorationBox = { inner ->
                                if (config.name.isBlank()) {
                                    Text(
                                        "Escribe tu nombre…",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                                inner()
                            },
                        )
                        Text(
                            "${config.name.length}/16",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Así te verán los demás jugadores. Se guarda al instante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            // === Estado guardado / error ===
            if (error != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = scheme.onErrorContainer, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(error!!, style = MaterialTheme.typography.bodySmall, color = scheme.onErrorContainer)
                    }
                }
            } else if (saved) {
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Cambios guardados",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                    )
                }
            }

            // === Botones ===
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { haptics.confirm(); store(config, launch = true) },
                    enabled = installed && engine != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primaryFixed,
                        contentColor = scheme.onPrimaryFixed,
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "Guardar y jugar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(
                    onClick = { store(config) },
                    enabled = engine != null,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Guardar sin jugar")
                }
                if (!installed) {
                    Text(
                        "Instala el juego para poder jugar en línea.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // === Detalles técnicos (plegables) ===
            var showDetails by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = { showDetails = !showDetails },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (showDetails) "Ocultar detalles" else "Detalles técnicos")
                }
                AnimatedVisibility(visible = showDetails) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Últimos eventos del motor",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { statusNonce++ }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Actualizar", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant,
                            )
                            HorizontalDivider()
                            Text(
                                "Servidor: ${if (config.server == OnlineEngineConfig.DEFAULT_ZIBELAN_SERVER) "comunitario de Zibelan" else "personalizado"} · Sala: ${config.room.ifBlank { "mundo" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(
                "El servidor Zibelan lo aloja la comunidad. Reloaded Online solo guarda tu nombre y la conexión; el motor nativo se encarga del resto.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
    }
}

private fun onlineEngineEvents(log: String): String {
    val events = log.lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.contains("[reloaded-online]") ||
                line.contains("[bridge]") ||
                line.contains("[online-menu]")
        }
        .map { if (it.length > 220) it.takeLast(220) else it }
        .toList()
    return if (events.isEmpty()) {
        "Sin eventos online recientes. El motor los escribe al conectar."
    } else {
        events.takeLast(4).joinToString("\n")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(appVersion: String, logo: Painter?, onOpenUrl: (String) -> Unit, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopAppBar(
                colors = reloadedTopBarColors(),
                title = { Text("Acerca de", modifier = Modifier.padding(start = 2.dp)) },
                navigationIcon = { ReloadedBackButton(onBack) },
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = BottomNavSpace),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            AboutHeader(appVersion = appVersion, logo = logo)

            AboutSection("EL JUEGO") {
                AboutCard {
                    AboutTextRow(
                        "Pokémon Reloaded es un fangame hecho con Game Maker 8 en 2008, con más de una " +
                            "década de actualizaciones. Todo su contenido pertenece a sus autores.",
                    )
                }
            }

            AboutSection("EL MOTOR") {
                AboutCard {
                    AboutPersonRow(
                        icon = Icons.Filled.Memory,
                        title = "OpenGMK",
                        subtitle = "Reimplementación nativa en Rust",
                        detail = "Código abierto",
                    )
                    AboutDivider()
                    AboutTextRow(
                        "En lugar de emular Windows, traduce los datos del Game Maker 8 a instrucciones " +
                            "nativas. Por eso el juego carga rápido y puede correr por encima de los 30 FPS.",
                    )
                }
            }

            AboutSection("ESTE LAUNCHER") {
                AboutCard {
                    AboutPersonRow(
                        icon = Icons.Filled.Favorite,
                        title = "Reloaded OpenGMK",
                        subtitle = "Launcher comunitario",
                        detail = "Sin ánimo de lucro",
                    )
                    AboutDivider()
                    AboutTextRow(
                        "Interfaz, importación del juego, partidas, mods y ajustes. " +
                            "Disponible para Android, Windows y Linux.",
                    )
                }
            }

            AboutSection("CÓDIGO Y LICENCIAS") {
                AboutCard {
                    AboutLicenseRow(
                        icon = Icons.Filled.Brush,
                        title = "Tipografía",
                        detail = "Google Sans Flex",
                        license = "SIL OFL",
                    )
                    AboutDivider()
                    AboutLicenseRow(
                        icon = Icons.Filled.Opacity,
                        title = "Liquid Glass",
                        detail = "Backdrop de Kyant0",
                        license = "Apache 2.0",
                    )
                    AboutDivider()
                    AboutLicenseRow(
                        icon = Icons.Filled.CatchingPokemon,
                        title = "Pokémon",
                        detail = "Nintendo, Game Freak y Creatures",
                        license = "Marcas de sus autores",
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onOpenUrl("https://github.com/Klisee/Reloaded-OpenGMK") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Ver código")
                    }
                    OutlinedButton(
                        onClick = { onOpenUrl("https://www.gnu.org/licenses/old-licenses/gpl-2.0.html") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Licencia GPL")
                    }
                }
                OutlinedButton(
                    onClick = { onOpenUrl("https://github.com/OpenGMK/OpenGMK") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Ver el motor OpenGMK")
                }
            }

            AboutSection("VERSIÓN") {
                AboutCard {
                    AboutVersionRow("Launcher", appVersion)
                    AboutDivider()
                    AboutVersionRow("Plataforma", platformLabel())
                    AboutDivider()
                    AboutVersionRow("Motor", "OpenGMK · Rust")
                }
            }

            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            Text(
                "Reloaded OpenGMK es un launcher no oficial para Pokemon Reloaded, no estamos afiliados a GameFreak, ni a Fer Productions",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AboutHeader(appVersion: String, logo: Painter?) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = scheme.primaryContainer,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (logo != null) {
                        Image(
                            painter = logo,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Filled.CatchingPokemon,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Reloaded OpenGMK",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Launcher para Pokémon Reloaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AboutBadge("v$appVersion")
                    AboutBadge(platformLabel())
                    AboutBadge("Beta")
                }
            }
        }
    }
}

@Composable
private fun AboutBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp),
        )
        content()
    }
}

@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(4.dp), content = content)
    }
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun AboutPersonRow(icon: ImageVector, title: String, subtitle: String, detail: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(detail, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutTextRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun AboutLicenseRow(icon: ImageVector, title: String, detail: String, license: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = scheme.surfaceContainerHighest,
        ) {
            Text(
                license,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AboutVersionRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
    }
}
