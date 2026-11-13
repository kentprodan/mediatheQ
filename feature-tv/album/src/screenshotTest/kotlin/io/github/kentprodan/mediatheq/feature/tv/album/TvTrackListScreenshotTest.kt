package io.github.kentprodan.mediatheq.feature.tv.album

import androidx.compose.runtime.Composable
import androidx.tv.material3.Surface
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.common.QualityLabel
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.designsystem.tv.PreviewFixtures
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ResumePoint
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private fun id(value: String) = MediaId(ServerId("s"), value)

private val artist = Artist(id("ar"), PreviewFixtures.ARTIST)

private fun track(
    n: Int,
    disc: Int = 1,
) = Track(
    id("d${disc}t$n"),
    if (n ==
        1
    ) {
        PreviewFixtures.TRACK
    } else {
        "Track $n"
    },
    listOf(artist),
    (200 + n * 13).seconds,
    trackNumber = n,
    discNumber = disc,
)

/** Two discs, the second track playing, Resume shown. No cover: layoutlib loads no image. */
private val album =
    TvTrackListUiState.Content(
        TvTrackListHeader(
            PreviewFixtures.ALBUM,
            PreviewFixtures.ARTIST,
            2009,
            trackCount = 5,
            duration = 18.minutes,
            quality = QualityLabel(QualityKind.HiResLossless, "FLAC · 24/96"),
            isFavorite = true,
        ),
        listOf(TvDisc(1, (1..3).map { track(it) }), TvDisc(2, (1..2).map { track(it, 2) })),
        currentTrackId = id("d1t2"),
        resume = ResumePoint(id("d1t2"), 1.minutes),
    )

private val playlist =
    TvTrackListUiState.Content(
        TvTrackListHeader("Road trip", trackCount = 3, duration = 11.minutes),
        listOf(TvDisc(1, listOf(track(4), track(1), track(7)))),
    )

@Composable
private fun Page(
    state: TvTrackListUiState,
    showNumbers: Boolean = true,
) = MediatheqTvTheme { Surface { TvTrackListScreen(state, {}, showNumbers = showNumbers) } }

@PreviewTest
@MqTvPreviews
@Composable
fun TvAlbumPageScreenshot() = Page(album)

@PreviewTest
@MqTvPreviews
@Composable
fun TvPlaylistPageScreenshot() = Page(playlist, showNumbers = false)

@PreviewTest
@MqTvPreviews
@Composable
fun TvTrackListLoadingScreenshot() = Page(TvTrackListUiState.Loading)

@PreviewTest
@MqTvPreviews
@Composable
fun TvTrackListNotFoundScreenshot() = Page(TvTrackListUiState.NotFound)
