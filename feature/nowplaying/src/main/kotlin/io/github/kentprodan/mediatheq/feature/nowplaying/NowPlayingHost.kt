package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqMiniPlayer
import io.github.kentprodan.mediatheq.core.designsystem.motion.LocalNavAnimatedVisibilityScope
import io.github.kentprodan.mediatheq.core.designsystem.motion.LocalSharedTransitionScope
import io.github.kentprodan.mediatheq.core.designsystem.motion.mqSharedCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.CoverColorCache
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqCoverTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.rememberCoverColorScheme
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Hosts the player around the app shell (architecture skill, section 6: an overlay, not a destination). [content] gets
 * the mini player to place above its navigation bar; the full player covers everything when opened.
 */
@Composable
fun NowPlayingHost(
    content: @Composable (miniPlayer: @Composable () -> Unit) -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NowPlayingLayout(state, viewModel.position, viewModel::onAction, content)
}

/** [NowPlayingHost] without the ViewModel: mini player <-> full player, shared cover, cover colors, back. */
@Composable
internal fun NowPlayingLayout(
    state: NowPlayingUiState,
    positions: Flow<Duration>,
    onAction: (NowPlayingAction) -> Unit,
    content: @Composable (miniPlayer: @Composable () -> Unit) -> Unit,
    expandedInitially: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(expandedInitially) }
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

    SharedTransitionLayout {
        val sharedScope = this
        Box(Modifier.fillMaxSize()) {
            content {
                AnimatedVisibility(
                    visible = isPlaying && !expanded,
                    enter =
                        expandVertically(motion.defaultSpatialSpec<IntSize>()) +
                            scaleIn(motion.defaultSpatialSpec()) +
                            fadeIn(motion.defaultEffectsSpec()),
                    exit =
                        shrinkVertically(motion.defaultSpatialSpec<IntSize>()) +
                            scaleOut(motion.defaultSpatialSpec()) +
                            fadeOut(motion.defaultEffectsSpec()),
                ) {
                    PlayerScope(sharedScope, this) {
                        shown?.let { playing ->
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
                                            .padding(
                                                start = MediatheqSpacing.l,
                                                end = MediatheqSpacing.l,
                                                bottom = MediatheqSpacing.s,
                                            ).swipeUpToExpand { expanded = true },
                                    coverBlurHash = playing.artwork?.blurHash,
                                    coverModifier = Modifier.mqSharedCover(PLAYER_COVER_KEY),
                                )
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
                // Sampled only while the full player is open; the mini player shows no position.
                val position = positions.collectAsStateWithLifecycle(Duration.ZERO)
                PlayerScope(sharedScope, this) {
                    shown?.let { playing ->
                        MqCoverTheme(coverScheme) {
                            NowPlayingScreen(
                                state = playing,
                                position = { position.value },
                                onAction = onAction,
                                onCollapse = { expanded = false },
                                coverModifier = Modifier.mqSharedCover(PLAYER_COVER_KEY),
                            )
                        }
                    }
                }
            }
        }
    }
    // Composed after the shell, so it wins over the tabs' back handling while the player is open.
    BackHandler(enabled = expanded) { expanded = false }
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

/** Opens on an upward fling or drag. */
@Composable
private fun Modifier.swipeUpToExpand(onExpand: () -> Unit): Modifier {
    var dragged by remember { mutableStateOf(0f) }
    return draggable(
        state = rememberDraggableState { dragged += it },
        orientation = Orientation.Vertical,
        onDragStarted = { dragged = 0f },
        onDragStopped = { velocity -> if (velocity < -FLING_VELOCITY || dragged < 0f) onExpand() },
    )
}

// Its own key, so it never meets the grid -> album covers (they use sharedCoverKey(albumId)).
private const val PLAYER_COVER_KEY = "player-cover"
