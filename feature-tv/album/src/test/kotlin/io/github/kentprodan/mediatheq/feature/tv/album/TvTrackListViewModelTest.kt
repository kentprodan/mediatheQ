package io.github.kentprodan.mediatheq.feature.tv.album

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ResumePoint
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakePlaylistRepository
import io.github.kentprodan.mediatheq.core.testing.FakeResumeRepository
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class TvTrackListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val playlists = FakePlaylistRepository()
    private val resumes = FakeResumeRepository()
    private val playback = FakePlaybackConnection()
    private val userData = FakeUserDataRepository()
    private val trackTaps = TrackTaps(playback, FakeLibraryPreferencesRepository())

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val ada = Artist(id("ada"), "Ada")
    private val album = Album(id("al"), "Strobe", listOf(ada), year = 2009, userData = UserData(isFavorite = true))
    private val flac = AudioFormat("flac", 96_000, 24, 2)

    private fun track(
        n: Int,
        disc: Int = 1,
    ) = Track(
        id("d${disc}t$n"),
        "Track $n",
        listOf(ada),
        4.minutes,
        albumId = album.id,
        trackNumber = n,
        discNumber = disc,
        audioFormat = flac,
    )

    private val tracks = listOf(track(1), track(2), track(1, disc = 2))

    private fun TestScope.albumPage(): TvAlbumViewModel {
        val viewModel = TvAlbumViewModel(album.id, library, resumes, playback, trackTaps, userData)
        backgroundScope.launch { viewModel.uiState.collect() }
        return viewModel
    }

    private fun TvTrackListViewModel.content() = uiState.value as TvTrackListUiState.Content

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `an album page shows its discs, its best quality, Resume and the playing track`() =
        test {
            library.albums.value = listOf(album)
            library.tracks.value = tracks
            resumes.resumes.value = mapOf(album.id to ResumePoint(tracks[1].id, 1.minutes))
            playback.state.value =
                playback.state.value.copy(
                    item =
                        io.github.kentprodan.mediatheq.core.playback
                            .NowPlayingItem(tracks[0].id, "Track 1", "Ada"),
                )

            val content = albumPage().content()

            assertThat(content.discs.map { it.number }).containsExactly(1, 2).inOrder()
            assertThat(content.header.subtitle).isEqualTo("Ada")
            assertThat(content.header.trackCount).isEqualTo(3)
            assertThat(content.header.duration).isEqualTo(12.minutes)
            assertThat(content.header.quality?.kind).isEqualTo(QualityKind.HiResLossless)
            assertThat(content.header.isFavorite).isTrue()
            assertThat(content.resume).isEqualTo(ResumePoint(tracks[1].id, 1.minutes))
            assertThat(content.currentTrackId).isEqualTo(tracks[0].id)
        }

    @Test
    fun `an album missing from the library is not found, an album without tracks is empty content`() =
        test {
            val viewModel = albumPage()
            assertThat(viewModel.uiState.value).isEqualTo(TvTrackListUiState.NotFound)

            library.albums.value = listOf(album)
            runCurrent()
            assertThat(viewModel.content().tracks).isEmpty()
        }

    @Test
    fun `play, shuffle, resume and a track start the right queue`() =
        test {
            library.albums.value = listOf(album)
            library.tracks.value = tracks
            resumes.resumes.value = mapOf(album.id to ResumePoint(tracks[1].id, 1.minutes))
            val viewModel = albumPage()

            viewModel.onAction(TvTrackListAction.Play)
            viewModel.onAction(TvTrackListAction.Shuffle)
            viewModel.onAction(TvTrackListAction.Resume)
            viewModel.onAction(TvTrackListAction.PlayTrack(2))
            runCurrent()

            val plays = playback.plays
            assertThat(plays.map { Triple(it.startIndex, it.shuffle, it.startPosition) })
                .containsExactly(
                    Triple(0, false, 0.minutes),
                    Triple(0, true, 0.minutes),
                    Triple(1, false, 1.minutes),
                    Triple(2, false, 0.minutes),
                ).inOrder()
            assertThat(plays.all { it.queue == tracks }).isTrue()
        }

    @Test
    fun `play next, add to queue and favorites`() =
        test {
            library.albums.value = listOf(album)
            library.tracks.value = tracks
            val viewModel = albumPage()

            viewModel.onAction(TvTrackListAction.PlayNext(tracks[0]))
            viewModel.onAction(TvTrackListAction.AddToQueue(tracks[1]))
            viewModel.onAction(TvTrackListAction.ToggleFavorite)
            viewModel.onAction(TvTrackListAction.ToggleTrackFavorite(tracks[2]))
            runCurrent()

            assertThat(playback.playedNext).containsExactly(listOf(tracks[0]))
            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[1]))
            assertThat(userData.calls).containsExactly("favorite:al:false", "favorite:d2t1:true").inOrder()
        }

    @Test
    fun `a playlist plays its entries in order and records into its own Resume`() =
        test {
            val playlist = Playlist(id("pl"), "Road trip")
            playlists.playlists.value = listOf(playlist)
            playlists.items.value =
                mapOf(playlist.id to listOf(PlaylistItem("e1", 0, tracks[2]), PlaylistItem("e2", 1, tracks[0])))
            resumes.resumes.value = mapOf(playlist.id to ResumePoint(tracks[0].id, 2.minutes))
            val viewModel = TvPlaylistViewModel(playlist.id, playlists, resumes, playback, trackTaps, userData)
            backgroundScope.launch { viewModel.uiState.collect() }

            assertThat(viewModel.content().tracks).containsExactly(tracks[2], tracks[0]).inOrder()
            assertThat(viewModel.content().header.quality).isNull()

            viewModel.onAction(TvTrackListAction.PlayTrack(1))
            viewModel.onAction(TvTrackListAction.Resume)
            runCurrent()

            assertThat(playback.plays.map { it.startIndex to it.playlistId })
                .containsExactly(1 to playlist.id, 1 to playlist.id)
                .inOrder()
            assertThat(playback.plays.last().startPosition).isEqualTo(2.minutes)
        }

    @Test
    fun `a playlist page refreshes the playlist, starts loading, and runs every action on its entries`() =
        test {
            val playlist = Playlist(id("pl"), "Road trip")
            val viewModel = TvPlaylistViewModel(playlist.id, playlists, resumes, playback, trackTaps, userData)
            assertThat(viewModel.uiState.value).isEqualTo(TvTrackListUiState.Loading)
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            assertThat(viewModel.uiState.value).isEqualTo(TvTrackListUiState.NotFound)
            assertThat(playlists.refreshes).containsExactly(playlist.id)

            playlists.playlists.value = listOf(playlist)
            playlists.items.value =
                mapOf(playlist.id to listOf(PlaylistItem("e1", 0, tracks[0]), PlaylistItem("e2", 1, tracks[1])))
            runCurrent()
            viewModel.onAction(TvTrackListAction.Play)
            viewModel.onAction(TvTrackListAction.Shuffle)
            viewModel.onAction(TvTrackListAction.PlayNext(tracks[0]))
            viewModel.onAction(TvTrackListAction.AddToQueue(tracks[1]))
            viewModel.onAction(TvTrackListAction.ToggleFavorite)
            viewModel.onAction(TvTrackListAction.ToggleTrackFavorite(tracks[0]))
            runCurrent()

            assertThat(playback.plays.map { it.shuffle to it.playlistId })
                .containsExactly(false to playlist.id, true to playlist.id)
                .inOrder()
            assertThat(playback.playedNext).containsExactly(listOf(tracks[0]))
            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[1]))
            assertThat(userData.calls).containsExactly("favorite:pl:true", "favorite:d1t1:true").inOrder()
        }
}
