package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.SearchHit
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class DefaultLibraryRepositoryTest {
    private val database = inMemoryDatabase()
    private val repository = DefaultLibraryRepository(database.libraryDao())

    @After
    fun close() = database.close()

    private suspend fun library(vararg titles: String) {
        database.syncDao().stageTracks(
            titles.mapIndexed {
                i,
                title,
                ->
                TrackStaging(TrackEntity("s", "t$i", title, title))
            },
        )
        database.syncDao().swap(1, "s", "user-1")
    }

    @Test
    fun `typed words become quoted terms and the last one a prefix`() {
        assertThat(ftsMatchExpression("  ada  mos ")).isEqualTo("\"ada\" \"mos\"*")
        assertThat(ftsMatchExpression("say \"hi\"")).isEqualTo("\"say\" \"\"\"hi\"\"\"*")
        assertThat(ftsMatchExpression("   ")).isNull()
    }

    @Test
    fun `search finds by prefix, without diacritics`() =
        runTest {
            library("Moșoi", "Țară")

            assertThat(
                repository.search("mos"),
            ).containsExactly(SearchHit(SearchHit.Kind.Track, MediaId(ServerId("s"), "t0"), "Moșoi"))
            assertThat(repository.search("tara").map { it.name }).containsExactly("Țară")
        }

    @Test
    fun `text that is FTS syntax is searched as plain text, never an error`() =
        runTest {
            library("AC/DC", "Rock-n-roll", "Do NOT disturb", "Re: Union", "Say \"hi\"")

            listOf("AC/DC", "rock-n-roll", "NOT", "re:", "\"", "say \"hi", "*", "(", "OR", "-").forEach { query ->
                repository.search(query) // must not throw
            }
            assertThat(repository.search("AC/DC").map { it.name }).containsExactly("AC/DC")
            assertThat(repository.search("NOT").map { it.name }).containsExactly("Do NOT disturb")
        }

    @Test
    fun `an empty query finds nothing`() =
        runTest {
            library("Moșoi")

            assertThat(repository.search("  ")).isEmpty()
        }

    @Test
    fun `an album and its tracks map to the domain`() =
        runTest {
            database.syncDao().stageAlbums(
                listOf(
                    io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging(
                        io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity(
                            "s",
                            "al1",
                            "Satelit",
                            "Satelit",
                            year = 2016,
                        ),
                    ),
                ),
            )
            database.syncDao().stageTracks(
                listOf(
                    TrackStaging(
                        TrackEntity(
                            "s",
                            "t1",
                            "Moșoi",
                            "Mosoi",
                            albumId = "al1",
                            trackNumber = 1,
                            durationMillis = 1_000,
                        ),
                    ),
                ),
            )
            database.syncDao().swap(1, "s", "user-1")
            val id = MediaId(ServerId("s"), "al1")

            assertThat(repository.album(id).first()?.year).isEqualTo(2016)
            val track = repository.albumTracks(id).first().single()
            assertThat(track.albumId).isEqualTo(id)
            assertThat(track.duration.inWholeMilliseconds).isEqualTo(1_000)
        }
}
