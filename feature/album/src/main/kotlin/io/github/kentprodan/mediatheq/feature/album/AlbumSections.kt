package io.github.kentprodan.mediatheq.feature.album

import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.common.albumQuality
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.AlbumCard
import io.github.kentprodan.mediatheq.core.ui.text
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** A section title (#132): `titleMedium`, heading. */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .padding(
                start = MediatheqSpacing.l,
                end = MediatheqSpacing.l,
                top = MediatheqSpacing.l,
                bottom = MediatheqSpacing.s,
            ).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

/** "More from <artist>": the artist's other albums in a row; a tap opens one. */
@Composable
internal fun MoreFromArtist(
    artistName: String,
    albums: List<Album>,
    onOpenAlbum: (MediaId) -> Unit,
) {
    Column {
        SectionTitle(stringResource(R.string.album_more_from, artistName))
        LazyRow(
            contentPadding = PaddingValues(horizontal = MediatheqSpacing.l),
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
        ) {
            // The page's own cover flies; these do not.
            items(albums, key = { it.id.toString() }) { album ->
                AlbumCard(
                    album,
                    onClick = { onOpenAlbum(album.id) },
                    modifier = Modifier.width(MediatheqSizes.albumCardInRow),
                    sharedCover = false,
                )
            }
        }
    }
}

/**
 * "About": the server's overview (three lines, a tap shows it all), then the facts the album has: genres, record
 * labels, release date, date added, play count, the files' size and format.
 */
@Composable
internal fun AboutSection(state: AlbumUiState.Content) {
    Column {
        SectionTitle(stringResource(R.string.album_about))
        state.album.overview?.let { overview ->
            var expanded by rememberSaveable { mutableStateOf(false) }
            Text(
                overview,
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel =
                            stringResource(
                                if (expanded) R.string.album_show_less else R.string.album_show_more,
                            ),
                    ) {
                        expanded = !expanded
                    }.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                    .padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else OVERVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val context = LocalContext.current
        // "FLAC 24/96", "MP3 320 kb/s", or the kind ("Lossless", "Dolby Atmos") when that says it all.
        val format = albumQuality(state.tracks)?.let { it.detail.ifEmpty { stringResource(it.kind.text) } }
        listOfNotNull(
            state.genres.takeIf { it.isNotEmpty() }?.let { R.string.album_about_genres to it.joinToString() },
            state.labels.takeIf { it.isNotEmpty() }?.let {
                R.string.album_about_labels to
                    it.joinToString { l -> l.name }
            },
            state.album.releaseDate?.let { R.string.album_about_released to formatDate(it) },
            state.album.dateAdded?.let { R.string.album_about_added to formatDate(it) },
            state.playCount.takeIf { it > 0 }?.let { R.string.album_about_plays to "" },
            state.sizeBytes?.let { size ->
                R.string.album_about_size to
                    listOfNotNull(Formatter.formatShortFileSize(context, size), format).joinToString(" · ")
            },
        ).forEach { (label, value) ->
            ListItem(
                content = { Text(stringResource(label)) },
                supportingContent = {
                    Text(
                        if (label == R.string.album_about_plays) {
                            pluralStringResource(R.plurals.album_play_count, state.playCount, state.playCount)
                        } else {
                            value
                        },
                    )
                },
            )
        }
    }
}

/** Dates in the device's medium style, read in UTC: the server stores release dates at midnight UTC. */
private fun formatDate(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneOffset.UTC).format(instant.toJavaInstant())

private const val OVERVIEW_LINES = 3
