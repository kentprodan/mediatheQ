package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.preview.PREVIEW_BLUR_HASH
import io.github.kentprodan.mediatheq.core.designsystem.preview.rememberPreviewCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

/** Track rows: with number, long title and artists (ellipsized, over an hour), without number. */
@PreviewTest
@MqPreviews
@Composable
fun TrackRowScreenshot() {
    MediatheqTheme {
        Surface {
            Column {
                TrackRow(PreviewData.track, onClick = {}, menuItems = {})
                TrackRow(PreviewData.longTrack, onClick = {}, menuItems = {})
                TrackRow(PreviewData.track, onClick = {}, menuItems = {}, showTrackNumber = false)
                TrackRow(PreviewData.track, onClick = {}, menuItems = {}, isCurrent = true)
                TrackRow(PreviewData.track, onClick = {}, menuItems = {}, showCover = true, isCurrent = true)
            }
        }
    }
}

/**
 * The queue items of track and album menus, drawn in place (screenshot rendering shows popups as blank boxes, so the
 * open DropdownMenu itself cannot be captured).
 */
@PreviewTest
@MqPreviews
@Composable
fun QueueMenuItemsScreenshot() {
    MediatheqTheme {
        Surface {
            Column(Modifier.width(IntrinsicSize.Max)) { QueueMenuItems(onPlayNext = {}, onAddToQueue = {}, close = {}) }
        }
    }
}

/** Album cards: loaded cover, BlurHash placeholder with a long title, no cover. */
@PreviewTest
@MqPreviews
@Composable
fun AlbumCardScreenshot() {
    MediatheqTheme {
        Surface {
            Row(
                Modifier.padding(MediatheqSpacing.l),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            ) {
                AlbumCard(
                    PreviewData.album,
                    onClick = {},
                    Modifier.width(CardWidth),
                    coverModel = rememberPreviewCover(),
                )
                AlbumCard(
                    PreviewData.longAlbum,
                    onClick = {},
                    Modifier.width(CardWidth),
                    coverBlurHash = PREVIEW_BLUR_HASH,
                )
                AlbumCard(PreviewData.album, onClick = {}, Modifier.width(CardWidth))
            }
        }
    }
}

/** Library rows: artist with and without picture, playlist with cover and count, track with its cover. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryRowsScreenshot() {
    MediatheqTheme {
        Surface {
            Column {
                ArtistRow(PreviewData.artist.copy(artwork = PreviewData.artwork), onClick = {})
                ArtistRow(PreviewData.longTrack.artists[1], onClick = {})
                PlaylistRow(PreviewData.playlist, onClick = {})
                PlaylistRow(PreviewData.playlist.copy(name = "Empty", trackCount = 1, artwork = null), onClick = {})
                TrackRow(
                    PreviewData.track.copy(artwork = PreviewData.artwork),
                    onClick = {},
                    showCover = true,
                    menuItems = {},
                )
                TrackRow(PreviewData.longTrack, onClick = {}, showCover = true, menuItems = {})
            }
        }
    }
}

private val CardWidth = 112.dp
