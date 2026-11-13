package io.github.kentprodan.mediatheq.core.database

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.dao.VideoDao.Companion.SORT_ADDED
import io.github.kentprodan.mediatheq.core.database.dao.VideoDao.Companion.SORT_NAME
import io.github.kentprodan.mediatheq.core.database.dao.VideoDao.Companion.SORT_YEAR
import io.github.kentprodan.mediatheq.core.database.entity.SeasonEntity
import io.github.kentprodan.mediatheq.core.database.entity.SeasonStaging
import io.github.kentprodan.mediatheq.core.database.entity.SeriesEntity
import io.github.kentprodan.mediatheq.core.database.entity.SeriesStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.database.entity.UserDataColumns
import io.github.kentprodan.mediatheq.core.database.entity.VideoEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoStaging
import io.github.kentprodan.mediatheq.core.database.entity.VideoStreamEntity
import io.github.kentprodan.mediatheq.core.database.entity.VideoStreamStaging
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The video reads (#270, ADR 0048). m1 "Moșoi" (2020, added last, watched), m2 "Alpha" (2024), m3 "Zeta" (no year);
 * series sr1 "Show" with season 2 (se2, e3) and season 1 (se1, e1 and e2), sr2 "Other" (watched, no episodes).
 */
class VideoQueriesTest {
    private val database = inMemoryDatabase()
    private val dao = database.videoDao()
    private val syncDao = database.syncDao()

    @After
    fun close() = database.close()

    @Before
    fun library() =
        runTest {
            syncDao.stageVideos(
                listOf(
                    movie("m1", "Moșoi", year = 2020, added = 30, played = true),
                    movie("m2", "Alpha", year = 2024, added = 10),
                    movie("m3", "Zeta", year = null, added = 20),
                    episode("e1", "se1", 1),
                    episode("e2", "se1", 2),
                    episode("e3", "se2", 1),
                ).map(::VideoStaging),
            )
            syncDao.stageSeries(
                listOf(
                    SeriesEntity("s", "sr1", "Show", "show", year = 2010),
                    SeriesEntity("s", "sr2", "Other", "other", userData = UserDataColumns(played = true)),
                ).map(::SeriesStaging),
            )
            syncDao.stageSeasons(
                listOf(SeasonEntity("s", "se2", "sr1", "Season 2", 2), SeasonEntity("s", "se1", "sr1", "Season 1", 1))
                    .map(::SeasonStaging),
            )
            syncDao.stageVideoStreams(
                listOf(VideoStreamEntity("s", "m1", 2, "Subtitle"), VideoStreamEntity("s", "m1", 1, "Audio"))
                    .map(::VideoStreamStaging),
            )
            syncDao.stageTracks(listOf(TrackStaging(TrackEntity("s", "t1", "Moșoi pe vale", "mosoi pe vale"))))
            syncDao.swap(1, "s", "u")
        }

    private fun movie(
        id: String,
        title: String,
        year: Int?,
        added: Long,
        played: Boolean = false,
    ) = VideoEntity(
        "s",
        id,
        "Movie",
        title,
        title.lowercase(),
        year = year,
        dateAddedMillis = added,
        userData = UserDataColumns(played = played),
    )

    private fun episode(
        id: String,
        season: String,
        number: Int,
    ) = VideoEntity(
        "s",
        id,
        "Episode",
        "Episode $id",
        "episode $id",
        seriesId = "sr1",
        seasonId = season,
        episodeNumber = number,
    )

    private suspend fun movies(
        sort: String = SORT_NAME,
        unwatched: Boolean = false,
        all: Boolean = true,
        downloaded: List<String> = emptyList(),
    ) = dao.movies(all, downloaded, unwatched, sort).first().map { it.id }

    @Test
    fun `movies only, by name, newest added or newest year first`() =
        runTest {
            assertThat(movies()).containsExactly("m2", "m1", "m3").inOrder()
            assertThat(movies(SORT_ADDED)).containsExactly("m1", "m3", "m2").inOrder()
            // A movie without a year goes last.
            assertThat(movies(SORT_YEAR)).containsExactly("m2", "m1", "m3").inOrder()
        }

    @Test
    fun `unwatched leaves out watched movies and series`() =
        runTest {
            assertThat(movies(unwatched = true)).containsExactly("m2", "m3").inOrder()
            assertThat(dao.series(true, emptyList(), true, SORT_NAME).first().map { it.id }).containsExactly("sr1")
        }

    @Test
    fun `series newest year first, one without a year last, and newest added first`() =
        runTest {
            syncDao.stageSeries(
                listOf(
                    SeriesEntity("s", "a", "A", "a", year = 2001, dateAddedMillis = 5),
                    SeriesEntity("s", "b", "B", "b", year = null, dateAddedMillis = 9),
                    SeriesEntity("s", "c", "C", "c", year = 2020, dateAddedMillis = null),
                ).map(::SeriesStaging),
            )
            syncDao.swap(2, "s", "u")

            assertThat(dao.series(true, emptyList(), false, SORT_YEAR).first().map { it.id })
                .containsExactly("c", "a", "b")
                .inOrder()
            assertThat(dao.series(true, emptyList(), false, SORT_ADDED).first().map { it.id })
                .containsExactly("b", "a", "c")
                .inOrder()
        }

    @Test
    fun `series by name and counts`() =
        runTest {
            assertThat(dao.series(true, emptyList(), false, SORT_NAME).first().map { it.id })
                .containsExactly("sr2", "sr1")
                .inOrder()
            assertThat(dao.movieCount(true, emptyList()).first()).isEqualTo(3)
            assertThat(dao.seriesCount(true, emptyList()).first()).isEqualTo(2)
        }

    @Test
    fun `offline lists keep only downloaded videos and what holds them, with no ids or 10,000`() =
        runTest {
            assertThat(movies(all = false)).isEmpty()
            assertThat(dao.seriesCount(false, emptyList()).first()).isEqualTo(0)
            val many = List(10_000) { "x$it" } + listOf("m3", "e2")
            assertThat(movies(all = false, downloaded = many)).containsExactly("m3")
            assertThat(dao.movieCount(false, many).first()).isEqualTo(1)
            assertThat(dao.series(false, many, false, SORT_NAME).first().map { it.id }).containsExactly("sr1")
            assertThat(dao.seasons("sr1", false, many).first().map { it.id }).containsExactly("se1")
            assertThat(dao.episodes("se1", false, many).first().map { it.id }).containsExactly("e2")
        }

    @Test
    fun `seasons by number, episodes by number, streams by index`() =
        runTest {
            assertThat(
                dao.seasons("sr1", true, emptyList()).first().map { it.id },
            ).containsExactly("se1", "se2").inOrder()
            assertThat(
                dao.episodes("se1", true, emptyList()).first().map { it.id },
            ).containsExactly("e1", "e2").inOrder()
            assertThat(dao.streams("m1").first().map { it.streamIndex }).containsExactly(1, 2).inOrder()
            assertThat(dao.video("m1").first()?.title).isEqualTo("Moșoi")
            assertThat(dao.seriesById("sr1").first()?.title).isEqualTo("Show")
        }

    @Test
    fun `search finds a movie title without its diacritics, and music search ignores videos`() =
        runTest {
            assertThat(dao.searchIds("mosoi")).containsExactly("m1")
            assertThat(dao.searchIds("show")).containsExactly("sr1")
            assertThat(
                database
                    .libraryDao()
                    .search("mosoi*", 10, true, emptyList())
                    .tracks
                    .map { it.track.id },
            ).containsExactly("t1")
        }

    @Test
    fun `sign-out forgets the videos`() =
        runTest {
            syncDao.clearAll()

            assertThat(movies()).isEmpty()
            assertThat(dao.seriesCount(true, emptyList()).first()).isEqualTo(0)
            assertThat(dao.streams("m1").first()).isEmpty()
        }
}
