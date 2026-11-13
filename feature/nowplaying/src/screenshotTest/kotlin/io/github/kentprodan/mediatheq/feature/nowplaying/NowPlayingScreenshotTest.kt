package io.github.kentprodan.mediatheq.feature.nowplaying

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.common.QualityLabel
import io.github.kentprodan.mediatheq.core.data.NowPlayingPreferences
import io.github.kentprodan.mediatheq.core.data.PlayerBackground
import io.github.kentprodan.mediatheq.core.data.PlayerProgress
import io.github.kentprodan.mediatheq.core.data.ProgressFallback
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.preview.rememberPreviewCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.CoverColors
import io.github.kentprodan.mediatheq.core.designsystem.theme.LocalMqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqCoverTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqPlayerColors
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeMode
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeOptions
import io.github.kentprodan.mediatheq.core.designsystem.theme.coverColorScheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.playerColorScheme
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.ServerId
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
        artists = listOf(Artist(MediaId(ServerId("s"), "ar1"), "deadmau5")),
        albumId = MediaId(ServerId("s"), "al1"),
        albumTitle = "For Lack of a Better Name",
        quality = QualityLabel(QualityKind.HiResLossless, "FLAC · 24/96 · 1250 kb/s"),
    )

private val previewLyrics =
    LyricsUiState.Content(
        Lyrics(
            listOf("Out in the dark", "Lights on the water", "Hold the moment", "Let it linger", "Sing it slow")
                .mapIndexed { i, text -> LyricLine(text, (i * 50).seconds) },
        ),
    )

