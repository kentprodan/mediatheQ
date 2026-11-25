package io.github.kentprodan.mediatheq.feature.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.common.QualityLabel
import io.github.kentprodan.mediatheq.core.data.AlbumButton
import io.github.kentprodan.mediatheq.core.data.AlbumHeaderBackground
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFavoriteToggle
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.motion.mqAboveSharedCovers
import io.github.kentprodan.mediatheq.core.designsystem.motion.mqSharedCover
import io.github.kentprodan.mediatheq.core.designsystem.motion.sharedCoverKey
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.AddToPlaylistMenuItem
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.MqMenuItem
import io.github.kentprodan.mediatheq.core.ui.OverflowMenu
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.RatingSheetHeader
import io.github.kentprodan.mediatheq.core.ui.StartRadioMenuItem
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
    coverOrigin: String? = null,
    topPadding: Dp,
    coverColor: Color?,
    onAction: (AlbumAction) -> Unit,
    onOpenArtist: (MediaId) -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    // [inside] flies with the cover (the fade into the page), so nothing pops in once it lands (#681).
    val cover = @Composable { modifier: Modifier, inside: Modifier ->
        MqCover(
            model = state.album.artwork,
            contentDescription = null,
            modifier = modifier.mqSharedCover(sharedCoverKey(albumId, coverOrigin)).then(inside),
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
                    val fade = Brush.verticalGradient(0.55f to Color.Transparent, 1f to surface)
                    cover(
                        Modifier.fillMaxSize(),
                        Modifier.drawWithContent {
                            drawContent()
                            drawRect(fade)
                        },
                    )
                    ArtistAvatar(state, onOpenArtist, Modifier.align(Alignment.BottomEnd).padding(MediatheqSpacing.l))
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
                    SmallCover(state, onOpenArtist) { cover(it, Modifier) }
                }
            }

            AlbumHeaderBackground.None -> {
                Spacer(Modifier.height(topPadding))
                SmallCover(state, onOpenArtist) { cover(it, Modifier) }
            }
        }
        HeaderText(state, onOpenArtist)
        HeaderButtons(albumId, state, onAction, onAddToPlaylist)
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
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs),
    ) {
        Text(
            state.title.name,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMediumEmphasized,
        )
        state.title.version?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        )
        // The picture on the cover stands for the first artist (owner, #681); the others keep their names.
        val named = if (state.avatarArtist != null) state.album.albumArtists.drop(1) else state.album.albumArtists
        if (named.isNotEmpty()) {
            // The first artist's text lines up with the title (#534): the button's start padding moves out.
            FlowRow(
                Modifier.offset(
                    x = -ButtonDefaults.TextButtonContentPadding.calculateStartPadding(LocalLayoutDirection.current),
                ),
            ) {
                named.forEach { artist ->
                    TextButton(onClick = { onOpenArtist(artist.id) }) { Text(artist.name) }
                }
            }
        }
    }
}

/** The cover at [COVER_WIDTH_FRACTION] of the width, the artist's picture on its bottom right corner. */
@Composable
private fun SmallCover(
    state: AlbumUiState.Content,
    onOpenArtist: (MediaId) -> Unit,
    cover: @Composable (Modifier) -> Unit,
) {
    Box(Modifier.fillMaxWidth(COVER_WIDTH_FRACTION).padding(vertical = MediatheqSpacing.m)) {
        cover(Modifier.fillMaxWidth())
        ArtistAvatar(state, onOpenArtist, Modifier.align(Alignment.BottomEnd).padding(MediatheqSpacing.s))
    }
}

/**
 * The first album artist's picture, small and round, on the cover's bottom right corner (#682, #681); it opens the
 * artist. None from the server: nothing, and the name shows instead.
 */
