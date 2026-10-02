package com.risediary.app.ui.update

import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import com.mikepenz.markdown.model.MarkdownState
import com.risediary.app.ui.components.springAnimateToPage
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.risediary.app.R
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.update.*
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles

/** The root owns this host and the VM; navigation destinations only request it. */
@Composable
fun UpdateSheetHost(
    viewModel: UpdateViewModel,
    interactionsBlocked: Boolean,
    presentation: UpdateSheetPresentation? = null,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val active = resumed && !interactionsBlocked
    LaunchedEffect(active) {
        if (active) {
            viewModel.checkAtStartup()
            viewModel.refreshDownload()
        }
    }
    LaunchedEffect(active, ui.visible, ui.download.recordOrNull()?.id) {
        while (active && ui.visible && ui.download.recordOrNull() != null) {
            viewModel.refreshDownload()
            delay(1000)
        }
    }

    var awaitingPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        awaitingPermission = false
        viewModel.onInstallPermissionResult(context.packageManager.canRequestPackageInstalls())
    }
    LaunchedEffect(ui.installUri, active, awaitingPermission) {
        val uri = ui.installUri ?: return@LaunchedEffect
        if (!active || awaitingPermission) return@LaunchedEffect
        if (!context.packageManager.canRequestPackageInstalls()) {
            viewModel.consumeInstallRequest()
            awaitingPermission = true
            try {
                permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri()))
            } catch (_: Exception) {
                awaitingPermission = false
                viewModel.consumeInstallRequest()
                viewModel.reportActionError(UpdateError.INSTALL)
            }
        } else {
            viewModel.consumeInstallRequest()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri.toUri(), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (_: Exception) { viewModel.reportActionError(UpdateError.INSTALL) }
        }
    }

    val uriHandler = LocalUriHandler.current
    val openLink: (String) -> Unit = { url ->
        runCatching {
            require(url.toUri().scheme in listOf("https", "http"))
            uriHandler.openUri(url)
        }.onFailure { viewModel.reportActionError(UpdateError.LINK) }
    }
    val notesScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    val windowInfo = LocalWindowInfo.current
    val height = windowInfo.containerDpSize.height * 0.8f
    val windowHeight = windowInfo.containerSize.height
    // One native shell, retained through exit: an automatic update cannot replace a closing developer page.
    var nativeDismissed by remember { mutableStateOf(true) }
    var retainedDeveloper by remember { mutableStateOf(false) }
    var retainedAuthorization by remember { mutableStateOf(false) }
    val developer = ui.developer.visible || retainedDeveloper
    val authorized = if (ui.developer.visible) ui.settings.developerEnabled else retainedAuthorization
    val show = (if (developer) ui.developer.visible else ui.visible) && active
    SideEffect {
        if (ui.developer.visible) { retainedDeveloper = true; retainedAuthorization = ui.settings.developerEnabled }
    }
    LaunchedEffect(ui.developer.visible, retainedDeveloper, nativeDismissed) {
        // A native exit may finish while locked/backgrounded, before an async reset closes the model.
        if (retainedDeveloper && !ui.developer.visible && nativeDismissed) {
            retainedDeveloper = false
            viewModel.onDeveloperDismissFinished()
        }
    }
    val dismissSheet: () -> Unit = { if (developer) viewModel.dismissDeveloper() else viewModel.dismiss() }
    var channelExpanded by remember { mutableStateOf(false) }
    SideEffect {
        if (show) nativeDismissed = false
        presentation?.updateVisibility(show)
        if (!active) presentation?.clearBackPreview()
        if (!show || (!developer && !ui.settingsPage)) channelExpanded = false
    }
    DisposableEffect(presentation) { onDispose { presentation?.reset() } }
    var sheetWindowBounds by remember { mutableStateOf<Rect?>(null) }
    var hostWindowOffset by remember { mutableStateOf(Offset.Zero) }
    // Nested overlays keep their own outside-tap dismissal; only the sheet itself is protected.
    val nestedOverlayOpen by rememberUpdatedState(channelExpanded || ui.confirmCancel || ui.developer.confirmReset)
    val notesTextStyles = MiuixTheme.textStyles
    val notesState = rememberReleaseNotesState(ui.release)
    UpdateSheetTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { hostWindowOffset = it.positionInWindow() }
                .then(if (active && (show || !nativeDismissed)) Modifier.blockOutsideSheetTouches(
                    bounds = { sheetWindowBounds },
                    windowOffset = { hostWindowOffset },
                    allowOutside = { nestedOverlayOpen },
                ) else Modifier),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { _ ->
            OverlayBottomSheet(
                backgroundColor = updateSheetBackground(),
                show = show,
                title = stringResource(if (developer) R.string.developer_title else if (ui.settingsPage) R.string.update_settings_title else R.string.update_sheet_title),
                startAction = {
                    Box(Modifier.padding(start = 12.dp)) {
                        IconButton(
                            onClick = { if (!developer && ui.settingsPage) viewModel.backToUpdate() else dismissSheet() },
                            enabled = show,
                            modifier = Modifier.size(48.dp).testTag("update_start_action"),
                        ) {
                            Icon(if (!developer && ui.settingsPage) AppIcons.ArrowBack else AppIcons.Close,
                                stringResource(if (developer) R.string.developer_close else if (ui.settingsPage) R.string.update_back else R.string.update_close),
                                Modifier.size(24.dp).testTag("update_start_icon"))
                        }
                    }
                },
                endAction = {
                    if (!developer) Box(Modifier.padding(end = 12.dp)) {
                        IconButton(
                            onClick = { if (ui.settingsPage) viewModel.dismiss() else viewModel.showSettings() },
                            enabled = show,
                            modifier = Modifier.size(48.dp).testTag("update_end_action"),
                        ) {
                            Icon(if (ui.settingsPage) AppIcons.Close else AppIcons.Settings,
                                stringResource(if (ui.settingsPage) R.string.update_close else R.string.update_settings_title),
                                Modifier.size(24.dp).testTag("update_end_icon"))
                        }
                    }
                },
                modifier = Modifier.height(height).testTag("update_sheet").onLayoutRectChanged(throttleMillis = 0, debounceMillis = 0) {
                    val position = it.positionInWindow
                    sheetWindowBounds = Rect(position.x.toFloat(), position.y.toFloat(),
                        (position.x + it.width).toFloat(), (position.y + it.height).toFloat())
                    presentation?.onSheetBounds(position.y, it.height, windowHeight)
                },
                insideMargin = DpSize(0.dp, 0.dp),
                enableWindowDim = false,
                enableNestedScroll = false,
                renderInRootScaffold = false,
                onDismissRequest = dismissSheet,
                onDismissFinished = {
                    nativeDismissed = true
                    presentation?.finishDismiss()
                    if (developer && !viewModel.ui.value.developer.visible) {
                        retainedDeveloper = false
                        viewModel.onDeveloperDismissFinished()
                    }
                },
            ) {
                // Observe the shared dispatcher without registering another consuming back handler.
                ObserveUpdateSheetBack(presentation,
                    closesSheet = show && (developer || !ui.settingsPage) && !channelExpanded && !ui.confirmCancel && !ui.developer.confirmReset)
                // Native handle/back dismissal remains; settings back returns to notes.
                NavigationBackHandler(
                    state = rememberNavigationEventState(NavigationEventInfo.None),
                    isBackEnabled = show && !developer && ui.settingsPage && !channelExpanded && !ui.confirmCancel,
                    onBackCompleted = viewModel::backToUpdate,
                )
                if (developer) DeveloperSheetContent(ui, viewModel, authorized, show) { channelExpanded = it }
                else UpdateSheetContent(ui, viewModel, notesScroll, settingsScroll, openLink, show, notesTextStyles, notesState) {
                    channelExpanded = it
                }
            }
            DeveloperResetDialog(show && developer && ui.developer.confirmReset, viewModel)
            OverlayDialog(
                show = show && !developer && ui.confirmCancel,
                renderInRootScaffold = false,
                title = stringResource(R.string.update_cancel_title),
                onDismissRequest = viewModel::keepDownloading,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.update_cancel_message))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            text = stringResource(R.string.update_continue_download),
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::keepDownloading,
                        )
                        TextButton(
                            text = stringResource(R.string.update_cancel_download),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = viewModel::confirmCancel,
                        )
                    }
                }
            }
        }
    }
}

