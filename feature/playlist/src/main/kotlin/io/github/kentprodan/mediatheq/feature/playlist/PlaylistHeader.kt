package io.github.kentprodan.mediatheq.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import io.github.kentprodan.mediatheq.core.data.PlaylistButton
import io.github.kentprodan.mediatheq.core.data.PlaylistHeaderBackground
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFavoriteToggle
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.OverflowMenu
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.formatTotalDuration
import io.github.kentprodan.mediatheq.core.ui.formatTrackCount

/**
 * The playlist page's header (#135): the playlist's image edge to edge fading into the page, its color as a gradient
 * behind a smaller image, or nothing; the name, "tracks · duration" (with a mark when all is downloaded) and the
 * buttons. [topPadding] is the top bar's height, which the background draws under.
 *
 * shortcut: the fourth copy of the album / artist / genre header pattern; a shared header component is the next step.
 */
@Composable
internal fun PlaylistHeader(
    state: PlaylistUiState.Content,
    topPadding: Dp,
    imageColor: Color?,
    onAction: (PlaylistAction) -> Unit,
    onDialog: (PlaylistDialog) -> Unit,
) {
    val image = state.playlist.artwork
    val surface = MaterialTheme.colorScheme.surface
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            state.page.headerBackground == PlaylistHeaderBackground.Image && image != null -> {
                Box(Modifier.fillMaxWidth()) {
                    MqCover(image, contentDescription = null, blurHash = image.blurHash, shape = RectangleShape)
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to surface)),
                    )
                }
            }

            state.page.headerBackground == PlaylistHeaderBackground.None -> {
                Spacer(Modifier.height(topPadding))
            }

            else -> {
                // Image color, or Image without an image: the smaller cover (a placeholder when there is none).
                Column(
                    Modifier.fillMaxWidth().background(
                        Brush.verticalGradient(
                            listOf(imageColor ?: MaterialTheme.colorScheme.surfaceContainerHigh, surface),
                        ),
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(topPadding))
                    MqCover(
                        image,
                        contentDescription = null,
                        Modifier.fillMaxWidth(COVER_WIDTH_FRACTION).padding(vertical = MediatheqSpacing.m),
                        blurHash = image?.blurHash,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }
            }
        }
        HeaderText(state)
        HeaderButtons(state, onAction, onDialog)
    }
}

@Composable
private fun HeaderText(state: PlaylistUiState.Content) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs),
    ) {
        Text(
            state.playlist.name,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs),
        ) {
            if (state.isDownloaded) {
                Icon(
                    MqIcons.DownloadForOffline,
                    stringResource(R.string.playlist_downloaded),
                    Modifier.size(ButtonDefaults.IconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                listOfNotNull(
                    formatTrackCount(state.items.size),
                    formatTotalDuration(state.duration).takeIf { state.items.isNotEmpty() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Play (and Resume) with labels, then icon buttons; a hidden one moves to More. Wraps at large font sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderButtons(
    state: PlaylistUiState.Content,
    onAction: (PlaylistAction) -> Unit,
    onDialog: (PlaylistDialog) -> Unit,
) {
    val enabled = state.items.isNotEmpty()
    val favorite = state.playlist.userData.isFavorite
    val onFavorite = { it: Boolean -> onAction(PlaylistAction.SetPlaylistFavorite(it)) }
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { onAction(PlaylistAction.Play) }, enabled = enabled) {
            Icon(MqIcons.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.playlist_play), Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
        if (state.resume != null && state.shows(PlaylistButton.Resume)) {
            FilledTonalButton(onClick = { onAction(PlaylistAction.Resume) }) {
                Icon(MqIcons.Resume, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.playlist_resume), Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
        if (state.shows(PlaylistButton.Shuffle)) {
            FilledTonalIconButton(onClick = { onAction(PlaylistAction.Shuffle) }, enabled = enabled) {
                Icon(MqIcons.Shuffle, stringResource(R.string.playlist_shuffle))
            }
        }
        if (state.shows(PlaylistButton.PlayNext)) {
            FilledTonalIconButton(onClick = { onAction(PlaylistAction.PlayAllNext) }, enabled = enabled) {
                Icon(MqIcons.PlayNext, stringResource(R.string.playlist_play_next))
            }
        }
        if (state.shows(PlaylistButton.AddToQueue)) {
            FilledTonalIconButton(onClick = { onAction(PlaylistAction.AddAllToQueue) }, enabled = enabled) {
                Icon(MqIcons.AddToQueue, stringResource(R.string.playlist_add_to_queue))
            }
        }
        if (state.shows(PlaylistButton.Favorite)) MqFavoriteToggle(favorite, onFavorite)
        OverflowMenu(stringResource(R.string.playlist_more_options, state.playlist.name)) { close ->
            if (state.resume != null && !state.shows(PlaylistButton.Resume)) {
                ActionItem(R.string.playlist_resume, { onAction(PlaylistAction.Resume) }, close) {
                    Icon(MqIcons.Resume, contentDescription = null)
                }
            }
            if (enabled && !state.shows(PlaylistButton.Shuffle)) {
                ActionItem(R.string.playlist_shuffle, { onAction(PlaylistAction.Shuffle) }, close) {
                    Icon(MqIcons.Shuffle, contentDescription = null)
                }
            }
            val next = { onAction(PlaylistAction.PlayAllNext) }
            val last = { onAction(PlaylistAction.AddAllToQueue) }
            if (enabled && !state.shows(PlaylistButton.PlayNext) && !state.shows(PlaylistButton.AddToQueue)) {
                QueueMenuItems(next, last, close)
            } else if (enabled && !state.shows(PlaylistButton.PlayNext)) {
                ActionItem(
                    R.string.playlist_play_next,
                    next,
                    close,
                ) { Icon(MqIcons.PlayNext, contentDescription = null) }
            } else if (enabled && !state.shows(PlaylistButton.AddToQueue)) {
                ActionItem(R.string.playlist_add_to_queue, last, close) {
                    Icon(MqIcons.AddToQueue, contentDescription = null)
                }
            }
            if (!state.shows(PlaylistButton.Favorite)) FavoriteMenuItem(favorite, onFavorite, close)
            if (enabled) {
                DownloadMenuItem({ onAction(PlaylistAction.Download()) }, close)
                ActionItem(R.string.playlist_edit, { onAction(PlaylistAction.StartEditing) }, close) {
                    Icon(MqIcons.Checklist, contentDescription = null)
                }
            }
            ActionItem(R.string.playlist_rename, { onDialog(PlaylistDialog.Rename) }, close) {
                Icon(MqIcons.Edit, contentDescription = null)
            }
            ActionItem(R.string.playlist_delete, { onDialog(PlaylistDialog.Delete) }, close) {
                Icon(MqIcons.Delete, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ActionItem(
    label: Int,
    onClick: () -> Unit,
    close: () -> Unit,
    icon: @Composable () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = icon,
        onClick = {
            onClick()
            close()
        },
    )
}

private const val COVER_WIDTH_FRACTION = 0.6f
