package io.github.kentprodan.mediatheq.feature.library

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.HomeLibrary
import io.github.kentprodan.mediatheq.core.data.HomeShuffle
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.TopButtonStyle
import io.github.kentprodan.mediatheq.core.data.TrackAction
import io.github.kentprodan.mediatheq.core.data.homeItems
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.RecordLabel
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.navigation.HomeSectionListKey
import io.github.kentprodan.mediatheq.core.navigation.RecordLabelKey
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
import io.github.kentprodan.mediatheq.core.testing.TestClock
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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

    private val genres =
        listOf(
            Genre(id("g-rock"), "Rock", albumCount = 1, trackCount = 5),
            Genre(id("g-ambient"), "Ambient", albumCount = 3, trackCount = 2),
        )

    private val labels =
        listOf(
            RecordLabel(id("l-atl"), "Atlantic", albumCount = 1),
            RecordLabel(id("l-ele"), "Electrecord", albumCount = 2),
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
        labelPage: RecordLabelKey? = null,
        sectionPage: HomeSectionListKey? = null,
    ): LibraryViewModel {
        library.albums.value = albums
        library.albumArtists.value = listOf(bob, ada)
        library.playlists.value = playlists
        library.tracks.value = tracks
        library.genres.value = genres
        library.recordLabels.value = labels
        sync.status.value = status
        val viewModel =
            LibraryViewModel(
                fixedFilter,
                yearPage,
                labelPage,
                sectionPage,
                library,
                sync,
                preferences,
                playback,
                trackTaps,
                commands,
                userData,
                downloads,
                playlistRepository,
                shuffle,
                TestClock(day(100)),
                mainDispatcherRule.testDispatcher,
            )
        if (collect) backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private val shuffle = HomeShuffle(1)

    private fun TestScope.section(section: HomeSection) =
        viewModel(fixedFilter = section.filter, sectionPage = HomeSectionListKey(section))

    @Test
    fun `a section's full list holds every item past Home's limit, in the section's order, whatever the sort`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferences.setSort(LibraryFilter.Albums, LibrarySort.Name)
            val many = (1..20).map { Album(id("n$it"), "N$it", listOf(ada), dateAdded = day(10 + it)) }
            val viewModel = section(HomeSection.RecentlyAddedAlbums)
            library.albums.value = albums + many

            // Newest first; the album without a date is not "recently added".
            assertThat(viewModel.ids())
                .containsExactlyElementsIn(
                    many.reversed().map { it.id.itemId } + listOf("a2", "a1"),
                ).inOrder()
            assertThat(
                viewModel.uiState.value.sectionPage,
            ).isEqualTo(HomeSectionListKey(HomeSection.RecentlyAddedAlbums))
            assertThat(preferences.preferences.first().sortFor(LibraryFilter.Albums)).isEqualTo(LibrarySort.Name)
        }

    @Test
    fun `a section's full list for artists, tracks and playlists`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val artists = section(HomeSection.FavoriteArtists)
            library.albumArtists.value = listOf(bob, ada.copy(userData = UserData(isFavorite = true)))
            assertThat(artists.uiState.value.filter).isEqualTo(LibraryFilter.Artists)
            assertThat(artists.ids()).containsExactly("ar-ada")

            val songs = section(HomeSection.FavoriteTracks)
            library.tracks.value = tracks.map { it.copy(userData = UserData(isFavorite = it.title != "Mid")) }
            assertThat(songs.uiState.value.filter).isEqualTo(LibraryFilter.Songs)
            assertThat(songs.ids()).containsExactly("t1", "t2").inOrder()

            val lists = section(HomeSection.FavoritePlaylists)
            library.playlists.value = listOf(playlists[0].copy(userData = UserData(isFavorite = true)), playlists[1])
            assertThat(lists.uiState.value.filter).isEqualTo(LibraryFilter.Playlists)
            assertThat(lists.ids()).containsExactly("p1")
        }

    @Test
    fun `a random section starts with Home's set, and pull to refresh draws a new one`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val many = albums + (1..30).map { Album(id("n$it"), "N$it", listOf(ada)) }
            val viewModel = section(HomeSection.RandomAlbums)
            library.albums.value = many
            val home = homeItems(HomeSection.RandomAlbums, HomeLibrary(albums = many), 16, shuffle.seed.value, day(100))

            assertThat(viewModel.ids()).hasSize(many.size)
            assertThat(viewModel.ids().take(16)).isEqualTo((home as HomeItems.Albums).albums.map { it.id.itemId })

            val seed = shuffle.seed.value
            viewModel.onAction(LibraryAction.Refresh)
            assertThat(shuffle.seed.value).isNotEqualTo(seed)
        }

    @Test
    fun `offline, a section with nothing downloaded says so`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.offline.value = true
            val viewModel = section(HomeSection.FavoriteAlbums)

            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.NothingDownloaded)
        }

    private fun LibraryViewModel.ids(): List<String> =
        when (val content = uiState.value.content) {
            is LibraryContent.Albums -> content.albums.map { it.id.itemId }
            is LibraryContent.Artists -> content.artists.map { it.id.itemId }
            is LibraryContent.Playlists -> content.playlists.map { it.id.itemId }
            is LibraryContent.Songs -> content.tracks.map { it.id.itemId }
            is LibraryContent.Years -> content.years.map { it.year.toString() }
            is LibraryContent.Genres -> content.genres.map { it.id.itemId }
            is LibraryContent.RecordLabels -> content.labels.map { it.id.itemId }
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
    fun `every filter and sort orders its items, both ways`() =
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
                    (LibraryFilter.Genres to LibrarySort.Name) to listOf("g-ambient", "g-rock"),
                    (LibraryFilter.Genres to LibrarySort.AlbumCount) to listOf("g-ambient", "g-rock"),
                    (LibraryFilter.Genres to LibrarySort.TrackCount) to listOf("g-rock", "g-ambient"),
                    (LibraryFilter.Years to LibrarySort.AlbumCount) to listOf("2020", "2001", "1999"),
                    (LibraryFilter.RecordLabels to LibrarySort.Name) to listOf("l-atl", "l-ele"),
                    (LibraryFilter.RecordLabels to LibrarySort.AlbumCount) to listOf("l-ele", "l-atl"),
                )
            assertThat(expected.keys.map { it.first to it.second })
                .containsExactlyElementsIn(LibraryFilter.entries.flatMap { f -> f.sorts.map { f to it } })

            expected.forEach { (filterAndSort, ids) ->
                val (filter, sort) = filterAndSort
                val viewModel = viewModel(fixedFilter = filter)
                viewModel.onAction(LibraryAction.SelectSort(sort))
                assertThat(viewModel.uiState.value.sort).isEqualTo(sort)
                assertWithMessage("$filter by $sort").that(viewModel.ids()).containsExactlyElementsIn(ids).inOrder()

                viewModel.onAction(LibraryAction.ReverseSort)
                assertThat(viewModel.uiState.value.sortReversed).isTrue()
                assertWithMessage("$filter by $sort, reversed")
                    .that(viewModel.ids())
                    .containsExactlyElementsIn(ids.reversed())
                    .inOrder()
                viewModel.onAction(LibraryAction.ReverseSort)
            }
        }

    @Test
    fun `reversing is per tab, shared by a year page, ignored by Home sections, a new sort starts natural`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel().onAction(LibraryAction.ReverseSort)

            assertThat(viewModel().ids()).containsExactly("a1", "a2", "a3").inOrder()
            assertThat(viewModel(fixedFilter = LibraryFilter.Songs).uiState.value.sortReversed).isFalse()
            assertThat(viewModel(yearPage = YearKey(2001)).uiState.value.sortReversed).isTrue()
            val section = viewModel(sectionPage = HomeSectionListKey(HomeSection.RecentlyAddedAlbums))
            assertThat(section.ids()).containsExactly("a2", "a1").inOrder()

            val albums = viewModel()
            albums.onAction(LibraryAction.SelectSort(LibrarySort.Year))
            assertThat(albums.uiState.value.sortReversed).isFalse()
            assertThat(albums.ids()).containsExactly("a3", "a1", "a2").inOrder()
        }

    @Test
    fun `reversing before the list has loaded keeps the stored sort`() =
        runTest(mainDispatcherRule.testDispatcher) {
            preferences.setSort(LibraryFilter.Albums, LibrarySort.Year)

            viewModel(collect = false).onAction(LibraryAction.ReverseSort)

            assertThat(preferences.preferences.first().sortFor(LibraryFilter.Albums)).isEqualTo(LibrarySort.Year)
            assertThat(preferences.preferences.first().isReversed(LibraryFilter.Albums)).isTrue()
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
                    null,
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
                    shuffle,
                    TestClock(day(100)),
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

    @Test
    fun `the genres list shows the genres, and offline with none says nothing is downloaded`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(fixedFilter = LibraryFilter.Genres)
            assertThat(viewModel.ids()).containsExactly("g-ambient", "g-rock").inOrder()

            library.offline.value = true
            library.genres.value = emptyList()
            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.NothingDownloaded)
        }

    @Test
    fun `play on the genres list plays each genre's tracks once, and the view choice is saved`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.genreTracks.value =
                mapOf(id("g-ambient") to listOf(tracks[0], tracks[1]), id("g-rock") to listOf(tracks[1]))
            val viewModel = viewModel(fixedFilter = LibraryFilter.Genres)

            viewModel.onAction(LibraryAction.Top(TopButton.Play))
            viewModel.onAction(LibraryAction.SetGenresGrid(true))

            assertThat(playback.plays.single().queue).containsExactly(tracks[0], tracks[1]).inOrder()
            assertThat(viewModel.uiState.value.genresGrid).isTrue()
        }

    @Test
    fun `the record labels list loads, shows the labels, and offline with none says nothing is downloaded`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val loading = viewModel(fixedFilter = LibraryFilter.RecordLabels, collect = false)
            assertThat(loading.uiState.value.content).isEqualTo(LibraryContent.Loading)

            val viewModel = viewModel(fixedFilter = LibraryFilter.RecordLabels)
            assertThat(viewModel.ids()).containsExactly("l-atl", "l-ele").inOrder()

            library.offline.value = true
            library.recordLabels.value = emptyList()
            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.NothingDownloaded)
        }

    @Test
    fun `play on the record labels list plays each label's albums once`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.labelAlbums.value = mapOf(id("l-atl") to listOf(albums[0]), id("l-ele") to albums.take(2))
            val viewModel = viewModel(fixedFilter = LibraryFilter.RecordLabels)
            library.tracks.value = listOf(tracks[0].copy(albumId = id("a1")), tracks[1].copy(albumId = id("a2")))

            viewModel.onAction(LibraryAction.Top(TopButton.Play))

            assertThat(
                playback.plays
                    .single()
                    .queue
                    .map { it.id.itemId },
            ).containsExactly("t1", "t2").inOrder()
        }

    @Test
    fun `a label's page lists its albums, sorts them by year, plays them, and is titled by the label`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val page = RecordLabelKey(id("l-ele"), "Electrecord")
            library.labelAlbums.value = mapOf(page.id to listOf(albums[0], albums[1]))
            val viewModel = viewModel(labelPage = page)
            library.tracks.value = listOf(tracks[0].copy(albumId = id("a1")), tracks[1].copy(albumId = id("a2")))

            assertThat(viewModel.uiState.value.labelPage).isEqualTo(page)
            assertThat(viewModel.ids()).containsExactly("a2", "a1").inOrder()
            viewModel.onAction(LibraryAction.SelectSort(LibrarySort.Year))
            // a1 2001, a2 1999: newest first.
            assertThat(viewModel.ids()).containsExactly("a1", "a2").inOrder()
            viewModel.onAction(LibraryAction.Top(TopButton.Shuffle))
            assertThat(
                playback.plays
                    .single()
                    .queue
                    .map { it.id.itemId },
            ).containsExactly("t1", "t2")
            assertThat(playback.plays.single().shuffle).isTrue()
        }

    @Test
    fun `offline a label's page with nothing downloaded says so`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.offline.value = true
            val viewModel = viewModel(labelPage = RecordLabelKey(id("l-atl"), "Atlantic"))

            assertThat(viewModel.uiState.value.content).isEqualTo(LibraryContent.NothingDownloaded)
        }
}
