package io.github.kentprodan.mediatheq.feature.downloads

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class DownloadsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val albumA = Album(id("A"), "Album A", emptyList())
    private val mix = Playlist(id("p1"), "Mix")
    private val a1 = track("a1", "A")
    private val a2 = track("a2", "A")
    private val song = track("s1", "B")
    private val repository =
        FakeDownloadRepository(
            listOf(
                TrackDownload(
                    a1,
                    DownloadState.Completed,
                    bytes = 100,
                    origins = setOf(DownloadOrigin.Album(albumA.id)),
                ),
                TrackDownload(a2, DownloadState.Downloading(0.5f), origins = setOf(DownloadOrigin.Album(albumA.id))),
                TrackDownload(song, DownloadState.Completed, bytes = 50, origins = setOf(DownloadOrigin.Single)),
            ),
        )
    private val library = FakeLibraryRepository().apply { albums.value = listOf(albumA) }
    private val playback = FakePlaybackConnection()
    private val viewModel by lazy { DownloadsViewModel(repository, library, playback) }

    private suspend fun content(): DownloadsUiState.Content {
        lateinit var content: DownloadsUiState.Content
        viewModel.uiState.test { content = expectMostRecentItem() as DownloadsUiState.Content }
        return content
    }

    @Test
    fun `loading first, then totals, what is downloading, the albums and the songs`() =
        runTest {
            assertThat(viewModel.uiState.value).isEqualTo(DownloadsUiState.Loading)
            repository.freeBytes.value = 1_000

            val content = content()

            assertThat(content.totals).isEqualTo(DownloadTotals(150, 6.minutes, 1_000))
            assertThat(content.downloading.map { it.track }).containsExactly(a2)
            assertThat(content.albums).containsExactly(DownloadedAlbum(albumA, listOf(a1), 100, 3.minutes))
            assertThat(content.songs.map { it.track }).containsExactly(song)
            assertThat(content.playlists).isEmpty()
        }

    @Test
    fun `with everything finished there is no downloading section`() =
        runTest {
            repository.setState(a2.id, DownloadState.Completed)

            assertThat(content().downloading).isEmpty()
        }

    @Test
    fun `with nothing finished only the downloading section has rows`() =
        runTest {
            repository.setState(a1.id, DownloadState.Queued)
            repository.setState(song.id, DownloadState.WaitingForWifi)

            val content = content()
            assertThat(content.downloading).hasSize(3)
            assertThat(content.albums).isEmpty()
            assertThat(content.songs).isEmpty()
        }

    @Test
    fun `no downloads is empty`() =
        runTest {
            repository.downloads.value = emptyList()

            viewModel.uiState.test { assertThat(expectMostRecentItem()).isEqualTo(DownloadsUiState.Empty) }
        }

    @Test
    fun `a song plays with the other downloaded songs, an unfinished download alone`() =
        runTest {
            content()

            viewModel.onAction(DownloadsAction.Play(song))
            viewModel.onAction(DownloadsAction.Play(a2))

            assertThat(playback.plays.map { it.queue }).containsExactly(listOf(song), listOf(a2)).inOrder()
        }

    @Test
    fun `a collection plays in order or shuffled`() {
        viewModel.onAction(DownloadsAction.PlayCollection(listOf(a1, song), shuffle = false))
        viewModel.onAction(DownloadsAction.PlayCollection(listOf(a1, song), shuffle = true))

        assertThat(playback.plays.map { it.shuffle }).containsExactly(false, true).inOrder()
        assertThat(playback.plays.first().queue).containsExactly(a1, song).inOrder()
    }

    @Test
    fun `removing a collection removes its downloads`() {
        viewModel.onAction(DownloadsAction.RemoveCollection(DownloadOrigin.Album(albumA.id)))

        assertThat(repository.downloads.value.map { it.track }).containsExactly(song)
    }

    @Test
    fun `remove deletes the download`() {
        viewModel.onAction(DownloadsAction.Remove(a1))

        assertThat(repository.downloads.value.map { it.track }).containsExactly(a2, song)
    }

    @Test
    fun `retry downloads a failed track again without a new origin`() {
        repository.remove(listOf(song.id))

        viewModel.onAction(DownloadsAction.Retry(song))

        assertThat(repository.downloads.value.last()).isEqualTo(TrackDownload(song, DownloadState.Queued))
    }

    @Test
    fun `play next and add to queue go to the player`() {
        viewModel.onAction(DownloadsAction.PlayNext(a1))
        viewModel.onAction(DownloadsAction.AddToQueue(song))

        assertThat(playback.playedNext).containsExactly(listOf(a1))
        assertThat(playback.addedToQueue).containsExactly(listOf(song))
    }

    @Test
    fun `a playlist's downloads show under it with the playlist's name`() =
        runTest {
            library.playlists.value = listOf(mix)
            repository.download(listOf(song), DownloadOrigin.Playlist(mix.id))

            val content = content()

            assertThat(content.playlists.map { it.playlist.name }).containsExactly("Mix")
            assertThat(content.songs.map { it.track }).containsExactly(song)
        }

    private fun track(
        id: String,
        album: String,
    ) = Track(
        id(id),
        "Track $id",
        emptyList(),
        3.minutes,
        albumId = id(album),
        albumTitle = "Album $album",
    )
}
