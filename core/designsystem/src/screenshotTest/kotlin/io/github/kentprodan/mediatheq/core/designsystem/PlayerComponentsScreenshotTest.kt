package io.github.kentprodan.mediatheq.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.component.MqMiniPlayer
import io.github.kentprodan.mediatheq.core.designsystem.component.MqSeekBar
import io.github.kentprodan.mediatheq.core.designsystem.component.MqTransportControls
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.preview.PREVIEW_BLUR_HASH
import io.github.kentprodan.mediatheq.core.designsystem.preview.rememberPreviewCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqCoverTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.coverColorScheme

/** Mini player states: playing, paused with no next track, long title, no cover (BlurHash only). */
@PreviewTest
@MqPreviews
@Composable
fun MqMiniPlayerScreenshot() {
    MediatheqTheme {
        Surface {
            Column(
                Modifier.padding(MediatheqSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            ) {
                MiniPlayer(title = "Strobe", isPlaying = true, cover = rememberPreviewCover())
                MiniPlayer(title = "Strobe", isPlaying = false, hasNext = false, cover = rememberPreviewCover())
                MiniPlayer(
                    title = "A very long track title that does not fit on one line (Extended Club Mix)",
                    isPlaying = true,
                    cover = rememberPreviewCover(),
                )
                MiniPlayer(title = "Strobe", isPlaying = true, cover = null)
            }
        }
    }
}

/** The mini player in a cover-derived scheme (a fixed seed stands in for a real cover). */
@PreviewTest
@MqPreviews
@Composable
fun MqMiniPlayerCoverColorsScreenshot() {
    MediatheqTheme {
        val dark = MaterialTheme.colorScheme.surface.red < 0.5f
        MqCoverTheme(coverColorScheme(PREVIEW_COVER_SEED, isDark = dark)) {
            Surface {
                MiniPlayer(
                    title = "Strobe",
                    isPlaying = true,
                    cover = rememberPreviewCover(),
                    Modifier.padding(MediatheqSpacing.l),
                )
            }
        }
    }
}

/** Seek bar while seekable, and disabled for a stream that cannot be seeked (a transcode). */
@PreviewTest
@MqPreviews
@Composable
fun MqSeekBarScreenshot() {
    MediatheqTheme {
        Surface {
            Column(
                Modifier.padding(MediatheqSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.l),
            ) {
                MqSeekBar(progress = 0.35f, elapsedLabel = "2:48", totalLabel = "8:00", onSeek = {})
                MqSeekBar(progress = 0.35f, elapsedLabel = "2:48", totalLabel = "8:00", onSeek = {}, enabled = false)
            }
        }
    }
}

/** Transport while playing (squarish pause) and paused at the first track (round play, previous still enabled). */
@PreviewTest
@MqPreviews
@Composable
fun MqTransportControlsScreenshot() {
    MediatheqTheme {
        Surface {
            Column(
                Modifier.padding(MediatheqSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.l),
            ) {
                MqTransportControls(isPlaying = true, hasPrevious = true, hasNext = true, {}, {}, {})
                MqTransportControls(isPlaying = false, hasPrevious = true, hasNext = false, {}, {}, {})
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    title: String,
    isPlaying: Boolean,
    cover: Any?,
    modifier: Modifier = Modifier,
    hasNext: Boolean = true,
) {
    MqMiniPlayer(
        title = title,
        artist = "deadmau5",
        cover = cover,
        isPlaying = isPlaying,
        hasNext = hasNext,
        onPlayPause = {},
        onNext = {},
        onOpen = {},
        modifier = modifier,
        coverBlurHash = PREVIEW_BLUR_HASH,
    )
}

private const val PREVIEW_COVER_SEED = 0xFF00897B.toInt()
