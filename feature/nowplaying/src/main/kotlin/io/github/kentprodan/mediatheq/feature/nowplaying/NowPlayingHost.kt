package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.data.MiniProgress
import io.github.kentprodan.mediatheq.core.designsystem.component.MqMiniPlayer
import io.github.kentprodan.mediatheq.core.designsystem.component.MqMiniProgress
import io.github.kentprodan.mediatheq.core.designsystem.motion.LocalNavAnimatedVisibilityScope
import io.github.kentprodan.mediatheq.core.designsystem.motion.LocalSharedTransitionScope
import io.github.kentprodan.mediatheq.core.designsystem.motion.mqSharedCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.CoverColorCache
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqCoverTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.rememberCoverColorScheme
import io.github.kentprodan.mediatheq.core.navigation.AddToPlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.AudioSettingsKey
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Hosts the player around the app shell (architecture skill, section 6: an overlay, not a destination). [content] gets
 * the mini player to place above its navigation bar; the full player covers everything when opened.
 */
@Composable
fun NowPlayingHost(
    content: @Composable (miniPlayer: @Composable (onBottomBar: Boolean) -> Unit) -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
    snackbarHost: @Composable () -> Unit = {},
    /** Opens a page or dialog behind the player (artist, album, Audio settings, Add to playlist, #125). */
    onNavigate: (NavKey) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NowPlayingLayout(
        state,
        viewModel.position,
        viewModel::onAction,
        content,
        queue = viewModel.queue,
        lyrics = viewModel.lyrics,
        snackbarHost = snackbarHost,
        autoExpand = viewModel.autoExpand.collectAsStateWithLifecycle().value,
        onNavigate = onNavigate,
    )
}

/**
 * [NowPlayingHost] without the ViewModel: mini player <-> full player <-> queue, shared cover, cover colors, back.
 * Public for tests of the whole shell.
 */