/** Consume only gestures starting outside the sheet, before native tap detection sees them.
 * The modifier belongs to the local Scaffold, which also parents its native popup host.
 * It is removed after native exit so it cannot intercept the original page afterwards.
 */
private fun Modifier.blockOutsideSheetTouches(
    bounds: () -> Rect?,
    windowOffset: () -> Offset,
    allowOutside: () -> Boolean,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!allowOutside() && bounds()?.contains(down.position + windowOffset()) != true) {
            down.consume()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
/** Keep application typography; only the native sheet header uses the agreed 18sp. */
@Composable
private fun UpdateSheetTheme(content: @Composable () -> Unit) {
    val styles = MiuixTheme.textStyles
    MiuixTheme(textStyles = styles.copy(title4 = styles.title4.copy(fontSize = 18.sp)), content = content)
}
@Composable
private fun ObserveUpdateSheetBack(presentation: UpdateSheetPresentation?, closesSheet: Boolean) {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val currentClosesSheet by rememberUpdatedState(closesSheet)
    LaunchedEffect(dispatcher, presentation) {
        if (presentation == null || dispatcher == null) return@LaunchedEffect
        var firstTransition = true
        dispatcher.transitionState.collect { transition ->
            when (transition) {
                is NavigationEventTransitionState.InProgress -> presentation.onBackProgress(
                    transition.latestEvent.progress,
                    // Joining an already-running gesture cannot make this new sheet its target.
                    closesSheet = !firstTransition && currentClosesSheet &&
                        transition.direction == NavigationEventTransitionState.TRANSITIONING_BACK,
                )
                is NavigationEventTransitionState.Idle -> presentation.onBackIdle()
            }
            firstTransition = false
        }
    }
    val settlementId = presentation?.settlementId
    LaunchedEffect(presentation, settlementId) {
        if (presentation == null || settlementId == null) return@LaunchedEffect
        // miuix 0.9.4 rebounds for 150ms. Use the same Compose animation clock (including the
        // system duration scale) only to resolve a cancel that produced no new layout rectangle.
        // Bounds drive every visible settle frame; this clock never interpolates the backdrop.
        animate(0f, 1f, animationSpec = tween(150)) { _, _ -> }
        presentation.finishBackSettlement(settlementId)
    }
}

@Composable
private fun UpdateSheetContent(
    ui: UpdateUiState,
    viewModel: UpdateViewModel,
    notesScroll: ScrollState,
    settingsScroll: ScrollState,
    openLink: (String) -> Unit,
    show: Boolean,
    notesTextStyles: TextStyles,
    notesState: MarkdownState?,
    onChannelExpandedChange: (Boolean) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val sheetBackground = updateSheetBackground()
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    var footerHeight by remember { mutableIntStateOf(0) }
    val bottomInset = with(density) { footerHeight.toDp() } + 20.dp
    val targetPage = if (ui.settingsPage) 1 else 0
    val pager = rememberPagerState(initialPage = targetPage) { 2 }
    LaunchedEffect(targetPage, show) {
        if (show) pager.springAnimateToPage(targetPage)
    }
    // The native title row stays fixed. Both full-width pages retain their scroll state.
    Box(Modifier.fillMaxSize().background(sheetBackground).testTag("update_body")) {
        // A stable sibling capture spans the whole viewport, including the text below glass.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(sheetBackground)) {
            HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize().clipToBounds(),
            ) { page ->
                val settingsPage = page == 1
                val interactive = show && settingsPage == ui.settingsPage && !pager.isScrollInProgress
                val pageModifier = Modifier.fillMaxSize()
                    .background(sheetBackground)
                    .then(if (interactive) Modifier else Modifier.clearAndSetSemantics { })
                if (settingsPage) {
                    Column(pageModifier.verticalScroll(settingsScroll, enabled = interactive)
                        .padding(top = 12.dp).navigationBarsPadding()) {
                        UpdateSettingsContent(ui.settings,
                            { if (interactive) viewModel.setAutomaticCheck(it) },
                            { if (interactive) viewModel.setChannel(it) },
                            onChannelExpandedChange, interactive)
                        ui.error?.let {
                            Box(Modifier.padding(horizontal = 28.dp)) { ErrorText(it) }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                } else {
                    Column(pageModifier.testTag("update_notes").verticalScroll(notesScroll, enabled = interactive)
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomInset),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        StatusHeader(ui)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.update_current_version, viewModel.currentVersion),
                                color = colors.onSurfaceVariantSummary)
                            Text(stringResource(R.string.update_latest_version, ui.release?.tagName
                                ?: stringResource(R.string.update_version_unknown)), color = colors.onSurfaceVariantSummary)
                        }
                        if (ui.check == UpdateCheckState.Failed) ErrorText(UpdateError.CHECK)
                        ui.downloadEligibilityError?.takeUnless { it == ui.error }?.let { ErrorText(it) }
                        ui.error?.takeUnless { it == UpdateError.CHECK }?.let { ErrorText(it) }
                        (ui.download as? DownloadState.Failed)?.let { ErrorText(it.reason) }
                        val release = ui.release
                        if (ui.check is UpdateCheckState.Available && release != null && selectReleaseApk(release) == null) {
                            Text(stringResource(R.string.update_apk_unavailable), color = colors.onSurfaceVariantSummary)
                        }
                        if (release != null) {
                            Text(stringResource(R.string.update_notes_title), fontWeight = FontWeight.SemiBold)
                            MiuixTheme(textStyles = notesTextStyles) {
                                ReleaseNotes(release.body, notesState) { if (interactive) openLink(it) }
                            }
                        }
                    }
                }
            }
        }
        // Capture only the body/background for glass sampling, never the buttons themselves.
        AnimatedVisibility(
            visible = !ui.settingsPage,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            UpdateFooter(ui, viewModel, backdrop, openLink, show,
                Modifier.onSizeChanged { footerHeight = it.height })
        }
    }
}

