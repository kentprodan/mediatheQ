package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.launch

/**
 * A track row that swipes (#123): past the threshold a haptic tick, on release [onSwipe] runs and the row springs
 * back (it is never removed). The action's icon and name show behind the row while swiping. Each enabled side is
 * also a custom accessibility action calling the same [onSwipe].
 */
@Composable
internal fun SwipeableTrack(
    track: Track,
    right: TrackSwipe?,
    left: TrackSwipe?,
    onSwipe: (right: Boolean) -> Unit,
    modifier: Modifier,
    row: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.targetValue) {
        if (state.targetValue != SwipeToDismissBoxValue.Settled) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }
    val rightLabel = right?.let { swipeLabel(it, track) }
    val leftLabel = left?.let { swipeLabel(it, track) }
    SwipeToDismissBox(
        state = state,
        modifier =
            modifier.semantics {
                customActions =
                    listOfNotNull(
                        rightLabel?.let { label ->
                            CustomAccessibilityAction(label) {
                                onSwipe(true)
                                true
                            }
                        },
                        leftLabel?.let { label ->
                            CustomAccessibilityAction(label) {
                                onSwipe(false)
                                true
                            }
                        },
                    )
            },
        enableDismissFromStartToEnd = right != null,
        enableDismissFromEndToStart = left != null,
        backgroundContent = {
            // Only while swiping: rows may be rounded, and a resting background would show at their corners.
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (right != null && rightLabel != null) SwipeBackground(right, rightLabel, Arrangement.Start)
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    if (left != null && leftLabel != null) SwipeBackground(left, leftLabel, Arrangement.End)
                }

                SwipeToDismissBoxValue.Settled -> {}
            }
        },
        onDismiss = { direction ->
            onSwipe(direction == SwipeToDismissBoxValue.StartToEnd)
            scope.launch { state.reset() }
        },
    ) { row() }
}

@Composable
private fun SwipeBackground(
    swipe: TrackSwipe,
    label: String,
    arrangement: Arrangement.Horizontal,
) {
    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = MediatheqSpacing.l),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(swipe.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            label,
            Modifier.padding(horizontal = MediatheqSpacing.s),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun swipeLabel(
    swipe: TrackSwipe,
    track: Track,
): String =
    stringResource(
        when (swipe) {
            TrackSwipe.AddToQueue -> {
                R.string.core_ui_add_to_queue
            }

            TrackSwipe.PlayNext -> {
                R.string.core_ui_play_next
            }

            TrackSwipe.AddToPlaylist -> {
                R.string.core_ui_add_to_playlist
            }

            TrackSwipe.Download -> {
                R.string.core_ui_download
            }

            TrackSwipe.ToggleFavorite -> {
                if (track.userData.isFavorite) R.string.core_ui_remove_favorite else R.string.core_ui_add_favorite
            }

            TrackSwipe.Rate -> {
                R.string.core_ui_rate
            }
        },
    )

private val TrackSwipe.icon: ImageVector
    @Composable get() =
        when (this) {
            TrackSwipe.AddToQueue -> MqIcons.AddToQueue
            TrackSwipe.PlayNext -> MqIcons.PlayNext
            TrackSwipe.AddToPlaylist -> MqIcons.AddToPlaylist
            TrackSwipe.Download -> MqIcons.Download
            TrackSwipe.ToggleFavorite -> MqIcons.Favorite
            TrackSwipe.Rate -> MqIcons.Star
        }
