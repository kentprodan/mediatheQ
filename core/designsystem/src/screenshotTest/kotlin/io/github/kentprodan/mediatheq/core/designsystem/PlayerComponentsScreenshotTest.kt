package io.github.kentprodan.mediatheq.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.component.MqIconToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqMiniPlayer
import io.github.kentprodan.mediatheq.core.designsystem.component.MqMiniProgress
import io.github.kentprodan.mediatheq.core.designsystem.component.MqPlayerCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqSeekBar
import io.github.kentprodan.mediatheq.core.designsystem.component.MqTransportControls
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
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

/** Each progress style, floating and docked (#126), at 40 % played. */
@PreviewTest
@MqPreviews
@Composable
fun MqMiniPlayerStylesScreenshot() {
    MediatheqTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m)) {
                listOf(false, true).forEach { docked ->
                    MqMiniProgress.entries.forEach { style ->
                        MqMiniPlayer(
                            title = "Strobe",
                            artist = "deadmau5 · ${style.name}",
                            cover = null,
                            isPlaying = true,
                            hasNext = true,
                            onPlayPause = {},
                            onNext = {},
                            onOpen = {},
                            modifier = if (docked) Modifier else Modifier.padding(horizontal = MediatheqSpacing.l),
                            coverBlurHash = PREVIEW_BLUR_HASH,
                            docked = docked,
                            progressStyle = style,
                            progress = { 0.4f },
                        )
                    }
                }
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

/**
 * Repeat and shuffle at the ends (#125): full sizes on a Pixel 9, one size down on a 320 dp phone; nothing goes to the
 * overflow menu.
 */
@PreviewTest
@MqPreviews
@Composable
fun MqTransportControlsWithTogglesScreenshot() {
    MediatheqTheme {
        Surface {
            Column(
                Modifier.padding(MediatheqSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.l),
            ) {
                listOf(Modifier.fillMaxWidth(), Modifier.width(288.dp)).forEach { width ->
                    MqTransportControls(
                        isPlaying = true,
                        hasPrevious = true,
                        hasNext = true,
                        {},
                        {},
                        {},
                        width,
                        leading = { MqIconToggle(false, {}, MqIcons.Repeat, "Repeat") },
                        trailing = { MqIconToggle(true, {}, MqIcons.Shuffle, "Shuffle") },
                    )
                }
            }
        }
    }
}

/** The large Now Playing cover: loaded, and BlurHash only. */
@PreviewTest
@MqPreviews
@Composable
fun MqPlayerCoverScreenshot() {
    MediatheqTheme {
        Surface {
            Column(
                Modifier.padding(MediatheqSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.l),
            ) {
                MqPlayerCover(model = rememberPreviewCover())
                MqPlayerCover(model = null, blurHash = PREVIEW_BLUR_HASH)
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
