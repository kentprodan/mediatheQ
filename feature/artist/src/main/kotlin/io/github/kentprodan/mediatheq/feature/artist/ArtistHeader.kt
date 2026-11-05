package io.github.kentprodan.mediatheq.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import io.github.kentprodan.mediatheq.core.data.ArtistButton
import io.github.kentprodan.mediatheq.core.data.ArtistHeaderBackground
import io.github.kentprodan.mediatheq.core.designsystem.component.MqArtistAvatar
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFavoriteToggle
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.AddToPlaylistMenuItem
import io.github.kentprodan.mediatheq.core.ui.DownloadMenuItem
import io.github.kentprodan.mediatheq.core.ui.FavoriteMenuItem
import io.github.kentprodan.mediatheq.core.ui.OverflowMenu
import io.github.kentprodan.mediatheq.core.ui.QueueMenuItems
import io.github.kentprodan.mediatheq.core.ui.formatTotalDuration
import io.github.kentprodan.mediatheq.core.ui.formatTrackCount

/**
 * The artist page's header (#133): the background (the backdrop, else the artist image, edge to edge fading into the
 * page; the image's color; or nothing), the name, "albums · tracks · duration" and the button row. [topPadding] is
 * the top bar's height, which an edge-to-edge image draws under.
 */
@Composable
internal fun ArtistHeader(
    state: ArtistUiState.Content,
    topPadding: Dp,
    imageColor: Color?,
    onAction: (ArtistAction) -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
) {
    val artist = state.artist
    // Offline a backdrop that is not cached fails to load: the artist image takes its place. Previews load nothing.
    val preview = LocalInspectionMode.current
    var backdropFailed by remember(artist.backdrop) { mutableStateOf(false) }
    val backdrop = artist.backdrop?.takeIf { !backdropFailed }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            state.page.headerBackground == ArtistHeaderBackground.Backdrop && backdrop != null -> {
                EdgeToEdge(backdrop, BACKDROP_RATIO, "artist-backdrop") { if (!preview) backdropFailed = true }
            }

            state.page.headerBackground in EDGE_TO_EDGE && artist.artwork != null -> {
                EdgeToEdge(artist.artwork!!, 1f, "artist-image") {}
            }

            state.page.headerBackground == ArtistHeaderBackground.ImageColor -> {
                Column(
                    Modifier.fillMaxWidth().background(
                        Brush.verticalGradient(
                            listOf(
                                imageColor ?: MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(topPadding))
                    Avatar(state)
                }
            }

            else -> {
                Spacer(Modifier.height(topPadding))
                Avatar(state)
            }
        }
        HeaderText(state)
        HeaderButtons(state, onAction, onAddToPlaylist)
    }
}

@Composable
private fun EdgeToEdge(
    image: Artwork,
    ratio: Float,
    tag: String,
    onError: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().testTag(tag)) {
        MqCover(
            model = image,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            blurHash = image.blurHash,
            shape = RectangleShape,
            aspectRatio = ratio,
            onError = onError,
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to surface)))
    }
}

@Composable
private fun Avatar(state: ArtistUiState.Content) {
    MqArtistAvatar(
        state.artist.artwork,
        Modifier
            .fillMaxWidth(AVATAR_WIDTH_FRACTION)
            .padding(vertical = MediatheqSpacing.m)
            .testTag("artist-avatar"),
        state.artist.artwork?.blurHash,
    )
}

@Composable
private fun HeaderText(state: ArtistUiState.Content) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs),
    ) {
        Text(
            state.artist.name,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            listOfNotNull(
                state.albums.size
                    .takeIf { it > 0 }
                    ?.let { pluralStringResource(R.plurals.artist_album_count, it, it) },
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

/**
 * Play with its label, then icon buttons; a hidden one moves to More. Wraps onto a second line instead of shrinking
 * at large font sizes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderButtons(
    state: ArtistUiState.Content,
    onAction: (ArtistAction) -> Unit,
    onAddToPlaylist: (List<MediaId>) -> Unit,
) {
    val enabled = state.tracks.isNotEmpty()
    val favorite = state.artist.userData.isFavorite
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { onAction(ArtistAction.Play) }, enabled = enabled) {
            Icon(MqIcons.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.artist_play), Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
        if (state.shows(ArtistButton.Shuffle)) {
            FilledTonalIconButton(onClick = { onAction(ArtistAction.Shuffle) }, enabled = enabled) {
                Icon(MqIcons.Shuffle, stringResource(R.string.artist_shuffle))
            }
        }
        if (state.shows(ArtistButton.PlayNext)) {
            FilledTonalIconButton(onClick = { onAction(ArtistAction.PlayNext) }, enabled = enabled) {
                Icon(MqIcons.PlayNext, stringResource(R.string.artist_play_next))
            }
        }
        if (state.shows(ArtistButton.AddToQueue)) {
            FilledTonalIconButton(onClick = { onAction(ArtistAction.AddToQueue) }, enabled = enabled) {
                Icon(MqIcons.AddToQueue, stringResource(R.string.artist_add_to_queue))
            }
        }
        if (state.shows(ArtistButton.Favorite)) {
            MqFavoriteToggle(favorite, { onAction(ArtistAction.SetFavorite(it)) })
        }
        OverflowMenu(stringResource(R.string.artist_more_options, state.artist.name)) { close ->
            if (enabled && !state.shows(ArtistButton.Shuffle)) {
                ActionItem(R.string.artist_shuffle, { onAction(ArtistAction.Shuffle) }, close) {
                    Icon(MqIcons.Shuffle, contentDescription = null)
                }
            }
            if (enabled && !state.shows(ArtistButton.PlayNext) && !state.shows(ArtistButton.AddToQueue)) {
                QueueMenuItems({ onAction(ArtistAction.PlayNext) }, { onAction(ArtistAction.AddToQueue) }, close)
            } else if (enabled && !state.shows(ArtistButton.PlayNext)) {
                ActionItem(R.string.artist_play_next, { onAction(ArtistAction.PlayNext) }, close) {
                    Icon(MqIcons.PlayNext, contentDescription = null)
                }
            } else if (enabled && !state.shows(ArtistButton.AddToQueue)) {
                ActionItem(R.string.artist_add_to_queue, { onAction(ArtistAction.AddToQueue) }, close) {
                    Icon(MqIcons.AddToQueue, contentDescription = null)
                }
            }
            if (!state.shows(ArtistButton.Favorite)) {
                FavoriteMenuItem(favorite, { onAction(ArtistAction.SetFavorite(it)) }, close)
            }
            if (enabled) {
                AddToPlaylistMenuItem({ onAddToPlaylist(state.tracks.map { it.id }) }, close)
            }
            if (state.albums.isNotEmpty()) {
                DownloadMenuItem({ onAction(ArtistAction.Download) }, close)
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

/** Backgrounds drawn edge to edge under the top bar; the image stands in for a missing backdrop. */
private val EDGE_TO_EDGE = setOf(ArtistHeaderBackground.Backdrop, ArtistHeaderBackground.ArtistImage)

private const val BACKDROP_RATIO = 16f / 9f
private const val AVATAR_WIDTH_FRACTION = 0.5f
