package io.github.kentprodan.mediatheq.feature.tv.library

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.tv.material3.Surface
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.designsystem.tv.PreviewFixtures
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId

private fun items(
    kind: TvLibraryItem.Kind,
    names: List<String>,
    subtitle: String? = null,
    albumCount: Int? = null,
    letters: Boolean = true,
): TvLibraryContent.Items {
    val items = names.map { TvLibraryItem(MediaId(ServerId("s"), it), kind, it, subtitle, albumCount = albumCount) }
    return TvLibraryContent.Items(items, if (letters) alphabetIndex(names) else emptyList())
}

private val albumNames =
    listOf(
        "Alive 2007",
        "Black Holes",
        "Discovery",
        PreviewFixtures.ALBUM,
        "Homework",
        "In Rainbows",
        "Kid A",
        "Mezzanine",
    )

/** A tab under the tab row, as the app shows it. No covers: layoutlib loads no image. */
@Composable
private fun Tab(
    tab: TvLibraryTab,
    state: TvLibraryUiState,
) = MediatheqTvTheme {
    Surface {
        Column {
            TvLibraryTabs(tab) {}
            TvLibraryList(state, {}, TvLibraryNavigation())
        }
    }
}

/** Albums by name, with the A-Z strip. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvLibraryAlbumsScreenshot() =
    Tab(
        TvLibraryTab.Albums,
        TvLibraryUiState(
            LibraryFilter.Albums,
            content = items(TvLibraryItem.Kind.Album, albumNames, PreviewFixtures.ARTIST),
        ),
    )

/** Artists: one sort, so no sort button. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvLibraryArtistsScreenshot() =
    Tab(
        TvLibraryTab.Artists,
        TvLibraryUiState(
            LibraryFilter.Artists,
            content = items(TvLibraryItem.Kind.Artist, listOf("Air", "Boards of Canada", "deadmau5")),
        ),
    )

/** Playlists by date added: no strip. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvLibraryPlaylistsScreenshot() =
    Tab(
        TvLibraryTab.Playlists,
        TvLibraryUiState(
            LibraryFilter.Playlists,
            LibrarySort.RecentlyAdded,
            items(TvLibraryItem.Kind.Playlist, listOf("Road trip", "Focus"), letters = false),
        ),
    )

@PreviewTest
@MqTvPreviews
@Composable
fun TvLibraryGenresScreenshot() =
    Tab(
        TvLibraryTab.Genres,
        TvLibraryUiState(
            LibraryFilter.Genres,
            content = items(TvLibraryItem.Kind.Genre, listOf("Ambient", "House", "Techno"), albumCount = 12),
        ),
    )

@PreviewTest
@MqTvPreviews
@Composable
fun TvLibraryEmptyScreenshot() =
    Tab(TvLibraryTab.Playlists, TvLibraryUiState(LibraryFilter.Playlists, content = TvLibraryContent.Empty))

@PreviewTest
@MqTvPreviews
@Composable
fun TvLibraryErrorScreenshot() =
    Tab(TvLibraryTab.Albums, TvLibraryUiState(LibraryFilter.Albums, content = TvLibraryContent.Error(MqError.Network)))

/** A genre's page: its name over the album grid. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvLibraryGenrePageScreenshot() =
    MediatheqTvTheme {
        Surface {
            TvLibraryList(
                TvLibraryUiState(
                    LibraryFilter.Albums,
                    content = items(TvLibraryItem.Kind.Album, albumNames.take(3), PreviewFixtures.ARTIST),
                ),
                {},
                TvLibraryNavigation(),
                title = "Techno",
            )
        }
    }

@PreviewTest
@MqTvPreviews
@Composable
fun TvLibraryLoadingScreenshot() = Tab(TvLibraryTab.Albums, TvLibraryUiState(LibraryFilter.Albums))

/** A year's page: the year over its albums. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvLibraryYearPageScreenshot() =
    MediatheqTvTheme {
        Surface {
            TvLibraryList(
                TvLibraryUiState(
                    LibraryFilter.Albums,
                    content = items(TvLibraryItem.Kind.Album, albumNames.take(2), PreviewFixtures.ARTIST),
                ),
                {},
                TvLibraryNavigation(),
                title = "1997",
            )
        }
    }
