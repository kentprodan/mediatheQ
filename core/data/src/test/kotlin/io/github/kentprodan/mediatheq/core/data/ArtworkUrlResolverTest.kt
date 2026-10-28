package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
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
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI
import kotlin.time.Duration

class ArtworkUrlResolverTest {
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), FakeTokenCipher())
    private val artwork = Artwork("al1", "tag")

    private suspend fun signIn() =
        accountStore.save(StoredAccount("http://nas:8096", ServerId("s"), "Home", "u", "Ana", accessToken = "token"))

    @Test
    fun `widths round up to shared sizes`() {
        assertThat(listOf(1, 128, 129, 300, 512, 513, 1024, 4000).map(::artworkWidthBucket))
            .containsExactly(128, 128, 256, 512, 512, 1024, 1024, 1024)
            .inOrder()
        assertThat(artworkWidthBucket(null)).isEqualTo(512)
        assertThat(artworkWidthBucket(0)).isEqualTo(512)
    }

    @get:Rule
    val folder = TemporaryFolder()

    private val downloads = FakeDownloadRepository()
    private val library = FakeLibraryRepository()

    private fun TestScope.resolver(): ArtworkUrlResolver {
        val source = FakeLibrarySource()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val covers =
            DownloadedCovers(downloads, library, source, accountStore, folder.root, dispatcher, backgroundScope)
                .also { it.start() }
        return ArtworkUrlResolver(accountStore, source, backgroundScope, covers)
    }

    @Test
    fun `R154 a downloaded album's cover resolves to its file on the device`() =
        runTest {
            signIn()
            library.albums.value =
                listOf(Album(MediaId(ServerId("s"), "al1"), "Satelit", emptyList(), artwork = artwork))
            val resolver = resolver()
            downloads.downloads.value =
                listOf(
                    TrackDownload(
                        Track(
                            MediaId(ServerId("s"), "t1"),
                            "Song",
                            emptyList(),
                            Duration.ZERO,
                            albumId = MediaId(ServerId("s"), "al1"),
                        ),
                        DownloadState.Completed,
                    ),
                )
            runCurrent()

            val url = resolver.url(artwork, 300)

            assertThat(url).startsWith("file:")
            assertThat(File(URI(url)).readText()).isEqualTo("cover al1")
        }

    @Test
    fun `the url uses the signed-in server and the bucketed width`() =
        runTest {
            signIn()
            val resolver = resolver()

            assertThat(resolver.url(artwork, 300)).isEqualTo("http://nas:8096|al1|tag|512")
        }

    @Test
    fun `a cover requested before the account is read waits for it instead of failing`() =
        runTest {
            val resolver = resolver()
            val url = async { resolver.url(artwork, 200) }
            runCurrent()
            assertThat(url.isCompleted).isFalse()

            signIn()

            assertThat(url.await()).isEqualTo("http://nas:8096|al1|tag|256")
        }
}
