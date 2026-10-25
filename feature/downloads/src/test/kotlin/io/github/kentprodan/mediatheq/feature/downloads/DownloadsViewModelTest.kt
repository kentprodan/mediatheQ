package io.github.kentprodan.mediatheq.feature.downloads

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class DownloadsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val a1 = track("a1", "Album A")
    private val a2 = track("a2", "Album A")
    private val b1 = track("b1", "Album B")
    private val repository =
        FakeDownloadRepository(
            listOf(
                TrackDownload(a1, DownloadState.Completed),
                TrackDownload(a2, DownloadState.Downloading(0.5f)),
                TrackDownload(b1, DownloadState.Completed),
            ),
        )
    private val playback = FakePlaybackConnection()
    private val viewModel by lazy { DownloadsViewModel(repository, playback) }

    @Test
    fun `loading first, then downloads grouped by album with the storage used`() =
        runTest {
            assertThat(viewModel.uiState.value).isEqualTo(DownloadsUiState.Loading)
            repository.storageUsed.value = 42L

            viewModel.uiState.test {
                val content = expectMostRecentItem() as DownloadsUiState.Content
                assertThat(content.albums.map { it.title }).containsExactly("Album A", "Album B").inOrder()
                assertThat(
                    content.albums
                        .first()
                        .downloads
                        .map { it.track },
                ).containsExactly(a1, a2).inOrder()
                assertThat(content.storageUsed).isEqualTo(42L)
            }
        }

    @Test
    fun `no downloads is empty`() =
        runTest {
            repository.downloads.value = emptyList()

            viewModel.uiState.test { assertThat(expectMostRecentItem()).isEqualTo(DownloadsUiState.Empty) }
        }

    @Test
    fun `play queues only the finished downloads, from the chosen one`() =
        runTest {
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.onAction(DownloadsAction.Play(b1))
                cancelAndIgnoreRemainingEvents()
            }

            val play = playback.plays.single()
            assertThat(play.queue).containsExactly(a1, b1).inOrder()
            assertThat(play.startIndex).isEqualTo(1)
        }

    @Test
    fun `play of an unfinished download plays it alone`() =
        runTest {
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.onAction(DownloadsAction.Play(a2))
                cancelAndIgnoreRemainingEvents()
            }

            assertThat(playback.plays.single().queue).containsExactly(a2)
        }

    @Test
    fun `remove deletes the download`() {
        viewModel.onAction(DownloadsAction.Remove(a1))

        assertThat(repository.downloads.value.map { it.track }).containsExactly(a2, b1)
    }

    @Test
    fun `retry downloads a failed track again`() {
        repository.remove(listOf(b1.id))

        viewModel.onAction(DownloadsAction.Retry(b1))

        assertThat(repository.downloads.value.last()).isEqualTo(TrackDownload(b1, DownloadState.Queued))
    }

    @Test
    fun `play next and add to queue go to the player`() {
        viewModel.onAction(DownloadsAction.PlayNext(a1))
        viewModel.onAction(DownloadsAction.AddToQueue(b1))

        assertThat(playback.playedNext).containsExactly(listOf(a1))
        assertThat(playback.addedToQueue).containsExactly(listOf(b1))
    }

    private fun track(
        id: String,
        album: String,
    ) = Track(
        MediaId(ServerId("s"), id),
        "Track $id",
        emptyList(),
        3.minutes,
        albumId = MediaId(ServerId("s"), album),
        albumTitle = album,
    )
}
