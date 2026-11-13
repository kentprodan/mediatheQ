package io.github.kentprodan.mediatheq.feature.tv.nowplaying

import androidx.compose.runtime.Composable
import androidx.tv.material3.Surface
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.common.QualityLabel
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.designsystem.tv.PreviewFixtures
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

/** A Dolby Atmos file that this output plays as stereo (#242 adds the output notes). */
@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingAtmosScreenshot() =
    Player(playing.copy(quality = QualityLabel(QualityKind.DolbyAtmos, "E-AC-3 JOC"), atmosConverted = true))

@PreviewTest
@MqTvPreviews
@Composable
fun TvNowPlayingIdleScreenshot() = Player(TvNowPlayingUiState.Idle)