@Composable
private fun UpdateFooter(
    ui: UpdateUiState,
    viewModel: UpdateViewModel,
    backdrop: com.kyant.backdrop.Backdrop,
    openLink: (String) -> Unit,
    interactive: Boolean,
    modifier: Modifier,
) {
    val action = primaryUpdateAction(ui)
    val colors = MiuixTheme.colorScheme
    val enabled = interactive && !ui.settingsPage && action != UpdatePrimaryAction.DISABLED
    Row(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LiquidGlassButton(
            onClick = {
                when (action) {
                    UpdatePrimaryAction.CHECK -> viewModel.openAndCheck()
                    UpdatePrimaryAction.DOWNLOAD -> viewModel.downloadUpdate()
                    UpdatePrimaryAction.CANCEL -> viewModel.requestCancel()
                    UpdatePrimaryAction.INSTALL -> viewModel.prepareInstall()
                    UpdatePrimaryAction.DISABLED -> Unit
                }
            },
            backdrop = backdrop, enabled = enabled, isInteractive = enabled,
            tint = colors.primary.copy(alpha = 0.075f), height = 52.dp,
            modifier = Modifier.weight(1f).testTag("update_download_action"),
        ) {
            // The key excludes progress: percentages change in-place, without crossfading each second.
            val label = primaryLabel(action, ui)
            val labelKey = if (ui.download is DownloadState.Running) "running" else label
            AnimatedContent(
                targetState = labelKey to label,
                modifier = Modifier.fillMaxWidth(),
                contentKey = { it.first },
                contentAlignment = Alignment.Center,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "updateActionLabel",
            ) { (_, label) ->
                Text(label, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LiquidGlassButton(
            onClick = { openLink(ui.release?.releaseUrl ?: RELEASES_URL) },
            backdrop = backdrop, enabled = interactive && !ui.settingsPage, isInteractive = interactive && !ui.settingsPage,
            height = 52.dp, horizontalPadding = 0.dp,
            modifier = Modifier.size(52.dp).testTag("update_github_action"),
        ) {
            Icon(painterResource(R.drawable.ic_github), stringResource(R.string.update_github), Modifier.size(24.dp))
        }
    }
}

@Composable
private fun StatusHeader(ui: UpdateUiState) {
    val title = when (ui.check) {
        UpdateCheckState.Checking -> R.string.update_checking
        UpdateCheckState.UpToDate -> R.string.update_up_to_date_title
        is UpdateCheckState.Available -> R.string.update_available_title
        UpdateCheckState.Failed -> R.string.update_failed_title
        else -> R.string.update_check_button
    }
    Crossfade(title, animationSpec = tween(180), label = "updateStatus") { status ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (status == R.string.update_checking) {
                Box(Modifier.size(56.dp).testTag("update_status_icon"), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val icon = when (status) {
                    R.string.update_up_to_date_title -> AppIcons.CheckCircle
                    R.string.update_available_title -> AppIcons.SystemUpdate
                    else -> AppIcons.Info
                }
                Icon(icon, null, Modifier.size(56.dp).testTag("update_status_icon"), tint = MiuixTheme.colorScheme.primary)
            }
            Text(stringResource(status), modifier = Modifier.weight(1f).testTag("update_status_title"),
                style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UpdateSettingsContent(
    settings: UpdateSettings,
    onAuto: (Boolean) -> Unit,
    onChannel: (UpdateChannel) -> Unit,
    onChannelExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column {
            SmallTitle(
                stringResource(R.string.update_check_settings_section),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            )
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = updateSheetCardColor()),
            ) {
                SwitchPreference(
                    checked = settings.automaticCheck,
                    onCheckedChange = onAuto,
                    title = stringResource(R.string.update_auto_check),
                    startAction = {
                        Icon(AppIcons.SystemUpdate, null, Modifier.padding(end = 8.dp).size(26.dp))
                    },
                    enabled = enabled,
                )
            }
        }
        Column {
            SmallTitle(
                stringResource(R.string.update_download_settings_section),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            )
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = updateSheetCardColor()),
            ) {
                // Disposing this native popup on page exit/lock also removes its back handler.
                key(enabled) {
                    OverlaySpinnerPreference(
                        title = stringResource(R.string.update_channel_title),
                        startAction = {
                            Icon(AppIcons.Download, null, Modifier.padding(end = 8.dp).size(26.dp))
                        },
                        items = listOf(
                            DropdownItem(stringResource(R.string.update_channel_official)),
                            DropdownItem(stringResource(R.string.update_channel_7ed)),
                        ),
                        selectedIndex = UpdateChannel.entries.indexOf(settings.channel),
                        onSelectedIndexChange = { index ->
                            UpdateChannel.entries.getOrNull(index)?.let(onChannel)
                        },
                        renderInRootScaffold = false,
                        onExpandedChange = onChannelExpandedChange,
                        enabled = enabled,
                    )
                }
            }
        }
    }
}
@Composable
@Suppress("DEPRECATION")
private fun ReleaseNotes(content: String, markdownState: MarkdownState?, openLink: (String) -> Unit) {
    if (content.isBlank() || markdownState == null) {
        Text(stringResource(R.string.update_notes_empty))
        return
    }
    val colors = MiuixTheme.colorScheme
    val sheetBackground = updateSheetBackground()
    val styles = MiuixTheme.textStyles
    val text = styles.body1.copy(color = colors.onSurface)
    val heading = text.copy(fontWeight = FontWeight.Bold)
    val code = text.copy(fontFamily = FontFamily.Monospace)
    CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
        override fun openUri(uri: String) = openLink(uri)
    }) {
        Markdown(
            markdownState = markdownState,
            modifier = Modifier.fillMaxWidth(),
            colors = DefaultMarkdownColors(colors.onSurface, colors.onSurface, colors.onSurface,
                colors.primary, sheetBackground, sheetBackground, colors.onSurfaceVariantSummary.copy(alpha = 0.2f),
                colors.onSurface, sheetBackground),
            typography = DefaultMarkdownTypography(
                h1 = styles.title2.copy(color = colors.onSurface), h2 = styles.title3.copy(color = colors.onSurface),
                h3 = styles.title4.copy(color = colors.onSurface), h4 = heading, h5 = heading, h6 = heading,
                text = text, code = code, inlineCode = code, quote = text, paragraph = text,
                ordered = text, bullet = text, list = text, link = text.copy(color = colors.primary),
                textLink = TextLinkStyles(style = SpanStyle(color = colors.primary)), table = text
            ),
            loading = { Text(stringResource(R.string.update_notes_loading)) },
            error = { Text(content) }
        )
    }
}

