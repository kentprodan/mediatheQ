package io.github.kentprodan.mediatheq.feature.tv.home

import androidx.compose.runtime.Composable
import androidx.tv.material3.Surface
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.designsystem.tv.PreviewFixtures
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration

private fun id(value: String) = MediaId(ServerId("s"), value)

private val artist = Artist(id("ar"), PreviewFixtures.ARTIST)

/** Mixes, a row of albums wider than the screen, artists and tracks. No covers: layoutlib loads no image. */
private val content =
    TvHomeUiState.Content(
        listOf(
            TvHomeRow(HomeSection.Shortcuts, HomeItems.Shortcuts(listOf(1990, 2000))),
            TvHomeRow(
                HomeSection.RecentlyAddedAlbums,
                HomeItems.Albums(
                    listOf(Album(id("al1"), PreviewFixtures.ALBUM, listOf(artist))) +
                        (2..8).map { Album(id("al$it"), "Album $it", listOf(artist)) },
                ),
            ),
            TvHomeRow(
                HomeSection.FavoriteArtists,
                HomeItems.Artists(listOf(artist, Artist(id("b"), "Boards of Canada"))),
            ),
            TvHomeRow(
                HomeSection.MostPlayedTracks,
                HomeItems.Tracks(listOf(Track(id("t"), PreviewFixtures.TRACK, listOf(artist), Duration.ZERO))),
            ),
        ),
    )

@Composable
private fun Home(state: TvHomeUiState) = MediatheqTvTheme { Surface { TvHomeScreen(state, {}, TvHomeNavigation()) } }

@PreviewTest
@MqTvPreviews
@Composable
fun TvHomeContentScreenshot() = Home(content)

@PreviewTest
@MqTvPreviews
@Composable
fun TvHomeSyncingScreenshot() = Home(TvHomeUiState.Syncing(120, 900))

@PreviewTest
@MqTvPreviews
@Composable
fun TvHomeEmptyScreenshot() = Home(TvHomeUiState.Empty)

@PreviewTest
@MqTvPreviews
@Composable
fun TvHomeErrorScreenshot() = Home(TvHomeUiState.Error(MqError.Network))
