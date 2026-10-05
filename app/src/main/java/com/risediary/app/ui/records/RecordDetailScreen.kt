package com.risediary.app.ui.records

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.media.VideoAccessState
import com.risediary.app.media.localVideoRef
import com.risediary.app.ui.components.*
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.ui.video.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RecordDetailScreen(
    flightId: Long,
    deleteForUndo: suspend (com.risediary.app.data.entity.Flight, Long) -> Unit,
    onRecordDeleted: (com.risediary.app.data.entity.Flight) -> Unit,
    viewModel: RecordDetailViewModel = hiltViewModel(),
    videoViewModel: DetailVideoViewModel = hiltViewModel()
) {
    val navigator = LocalNavigator.current
    val flight by viewModel.flight.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val videoAccess by viewModel.videoAccess.collectAsStateWithLifecycle()
    val videoBusy by viewModel.videoBusy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    // Concealment must update even during ON_STOP, before a resumed native surface can draw.
    val videoState by videoViewModel.state.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val active = LocalPageEffectsActive.current && navigator.current() == Route.RecordDetail(flightId)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalContext.current.findVideoActivity()
    val controller = videoViewModel.controller
    val fullscreen = rememberVideoFullscreenState(controller, active)
    val owner = remember(controller) { VideoSurfaceOwner(controller) }
    DisposableEffect(owner) { onDispose { owner.release(); controller.pause() } }
    val video = flight?.localVideoRef()
    val matching = videoState.recordId == flightId && videoState.video == video
    val shownState = if (matching) videoState else DetailVideoState(recordId = flightId, video = video)
    val currentFullscreen by rememberUpdatedState(fullscreen)
    val currentActive by rememberUpdatedState(active)

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) viewModel.selectVideo(uri.toString(), data.flags)
        else viewModel.cancelVideoSelection()
    }
    fun pickVideo() {
        if (!viewModel.beginVideoSelection()) return
        videoViewModel.session.onBackground()
        controller.pause()
        fullscreen.exit()
        try {
            videoPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            })
        } catch (_: Exception) { viewModel.cancelVideoSelection() }
    }
    fun leave() {
        if (deleting) return
        if (showDeleteConfirm) showDeleteConfirm = false
        else if (fullscreen.inSession) fullscreen.exit()
        else { videoViewModel.session.onBackground(); navigator.pop() }
    }
    LaunchedEffect(flightId, active) { if (active) viewModel.load(flightId) }
    LaunchedEffect(flightId, video) { videoViewModel.session.bind(flightId, video) }
    LaunchedEffect(active) {
        if (!active) { videoViewModel.session.onBackground(); fullscreen.exit() }
    }
    LaunchedEffect(shownState.hidden, shownState.settingsReady, matching) {
        if (shownState.hidden || !shownState.settingsReady || !matching) fullscreen.exit()
    }
    LaunchedEffect(videoAccess, videoBusy) {
        if (videoAccess != null && videoAccess != VideoAccessState.READABLE) controller.pause()
    }
    DisposableEffect(lifecycle, activity, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) {
                videoViewModel.session.onBackground()
                if (videoViewModel.state.value.hidden) currentFullscreen.exit()
            }
            if (event == Lifecycle.Event.ON_RESUME && currentActive) viewModel.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(deleted) { if (deleted) {
        controller.pause()
        flight?.let(onRecordDeleted)
        navigator.pop()
    } }
    // Leave the normal page's predictive back with NavDisplay; intercept only overlays/fullscreen.
    PageBackHandler(enabled = showDeleteConfirm || fullscreen.inSession || deleting, onBack = ::leave)
    val scrollState = rememberScrollState()
    val unavailable = videoAccess != null && videoAccess != VideoAccessState.READABLE
    val displayState = if (unavailable && !shownState.hidden && shownState.problem == null)
        shownState.copy(problem = stringResource(R.string.detail_video_unavailable)) else shownState
    val onRetryVideo: () -> Unit = { viewModel.refresh(); videoViewModel.session.retry() }

    if (fullscreen.fullScreen && matching && !shownState.hidden && shownState.settingsReady && video != null) {
        // A single owner moves the same native view between hosts; no movable Compose subtree.
        if (displayState.problem != null) {
            val backdrop = rememberLayerBackdrop { drawRect(Color.Black); drawContent() }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.matchParentSize().layerBackdrop(backdrop))
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    VideoStateContent(false, displayState.problem, true, false, backdrop,
                        { fullscreen.exit(); onRetryVideo() }, onExitFullScreen = fullscreen.exit)
                    Spacer(Modifier.height(12.dp))
                    VideoGlassButton(::pickVideo, backdrop, icon = AppIcons.Video,
                        description = stringResource(R.string.video_relink), fullScreen = true,
                        enabled = active && !videoBusy, modifier = Modifier.size(52.dp))
                }
            }
        } else LocalVideoPlayer(controller, owner, true, fullscreen.exit, Modifier.fillMaxSize(),
            onToggleOrientation = fullscreen.toggleOrientation, direction = fullscreen.direction,
            controlsEnabled = displayState.controlsEnabled && active, surfaceEnabled = displayState.prepared)
    } else {
        SecondaryPageScaffold(
            title = stringResource(R.string.record_detail_title), onBack = ::leave,
            topBarActionsWidth = 104.dp,
            topBlurProgress = if (!loading && flight != null) rememberTopBlurProgress(scrollState) else { { 0f } },
            actions = {
                flight?.let { value ->
                    FloatingToolbar(outSidePadding = PaddingValues(0.dp), cornerRadius = 100.dp) {
                        Row(Modifier.padding(horizontal = 2.dp)) {
                            IconButton(enabled = !videoBusy && !loading, onClick = {
                                videoViewModel.session.onBackground()
                                navigator.push(Route.RecordEdit(value.id))
                            }, modifier = Modifier.size(48.dp)) {
                                Icon(AppIcons.Edit, stringResource(R.string.action_edit), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { controller.pause(); showDeleteConfirm = true }, enabled = !videoBusy && !loading,
                                modifier = Modifier.size(48.dp)) {
                                Icon(AppIcons.Delete, stringResource(R.string.action_delete), modifier = Modifier.size(20.dp),
                                    tint = MiuixTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            when {
                loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                flight == null -> Column(Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: stringResource(R.string.record_detail_missing))
                    if (error != null) TextButton(stringResource(R.string.action_retry), viewModel::refresh)
                }
                else -> RecordDetailContent(checkNotNull(flight), padding, scrollState, error, videoBusy,
                    viewModel::refresh, videoContent = video?.let { ref -> ({
                        DetailVideoCard(ref, displayState, controller, owner,
                            videoViewModel.session::show, videoViewModel.session::hide, onRetryVideo, ::pickVideo,
                            { if (displayState.controlsEnabled && !videoBusy) fullscreen.enter() },
                            modifier = Modifier.fillMaxWidth(), busy = videoBusy, active = active)
                    }) })
            }
        }
    }
    if (showDeleteConfirm) {
        LiquidAlertDialog(onDismissRequest = { showDeleteConfirm = false }, adaptiveActions = true,
            title = { Text(stringResource(R.string.record_detail_delete_dialog_title)) },
            text = { Text(stringResource(R.string.record_detail_delete_dialog_message)) },
            confirmButton = {
                TextButton(stringResource(R.string.action_delete), {
                    showDeleteConfirm = false; controller.pause(); viewModel.delete(deleteForUndo)
                }, colors = ButtonDefaults.textButtonColors(color = Color.Transparent, disabledColor = Color.Transparent,
                    textColor = MiuixTheme.colorScheme.error, disabledTextColor = MiuixTheme.colorScheme.error))
            }, dismissButton = {
                TextButton(stringResource(R.string.action_cancel), { showDeleteConfirm = false }, colors = liquidDialogCancelButtonColors())
            })
    }
}
