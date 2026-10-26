package io.github.kentprodan.mediatheq.feature.library

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.FilterControlStyle
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Instant

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val sync = FakeLibrarySyncRepository()
    private val preferences = FakeLibraryPreferencesRepository()
    private val playback = FakePlaybackConnection()
    private val userData = FakeUserDataRepository()
    private val downloads = FakeDownloadRepository()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private fun day(n: Int) = Instant.fromEpochSeconds(n * 86_400L)

    private val ada = Artist(id("ar-ada"), "Ada Moșoi", sortName = "Mosoi, Ada")
    private val bob = Artist(id("ar-bob"), "bob", sortName = "bob")

    // Names chosen so each sort gives a different order: name B < c < Mo(ș)oi, newest c, latest year B.
    private val albums =
        listOf(
            Album(id("a1"), "Moșoi", listOf(bob), year = 2001, sortName = "Moșoi", dateAdded = day(2)),
            Album(id("a2"), "c", listOf(ada), year = 1999, sortName = "c", dateAdded = day(3)),
            Album(id("a3"), "B", listOf(bob), year = 2020, sortName = "B", dateAdded = null),
        )

    private val tracks =
        listOf(
            Track(
                id("t1"),
                "Zed",
                listOf(ada),
                Duration.ZERO,
                albumTitle = "B",
                trackNumber = 2,
                sortName = "Zed",
                dateAdded = day(1),
            ),
            Track(
                id("t2"),
                "alpha",
                listOf(bob),
                Duration.ZERO,
                albumTitle = "A",
                trackNumber = 1,
                sortName = "alpha",
                dateAdded = day(5),
            ),
            Track(
                id("t3"),
                "Mid",
                listOf(ada),
                Duration.ZERO,
                albumTitle = "B",
                trackNumber = 1,
                sortName = "Mid",
                dateAdded = null,
            ),
        )

    private val playlists =
        listOf(
            Playlist(id("p1"), "Road", sortName = "Road", dateAdded = day(1)),
            Playlist(id("p2"), "Gym", sortName = "Gym", dateAdded = day(9)),
        )

    private fun TestScope.viewModel(
        status: SyncStatus = SyncStatus.Idle(day(10)),
        savedState: SavedStateHandle = SavedStateHandle(),
        collect: Boolean = true,
    ): LibraryViewModel {
        library.albums.value = albums
        library.albumArtists.value = listOf(bob, ada)
        library.playlists.value = playlists
        library.tracks.value = tracks
        sync.status.value = status
        val viewModel =
            LibraryViewModel(
                savedState,
                library,
                sync,
                preferences,
                playback,
                userData,
                downloads,
                mainDispatcherRule.testDispatcher,
            )
        if (collect) backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private fun LibraryViewModel.ids(): List<String> =
        when (val content = uiState.value.content) {
            is LibraryContent.Albums -> content.albums.map { it.id.itemId }
            is LibraryContent.Artists -> content.artists.map { it.id.itemId }
            is LibraryContent.Playlists -> content.playlists.map { it.id.itemId }
            is LibraryContent.Songs -> content.tracks.map { it.id.itemId }
            else -> error("no items: $content")
        }

    @Test
    fun `a song's menu downloads it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel().onAction(LibraryAction.Download(tracks[0]))

            assertThat(downloads.downloads.value.map { it.track }).containsExactly(tracks[0])
        }

    @Test
    fun `a song's menu queues it next or last`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(LibraryAction.PlayNext(tracks[0]))
            viewModel.onAction(LibraryAction.AddToQueue(tracks[1]))

            assertThat(playback.playedNext).containsExactly(listOf(tracks[0]))
            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[1]))
        }

    @Test
    fun `a song menu favorites the track`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(LibraryAction.SetFavorite(tracks[0], true))

            assertThat(userData.calls).containsExactly("favorite:${tracks[0].id.itemId}:true")
        }

    @Test
    fun `R52 the first frame shows no filter style rather than the default, then the saved one`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferences.setFilterStyle(FilterControlStyle.ConnectedButtons)
            val viewModel = viewModel(collect = false)

            // What the screen draws before the state is collected: chips here would flash before the saved style.
            assertThat(viewModel.uiState.value.filterStyle).isNull()

            backgroundScope.launch { viewModel.uiState.collect() }
            assertThat(viewModel.uiState.value.filterStyle).isEqualTo(FilterControlStyle.ConnectedButtons)
        }

    @Test
    fun `starts on albums by name, two columns, chips`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            val state = viewModel.uiState.value
            assertThat(state.filter).isEqualTo(LibraryFilter.Albums)
            assertThat(state.sort).isEqualTo(LibrarySort.Name)
            assertThat(state.albumColumns).isEqualTo(2)
            assertThat(state.filterStyle).isEqualTo(FilterControlStyle.Chips)
            assertThat(viewModel.ids()).containsExactly("a3", "a2", "a1").inOrder()
        }

    @Test
    fun `every filter and sort orders its items`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            val expected =
                mapOf(
                    (LibraryFilter.Albums to LibrarySort.Name) to listOf("a3", "a2", "a1"),
                    (LibraryFilter.Albums to LibrarySort.RecentlyAdded) to listOf("a2", "a1", "a3"),
                    (LibraryFilter.Albums to LibrarySort.Year) to listOf("a3", "a1", "a2"),
                    // bob's albums (a1 2001, a3 2020) before Ada's ("Mosoi, Ada" sorts after "bob").
                    (LibraryFilter.Albums to LibrarySort.Artist) to listOf("a1", "a3", "a2"),
                    (LibraryFilter.Artists to LibrarySort.Name) to listOf("ar-bob", "ar-ada"),
                    (LibraryFilter.Playlists to LibrarySort.Name) to listOf("p2", "p1"),
                    (LibraryFilter.Playlists to LibrarySort.RecentlyAdded) to listOf("p2", "p1"),
                    (LibraryFilter.Songs to LibrarySort.Name) to listOf("t2", "t3", "t1"),
                    (LibraryFilter.Songs to LibrarySort.RecentlyAdded) to listOf("t2", "t1", "t3"),
                    (LibraryFilter.Songs to LibrarySort.Artist) to listOf("t2", "t3", "t1"),
                    (LibraryFilter.Songs to LibrarySort.Album) to listOf("t2", "t3", "t1"),
                )
            assertThat(expected.keys.map { it.first to it.second })
                .containsExactlyElementsIn(LibraryFilter.entries.flatMap { f -> f.sorts.map { f to it } })

            expected.forEach { (filterAndSort, ids) ->
                val (filter, sort) = filterAndSort
                viewModel.onAction(LibraryAction.SelectFilter(filter))
                viewModel.onAction(LibraryAction.SelectSort(sort))
                assertThat(viewModel.uiState.value.sort).isEqualTo(sort)
                assertWithMessage("$filter by $sort").that(viewModel.ids()).containsExactlyElementsIn(ids).inOrder()
            }
        }

    @Test
    fun `each tab remembers its own sort`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            viewModel.onAction(LibraryAction.SelectSort(LibrarySort.Year))

            viewModel.onAction(LibraryAction.SelectFilter(LibraryFilter.Songs))
            assertThat(viewModel.uiState.value.sort).isEqualTo(LibrarySort.Name)

            viewModel.onAction(LibraryAction.SelectFilter(LibraryFilter.Albums))
            assertThat(viewModel.uiState.value.sort).isEqualTo(LibrarySort.Year)
        }

    @Test
    fun `columns and filter style are saved`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(LibraryAction.SetAlbumColumns(4))
            viewModel.onAction(LibraryAction.SetFilterStyle(FilterControlStyle.ConnectedButtons))

            assertThat(viewModel.uiState.value.albumColumns).isEqualTo(4)
            assertThat(viewModel.uiState.value.filterStyle).isEqualTo(FilterControlStyle.ConnectedButtons)
            assertThat(preferences.preferences.value.albumColumns).isEqualTo(4)
        }

    @Test
    fun `pull to refresh syncs and shows the spinner while it runs`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(LibraryAction.Refresh)
            sync.status.value = SyncStatus.Running(SyncStep.Tracks, 500, 2729)

            assertThat(sync.syncCalls).isEqualTo(1)
            assertThat(viewModel.uiState.value.refreshing).isTrue()
            assertThat(viewModel.ids()).isNotEmpty()
        }

    @Test
    fun `before the first complete sync an empty tab shows the sync state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = emptyList()
            val expected =
                mapOf(
                    SyncStatus.Running(SyncStep.Albums, 0, 10) to LibraryContent.Loading,
                    SyncStatus.Idle(null) to LibraryContent.Loading,
                    SyncStatus.Failed(MqError.Network, null) to LibraryContent.Offline,
                    SyncStatus.Failed(MqError.Server(500), null) to LibraryContent.Error(MqError.Server(500)),
                )

            expected.forEach { (status, content) ->
                val viewModel = viewModel(status = status)
                library.albums.value = emptyList()
                assertWithMessage(status.toString()).that(viewModel.uiState.value.content).isEqualTo(content)
            }
        }

    @Test
    fun `after a complete sync an empty tab stays empty while syncing or failing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            viewModel.onAction(LibraryAction.SelectFilter(LibraryFilter.Playlists))
            library.playlists.value = emptyList()

            sync.status.value = SyncStatus.Running(SyncStep.Albums, 0, 10)
            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.Empty)

            sync.status.value = SyncStatus.Failed(MqError.Network, day(10))
            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.Empty)
            assertThat(viewModel.uiState.value.refreshError).isEqualTo(MqError.Network)
        }

    @Test
    fun `the same failure after a new sync is shown again`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            sync.status.value = SyncStatus.Failed(MqError.Network, day(10))
            viewModel.onAction(LibraryAction.ErrorShown)

            sync.status.value = SyncStatus.Running(SyncStep.Albums, 0, 10)
            sync.status.value = SyncStatus.Failed(MqError.Network, day(10))

            assertThat(viewModel.uiState.value.refreshError).isEqualTo(MqError.Network)
        }

    @Test
    fun `the selected tab survives process death`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val savedState = SavedStateHandle()
            viewModel(savedState = savedState).onAction(LibraryAction.SelectFilter(LibraryFilter.Songs))

            val restored = viewModel(savedState = savedState)

            assertThat(restored.uiState.value.filter).isEqualTo(LibraryFilter.Songs)
        }

    @Test
    fun `a failed refresh over existing items is a one-time message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            sync.status.value = SyncStatus.Failed(MqError.Network, day(10))
            assertThat(viewModel.uiState.value.refreshError).isEqualTo(MqError.Network)
            assertThat(viewModel.ids()).isNotEmpty()

            viewModel.onAction(LibraryAction.ErrorShown)
            assertThat(viewModel.uiState.value.refreshError).isNull()
        }

    @Test
    fun `the songs tab loads tracks only when opened`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            library.tracks.value = emptyList()

            viewModel.onAction(LibraryAction.SelectFilter(LibraryFilter.Songs))

            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.Empty)
        }

    @Test
    fun `switching tabs shows loading until the new tab's items are ready`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = albums
            library.tracks.value = tracks
            sync.status.value = SyncStatus.Idle(day(10))
            val sorting = StandardTestDispatcher(testScheduler)
            val viewModel =
                LibraryViewModel(SavedStateHandle(), library, sync, preferences, playback, userData, downloads, sorting)
            backgroundScope.launch { viewModel.uiState.collect() }
            sorting.scheduler.runCurrent()

            viewModel.onAction(LibraryAction.SelectFilter(LibraryFilter.Songs))
            assertThat(viewModel.uiState.value.filter).isEqualTo(LibraryFilter.Songs)
            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.Loading)

            sorting.scheduler.runCurrent()
            assertThat(viewModel.uiState.value.content).isInstanceOf(LibraryContent.Songs::class.java)
        }
}
