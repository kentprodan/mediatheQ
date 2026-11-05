package io.github.kentprodan.mediatheq.feature.album

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AlbumPagePreferences
import io.github.kentprodan.mediatheq.core.data.AlbumSection
import io.github.kentprodan.mediatheq.core.data.MoreAlbumsSort
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.TrackAction
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.AlbumResume
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RecordLabel
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.testing.FakeAlbumResumeRepository
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection.PlayCall
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.core.ui.AlbumTitle
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AlbumViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val playback = FakePlaybackConnection()
    private val trackTaps by lazy { TrackTaps(playback, preferences) }
    private val commands by lazy { TrackCommands(playback, userData, downloads, preferences) }
    private val userData = FakeUserDataRepository()
    private val albumId = MediaId(ServerId("s"), "al1")
    private val album = Album(albumId, "Satelit", emptyList(), year = 2016)

    private fun track(
        id: String,
        disc: Int?,
        number: Int,
    ) = Track(
        MediaId(ServerId("s"), id),
        id,
        emptyList(),
        3.minutes,
        albumId = albumId,
        discNumber = disc,
        trackNumber = number,
    )

    private val disc1 = listOf(track("d1t1", 1, 1), track("d1t2", 1, 2))
    private val disc2 = listOf(track("d2t1", 2, 1))

    private val downloads = FakeDownloadRepository()
    private val preferences = FakeLibraryPreferencesRepository()
    private val resumes = FakeAlbumResumeRepository()

    private fun TestScope.viewModel(): AlbumViewModel {
        val viewModel =
            AlbumViewModel(albumId, library, playback, trackTaps, commands, userData, downloads, preferences, resumes)
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    @Test
    fun `starts loading`() {
        assertThat(
            AlbumViewModel(
                albumId,
                library,
                playback,
                trackTaps,
                commands,
                userData,
                downloads,
                preferences,
                resumes,
            ).uiState.value,
        ).isEqualTo(AlbumUiState.Loading)
    }

    @Test
    fun `an album groups its tracks by disc and counts them`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2

            val state = viewModel().uiState.value as AlbumUiState.Content

            assertThat(state.discs).containsExactly(Disc(1, disc1), Disc(2, disc2)).inOrder()
            assertThat(state.showDiscHeaders).isTrue()
            assertThat(state.trackCount).isEqualTo(3)
            assertThat(state.duration).isEqualTo(9.minutes)
        }

    @Test
    fun `a single disc, or tracks without disc numbers, has no disc headers`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = listOf(track("a", null, 1), track("b", 1, 2))

            val state = viewModel().uiState.value as AlbumUiState.Content

            assertThat(state.discs.map { it.number }).containsExactly(1)
            assertThat(state.showDiscHeaders).isFalse()
        }

    @Test
    fun `an album missing from the library is not found`() =
        runTest(mainDispatcherRule.testDispatcher) {
            assertThat(viewModel().uiState.value).isEqualTo(AlbumUiState.NotFound)
        }

    @Test
    fun `play queues the whole album from the first track`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.Play)

            assertThat(playback.plays).containsExactly(PlayCall(disc1 + disc2, 0, false))
        }

    @Test
    fun `shuffle queues the album in order and lets the player shuffle`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.Shuffle)

            assertThat(playback.plays).containsExactly(PlayCall(disc1 + disc2, 0, true))
        }

    @Test
    fun `tapping the first track of disc 2 starts after all of disc 1`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.PlayTrack(disc2.first().id))

            assertThat(playback.plays).containsExactly(PlayCall(disc1 + disc2, disc1.size, false))
        }

    @Test
    fun `tapping a track follows the default track action`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            preferences.setNavigation(NavigationPreferences(trackAction = TrackAction.PlayNext))
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.PlayTrack(disc2.first().id))

            assertThat(playback.playedNext).containsExactly(listOf(disc2.first()))
            assertThat(playback.plays).isEmpty()
        }

    @Test
    fun `download takes the whole album, or one track from its menu`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.Download(disc2[0].id))
            assertThat(downloads.downloads.value.map { it.track }).containsExactly(disc2[0])

            viewModel.onAction(AlbumAction.Download())
            assertThat(downloads.downloads.value.map { it.track }).containsExactlyElementsIn(disc1 + disc2)
        }

    @Test
    fun `a track's menu queues just that track, next or last`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.PlayNext(disc1[1].id))
            viewModel.onAction(AlbumAction.AddToQueue(disc2[0].id))

            assertThat(playback.playedNext).containsExactly(listOf(disc1[1]))
            assertThat(playback.addedToQueue).containsExactly(listOf(disc2[0]))
            assertThat(playback.plays).isEmpty()
        }

    @Test
    fun `the album menu queues the whole album in play order`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.PlayNext())
            viewModel.onAction(AlbumAction.AddToQueue())

            assertThat(playback.playedNext).containsExactly(disc1 + disc2)
            assertThat(playback.addedToQueue).containsExactly(disc1 + disc2)
        }

    @Test
    fun `an album without tracks plays nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.Play)
            viewModel.onAction(AlbumAction.Shuffle)
            viewModel.onAction(AlbumAction.AddToQueue())

            assertThat((viewModel.uiState.value as AlbumUiState.Content).trackCount).isEqualTo(0)
            assertThat(playback.plays).isEmpty()
            assertThat(playback.addedToQueue).isEmpty()
        }

    @Test
    fun `the playing track follows the player`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()
            assertThat((viewModel.uiState.value as AlbumUiState.Content).currentTrackId).isNull()

            playback.state.value = PlayerUiModel(item = NowPlayingItem(disc2.first().id, "d2t1", ""))

            assertThat((viewModel.uiState.value as AlbumUiState.Content).currentTrackId).isEqualTo(disc2.first().id)

            playback.state.value = PlayerUiModel()

            assertThat((viewModel.uiState.value as AlbumUiState.Content).currentTrackId).isNull()
        }

    @Test
    fun `favorite and rating go to the album, and a track menu favorites the track`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.SetFavorite(albumId, true))
            viewModel.onAction(AlbumAction.Rate(10))
            viewModel.onAction(AlbumAction.SetFavorite(disc1.first().id, true))

            assertThat(
                userData.calls,
            ).containsExactly("favorite:al1:true", "rating:al1:10", "favorite:d1t1:true").inOrder()
        }

    @Test
    fun `an album without tracks can still be rated`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.Rate(null))

            assertThat(userData.calls).containsExactly("rating:al1:null")
        }

    @Test
    fun `a swipe runs its action and a rating rates the track`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1
            val viewModel = viewModel()

            viewModel.onAction(AlbumAction.SwipeTrack(disc1.first(), right = true))
            viewModel.onAction(AlbumAction.RateTrack(disc1.first(), 4))

            assertThat(playback.addedToQueue).containsExactly(listOf(disc1.first()))
            assertThat(userData.calls).contains("rating:${disc1.first().id.itemId}:4")
        }

    @Test
    fun `selected tracks toggle, select all, and a toolbar action runs on them and ends selecting`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album)
            library.tracks.value = disc1
            val viewModel = viewModel()
            val list = disc1

            viewModel.onAction(AlbumAction.ToggleSelection(list[0].id))
            viewModel.onAction(AlbumAction.ToggleSelection(list[1].id))
            viewModel.onAction(AlbumAction.ToggleSelection(list[0].id))
            assertThat((viewModel.uiState.value as AlbumUiState.Content).selection.keys).containsExactly(list[1].id)

            viewModel.onAction(AlbumAction.SelectAll)
            assertThat((viewModel.uiState.value as AlbumUiState.Content).selection.size).isEqualTo(list.size)

            viewModel.onAction(AlbumAction.OnSelection(SelectionCommand.PlayNext))
            assertThat(playback.playedNext).containsExactly(list)
            assertThat((viewModel.uiState.value as AlbumUiState.Content).selection.isActive).isFalse()
        }

    private fun TestScope.content(): AlbumUiState.Content {
        library.albums.value = listOf(album.copy(albumArtists = listOf(artist)))
        library.tracks.value = disc1 + disc2
        val viewModel = viewModel()
        return viewModel.uiState.value as AlbumUiState.Content
    }

    private val artist = Artist(MediaId(ServerId("s"), "ar1"), "Subcarpați")

    @Test
    fun `resume continues where the album was left, and a finished album has none`() =
        runTest(mainDispatcherRule.testDispatcher) {
            resumes.resumes.value = mapOf(albumId to AlbumResume(disc1[1].id, 70.seconds))
            library.albums.value = listOf(album)
            library.tracks.value = disc1 + disc2
            val viewModel = viewModel()

            assertThat(
                (viewModel.uiState.value as AlbumUiState.Content).resume,
            ).isEqualTo(AlbumResume(disc1[1].id, 70.seconds))
            viewModel.onAction(AlbumAction.Resume)
            assertThat(playback.plays.single()).isEqualTo(PlayCall(disc1 + disc2, 1, false, 70.seconds))

            resumes.resumes.value = mapOf(albumId to AlbumResume(disc2[0].id, 3.minutes))
            assertThat((viewModel.uiState.value as AlbumUiState.Content).resume).isNull()
        }

    @Test
    fun `more from the artist leaves this album out and sorts as the page settings say`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val old = Album(MediaId(ServerId("s"), "old"), "Old", listOf(artist), year = 1999)
            val new = Album(MediaId(ServerId("s"), "new"), "Abc", listOf(artist), year = 2020)
            library.artistAlbums.value = mapOf(artist.id to listOf(old, album, new))

            assertThat(content().moreAlbums).containsExactly(new, old).inOrder()

            preferences.setAlbumPage(AlbumPagePreferences(moreAlbumsSort = MoreAlbumsSort.Title))
            val viewModel = viewModel()
            assertThat((viewModel.uiState.value as AlbumUiState.Content).moreAlbums).containsExactly(new, old).inOrder()
            preferences.setAlbumPage(AlbumPagePreferences(moreAlbumsSort = MoreAlbumsSort.YearAscending))
            assertThat((viewModel.uiState.value as AlbumUiState.Content).moreAlbums).containsExactly(old, new).inOrder()
        }

    @Test
    fun `about holds the genres, labels, play count and file size, and sections follow the settings`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albumGenres.value = mapOf(albumId to listOf("Hip-Hop"))
            library.albumLabels.value = mapOf(albumId to listOf(RecordLabel(MediaId(ServerId("s"), "l1"), "Roton")))
            library.tracks.value = (disc1 + disc2).map { it.copy(sizeBytes = 10, userData = UserData(playCount = 2)) }
            library.albums.value = listOf(album)
            val viewModel = viewModel()
            val shown = viewModel.uiState.value as AlbumUiState.Content

            assertThat(shown.genres).containsExactly("Hip-Hop")
            assertThat(shown.labels.single().name).isEqualTo("Roton")
            assertThat(shown.sizeBytes).isEqualTo(30)
            assertThat(shown.playCount).isEqualTo(6)
            // Nothing more from the artist: that section is left out.
            assertThat(shown.sections).containsExactly(AlbumSection.Tracks, AlbumSection.About).inOrder()

            preferences.setAlbumPage(
                AlbumPagePreferences(
                    sectionOrder = listOf(AlbumSection.About, AlbumSection.Tracks, AlbumSection.MoreFromArtist),
                ),
            )
            assertThat((viewModel.uiState.value as AlbumUiState.Content).sections)
                .containsExactly(AlbumSection.About, AlbumSection.Tracks)
                .inOrder()
            preferences.setAlbumPage(AlbumPagePreferences(showAbout = false))
            assertThat((viewModel.uiState.value as AlbumUiState.Content).sections).containsExactly(AlbumSection.Tracks)
        }

    @Test
    fun `an album with nothing to tell leaves About out`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.tracks.value = disc1 + disc2
            library.albums.value = listOf(album)

            assertThat(
                (viewModel().uiState.value as AlbumUiState.Content).sections,
            ).containsExactly(AlbumSection.Tracks)
        }

    @Test
    fun `a title with an edition shows it as the version, and the quality follows its setting`() =
        runTest(mainDispatcherRule.testDispatcher) {
            library.albums.value = listOf(album.copy(title = "Satelit (Deluxe Edition)"))
            library.tracks.value = disc1.map { it.copy(audioFormat = AudioFormat("flac", 96_000, 24)) }
            val viewModel = viewModel()
            val shown = viewModel.uiState.value as AlbumUiState.Content

            assertThat(shown.title).isEqualTo(AlbumTitle("Satelit", "Deluxe Edition"))
            assertThat(shown.quality?.detail).isEqualTo("FLAC 24/96")

            preferences.setAlbumPage(AlbumPagePreferences(showQuality = false))
            assertThat((viewModel.uiState.value as AlbumUiState.Content).quality).isNull()
        }
}
