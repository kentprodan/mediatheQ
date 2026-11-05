package io.github.kentprodan.mediatheq.feature.library

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.TopButtonStyle
import io.github.kentprodan.mediatheq.core.data.TrackAction
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.navigation.YearKey
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakePlaylistRepository
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
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
    private val trackTaps by lazy { TrackTaps(playback, preferences) }
    private val userData = FakeUserDataRepository()
    private val downloads = FakeDownloadRepository()
    private val playlistRepository = FakePlaylistRepository()
    private val commands by lazy { TrackCommands(playback, userData, downloads, preferences) }

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
        collect: Boolean = true,
        fixedFilter: LibraryFilter = LibraryFilter.Albums,
        yearPage: YearKey? = null,
    ): LibraryViewModel {
        library.albums.value = albums
        library.albumArtists.value = listOf(bob, ada)
        library.playlists.value = playlists
        library.tracks.value = tracks
        sync.status.value = status
        val viewModel =
            LibraryViewModel(
                fixedFilter,
                yearPage,
                library,
                sync,
                preferences,
                playback,
                trackTaps,
                commands,
                userData,
                downloads,
                playlistRepository,
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
            is LibraryContent.Years -> content.years.map { it.year.toString() }
            else -> error("no items: $content")
        }

    @Test
    fun `a song's menu downloads it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel().onAction(LibraryAction.Download(tracks[0]))

            assertThat(downloads.downloads.value.map { it.track }).containsExactly(tracks[0])
        }

    @Test
    fun `tapping a song plays the sorted songs from it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(fixedFilter = LibraryFilter.Songs)
            val shown = (viewModel.uiState.value.content as LibraryContent.Songs).tracks

            viewModel.onAction(LibraryAction.PlayTrack(shown[1]))

            assertThat(playback.plays).containsExactly(FakePlaybackConnection.PlayCall(shown, 1, false))
        }

    @Test
    fun `tapping a song follows the default track action`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferences.setNavigation(NavigationPreferences(trackAction = TrackAction.AddToQueue))
            val viewModel = viewModel(fixedFilter = LibraryFilter.Songs)

            viewModel.onAction(LibraryAction.PlayTrack(tracks[0]))

            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[0]))
            assertThat(playback.plays).isEmpty()
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
    fun `the header counts the items and follows the media lists settings`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferences.setMediaLists(MediaListPreferences(topButtonStyle = TopButtonStyle.Normal))
            val viewModel = viewModel()

            assertThat(viewModel.uiState.value.itemCount).isEqualTo(3)
            assertThat(viewModel.uiState.value.header.topButtonStyle).isEqualTo(TopButtonStyle.Normal)
        }

    @Test
    fun `search in list narrows the items without diacritics, and closing it shows them all`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(LibraryAction.Search("mosoi"))
            // The title "Moșoi" (a1), and "c" (a2) by Ada Moșoi: titles and artists both match.
            assertThat(viewModel.ids()).containsExactly("a2", "a1").inOrder()
            assertThat(viewModel.uiState.value.itemCount).isEqualTo(2)

            viewModel.onAction(LibraryAction.Search("nothing like it"))
            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.NoMatches)

            viewModel.onAction(LibraryAction.Search(null))
            assertThat(viewModel.ids()).hasSize(3)
        }

    @Test
    fun `favorites only and downloaded only filter the list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = albums
            val viewModel = viewModel()
            library.albums.value = albums.mapIndexed { i, a -> a.copy(userData = UserData(isFavorite = i != 1)) }
            library.fullyDownloaded.value = setOf(id("a1"), id("a2"))

            viewModel.onAction(LibraryAction.SetFavoritesOnly(true))
            assertThat(viewModel.ids()).containsExactly("a3", "a1").inOrder()

            viewModel.onAction(LibraryAction.SetDownloadedOnly(true))
            assertThat(viewModel.ids()).containsExactly("a1")
        }

    @Test
    fun `a top button acts on the filtered list, albums by their tracks in list order`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            library.tracks.value =
                listOf(
                    tracks[0].copy(albumId = id("a1")),
                    tracks[1].copy(albumId = id("a3")),
                    tracks[2].copy(albumId = id("a1")),
                )

            viewModel.onAction(LibraryAction.Search("bob"))
            viewModel.onAction(LibraryAction.Top(TopButton.Play))

            // Albums by bob, by name: B (a3) then Moșoi (a1).
            val queued =
                playback.plays
                    .single()
                    .queue
                    .map { it.id.itemId }
            assertThat(queued).containsExactly("t2", "t1", "t3").inOrder()
        }

    @Test
    fun `selection toggles, selects all and clears`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(LibraryAction.ToggleSelection(id("a1")))
            viewModel.onAction(LibraryAction.ToggleSelection(id("a2")))
            viewModel.onAction(LibraryAction.ToggleSelection(id("a1")))
            assertThat(viewModel.uiState.value.selection.keys).containsExactly(id("a2"))

            viewModel.onAction(LibraryAction.SelectAll)
            assertThat(viewModel.uiState.value.selection.size).isEqualTo(3)

            viewModel.onAction(LibraryAction.ClearSelection)
            assertThat(viewModel.uiState.value.selection.isActive).isFalse()
        }

    @Test
    fun `a selection action runs on the selected albums' tracks in list order and ends selecting`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            library.tracks.value =
                listOf(
                    tracks[0].copy(albumId = id("a1")),
                    tracks[1].copy(albumId = id("a3")),
                    tracks[2].copy(albumId = id("a2")),
                )

            viewModel.onAction(LibraryAction.ToggleSelection(id("a1")))
            viewModel.onAction(LibraryAction.ToggleSelection(id("a3")))
            viewModel.onAction(LibraryAction.OnSelection(SelectionCommand.AddToQueue))

            // By name: B (a3) before Moșoi (a1).
            assertThat(playback.addedToQueue.single().map { it.id.itemId }).containsExactly("t2", "t1").inOrder()
            assertThat(viewModel.uiState.value.selection.isActive).isFalse()
        }

    @Test
    fun `selected albums download as albums, so Downloads can remove them whole`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            library.tracks.value = listOf(tracks[0].copy(albumId = id("a1")), tracks[1].copy(albumId = id("a3")))

            viewModel.onAction(LibraryAction.ToggleSelection(id("a1")))
            viewModel.onAction(LibraryAction.ToggleSelection(id("a3")))
            viewModel.onAction(LibraryAction.OnSelection(SelectionCommand.Download))

            assertThat(downloads.downloads.value.map { it.track.id.itemId to it.origins }).containsExactly(
                "t1" to setOf(DownloadOrigin.Album(id("a1"))),
                "t2" to setOf(DownloadOrigin.Album(id("a3"))),
            )
        }

    @Test
    fun `add to playlist from a selection hands its tracks to the screen once`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(fixedFilter = LibraryFilter.Songs)

            viewModel.onAction(LibraryAction.ToggleSelection(id("t3")))
            viewModel.onAction(LibraryAction.OnSelection(SelectionCommand.AddToPlaylist))
            assertThat(viewModel.uiState.value.addToPlaylist).containsExactly(id("t3"))

            viewModel.onAction(LibraryAction.AddToPlaylistShown)
            assertThat(viewModel.uiState.value.addToPlaylist).isNull()
        }

    @Test
    fun `swipes run the chosen actions and Rate rates`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(LibraryAction.SwipeTrack(tracks[0], right = true))
            viewModel.onAction(LibraryAction.SwipeTrack(tracks[1], right = false))
            viewModel.onAction(LibraryAction.RateTrack(tracks[0], 6))

            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[0]))
            assertThat(playback.playedNext).containsExactly(listOf(tracks[1]))
            assertThat(userData.calls).containsExactly("rating:${tracks[0].id.itemId}:6")
        }

    @Test
    fun `albums start by name in two columns`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            val state = viewModel.uiState.value
            assertThat(state.filter).isEqualTo(LibraryFilter.Albums)
            assertThat(state.sort).isEqualTo(LibrarySort.Name)
            assertThat(state.albumColumns).isEqualTo(2)
            assertThat(viewModel.ids()).containsExactly("a3", "a2", "a1").inOrder()
        }

    @Test
    fun `every filter and sort orders its items`() =
        runTest(mainDispatcherRule.testDispatcher) {
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
                    // a1 2001, a2 1999, a3 2020: one album each, so by count the years keep newest first.
                    (LibraryFilter.Years to LibrarySort.Year) to listOf("2020", "2001", "1999"),
                    (LibraryFilter.Years to LibrarySort.AlbumCount) to listOf("2020", "2001", "1999"),
                )
            assertThat(expected.keys.map { it.first to it.second })
                .containsExactlyElementsIn(LibraryFilter.entries.flatMap { f -> f.sorts.map { f to it } })

            expected.forEach { (filterAndSort, ids) ->
                val (filter, sort) = filterAndSort
                val viewModel = viewModel(fixedFilter = filter)
                viewModel.onAction(LibraryAction.SelectSort(sort))
                assertThat(viewModel.uiState.value.sort).isEqualTo(sort)
                assertWithMessage("$filter by $sort").that(viewModel.ids()).containsExactlyElementsIn(ids).inOrder()
            }
        }

    @Test
    fun `each list remembers its own sort`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel().onAction(LibraryAction.SelectSort(LibrarySort.Year))

            assertThat(viewModel(fixedFilter = LibraryFilter.Songs).uiState.value.sort).isEqualTo(LibrarySort.Name)
            assertThat(viewModel().uiState.value.sort).isEqualTo(LibrarySort.Year)
        }

    @Test
    fun `album columns are saved`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(LibraryAction.SetAlbumColumns(4))

            assertThat(viewModel.uiState.value.albumColumns).isEqualTo(4)
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
            val viewModel = viewModel(fixedFilter = LibraryFilter.Playlists)
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
    fun `offline with nothing downloaded for the tab says so, whatever the sync did`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.offline.value = true
            val viewModel = viewModel(SyncStatus.Failed(MqError.Network, null))
            library.albums.value = emptyList()

            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.NothingDownloaded)
        }

    @Test
    fun `offline the downloads show, and a sync failing for lack of network is no message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.offline.value = true
            val viewModel = viewModel()

            sync.status.value = SyncStatus.Failed(MqError.Network, day(10))

            assertThat(viewModel.ids()).isNotEmpty()
            assertThat(viewModel.uiState.value.refreshError).isNull()
        }

    @Test
    fun `an empty songs list is empty after a sync`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(fixedFilter = LibraryFilter.Songs)
            library.tracks.value = emptyList()

            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.Empty)
        }

    @Test
    fun `a list shows loading until its items are sorted`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = albums
            library.tracks.value = tracks
            sync.status.value = SyncStatus.Idle(day(10))
            val sorting = StandardTestDispatcher(testScheduler)
            val viewModel =
                LibraryViewModel(
                    LibraryFilter.Songs,
                    null,
                    library,
                    sync,
                    preferences,
                    playback,
                    trackTaps,
                    commands,
                    userData,
                    downloads,
                    playlistRepository,
                    sorting,
                )
            backgroundScope.launch { viewModel.uiState.collect() }

            assertThat(viewModel.uiState.value.filter).isEqualTo(LibraryFilter.Songs)
            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.Loading)

            sorting.scheduler.runCurrent()
            assertThat(viewModel.uiState.value.content).isInstanceOf(LibraryContent.Songs::class.java)
        }

    @Test
    fun `years list the album years newest first with their albums, unknown year last`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = albums + Album(id("a4"), "No date", listOf(ada))
            val viewModel = viewModel(fixedFilter = LibraryFilter.Years)
            library.albums.value = albums + Album(id("a4"), "No date", listOf(ada))

            val years = (viewModel.uiState.value.content as LibraryContent.Years).years
            assertThat(years.map { it.year }).containsExactly(2020, 2001, 1999, null).inOrder()
            assertThat(years.last().albums.map { it.title }).containsExactly("No date")
            assertThat(viewModel.uiState.value.itemCount).isEqualTo(4)
        }

    @Test
    fun `a year's page lists that year's albums, and the unknown year those without one`() =
        runTest(mainDispatcherRule.testDispatcher) {
            assertThat(viewModel(yearPage = YearKey(2001)).ids()).containsExactly("a1")
            assertThat(viewModel(yearPage = YearKey(2001)).uiState.value.yearPage).isEqualTo(YearKey(2001))

            library.albums.value = albums + Album(id("a4"), "No date", listOf(ada))
            val unknown = viewModel(yearPage = YearKey(null), collect = false)
            library.albums.value = albums + Album(id("a4"), "No date", listOf(ada))
            backgroundScope.launch { unknown.uiState.collect() }
            assertThat(unknown.ids()).containsExactly("a4")
        }

    @Test
    fun `offline with no downloaded album the years list says nothing is downloaded`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.offline.value = true
            val viewModel = viewModel(fixedFilter = LibraryFilter.Years)
            library.albums.value = emptyList()

            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.NothingDownloaded)
        }

    @Test
    fun `play on the years list plays the albums' tracks in year order`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(fixedFilter = LibraryFilter.Years)
            library.tracks.value =
                listOf(
                    tracks[0].copy(albumId = id("a1")),
                    tracks[1].copy(albumId = id("a3")),
                    tracks[2].copy(albumId = id("a2")),
                )

            viewModel.onAction(LibraryAction.Top(TopButton.Play))

            // 2020 (a3), 2001 (a1), 1999 (a2).
            assertThat(
                playback.plays
                    .single()
                    .queue
                    .map { it.id.itemId },
            ).containsExactly("t2", "t1", "t3").inOrder()
        }

    @Test
    fun `the years list loads, then is empty after a sync without albums`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(fixedFilter = LibraryFilter.Years, collect = false)
            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.Loading)

            backgroundScope.launch { viewModel.uiState.collect() }
            library.albums.value = emptyList()

            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.Empty)
        }

    @Test
    fun `album count puts the year with most albums first, ties newest first`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val more =
                listOf(Album(id("x1"), "x1", listOf(ada), year = 1999), Album(id("x2"), "x2", listOf(ada), year = 1999))
            val viewModel = viewModel(fixedFilter = LibraryFilter.Years)
            library.albums.value = albums + more

            viewModel.onAction(LibraryAction.SelectSort(LibrarySort.AlbumCount))

            // 1999 has three albums; 2020 and 2001 one each, newest first.
            assertThat(viewModel.ids()).containsExactly("1999", "2020", "2001").inOrder()
        }

    @Test
    fun `a year page without albums of that year is empty after a sync`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(yearPage = YearKey(1970))

            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.Empty)
        }
}
