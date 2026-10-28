package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class DefaultDownloadOriginsTest {
    private val database = inMemoryDatabase()
    private val origins = DefaultDownloadOrigins(database.downloadOriginDao())

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val album = DownloadOrigin.Album(id("a1"))
    private val playlist = DownloadOrigin.Playlist(id("p1"))

    @After
    fun close() = database.close()

    @Test
    fun `each origin reads back as it was added, per track`() =
        runTest {
            origins.add(listOf(id("t1"), id("t2")), album)
            origins.add(listOf(id("t1")), playlist)
            origins.add(listOf(id("t3")), DownloadOrigin.Single)

            assertThat(origins.origins.first())
                .containsExactly(
                    id("t1"),
                    setOf(album, playlist),
                    id("t2"),
                    setOf(album),
                    id("t3"),
                    setOf(DownloadOrigin.Single),
                )
        }

    @Test
    fun `removing a collection keeps the other origins`() =
        runTest {
            origins.add(listOf(id("t1"), id("t2")), playlist)
            origins.add(listOf(id("t1")), album)

            origins.removeCollection(playlist)

            assertThat(origins.origins.first()).containsExactly(id("t1"), setOf(album))
        }

    @Test
    fun `forgetting tracks drops all their origins`() =
        runTest {
            origins.add(listOf(id("t1"), id("t2")), album)
            origins.add(listOf(id("t1")), DownloadOrigin.Single)

            origins.forget(listOf(id("t1")))

            assertThat(origins.origins.first()).containsExactly(id("t2"), setOf(album))
        }
}