@Composable
private fun Screen(
    state: NowPlayingUiState.Playing,
    lyrics: LyricsUiState = LyricsUiState.None,
) {
    NowPlayingScreen(
        state = state,
        position = { 3.minutes + 12.seconds },
        onAction = {},
        onCollapse = {},
        lyrics = lyrics,
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
    MediatheqTheme { Screen(playing.copy(isFavorite = true, halfStars = 8)) }
}

/** The track's waveform, computed on the device (#124), as the seek bar. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingWaveformScreenshot() {
    MediatheqTheme { Screen(playing.copy(waveform = List(200) { (0.3f + 0.7f * ((it * 37) % 100) / 100f) })) }
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

/** Shuffle on and repeat all: both toggles squarish and filled. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingShuffleRepeatAllScreenshot() {
    MediatheqTheme { Screen(playing.copy(shuffle = true, repeatMode = RepeatMode.All)) }
}

/** Repeat one: its own icon. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingRepeatOneScreenshot() {
    MediatheqTheme { Screen(playing.copy(repeatMode = RepeatMode.One)) }
}

/** The player color options of Settings -> Theme, font and colors (#121), with a fixed seed for the cover. */
@Composable
private fun PlayerColors(options: MqThemeOptions) {
    MediatheqTheme(options) {
        val colors =
            CoverColors(
                coverColorScheme(PREVIEW_COVER_SEED, isDark = false),
                coverColorScheme(PREVIEW_COVER_SEED, isDark = true),
                coverColorScheme(PREVIEW_COVER_SEED, isDark = false, vibrant = true),
                coverColorScheme(PREVIEW_COVER_SEED, isDark = true, vibrant = true),
            )
        MqCoverTheme(playerColorScheme(colors, LocalMqTheme.current)) { Screen(playing.copy(isPlaying = false)) }
    }
}

/** Now playing color source Off: the app theme. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingColorsOffScreenshot() = PlayerColors(MqThemeOptions(playerColors = MqPlayerColors.Off))

/** Cover vibrant: SchemeVibrant from the cover seed. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingCoverVibrantScreenshot() = PlayerColors(MqThemeOptions(playerColors = MqPlayerColors.CoverVibrant))

/** Cover colors with background and surfaces from the app theme. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingIgnoreSurfacesScreenshot() = PlayerColors(MqThemeOptions(ignoreSurfaces = true))

/** Black mode: cover accents on pure black. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingBlackScreenshot() = PlayerColors(MqThemeOptions(mode = MqThemeMode.Black))

/** Lyrics in place of the cover, the line being sung marked (#125). */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingLyricsScreenshot() {
    MediatheqTheme { Screen(playing.copy(lyricsShown = true), previewLyrics) }
}

/** Landscape on a Pixel 9: cover on the left, the rest on the right; no room for the stars. */
@PreviewTest
@Preview(name = "Landscape", device = "spec:width=915dp,height=412dp,dpi=420")
@Preview(name = "Landscape dark", device = "spec:width=915dp,height=412dp,dpi=420", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Landscape 2x", device = "spec:width=915dp,height=412dp,dpi=420", fontScale = 2f)
@Composable
fun NowPlayingLandscapeScreenshot() {
    MediatheqTheme { Screen(playing) }
}

/** Landscape with lyrics in the cover's place. */
@PreviewTest
@Preview(name = "Landscape", device = "spec:width=915dp,height=412dp,dpi=420")
@Preview(name = "Landscape 2x", device = "spec:width=915dp,height=412dp,dpi=420", fontScale = 2f)
@Preview(name = "Landscape dark", device = "spec:width=915dp,height=412dp,dpi=420", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun NowPlayingLandscapeLyricsScreenshot() {
    MediatheqTheme { Screen(playing.copy(lyricsShown = true), previewLyrics) }
}

/** A long title scrolls (still in a screenshot); ratings off hide the stars. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingLongTitleNoRatingScreenshot() {
    MediatheqTheme {
        Screen(
            playing.copy(
                title = "Strobe (Extended Mix with a Very Long Name for the Marquee)",
                ratingMode = RatingMode.Off,
            ),
        )
    }
}

/** Every quality label, as its kind and as its detail. */
@PreviewTest
@Preview(name = "Light", widthDp = 412)
@Preview(name = "Dark", widthDp = 412, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun NowPlayingQualityLabelsScreenshot() {
    val labels =
        listOf(
            QualityLabel(QualityKind.DolbyAtmos, "E-AC-3 · 768 kb/s"),
            QualityLabel(QualityKind.HiResLossless, "FLAC · 24/96 · 1250 kb/s"),
            QualityLabel(QualityKind.Lossless, "FLAC · 16/44.1 · 900 kb/s"),
            QualityLabel(QualityKind.HighQuality, "Opus · 256 kb/s"),
            QualityLabel(QualityKind.HighEfficiency, "AAC · 128 kb/s"),
        )
    MediatheqTheme {
        Surface {
            Column {
                labels.forEach { label ->
                    Row {
                        QualityLabelButton(label, detail = false, onAction = {})
                        QualityLabelButton(label, detail = true, onAction = {})
                    }
                }
            }
        }
    }
}

/** The background styles (#126): solid cover color, theme background (gradient is the default above). */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingSolidBackgroundScreenshot() {
    MediatheqTheme { Screen(playing.copy(settings = NowPlayingPreferences(background = PlayerBackground.Solid))) }
}

@PreviewTest
@MqPreviews
@Composable
fun NowPlayingThemeBackgroundScreenshot() {
    MediatheqTheme { Screen(playing.copy(settings = NowPlayingPreferences(background = PlayerBackground.Theme))) }
}

/** Each progress bar style with a waveform known (#126); Basic and Wavy ignore it. */
class PlayerProgressStyles : PreviewParameterProvider<PlayerProgress> {
    override val values = PlayerProgress.entries.asSequence()
}

@PreviewTest
@Preview(name = "Light", device = "spec:width=412dp,height=915dp,dpi=420")
@Composable
fun NowPlayingProgressStylesScreenshot(
    @PreviewParameter(PlayerProgressStyles::class) style: PlayerProgress,
) {
    val levels = List(200) { (0.3f + 0.7f * ((it * 37) % 100) / 100f) }
    MediatheqTheme { Screen(playing.copy(waveform = levels, settings = NowPlayingPreferences(progress = style))) }
}

/** Paused with the play button over the cover; the stars switched off. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingPlayButtonNoStarsScreenshot() {
    MediatheqTheme {
        Screen(
            playing.copy(
                isPlaying = false,
                settings =
                    NowPlayingPreferences(
                        playButtonWhenPaused = true,
                        showRating = false,
                        fallback = ProgressFallback.Wavy,
                    ),
            ),
        )
    }
}

/** Advanced settings -> Show track number (#137): the place in the queue above the title. */
@PreviewTest
@MqPreviews
@Composable
fun NowPlayingTrackNumberScreenshot() {
    MediatheqTheme { Screen(playing.copy(trackNumber = QueuePosition(3, 12))) }
}

@PreviewTest
@Preview(name = "Landscape", device = "spec:width=915dp,height=412dp,dpi=420")
@Preview(name = "Landscape 2x", device = "spec:width=915dp,height=412dp,dpi=420", fontScale = 2f)
@Composable
fun NowPlayingLandscapeTrackNumberScreenshot() {
    MediatheqTheme { Screen(playing.copy(trackNumber = QueuePosition(3, 12))) }
}
