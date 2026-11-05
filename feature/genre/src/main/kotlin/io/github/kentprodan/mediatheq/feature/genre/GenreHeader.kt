package io.github.kentprodan.mediatheq.feature.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import io.github.kentprodan.mediatheq.core.data.GenreButton
import io.github.kentprodan.mediatheq.core.data.GenreHeaderBackground
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.AddToPlaylistMenuItem
import io.github.kentprodan.mediatheq.core.ui.OverflowMenu
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.formatTotalDuration
import io.github.kentprodan.mediatheq.core.ui.formatTrackCount

/**
 * The genre page's header (#134): the most played album's cover edge to edge fading into the page, its color as a
 * gradient behind a smaller cover, or nothing; the name, "albums · artists · tracks · duration" and the buttons.
 * [topPadding] is the top bar's height, which the background draws under.
 *
 * shortcut: the third copy of the album / artist header pattern; a shared header component when a fourth page needs it.
 */
@Composable
internal fun GenreHeader(
    state: GenreUiState.Content,
    topPadding: Dp,
    imageColor: Color?,
    onAction: (GenreAction) -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
) {
    val image = state.headerArtwork
    val surface = MaterialTheme.colorScheme.surface
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            state.page.headerBackground == GenreHeaderBackground.Image && image != null -> {
                Box(Modifier.fillMaxWidth()) {
                    MqCover(image, contentDescription = null, blurHash = image.blurHash, shape = RectangleShape)
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to surface)),
                    )
                }
            }

            state.page.headerBackground == GenreHeaderBackground.ImageColor -> {
                Column(
                    Modifier.fillMaxWidth().background(
                        Brush.verticalGradient(
                            listOf(imageColor ?: MaterialTheme.colorScheme.surfaceContainerHigh, surface),
                        ),
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(topPadding))
                    if (image != null) {
                        MqCover(
                            image,
                            contentDescription = null,
                            Modifier.fillMaxWidth(COVER_WIDTH_FRACTION).padding(vertical = MediatheqSpacing.m),
                            blurHash = image.blurHash,
                            shape = MaterialTheme.shapes.extraLarge,
                        )
                    }
                }
            }

            else -> {
                Spacer(Modifier.height(topPadding))
            }
        }
        HeaderText(state)
        HeaderButtons(state, onAction, onAddToPlaylist)
    }
}

@Composable
private fun HeaderText(state: GenreUiState.Content) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs),
    ) {
        Text(
            state.genre.name,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            listOfNotNull(
                state.albums.size
                    .takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.genre_album_count, it, it) },
                state.trackArtists.size
                    .takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.genre_artist_count, it, it) },
                state.tracks.size
                    .takeIf { it > 0 }
                    ?.let { formatTrackCount(it) },
                state.tracks.takeIf { it.isNotEmpty() }?.let { formatTotalDuration(state.duration) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Play with its label, then icon buttons; a hidden one moves to More. Wraps at large font sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderButtons(
    state: GenreUiState.Content,
    onAction: (GenreAction) -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
) {
    val enabled = state.tracks.isNotEmpty()
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { onAction(GenreAction.Play) }, enabled = enabled) {
            Icon(MqIcons.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.genre_play), Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
        if (state.shows(GenreButton.Shuffle)) {
            FilledTonalIconButton(onClick = { onAction(GenreAction.Shuffle) }, enabled = enabled) {
                Icon(MqIcons.Shuffle, stringResource(R.string.genre_shuffle))
            }
        }
        if (state.shows(GenreButton.PlayNext)) {
            FilledTonalIconButton(onClick = { onAction(GenreAction.PlayNext) }, enabled = enabled) {
                Icon(MqIcons.PlayNext, stringResource(R.string.genre_play_next))
            }
        }
        if (state.shows(GenreButton.AddToQueue)) {
            FilledTonalIconButton(onClick = { onAction(GenreAction.AddToQueue) }, enabled = enabled) {
                Icon(MqIcons.AddToQueue, stringResource(R.string.genre_add_to_queue))
            }
        }
        if (enabled) {
            OverflowMenu(stringResource(R.string.genre_more_options, state.genre.name)) { close ->
                if (!state.shows(GenreButton.Shuffle)) {
                    ActionItem(R.string.genre_shuffle, { onAction(GenreAction.Shuffle) }, close) {
                        Icon(MqIcons.Shuffle, contentDescription = null)
                    }
                }
                if (!state.shows(GenreButton.PlayNext) && !state.shows(GenreButton.AddToQueue)) {
                    QueueMenuItems({ onAction(GenreAction.PlayNext) }, { onAction(GenreAction.AddToQueue) }, close)
                } else if (!state.shows(GenreButton.PlayNext)) {
                    ActionItem(R.string.genre_play_next, { onAction(GenreAction.PlayNext) }, close) {
                        Icon(MqIcons.PlayNext, contentDescription = null)
                    }
                } else if (!state.shows(GenreButton.AddToQueue)) {
                    ActionItem(R.string.genre_add_to_queue, { onAction(GenreAction.AddToQueue) }, close) {
                        Icon(MqIcons.AddToQueue, contentDescription = null)
                    }
                }
                // No Download: a genre can hold thousands of tracks.
                AddToPlaylistMenuItem({ onAddToPlaylist(state.tracks.map { it.id }) }, close)
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
