package io.github.kentprodan.mediatheq.feature.tv.search

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.designsystem.tv.PreviewFixtures
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration.Companion.minutes

private fun id(value: String) = MediaId(ServerId("s"), value)

private val artist = Artist(id("ar"), PreviewFixtures.ARTIST)

@Composable
private fun Search(
    query: String,
    state: TvSearchUiState,
) = MediatheqTvTheme { TvSearchScreen(query, state, {}, TvSearchNavigation()) }

@PreviewTest
@MqTvPreviews
@Composable
fun TvSearchRecentScreenshot() = Search("", TvSearchUiState.Recent(listOf("deadmau5", "Moșoi", "boards of canada")))

@PreviewTest
@MqTvPreviews
@Composable
fun TvSearchEmptyScreenshot() = Search("", TvSearchUiState.Recent(emptyList()))

@PreviewTest
@MqTvPreviews
@Composable
fun TvSearchResultsScreenshot() =
    Search(
        "dead",
        TvSearchUiState.Results(
            albums = listOf(Album(id("al"), PreviewFixtures.ALBUM, listOf(artist))),
            artists = listOf(artist),
            tracks = (1..4).map { Track(id("t$it"), "Track $it", listOf(artist), 3.minutes) },
            playlists = listOf(Playlist(id("pl"), "Dead mix")),
        ),
    )

@PreviewTest
@MqTvPreviews
@Composable
fun TvSearchNoResultsScreenshot() = Search("zzz", TvSearchUiState.NoResults)

@PreviewTest
@MqTvPreviews
@Composable
fun TvSearchErrorScreenshot() = Search("dead", TvSearchUiState.Error(MqError.Storage))
