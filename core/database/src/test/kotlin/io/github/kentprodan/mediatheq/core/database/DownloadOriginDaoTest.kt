package io.github.kentprodan.mediatheq.core.database

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.DownloadOriginEntity
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class DownloadOriginDaoTest {
    private val database = inMemoryDatabase()
    private val dao = database.downloadOriginDao()

    @After
    fun close() = database.close()

    private fun origin(
        track: String,
        kind: String,
        collection: String = "",
        server: String = "s",
    ) = DownloadOriginEntity(server, track, kind, collection)

    @Test
    fun `a track keeps one row per collection, and the same row twice is one`() =
        runTest {
            dao.add(listOf(origin("t1", "Album", "a1"), origin("t1", "Playlist", "p1")))
            dao.add(listOf(origin("t1", "Album", "a1")))

            assertThat(dao.all().first()).containsExactly(origin("t1", "Album", "a1"), origin("t1", "Playlist", "p1"))
        }

    @Test
    fun `removing a collection keeps the track's other origins`() =
        runTest {
            dao.add(listOf(origin("t1", "Album", "a1"), origin("t1", "Playlist", "p1"), origin("t2", "Playlist", "p1")))

            dao.removeCollection("Playlist", "s", "p1")

            assertThat(dao.all().first()).containsExactly(origin("t1", "Album", "a1"))
        }

    @Test
    fun `removing tracks forgets all their origins, on that server only`() =
        runTest {
            dao.add(
                listOf(
                    origin("t1", "Album", "a1"),
                    origin("t1", "Single"),
                    origin("t2", "Single"),
                    origin("t1", "Single", server = "other"),
                ),
            )

            dao.removeTracks("s", listOf("t1"))

            assertThat(dao.all().first())
                .containsExactly(origin("t2", "Single"), origin("t1", "Single", server = "other"))
        }
}
