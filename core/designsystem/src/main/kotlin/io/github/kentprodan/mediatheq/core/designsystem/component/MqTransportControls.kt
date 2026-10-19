package io.github.kentprodan.mediatheq.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.kentprodan.mediatheq.core.designsystem.R
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/**
 * The Now Playing transport row (expressive-design skill, section 3): previous, play/pause, next in a `ButtonGroup`.
 * Play/pause is the largest; it is round while paused and squarish while playing, so the state is not shown by the
 * icon alone. Pressing a button widens it and narrows its neighbours.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MqTransportControls(
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previousLabel = stringResource(R.string.mq_previous_track)
    val playPauseLabel = stringResource(if (isPlaying) R.string.mq_pause else R.string.mq_play)
    val nextLabel = stringResource(R.string.mq_next_track)
    val previousInteraction = remember { MutableInteractionSource() }
    val playInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    // Medium sides and a large wide centre fit a 320 dp wide screen; the group would otherwise move buttons to a menu.
    val sideSize = IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Uniform)
    val playSize = IconButtonDefaults.largeContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)
    val playShape = if (isPlaying) MaterialTheme.shapes.large else IconButtonDefaults.shapes().shape
    ButtonGroup(
        // Only on very narrow windows: the buttons that do not fit go to this menu.
        overflowIndicator = { menu ->
            IconButton(onClick = { if (menu.isShowing) menu.dismiss() else menu.show() }) {
                Icon(MqIcons.MoreVert, contentDescription = stringResource(R.string.mq_more))
            }
        },
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s, Alignment.CenterHorizontally),
    ) {
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = onPrevious,
                    enabled = hasPrevious,
                    shapes = IconButtonDefaults.shapes(),
                    interactionSource = previousInteraction,
                    modifier = Modifier.size(sideSize).animateWidth(previousInteraction),
                ) {
                    TransportIcon(MqIcons.SkipPreviousFilled, previousLabel, IconButtonDefaults.mediumIconSize)
                }
            },
            menuContent = { menu ->
                DropdownMenuItem(
                    text = { Text(previousLabel) },
                    onClick = {
                        onPrevious()
                        menu.dismiss()
                    },
                    enabled = hasPrevious,
                )
            },
        )
        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = onPlayPause,
                    shapes = IconButtonDefaults.shapes(shape = playShape),
                    interactionSource = playInteraction,
                    modifier = Modifier.size(playSize).animateWidth(playInteraction),
                ) {
                    TransportIcon(
                        if (isPlaying) MqIcons.PauseFilled else MqIcons.PlayFilled,
                        playPauseLabel,
                        IconButtonDefaults.largeIconSize,
                    )
                }
            },
            menuContent = { menu ->
                DropdownMenuItem(
                    text = { Text(playPauseLabel) },
                    onClick = {
                        onPlayPause()
                        menu.dismiss()
                    },
                )
            },
        )
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = onNext,
                    enabled = hasNext,
                    shapes = IconButtonDefaults.shapes(),
                    interactionSource = nextInteraction,
                    modifier = Modifier.size(sideSize).animateWidth(nextInteraction),
                ) {
                    TransportIcon(MqIcons.SkipNextFilled, nextLabel, IconButtonDefaults.mediumIconSize)
                }
            },
            menuContent = { menu ->
                DropdownMenuItem(
                    text = { Text(nextLabel) },
                    onClick = {
                        onNext()
                        menu.dismiss()
                    },
                    enabled = hasNext,
                )
            },
        )
    }
}

@Composable
private fun TransportIcon(
    icon: ImageVector,
    label: String,
    size: Dp,
) {
    Icon(icon, contentDescription = label, modifier = Modifier.size(size))
}
