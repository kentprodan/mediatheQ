package io.github.kentprodan.mediatheq.feature.artist

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.designsystem.component.MqStarRating
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.ui.AlbumCard
import io.github.kentprodan.mediatheq.core.ui.TrackRow
import io.github.kentprodan.mediatheq.core.ui.formatTrackCount
import io.github.kentprodan.mediatheq.core.ui.stateOf

/** A section title (#133): `titleMedium`, heading; [action] sits at its end ("Show all"). */
@Composable
internal fun SectionTitle(
    @StringRes title: Int,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = MediatheqSpacing.l, end = MediatheqSpacing.s, top = MediatheqSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(title),
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        action?.invoke()
    }
}

/** The album grid's columns: the Artist page setting for the current orientation. */
@Composable
internal fun albumColumns(state: ArtistUiState.Content): Int =
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        state.page.landscapeAlbumColumns
    } else {
        state.page.albumColumns
    }

/**
 * A titled grid of [albums] as rows of the page's list: a nested lazy grid cannot scroll inside it, and the track
 * rows must keep their full width.
 */
internal fun LazyListScope.albumGrid(
    key: String,
    @StringRes title: Int,
    albums: List<Album>,
    columns: Int,
    state: ArtistUiState.Content,
    onOpenAlbum: (MediaId) -> Unit,
    longPress: (MediaId) -> () -> Unit,
) {
    item(key = "$key-title") { SectionTitle(title) }
    albums.chunked(columns).forEachIndexed { index, row ->
        item(key = "$key-$index") {
            Row(
                Modifier.padding(start = MediatheqSpacing.l, end = MediatheqSpacing.l, top = MediatheqSpacing.s),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            ) {
                row.forEach { album ->
                    AlbumCard(
                        album,
                        onClick = { onOpenAlbum(album.id) },
                        Modifier.weight(1f),
                        onLongClick = longPress(album.id),
                        selected = state.selection.stateOf(album.id),
                    )
                }
                // Keeps the last row's cards the same width as the others.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Top tracks with a "Show all" that opens every played track (#133). */
internal fun LazyListScope.topTracks(
    state: ArtistUiState.Content,
    onAction: (ArtistAction) -> Unit,
    onShowAll: () -> Unit,
    longPress: (MediaId) -> () -> Unit,
    swipe: (Track, Boolean) -> Unit,
) {
    item(key = "top-title") {
        SectionTitle(R.string.artist_top_tracks) {
            if (state.playedTracks.size > state.topTracks.size) {
                TextButton(onClick = onShowAll) { Text(stringResource(R.string.artist_show_all)) }
            }
        }
    }
    items(state.topTracks, key = { "top-${it.id}" }) { track ->
        ArtistTrackRow(track, state, onAction, longPress, swipe, all = false)
    }
}

@Composable
internal fun ArtistTrackRow(
    track: Track,
    state: ArtistUiState.Content,
    onAction: (ArtistAction) -> Unit,
    longPress: (MediaId) -> () -> Unit,
    swipe: (Track, Boolean) -> Unit,
    all: Boolean,
) {
    TrackRow(
        track,
        onClick = {
            if (state.selection.isActive) {
                onAction(ArtistAction.ToggleSelection(track.id))
            } else {
                onAction(ArtistAction.PlayTopTrack(track.id, all))
            }
        },
        onLongClick = longPress(track.id),
        selected = state.selection.stateOf(track.id),
        onSwipe = { swipe(track, it) },
        showCover = true,
        isCurrent = track.id == state.currentTrackId,
    )
}

/** "About": the biography (three lines, a tap shows it all), then genres, counts, favorite and rating. */
@Composable
internal fun AboutSection(
    state: ArtistUiState.Content,
    onAction: (ArtistAction) -> Unit,
) {
    Column {
        SectionTitle(R.string.artist_about)
        state.artist.overview?.let { overview ->
            var expanded by rememberSaveable { mutableStateOf(false) }
            Text(
                overview,
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel =
                            stringResource(if (expanded) R.string.artist_show_less else R.string.artist_show_more),
                    ) { expanded = !expanded }
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                    .padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else OVERVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.genres.isNotEmpty()) {
            Fact(R.string.artist_about_genres, state.genres.joinToString())
        }
        if (state.albums.isNotEmpty()) {
            Fact(
                R.string.artist_about_albums,
                pluralStringResource(R.plurals.artist_album_count, state.albums.size, state.albums.size),
            )
        }
        if (state.tracks.isNotEmpty()) {
            Fact(R.string.artist_about_tracks, formatTrackCount(state.tracks.size))
        }
        if (state.artist.userData.isFavorite) {
            Fact(R.string.artist_about_favorite, stringResource(R.string.artist_about_favorite_yes))
        }
        if (state.ratingMode != RatingMode.Off) {
            ListItem(
                content = { Text(stringResource(R.string.artist_about_rating)) },
                supportingContent = {
                    MqStarRating(
                        state.artist.userData.halfStars,
                        { onAction(ArtistAction.Rate(it)) },
                        allowHalf = state.ratingMode == RatingMode.HalfStars,
                    )
                },
            )
        }
    }
}

@Composable
private fun Fact(
    @StringRes label: Int,
    value: String,
) {
    ListItem(content = { Text(stringResource(label)) }, supportingContent = { Text(value) })
}

private const val OVERVIEW_LINES = 3