@Composable
private fun ErrorText(error: UpdateError) {
    val id = when (error) {
        UpdateError.CHECK -> R.string.update_error_check
        UpdateError.SETTINGS -> R.string.update_error_settings
        UpdateError.DOWNLOAD -> R.string.update_error_download
        UpdateError.STORAGE -> R.string.update_error_storage
        UpdateError.FILE_MISSING -> R.string.update_error_file
        UpdateError.INTEGRITY -> R.string.update_error_integrity
        UpdateError.INSTALL -> R.string.update_error_install
        UpdateError.PERMISSION -> R.string.update_error_permission
        UpdateError.APK_UNAVAILABLE -> R.string.update_error_apk_unavailable
        UpdateError.RECHECK_REQUIRED -> R.string.update_error_recheck
        UpdateError.LINK -> R.string.update_error_link
    }
    Text(stringResource(id), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
private fun primaryLabel(action: UpdatePrimaryAction, ui: UpdateUiState): String {
    val download = ui.download
    return when {
    download is DownloadState.Starting -> stringResource(R.string.update_download_starting)
    download is DownloadState.Verifying -> stringResource(R.string.update_verifying)
    download is DownloadState.Running -> if (download.percent == null) stringResource(R.string.update_downloading)
        else stringResource(R.string.update_download_percent, download.percent)
    download is DownloadState.Paused -> stringResource(if (download.waitingForWifi) R.string.update_wait_wifi else R.string.update_wait_network)
    action == UpdatePrimaryAction.INSTALL -> stringResource(R.string.update_install)
    action == UpdatePrimaryAction.DOWNLOAD && ui.retryingOriginalVersion -> stringResource(R.string.update_retry_original)
    action == UpdatePrimaryAction.DOWNLOAD && download is DownloadState.Failed -> stringResource(R.string.update_retry_download)
    action == UpdatePrimaryAction.DOWNLOAD -> stringResource(R.string.update_download)
    action == UpdatePrimaryAction.CHECK && ui.check == UpdateCheckState.Failed -> stringResource(R.string.update_retry_check)
    action == UpdatePrimaryAction.CHECK -> stringResource(R.string.update_check_button)
    else -> stringResource(if (ui.check is UpdateCheckState.Available) R.string.update_apk_button_unavailable else R.string.update_checking)
    }
}