@Composable
private fun ArtistAvatar(
    state: AlbumUiState.Content,
    onOpenArtist: (MediaId) -> Unit,
    modifier: Modifier,
) {
    val artist = state.avatarArtist ?: return
    MqCover(
        model = artist.artwork,
        contentDescription = artist.name,
        modifier =
            modifier
                .mqAboveSharedCovers()
                .size(ARTIST_AVATAR_SIZE)
                .clip(CircleShape)
                .clickable { onOpenArtist(artist.id) },
        blurHash = artist.artwork?.blurHash,
        shape = CircleShape,
    )
}

/** "Dolby Atmos", "Hi-Res Lossless", "Lossless", or a lossy file's "MP3 320 kb/s"; the format is in About (#534). */
@Composable
private fun qualityText(quality: QualityLabel): String =
    when (quality.kind) {
        QualityKind.HighQuality, QualityKind.HighEfficiency -> quality.detail
        else -> stringResource(quality.kind.text)
    }

/**
 * Play (and Resume) with labels and the queue icons on the left, Favorite and More on the right; a hidden one moves
 * to More. The left group wraps onto a second line instead of shrinking at large font sizes.
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
    // Favorite and More sit on the right, their ⋮ above the tracks' ⋮; the play buttons on the left (owner, #681).
    Row(
        Modifier.fillMaxWidth().padding(
            start = MediatheqSpacing.l,
            end = TRACK_END_PADDING,
            top = MediatheqSpacing.s,
            bottom = MediatheqSpacing.s,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
            verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            // One main button (#531): Resume when there is a resume point, else Play; the other one is in More.
            if (resume) {
                Button(onClick = { onAction(AlbumAction.Resume) }) {
                    Icon(MqIcons.Resume, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.album_resume), Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
            } else {
                Button(onClick = { onAction(AlbumAction.Play) }, enabled = enabled) {
                    Icon(MqIcons.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.album_play), Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
            }
            if (state.shows(AlbumButton.Shuffle)) {
                FilledTonalButton(onClick = { onAction(AlbumAction.Shuffle) }, enabled = enabled) {
                    Icon(MqIcons.Shuffle, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.album_shuffle), Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
            }
            if (state.shows(AlbumButton.PlayNext)) {
                IconButton(onClick = { onAction(AlbumAction.PlayNext()) }, enabled = enabled) {
                    Icon(MqIcons.PlayNext, stringResource(R.string.album_play_next))
                }
            }
            if (state.shows(AlbumButton.AddToQueue)) {
                IconButton(onClick = { onAction(AlbumAction.AddToQueue()) }, enabled = enabled) {
                    Icon(MqIcons.AddToQueue, stringResource(R.string.album_add_to_queue))
                }
            }
        }
        if (state.shows(AlbumButton.Favorite)) {
            MqFavoriteToggle(state.album.userData.isFavorite, { onAction(AlbumAction.SetFavorite(albumId, it)) })
        }
        OverflowMenu(
            stringResource(R.string.album_more_options, state.album.title),
            header =
                if (state.ratingMode != RatingMode.Off) {
                    {
                        RatingSheetHeader(
                            state.album.userData.halfStars,
                            { onAction(AlbumAction.Rate(it)) },
                            allowHalf = state.ratingMode == RatingMode.HalfStars,
                        )
                    }
                } else {
                    null
                },
        ) { close ->
            if (resume) {
                ActionItem(R.string.album_play_from_start, { onAction(AlbumAction.Play) }, close) {
                    Icon(MqIcons.PlayArrow, contentDescription = null)
                }
            }
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
                StartRadioMenuItem({ onAction(AlbumAction.StartRadio(albumId)) }, close)
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
    MqMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = icon,
        onClick = {
            onClick()
            close()
        },
    )
}

private const val COVER_WIDTH_FRACTION = 0.6f

private val ARTIST_AVATAR_SIZE = 56.dp

// So the header's ⋮ lines up with the tracks' (measured: their ListItem ends 4 dp further right).
private val TRACK_END_PADDING = MediatheqSpacing.m