@Composable
fun NowPlayingLayout(
    state: NowPlayingUiState,
    positions: StateFlow<Duration>,
    onAction: (NowPlayingAction) -> Unit,
    content: @Composable (miniPlayer: @Composable (onBottomBar: Boolean) -> Unit) -> Unit,
    expandedInitially: Boolean = false,
    queue: StateFlow<List<QueueItem>> = NoQueue,
    lyrics: StateFlow<LyricsUiState> = NoLyrics,
    /** The app's messages, shown over the full player while it covers the shell. */
    snackbarHost: @Composable () -> Unit = {},
    /** Open the player now (playback just started, #122); acknowledged with [NowPlayingAction.AutoExpanded]. */
    autoExpand: Boolean = false,
    onNavigate: (NavKey) -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(expandedInitially) }
    LaunchedEffect(autoExpand) {
        if (autoExpand) {
            expanded = true
            onAction(NowPlayingAction.AutoExpanded)
        }
    }
    var panel by rememberSaveable { mutableStateOf(PlayerPanel.Player) }
    LaunchedEffect(expanded) { if (!expanded) panel = PlayerPanel.Player }
    // Keeps showing the last track while the player animates away after the queue was cleared.
    var last by remember { mutableStateOf(state as? NowPlayingUiState.Playing) }
    LaunchedEffect(state) {
        if (state is NowPlayingUiState.Playing) last = state else expanded = false
    }
    val shown = state as? NowPlayingUiState.Playing ?: last
    val isPlaying = state is NowPlayingUiState.Playing
    val cache = remember { CoverColorCache() }
    val artwork = shown?.artwork
    val coverScheme = rememberCoverColorScheme(artwork?.let { "${it.itemId}:${it.tag}" }, artwork, cache)
    val motion = MaterialTheme.motionScheme
    // The Theme background style (#126) uses the app theme, not the cover's scheme.
    val themeBackground = MaterialTheme.colorScheme.let { it.surface to it.onSurface }

    SharedTransitionLayout {
        val sharedScope = this
        Box(Modifier.fillMaxSize()) {
            // While the player covers the shell, TalkBack must not reach the screens and the bar behind it.
            Box(Modifier.semantics { if (expanded) hideFromAccessibility() }) {
                content { onBottomBar ->
                    // Size changes only when the queue appears or empties, so opening the player never re-lays out
                    // the screen behind it.
                    AnimatedVisibility(
                        visible = isPlaying,
                        enter =
                            expandVertically(motion.defaultSpatialSpec<IntSize>()) +
                                scaleIn(motion.defaultSpatialSpec()) +
                                fadeIn(motion.defaultEffectsSpec()),
                        exit =
                            shrinkVertically(motion.defaultSpatialSpec<IntSize>()) +
                                scaleOut(motion.defaultSpatialSpec()) +
                                fadeOut(motion.defaultEffectsSpec()),
                    ) {
                        MiniPlayerSlot(expanded) {
                            PlayerScope(sharedScope, this) {
                                shown?.let { playing ->
                                    val settings = playing.settings
                                    // Docked only on the bottom bar (#126); with a toolbar, a rail or no navigation
                                    // it stays a floating pill.
                                    val docked = onBottomBar && !settings.floatingMiniPlayer
                                    val miniProgress = settings.miniProgress.toMq()
                                    // Sampled only when the mini player shows the position.
                                    val position =
                                        if (miniProgress != MqMiniProgress.Hidden && !expanded) {
                                            positions.collectAsStateWithLifecycle()
                                        } else {
                                            null
                                        }
                                    MqCoverTheme(coverScheme) {
                                        MqMiniPlayer(
                                            title = playing.title,
                                            artist = playing.artist,
                                            cover = playing.artwork,
                                            isPlaying = playing.isPlaying,
                                            hasNext = playing.hasNext,
                                            onPlayPause = { onAction(NowPlayingAction.TogglePlayPause) },
                                            onNext = { onAction(NowPlayingAction.Next) },
                                            onOpen = { expanded = true },
                                            modifier =
                                                Modifier
                                                    .then(
                                                        if (docked) {
                                                            Modifier
                                                        } else {
                                                            Modifier.padding(
                                                                start = MediatheqSpacing.l,
                                                                end = MediatheqSpacing.l,
                                                                bottom = MediatheqSpacing.s,
                                                            )
                                                        },
                                                    ).swipeUpToExpand { expanded = true }
                                                    .swipeToChangeTrack(
                                                        playing.hasPrevious,
                                                        playing.hasNext,
                                                        onAction,
                                                        enabled = settings.miniSwipeToSkip,
                                                    ),
                                            coverBlurHash = playing.artwork?.blurHash,
                                            coverModifier = Modifier.mqSharedCover(PLAYER_COVER_KEY),
                                            docked = docked,
                                            progressStyle = miniProgress,
                                            progress = {
                                                val duration = playing.duration
                                                val at = position?.value ?: Duration.ZERO
                                                if (duration.isPositive()) (at / duration).toFloat() else 0f
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = isPlaying && expanded,
                enter =
                    slideInVertically(motion.slowSpatialSpec<IntOffset>()) { it / 2 } +
                        fadeIn(motion.slowEffectsSpec()),
                exit =
                    slideOutVertically(motion.slowSpatialSpec<IntOffset>()) { it / 2 } +
                        fadeOut(motion.slowEffectsSpec()),
            ) {
                // Registered when the player opens, so it comes after the shell's own back handling and wins; back
                // closes the queue first.
                BackHandler(enabled = expanded) {
                    if (panel != PlayerPanel.Player) panel = PlayerPanel.Player else expanded = false
                }
                // Collected while the full player is open, so the queue and lyrics are current by the time they open.
                val entries by queue.collectAsStateWithLifecycle()
                val lyricsState by lyrics.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize()) {
                    PlayerScope(sharedScope, this@AnimatedVisibility) {
                        shown?.let { playing ->
                            MqCoverTheme(coverScheme) {
                                AnimatedContent(
                                    targetState = panel,
                                    transitionSpec = {
                                        fadeIn(motion.defaultEffectsSpec()) togetherWith
                                            fadeOut(motion.defaultEffectsSpec())
                                    },
                                    label = "panel",
                                ) { shownPanel ->
                                    when (shownPanel) {
                                        PlayerPanel.Queue -> {
                                            QueueScreen(
                                                queue = entries,
                                                currentEntryId = playing.currentEntryId,
                                                canReorder = !playing.shuffle,
                                                onAction = onAction,
                                                onClose = { panel = PlayerPanel.Player },
                                            )
                                        }

                                        PlayerPanel.Player -> {
                                            // Sampled only while the full player is open (the mini player samples it itself when it shows it).
                                            val position = positions.collectAsStateWithLifecycle()
                                            NowPlayingScreen(
                                                state = playing,
                                                position = { position.value },
                                                onAction = onAction,
                                                onCollapse = { expanded = false },
                                                lyrics = lyricsState,
                                                navigation =
                                                    NowPlayingNavigation(
                                                        onOpenQueue = { panel = PlayerPanel.Queue },
                                                        onOpenArtist = { id ->
                                                            expanded = false
                                                            onNavigate(ArtistKey(id))
                                                        },
                                                        onOpenAlbum = { id ->
                                                            expanded = false
                                                            onNavigate(AlbumKey(id))
                                                        },
                                                        onOpenEqualizer = {
                                                            expanded = false
                                                            onNavigate(AudioSettingsKey)
                                                        },
                                                        onAddToPlaylist = {
                                                            expanded = false
                                                            onNavigate(AddToPlaylistKey(listOfNotNull(playing.trackId)))
                                                        },
                                                    ),
                                                coverModifier = Modifier.mqSharedCover(PLAYER_COVER_KEY),
                                                themeBackground = themeBackground,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding()) { snackbarHost() }
                }
            }
        }
    }
}

/**
 * Fades the pill out while the full player is open but keeps its height, so the Scaffold padding (and the list above)
 * stays put.
 */
@Composable
private fun MiniPlayerSlot(
    expanded: Boolean,
    pill: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    var height by remember { mutableIntStateOf(0) }
    val minHeight = with(LocalDensity.current) { height.toDp() }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Box(Modifier.heightIn(min = minHeight)) {
        AnimatedVisibility(
            visible = !expanded,
            modifier = Modifier.onSizeChanged { if (it.height > 0) height = it.height },
            enter = fadeIn(effects),
            exit = fadeOut(effects),
            content = pill,
        )
    }
}

/** Lets `mqSharedCover` fly the cover between the mini player and the full player. */
@Composable
private fun PlayerScope(
    shared: SharedTransitionScope,
    visibility: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSharedTransitionScope provides shared,
        LocalNavAnimatedVisibilityScope provides visibility,
        content = content,
    )
}

/** Opens on an upward fling, or an upward drag of half the pill's height. */
@Composable
private fun Modifier.swipeUpToExpand(onExpand: () -> Unit): Modifier {
    var dragged by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val flingVelocity = flingVelocityPx()
    return onSizeChanged { height = it.height }
        .draggable(
            state = rememberDraggableState { dragged += it },
            orientation = Orientation.Vertical,
            onDragStarted = { dragged = 0f },
            onDragStopped = { velocity -> if (velocity < -flingVelocity || dragged < -height / 2f) onExpand() },
        )
}

// Its own key, so it never meets the grid -> album covers (they use sharedCoverKey(albumId)).
private const val PLAYER_COVER_KEY = "player-cover"

private val NoQueue: StateFlow<List<QueueItem>> = MutableStateFlow(emptyList())

private val NoLyrics: StateFlow<LyricsUiState> = MutableStateFlow(LyricsUiState.None)

/** What the full player shows: itself, or the queue opened from its bottom bar. */
private enum class PlayerPanel { Player, Queue }

private fun MiniProgress.toMq(): MqMiniProgress =
    when (this) {
        MiniProgress.Hidden -> MqMiniProgress.Hidden
        MiniProgress.Line -> MqMiniProgress.Line
        MiniProgress.Background -> MqMiniProgress.Background
    }
