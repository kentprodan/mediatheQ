package io.github.kentprodan.mediatheq.feature.nowplaying

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.preview.rememberPreviewCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqCoverTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.coverColorScheme
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val playing =
    NowPlayingUiState.Playing(
        title = "Strobe",
        artist = "deadmau5",
        artwork = null,
        isPlaying = true,
        duration = 10.minutes + 37.seconds,
        isSeekable = true,
        hasNext = true,
        hasPrevious = true,
    )

@Composable
private fun Screen(state: NowPlayingUiState.Playing) {
    NowPlayingScreen(
        state = state,
        position = { 3.minutes + 12.seconds },
        onAction = {},
        onCollapse = {},
        cover = rememberPreviewCover(),
    )
}

/** Iris theme (no cover colors yet), playing. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingScreenshot() {
    MediatheqTheme { Screen(playing) }
}

/** Cover-derived colors (a fixed seed stands in for a cover), paused. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingCoverColorsScreenshot() {
    MediatheqTheme {
        val dark = MaterialTheme.colorScheme.surface.red < 0.5f
        MqCoverTheme(coverColorScheme(PREVIEW_COVER_SEED, isDark = dark)) { Screen(playing.copy(isPlaying = false)) }
    }
}

/** Long title and artist, last track of the queue. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingLongTitlesScreenshot() {
    MediatheqTheme {
        Screen(
            playing.copy(
                title = "Concerto for Violin and Orchestra in D major, Op. 35: III. Finale. Allegro vivacissimo",
                artist = "Hilary Hahn, Royal Liverpool Philharmonic Orchestra, Vasily Petrenko",
                hasNext = false,
            ),
        )
    }
}

/** A transcode on mobile data: the seek bar is disabled (#38). */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingUnseekableScreenshot() {
    MediatheqTheme { Screen(playing.copy(isSeekable = false)) }
}

private const val PREVIEW_COVER_SEED = 0xFFC2185B.toInt()

/** A favorite rated with four stars. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingFavoriteRatedScreenshot() {
    MediatheqTheme { Screen(playing.copy(isFavorite = true, stars = 4)) }
}

/** A Dolby Atmos track this phone cannot play: the server's stereo version, and the note saying so. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingAtmosConvertedScreenshot() {
    MediatheqTheme { Screen(playing.copy(atmosConverted = true)) }
}

/** Bit-perfect to a USB DAC: the sample rate and the volume note. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingBitPerfectScreenshot() {
    MediatheqTheme { Screen(playing.copy(bitPerfectSampleRateHz = 96_000)) }
}
