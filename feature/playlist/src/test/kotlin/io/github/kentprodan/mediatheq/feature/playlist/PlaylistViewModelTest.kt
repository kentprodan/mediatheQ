package io.github.kentprodan.mediatheq.feature.playlist

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.PlaylistButton
import io.github.kentprodan.mediatheq.core.data.PlaylistHeaderBackground
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.data.PlaylistItem.Companion.PENDING_ENTRY_ID
import io.github.kentprodan.mediatheq.core.data.PlaylistPagePreferences
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ResumePoint
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection.PlayCall
import io.github.kentprodan.mediatheq.core.testing.FakePlaylistRepository
import io.github.kentprodan.mediatheq.core.testing.FakeResumeRepository
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PlaylistViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePlaylistRepository()
    private val playback = FakePlaybackConnection()
    private val trackTaps by lazy { TrackTaps(playback, FakeLibraryPreferencesRepository()) }
    private val commands by lazy { TrackCommands(playback, userData, downloads, FakeLibraryPreferencesRepository()) }
    private val userData = FakeUserDataRepository()
    private val downloads = FakeDownloadRepository()
    private val playlistId = MediaId(ServerId("s"), "p1")

    private fun track(id: String) = Track(MediaId(ServerId("s"), id), id, emptyList(), 3.minutes)

    // Position 1 is an entry whose track is not in the library: positions skip it.
    private val items =
        listOf(
            PlaylistItem("e1", 0, track("t1")),
            PlaylistItem("e2", 2, track("t2")),
            PlaylistItem("e3", 3, track("t3")),
        )
    private val tracks = items.map { it.track }

    private fun TestScope.viewModel(): PlaylistViewModel {
        repository.playlists.value = listOf(Playlist(playlistId, "Drum"))
        repository.items.value = mapOf(playlistId to items)
        val viewModel = newViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private val PlaylistViewModel.content get() = uiState.value as PlaylistUiState.Content

    private val preferences = FakeLibraryPreferencesRepository()
    private val resumes = FakeResumeRepository()
    private val library = FakeLibraryRepository()

    private fun newViewModel() =
        PlaylistViewModel(
            playlistId,
            repository,
            playback,
            trackTaps,
            commands,
            userData,
            downloads,
            preferences,
            resumes,
            library,
        )

    @Test
    fun `starts loading`() {
        assertThat(
            newViewModel().uiState.value,
        ).isEqualTo(PlaylistUiState.Loading)
    }

    @Test
    fun `a playlist shows its items and total duration`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val state = viewModel().content

            assertThat(state.items).isEqualTo(items)
            assertThat(state.duration).isEqualTo(9.minutes)
            assertThat(state.canEdit).isTrue()
            assertThat(repository.refreshes).containsExactly(playlistId)
        }

    @Test
    fun `a playlist missing from the library is not found`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            repository.playlists.value = emptyList()

            assertThat(viewModel.uiState.value).isEqualTo(PlaylistUiState.NotFound)
        }

    @Test
    fun `play, shuffle and play item start the playlist`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.Play)
            viewModel.onAction(PlaylistAction.Shuffle)
            viewModel.onAction(PlaylistAction.PlayItem(position = 2))

            assertThat(playback.plays)
                .containsExactly(
                    PlayCall(tracks, 0, false, playlistId = playlistId),
                    PlayCall(tracks, 0, true, playlistId = playlistId),
                    PlayCall(tracks, 1, false, playlistId = playlistId),
                ).inOrder()
        }

    @Test
    fun `download takes one item from its menu, or the whole playlist`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.Download(3))
            assertThat(downloads.downloads.value.map { it.track }).containsExactly(tracks[2])

            viewModel.onAction(PlaylistAction.Download())
            assertThat(downloads.downloads.value.map { it.track }).containsExactlyElementsIn(tracks)
        }

    @Test
    fun `play next and add to queue take one item`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.PlayNext(3))
            viewModel.onAction(PlaylistAction.AddToQueue(0))

            assertThat(playback.playedNext).containsExactly(listOf(tracks[2]))
            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[0]))
        }

    @Test
    fun `a track menu favorites the track at that position`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.SetFavorite(3, true))
            viewModel.onAction(PlaylistAction.SetFavorite(99, true))

            assertThat(userData.calls).containsExactly("favorite:${tracks[2].id.itemId}:true")
        }

    @Test
    fun `selecting toggles items and removing the selection sends their entry ids`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.ToggleSelection(0))
            viewModel.onAction(PlaylistAction.ToggleSelection(3))
            viewModel.onAction(PlaylistAction.ToggleSelection(2))
            viewModel.onAction(PlaylistAction.ToggleSelection(2))
            assertThat(viewModel.content.selected).containsExactly("e1", "e3")

            viewModel.onAction(PlaylistAction.RemoveSelected)

            assertThat(repository.calls).containsExactly("remove:p1:e1,e3")
            assertThat(viewModel.content.selected).isEmpty()
            assertThat(viewModel.content.items.map { it.entryId }).containsExactly("e2")
        }

    @Test
    fun `clear selection leaves selection mode`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            viewModel.onAction(PlaylistAction.ToggleSelection(0))

            viewModel.onAction(PlaylistAction.ClearSelection)

            assertThat(viewModel.content.isSelecting).isFalse()
        }

    @Test
    fun `move passes both items to the repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.Move(position = 3, targetPosition = 0))

            assertThat(repository.calls).containsExactly("move:p1:e3:0")
            assertThat(viewModel.content.items.map { it.entryId }).containsExactly("e3", "e1", "e2").inOrder()
        }

    @Test
    fun `pending items block moves, removals and selection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            repository.items.value = mapOf(playlistId to items + PlaylistItem(PENDING_ENTRY_ID, 4, track("t4")))

            viewModel.onAction(PlaylistAction.Move(3, 0))
            viewModel.onAction(PlaylistAction.Remove(0))
            viewModel.onAction(PlaylistAction.ToggleSelection(4))

            assertThat(viewModel.content.canEdit).isFalse()
            assertThat(viewModel.content.selected).isEmpty()
            assertThat(repository.calls).isEmpty()
        }

    @Test
    fun `a failed edit shows the error until it is dismissed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            repository.failure = MqError.Network

            viewModel.onAction(PlaylistAction.Remove(2))

            assertThat(viewModel.content.error).isEqualTo(MqError.Network)
            assertThat(viewModel.content.items).isEqualTo(items)
            viewModel.onAction(PlaylistAction.DismissError)
            assertThat(viewModel.content.error).isNull()
        }

    @Test
    fun `rename trims the name and skips an unchanged or blank one`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.Rename("  "))
            viewModel.onAction(PlaylistAction.Rename("Drum"))
            viewModel.onAction(PlaylistAction.Rename(" Seară "))

            assertThat(repository.calls).containsExactly("rename:p1:Seară")
            assertThat(viewModel.content.playlist.name).isEqualTo("Seară")
        }

    @Test
    fun `a deleted playlist closes the screen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.Delete)

            assertThat(repository.calls).containsExactly("delete:p1")
            assertThat(viewModel.uiState.value).isEqualTo(PlaylistUiState.Deleted)
        }

    @Test
    fun `a failed delete keeps the screen and shows the error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            repository.failure = MqError.Forbidden

            viewModel.onAction(PlaylistAction.Delete)

            assertThat(viewModel.content.error).isEqualTo(MqError.Forbidden)
        }

    @Test
    fun `a swipe runs its action and a rating rates the track`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.SwipeTrack(tracks.first(), right = true))
            viewModel.onAction(PlaylistAction.RateTrack(tracks.first(), 4))

            assertThat(playback.addedToQueue).containsExactly(listOf(tracks.first()))
            assertThat(userData.calls).contains("rating:${tracks.first().id.itemId}:4")
        }

    @Test
    fun `select all and a toolbar action run on the selected tracks and end selecting`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.SelectAll)
            assertThat(viewModel.content.selected).hasSize(items.size)
            viewModel.onAction(PlaylistAction.ClearSelection)

            viewModel.onAction(PlaylistAction.ToggleSelection(items[2].position))
            viewModel.onAction(PlaylistAction.ToggleSelection(items[0].position))
            viewModel.onAction(PlaylistAction.OnSelection(SelectionCommand.AddToQueue))

            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[0], tracks[2]))
            assertThat(viewModel.content.isSelecting).isFalse()
        }

    @Test
    fun `never played from here, there is no Resume`() =
        runTest(mainDispatcherRule.testDispatcher) {
            assertThat(viewModel().content.resume).isNull()
        }

    @Test
    fun `Resume plays on from the track and position where the playlist was left`() =
        runTest(mainDispatcherRule.testDispatcher) {
            resumes.resumes.value = mapOf(playlistId to ResumePoint(tracks[1].id, 42.seconds))
            val viewModel = viewModel()
            assertThat(viewModel.content.resume).isEqualTo(ResumePoint(tracks[1].id, 42.seconds))

            viewModel.onAction(PlaylistAction.Resume)

            assertThat(playback.plays.single()).isEqualTo(PlayCall(tracks, 1, false, 42.seconds, playlistId))
        }

    @Test
    fun `a finished track resumes at the next one, the last one finished or a removed one hides Resume`() =
        runTest(mainDispatcherRule.testDispatcher) {
            resumes.resumes.value = mapOf(playlistId to ResumePoint(tracks[0].id, 3.minutes))
            val viewModel = viewModel()
            assertThat(viewModel.content.resume).isEqualTo(ResumePoint(tracks[1].id, Duration.ZERO))

            resumes.resumes.value = mapOf(playlistId to ResumePoint(tracks[2].id, 3.minutes))
            assertThat(viewModel.content.resume).isNull()

            resumes.resumes.value = mapOf(playlistId to ResumePoint(track("gone").id, 10.seconds))
            assertThat(viewModel.content.resume).isNull()
        }

    @Test
    fun `offline, Resume goes by the downloaded tracks the playlist shows`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Offline the repository gives only the downloaded items: t2 is not one of them.
            resumes.resumes.value = mapOf(playlistId to ResumePoint(tracks[0].id, 3.minutes))
            val viewModel = viewModel()
            repository.items.value = mapOf(playlistId to listOf(items[0], items[2]))

            assertThat(viewModel.content.resume).isEqualTo(ResumePoint(tracks[2].id, Duration.ZERO))
        }

    @Test
    fun `a track in the playlist twice resumes at its first copy`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            repository.items.value =
                mapOf(playlistId to items + PlaylistItem("e4", 4, tracks[0]))
            resumes.resumes.value = mapOf(playlistId to ResumePoint(tracks[0].id, 10.seconds))

            viewModel.onAction(PlaylistAction.Resume)

            assertThat(playback.plays.single().startIndex).isEqualTo(0)
        }

    @Test
    fun `the downloaded mark follows the whole playlist being downloaded`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            assertThat(viewModel.content.isDownloaded).isFalse()

            library.fullyDownloaded.value = setOf(playlistId)

            assertThat(viewModel.content.isDownloaded).isTrue()
        }

    @Test
    fun `the header queues the whole playlist and favorites the playlist itself`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.PlayAllNext)
            viewModel.onAction(PlaylistAction.AddAllToQueue)
            viewModel.onAction(PlaylistAction.SetPlaylistFavorite(true))

            assertThat(playback.playedNext).containsExactly(tracks)
            assertThat(playback.addedToQueue).containsExactly(tracks)
            assertThat(userData.calls).containsExactly("favorite:p1:true")
        }

    @Test
    fun `Edit starts selecting with nothing selected, and clearing ends it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()

            viewModel.onAction(PlaylistAction.StartEditing)
            assertThat(viewModel.content.isSelecting).isTrue()
            assertThat(viewModel.content.selected).isEmpty()

            viewModel.onAction(PlaylistAction.ToggleSelection(2))
            viewModel.onAction(PlaylistAction.RemoveSelected)
            assertThat(viewModel.content.isSelecting).isFalse()
            assertThat(viewModel.content.items.map { it.entryId }).containsExactly("e1", "e3").inOrder()

            viewModel.onAction(PlaylistAction.StartEditing)
            viewModel.onAction(PlaylistAction.ClearSelection)
            assertThat(viewModel.content.isSelecting).isFalse()
        }

    @Test
    fun `the page settings apply at once`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            val page =
                PlaylistPagePreferences(
                    headerBackground = PlaylistHeaderBackground.None,
                    hiddenButtons = setOf(PlaylistButton.Shuffle),
                    dynamicColors = true,
                )

            preferences.setPlaylistPage(page)

            assertThat(viewModel.content.page).isEqualTo(page)
            assertThat(viewModel.content.shows(PlaylistButton.Shuffle)).isFalse()
            assertThat(viewModel.content.shows(PlaylistButton.Resume)).isTrue()
        }
}
