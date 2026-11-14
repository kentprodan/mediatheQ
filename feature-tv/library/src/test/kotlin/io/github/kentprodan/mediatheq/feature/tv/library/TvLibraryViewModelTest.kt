package io.github.kentprodan.mediatheq.feature.tv.library

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.YearKey
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class TvLibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val sync = FakeLibrarySyncRepository()
    private val preferences = FakeLibraryPreferencesRepository()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private fun day(n: Int) = Instant.fromEpochSeconds(0) + n.days

    private val ada = Artist(id("ada"), "Ada")
    private val zed = Album(id("z"), "Zed", listOf(ada), year = 1994, dateAdded = day(9))
    private val alpha = Album(id("a"), "Alpha", listOf(ada), year = 2001, dateAdded = day(1))
    private val beta = Album(id("b"), "beta", emptyList(), dateAdded = day(5))

    private fun TestScope.viewModel(
        filter: LibraryFilter,
        genre: MediaId? = null,
        year: YearKey? = null,
    ): TvLibraryViewModel {
        val viewModel =
            TvLibraryViewModel(filter, genre, year, library, sync, preferences, mainDispatcherRule.testDispatcher)
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private fun TvLibraryViewModel.items() = (uiState.value.content as TvLibraryContent.Items)

    private fun TvLibraryViewModel.titles() = items().items.map { it.title }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `albums come by name with their artists and the A-Z strip, a sort changes the order and drops the strip`() =
        test {
            sync.status.value = SyncStatus.Idle(day(10))
            library.albums.value = listOf(zed, alpha, beta)
            val viewModel = viewModel(LibraryFilter.Albums)

            assertThat(viewModel.titles()).containsExactly("Alpha", "beta", "Zed").inOrder()
            assertThat(
                viewModel
                    .items()
                    .items
                    .first()
                    .subtitle,
            ).isEqualTo("Ada")
            assertThat(
                viewModel.items().letters,
            ).containsExactly(Letter('A', 0), Letter('B', 1), Letter('Z', 2)).inOrder()

            viewModel.onAction(TvLibraryAction.SetSort(LibrarySort.RecentlyAdded))
            runCurrent()

            assertThat(viewModel.uiState.value.sort).isEqualTo(LibrarySort.RecentlyAdded)
            assertThat(viewModel.titles()).containsExactly("Zed", "beta", "Alpha").inOrder()
            assertThat(viewModel.items().letters).isEmpty()
        }

    @Test
    fun `Reverse order turns the list and the A-Z strip around, and back`() =
        test {
            sync.status.value = SyncStatus.Idle(day(10))
            val gamma = Album(id("g"), "Gamma", listOf(ada))
            library.albums.value = listOf(zed, alpha, beta, gamma)
            val viewModel = viewModel(LibraryFilter.Albums)

            viewModel.onAction(TvLibraryAction.ReverseSort)
            runCurrent()

            assertThat(viewModel.uiState.value.reversed).isTrue()
            assertThat(viewModel.titles()).containsExactly("Zed", "Gamma", "beta", "Alpha").inOrder()
            assertThat(viewModel.items().letters)
                .containsExactly(Letter('Z', 0), Letter('G', 1), Letter('B', 2), Letter('A', 3))
                .inOrder()

            viewModel.onAction(TvLibraryAction.ReverseSort)
            runCurrent()
            assertThat(viewModel.titles()).containsExactly("Alpha", "beta", "Gamma", "Zed").inOrder()
        }

    @Test
    fun `a letter with several cards jumps to its first card in the reversed order`() {
        val items = (0..4).map { TvLibraryItem(id("$it"), TvLibraryItem.Kind.Album, "t$it") }
        // A A B B B -> reversed B B B A A: B at 0, A at 3.
        val reversed = TvLibraryContent.Items(items, listOf(Letter('A', 0), Letter('B', 2))).reversed()

        assertThat(reversed.letters).containsExactly(Letter('B', 0), Letter('A', 3)).inOrder()
    }

    @Test
    fun `artists, playlists and genres are their own tabs`() =
        test {
            sync.status.value = SyncStatus.Idle(day(10))
            library.albumArtists.value = listOf(Artist(id("r2"), "Röyksopp"), ada)
            library.playlists.value = listOf(Playlist(id("p"), "Road trip"))
            library.genres.value = listOf(Genre(id("g"), "Techno", albumCount = 3))

            assertThat(viewModel(LibraryFilter.Artists).titles()).containsExactly("Ada", "Röyksopp").inOrder()
            assertThat(viewModel(LibraryFilter.Playlists).titles()).containsExactly("Road trip")
            val artists = viewModel(LibraryFilter.Artists)
            assertThat(artists.items().letters).containsExactly(Letter('A', 0), Letter('R', 1)).inOrder()
            val genres = viewModel(LibraryFilter.Genres)
            assertThat(
                genres
                    .items()
                    .items
                    .single()
                    .albumCount,
            ).isEqualTo(3)
            assertThat(
                genres
                    .items()
                    .items
                    .single()
                    .kind,
            ).isEqualTo(TvLibraryItem.Kind.Genre)

            // By album count: no strip.
            genres.onAction(TvLibraryAction.SetSort(LibrarySort.AlbumCount))
            runCurrent()
            assertThat(genres.items().letters).isEmpty()
        }

    @Test
    fun `a genre page lists the genre's albums under its name, a year page the year's albums`() =
        test {
            sync.status.value = SyncStatus.Idle(day(10))
            library.albums.value = listOf(zed, alpha, beta)
            library.genres.value = listOf(Genre(id("g"), "Techno"))
            library.genreAlbums.value = mapOf(id("g") to listOf(zed))

            val genre = viewModel(LibraryFilter.Albums, genre = id("g"))
            backgroundScope.launch { genre.genreName.collect() }
            runCurrent()
            assertThat(genre.titles()).containsExactly("Zed")
            assertThat(genre.genreName.value).isEqualTo("Techno")

            assertThat(viewModel(LibraryFilter.Albums, year = YearKey(2001)).titles()).containsExactly("Alpha")
            assertThat(viewModel(LibraryFilter.Albums, year = YearKey(null)).titles()).containsExactly("beta")
        }

    @Test
    fun `an empty list shows loading, the failed first sync, or that it is empty, and Sync retries`() =
        test {
            sync.status.value = SyncStatus.Idle(null)
            val viewModel = viewModel(LibraryFilter.Albums)
            assertThat(viewModel.uiState.value.content).isEqualTo(TvLibraryContent.Loading)

            sync.status.value = SyncStatus.Failed(MqError.Network, null)
            runCurrent()
            assertThat(viewModel.uiState.value.content).isEqualTo(TvLibraryContent.Error(MqError.Network))

            sync.status.value = SyncStatus.Idle(day(10))
            runCurrent()
            assertThat(viewModel.uiState.value.content).isEqualTo(TvLibraryContent.Empty)

            viewModel.onAction(TvLibraryAction.Sync)
            runCurrent()
            assertThat(sync.syncCalls).isEqualTo(1)
        }
}
