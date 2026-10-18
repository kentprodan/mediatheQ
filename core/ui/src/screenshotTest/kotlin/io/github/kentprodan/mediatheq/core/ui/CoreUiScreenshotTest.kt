package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
                TrackRow(PreviewData.track, onClick = {}, onMoreClick = {})
                TrackRow(PreviewData.longTrack, onClick = {}, onMoreClick = {})
                TrackRow(PreviewData.track, onClick = {}, onMoreClick = {}, showTrackNumber = false)
            }
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

private val CardWidth = 112.dp
