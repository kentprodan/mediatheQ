package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ResumePoint
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import io.github.kentprodan.mediatheq.core.testing.rows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ResumeTest {
    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val tracks = List(3) { Track(id("t$it"), "T$it", emptyList(), 3.minutes) }

    @Test
    fun `Resume continues the track where it stopped`() {
        val resume = ResumePoint(id("t1"), 70.seconds)

        assertThat(resumeTarget(resume, tracks)).isEqualTo(resume)
    }

    @Test
    fun `a track played to its end resumes with the next one from the start`() {
        assertThat(
            resumeTarget(ResumePoint(id("t0"), 3.minutes), tracks),
        ).isEqualTo(ResumePoint(id("t1"), Duration.ZERO))
        // Within ten seconds of the end counts as played.
        assertThat(
            resumeTarget(ResumePoint(id("t0"), 171.seconds), tracks),
        ).isEqualTo(ResumePoint(id("t1"), Duration.ZERO))
        assertThat(
            resumeTarget(ResumePoint(id("t0"), 169.seconds), tracks),
        ).isEqualTo(ResumePoint(id("t0"), 169.seconds))
    }

    @Test
    fun `a short interlude paused early keeps its place, near its end resumes with the next track`() {
        val short = listOf(Track(id("i"), "Intro", emptyList(), 8.seconds)) + tracks

        assertThat(resumeTarget(ResumePoint(id("i"), 3.seconds), short)).isEqualTo(ResumePoint(id("i"), 3.seconds))
        assertThat(resumeTarget(ResumePoint(id("i"), 5.seconds), short)).isEqualTo(ResumePoint(id("t0"), Duration.ZERO))
    }

    @Test
    fun `nothing played, a finished album or a track no longer on it has no Resume`() {
        assertThat(resumeTarget(null, tracks)).isNull()
        assertThat(resumeTarget(ResumePoint(id("t2"), 3.minutes), tracks)).isNull()
        assertThat(resumeTarget(ResumePoint(id("gone"), 10.seconds), tracks)).isNull()
    }

    private val database = inMemoryDatabase()
    private val repository = DefaultResumeRepository(database.resumeDao())

    @After
    fun close() = database.close()

    @Test
    fun `a recorded position is kept for the track's album, and a track without an album is ignored`() =
        runTest {
            database.syncDao().stageAlbums(listOf(AlbumStaging(AlbumEntity("s", "al1", "A", "A"))))
            database.syncDao().stageTracks(
                listOf(
                    TrackStaging(TrackEntity("s", "t1", "One", "One", albumId = "al1")),
                    TrackStaging(TrackEntity("s", "t9", "Loose", "Loose")),
                ),
            )
            database.syncDao().swap(1, "s", "user-1")

            repository.record(id("t1"), 42.seconds)
            repository.record(id("t9"), 5.seconds)

            assertThat(repository.resume(id("al1")).first()).isEqualTo(ResumePoint(id("t1"), 42.seconds))
            assertThat(
                database
                    .rows("SELECT COUNT(*) FROM album_resume")
                    .single()
                    .single()
                    .toString(),
            ).isEqualTo("1")
        }

    @Test
    fun `a track played from a playlist is kept for the playlist too, also without an album`() =
        runTest {
            database.syncDao().stageAlbums(listOf(AlbumStaging(AlbumEntity("s", "al1", "A", "A"))))
            database.syncDao().stageTracks(
                listOf(
                    TrackStaging(TrackEntity("s", "t1", "One", "One", albumId = "al1")),
                    TrackStaging(TrackEntity("s", "t9", "Loose", "Loose")),
                ),
            )
            database.syncDao().swap(1, "s", "user-1")

            repository.record(id("t1"), 42.seconds, playlistId = id("p1"))
            assertThat(repository.playlistResume(id("p1")).first()).isEqualTo(ResumePoint(id("t1"), 42.seconds))
            assertThat(repository.resume(id("al1")).first()).isEqualTo(ResumePoint(id("t1"), 42.seconds))

            repository.record(id("t9"), 5.seconds, playlistId = id("p1"))
            assertThat(repository.playlistResume(id("p1")).first()).isEqualTo(ResumePoint(id("t9"), 5.seconds))
            assertThat(repository.playlistResume(id("p2")).first()).isNull()
        }
}
