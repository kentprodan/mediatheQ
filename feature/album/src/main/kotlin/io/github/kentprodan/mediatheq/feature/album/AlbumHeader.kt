package io.github.kentprodan.mediatheq.feature.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.TextButton
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
import io.github.kentprodan.mediatheq.core.data.AlbumButton
import io.github.kentprodan.mediatheq.core.data.AlbumHeaderBackground
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFavoriteToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqStarRating
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.motion.mqSharedCover
import io.github.kentprodan.mediatheq.core.designsystem.motion.sharedCoverKey
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.AddToPlaylistMenuItem
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.OverflowMenu
import io.github.kentprodan.mediatheq.core.ui.QualityKind
import io.github.kentprodan.mediatheq.core.ui.QualityLabel
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.formatTotalDuration
import io.github.kentprodan.mediatheq.core.ui.text

/**
 * The album page's header (#132): the background (cover edge to edge fading into the page, the cover's color, or
 * nothing), title and version, "duration · year · quality", the album artists, the button row and the optional stars.
 * [topPadding] is the top bar's height, which the cover image draws under.
 */
@Composable
internal fun AlbumHeader(
    albumId: MediaId,
    state: AlbumUiState.Content,
    topPadding: Dp,
    coverColor: Color?,
    onAction: (AlbumAction) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val cover = @Composable { modifier: Modifier ->
        MqCover(
            model = state.album.artwork,
            contentDescription = null,
            modifier = modifier.mqSharedCover(sharedCoverKey(albumId)),
            blurHash = state.album.artwork?.blurHash,
            shape =
                if (state.page.headerBackground == AlbumHeaderBackground.CoverImage) {
                    RectangleShape
                } else {
                    MaterialTheme.shapes.extraLarge
                },
        )
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (state.page.headerBackground) {
            AlbumHeaderBackground.CoverImage -> {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    cover(Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to surface,
                            ),
                        ),
                    )
                }
            }

            AlbumHeaderBackground.CoverColor -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(coverColor ?: MaterialTheme.colorScheme.surfaceContainerHigh, surface),
                            ),
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(topPadding))
                    cover(Modifier.fillMaxWidth(COVER_WIDTH_FRACTION).padding(vertical = MediatheqSpacing.m))
                }
            }

            AlbumHeaderBackground.None -> {
                Spacer(Modifier.height(topPadding))
                cover(Modifier.fillMaxWidth(COVER_WIDTH_FRACTION).padding(vertical = MediatheqSpacing.m))
            }
        }
        HeaderText(state, onOpenArtist)
        HeaderButtons(albumId, state, onAction, onAddToPlaylist)
        if (state.showRating) {
            MqStarRating(
                state.album.userData.halfStars,
                { onAction(AlbumAction.Rate(it)) },
                allowHalf = state.ratingMode == RatingMode.HalfStars,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderText(
    state: AlbumUiState.Content,
    onOpenArtist: (MediaId) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs),
    ) {
        Text(
            state.title.name,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        state.title.version?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            listOfNotNull(
                formatTotalDuration(state.duration),
                state.album.year?.toString(),
                state.quality?.let { qualityText(it) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FlowRow(horizontalArrangement = Arrangement.Center) {
            state.album.albumArtists.forEach { artist ->
                TextButton(onClick = { onOpenArtist(artist.id) }) { Text(artist.name) }
            }
        }
    }
}

/** "Dolby Atmos", "Hi-Res Lossless (FLAC 24/96)", "Lossless", or a lossy file's "MP3 320 kb/s". */
@Composable
private fun qualityText(quality: QualityLabel): String =
    when (quality.kind) {
        QualityKind.HiResLossless -> "${stringResource(quality.kind.text)} (${quality.detail})"
        QualityKind.HighQuality, QualityKind.HighEfficiency -> quality.detail
        else -> stringResource(quality.kind.text)
    }

/**
 * Play (and Resume) with labels, then icon buttons; a hidden one moves to More. Wraps onto a second line instead of
 * shrinking at large font sizes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderButtons(
    albumId: MediaId,
    state: AlbumUiState.Content,
    onAction: (AlbumAction) -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
) {
    val enabled = state.tracks.isNotEmpty()
    val resume = state.resume != null && state.shows(AlbumButton.Resume)
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { onAction(AlbumAction.Play) }, enabled = enabled) {
            Icon(MqIcons.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.album_play), Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
        if (resume) {
            FilledTonalButton(onClick = { onAction(AlbumAction.Resume) }) {
                Icon(MqIcons.Resume, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.album_resume), Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
        if (state.shows(AlbumButton.Shuffle)) {
            FilledTonalIconButton(onClick = { onAction(AlbumAction.Shuffle) }, enabled = enabled) {
                Icon(MqIcons.Shuffle, stringResource(R.string.album_shuffle))
            }
        }
        if (state.shows(AlbumButton.PlayNext)) {
            FilledTonalIconButton(onClick = { onAction(AlbumAction.PlayNext()) }, enabled = enabled) {
                Icon(MqIcons.PlayNext, stringResource(R.string.album_play_next))
            }
        }
        if (state.shows(AlbumButton.AddToQueue)) {
            FilledTonalIconButton(onClick = { onAction(AlbumAction.AddToQueue()) }, enabled = enabled) {
                Icon(MqIcons.AddToQueue, stringResource(R.string.album_add_to_queue))
            }
        }
        if (state.shows(AlbumButton.Favorite)) {
            MqFavoriteToggle(state.album.userData.isFavorite, { onAction(AlbumAction.SetFavorite(albumId, it)) })
        }
        OverflowMenu(stringResource(R.string.album_more_options, state.album.title)) { close ->
            if (state.resume != null && !state.shows(AlbumButton.Resume)) {
                ActionItem(R.string.album_resume, { onAction(AlbumAction.Resume) }, close) {
                    Icon(MqIcons.Resume, contentDescription = null)
                }
            }
            if (enabled && !state.shows(AlbumButton.Shuffle)) {
                ActionItem(R.string.album_shuffle, { onAction(AlbumAction.Shuffle) }, close) {
                    Icon(MqIcons.Shuffle, contentDescription = null)
                }
            }
            if (enabled && !state.shows(AlbumButton.PlayNext) && !state.shows(AlbumButton.AddToQueue)) {
                QueueMenuItems({ onAction(AlbumAction.PlayNext()) }, { onAction(AlbumAction.AddToQueue()) }, close)
            } else if (enabled && !state.shows(AlbumButton.PlayNext)) {
                ActionItem(R.string.album_play_next, { onAction(AlbumAction.PlayNext()) }, close) {
                    Icon(MqIcons.PlayNext, contentDescription = null)
                }
            } else if (enabled && !state.shows(AlbumButton.AddToQueue)) {
                ActionItem(R.string.album_add_to_queue, { onAction(AlbumAction.AddToQueue()) }, close) {
                    Icon(MqIcons.AddToQueue, contentDescription = null)
                }
            }
            if (!state.shows(AlbumButton.Favorite)) {
                FavoriteMenuItem(
                    state.album.userData.isFavorite,
                    { onAction(AlbumAction.SetFavorite(albumId, it)) },
                    close,
                )
            }
            if (enabled) {
                AddToPlaylistMenuItem({ onAddToPlaylist(state.tracks.map { it.id }) }, close)
                DownloadMenuItem({ onAction(AlbumAction.Download()) }, close)
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
