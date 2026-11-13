package io.github.kentprodan.mediatheq.feature.tv.nowplaying

import androidx.compose.runtime.Composable
import androidx.tv.material3.Surface
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.common.QualityLabel
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.designsystem.tv.PreviewFixtures
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.playback.QueueItem
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val queue =
    listOf(PreviewFixtures.TRACK, "Ghosts 'n' Stuff", "Moar Ghosts 'n' Stuff", "FML", "Some Chords").mapIndexed {
        i,
        title,
        ->
        QueueItem("e$i", MediaId(ServerId("s"), "t$i"), title, PreviewFixtures.ARTIST)
    }

/** Playing the first of five, shuffle on, repeat all, a favorite, Hi-Res. No cover: layoutlib loads no image. */
private val playing =
    TvNowPlayingUiState.Playing(
        PreviewFixtures.TRACK,
        PreviewFixtures.ARTIST,
        PreviewFixtures.ALBUM,
        isPlaying = true,
        duration = 634.seconds,
        isSeekable = true,
        hasNext = true,
        shuffle = true,
        repeatMode = RepeatMode.All,
        isFavorite = true,
        quality = QualityLabel(QualityKind.HiResLossless, "FLAC · 24/96"),
        queue = queue,
        currentEntryId = "e0",
    )

@Composable
private fun Player(state: TvNowPlayingUiState) =
    MediatheqTvTheme { Surface { TvNowPlayingScreen(state, 3.minutes + 12.seconds, {}) } }

@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingScreenshot() = Player(playing)

@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingPausedScreenshot() =
    Player(playing.copy(isPlaying = false, shuffle = false, repeatMode = RepeatMode.One))

@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingBufferingScreenshot() = Player(playing.copy(isBuffering = true, quality = null))

/** Dolby Atmos passing through over HDMI: the badge (#242). */
@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingAtmosScreenshot() = Player(playing.copy(quality = QualityLabel(QualityKind.DolbyAtmos, "E-AC-3 JOC")))

/** A Dolby Atmos file decoded on a route without passthrough: no badge, the note. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingAtmosDecodedScreenshot() = Player(playing.copy(quality = null, atmosDecoded = true))

/** A Dolby Atmos file the server converts to stereo: the Opus label and the note. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingAtmosStereoScreenshot() =
    Player(
        playing.copy(quality = QualityLabel(QualityKind.HighQuality, "Opus · 256 kb/s"), atmosConverted = true),
    )

private val synced =
    Lyrics(
        listOf("I wake up to the sound", "of music in my head", "", "and I follow it down", "to the light on the floor")
            .mapIndexed { i, text -> LyricLine(text, (i * 5).seconds) },
    )

/** Synced lyrics in the queue's place (#245): the sung line large and centered, the others dimmed. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingLyricsSyncedScreenshot() =
    MediatheqTvTheme {
        Surface {
            TvNowPlayingScreen(playing, 6.seconds, {}, lyrics = TvLyricsUiState.Content(synced), lyricsOpen = true)
        }
    }

@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingLyricsPlainScreenshot() =
    MediatheqTvTheme {
        Surface {
            TvNowPlayingScreen(
                playing,
                11.seconds,
                {},
                lyrics = TvLyricsUiState.Content(Lyrics(synced.lines.map { LyricLine(it.text) })),
                lyricsOpen = true,
            )
        }
    }

/** No lyrics: the Lyrics button is off and the hint under the controls says why. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingNoLyricsScreenshot() =
    MediatheqTvTheme { Surface { TvNowPlayingScreen(playing, 11.seconds, {}, lyrics = TvLyricsUiState.None) } }

@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingIdleScreenshot() = Player(TvNowPlayingUiState.Idle)
