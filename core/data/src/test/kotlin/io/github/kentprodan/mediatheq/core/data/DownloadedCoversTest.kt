package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySource
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration

class DownloadedCoversTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val server = ServerId("s")
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), FakeTokenCipher())
    private val source = FakeLibrarySource()
    private val downloads = FakeDownloadRepository()
    private val library = FakeLibraryRepository()

    private val albumCover = Artwork("al1", "atag")

    /** Tracks with their own embedded image, like the owner's library (#154): the album cover is still the one kept. */
    private fun track(n: Int) =
        Track(MediaId(server, "t$n"), "Track $n", emptyList(), Duration.ZERO, albumId = MediaId(server, "al1"))
            .copy(artwork = Artwork("t$n", "ttag$n"))

    private fun download(
        vararg tracks: Track,
        state: DownloadState = DownloadState.Completed,
    ) {
        downloads.downloads.value = tracks.map { TrackDownload(it, state) }
    }

    private suspend fun signIn() =
        accountStore.save(StoredAccount("http://nas:8096", server, "Home", "u", "Ana", accessToken = "token"))

    private fun TestScope.covers() =
        DownloadedCovers(downloads, library, source, accountStore, folder.root, dispatcher, backgroundScope)
            .also { it.start() }

    private fun setUpAlbum() {
        library.albums.value = listOf(Album(MediaId(server, "al1"), "fruitcake", emptyList(), artwork = albumCover))
    }

    @Test
    fun `R154 a downloaded track has its album cover on the device, for the track and the album`() =
        runTest(dispatcher) {
            signIn()
            setUpAlbum()
            val covers = covers()

            download(track(1), track(2))
            runCurrent()

            val file = covers.file(Artwork("t1", "ttag1"))
            assertThat(file?.readText()).isEqualTo("cover al1")
            assertThat(covers.file(Artwork("t2", "ttag2"))).isEqualTo(file)
            assertThat(covers.file(albumCover)).isEqualTo(file)
        }

    @Test
    fun `one album cover is fetched once, at 1024 px, whatever the number of tracks`() =
        runTest(dispatcher) {
            signIn()
            setUpAlbum()
            covers()

            download(track(1), track(2), track(3))
            runCurrent()
            download(track(1), track(2))
            runCurrent()

            assertThat(source.imageCalls).containsExactly("al1|atag|1024")
            assertThat(folder.root.list()).hasLength(1)
        }

    @Test
    fun `the cover stays while a track of the album is downloaded and goes with the last one`() =
        runTest(dispatcher) {
            signIn()
            setUpAlbum()
            val covers = covers()
            download(track(1), track(2))
            runCurrent()

            download(track(2))
            runCurrent()
            assertThat(covers.file(albumCover)).isNotNull()

            download()
            runCurrent()
            assertThat(covers.file(albumCover)).isNull()
            assertThat(folder.root.list()).isEmpty()
        }

    @Test
    fun `a download still running gets no cover yet`() =
        runTest(dispatcher) {
            signIn()
            setUpAlbum()
            val covers = covers()

            download(track(1), state = DownloadState.Downloading(0.5f))
            runCurrent()

            assertThat(covers.file(albumCover)).isNull()
            assertThat(source.imageCalls).isEmpty()
        }

    @Test
    fun `a failed fetch leaves no file and is tried again at the next change`() =
        runTest(dispatcher) {
            signIn()
            setUpAlbum()
            val covers = covers()
            source.imageFailure = MqError.Network

            download(track(1))
            runCurrent()
            assertThat(covers.file(albumCover)).isNull()
            assertThat(folder.root.list()).isEmpty()

            source.imageFailure = null
            download(track(1), track(2))
            runCurrent()
            assertThat(covers.file(albumCover)).isNotNull()
        }

    @Test
    fun `a track without an album row keeps its own image`() =
        runTest(dispatcher) {
            signIn()
            val covers = covers()

            download(track(1))
            runCurrent()

            assertThat(covers.file(Artwork("t1", "ttag1"))?.readText()).isEqualTo("cover t1")
        }

    @Test
    fun `files from an earlier run are found again, and stray ones are removed`() =
        runTest(dispatcher) {
            signIn()
            setUpAlbum()
            covers()
            download(track(1))
            runCurrent()
            folder.newFile("stray.jpg.tmp")
            source.imageCalls.clear()

            val restarted = covers()
            runCurrent()

            assertThat(restarted.file(albumCover)).isNotNull()
            assertThat(source.imageCalls).isEmpty()
            assertThat(folder.root.list()).asList().doesNotContain("stray.jpg.tmp")
        }
}
