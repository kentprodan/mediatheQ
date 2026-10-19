package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PlaybackReporterTest {
    private val repository = FakePlaybackRepository()
    private val t1 = MediaId(ServerId("s"), "t1")
    private val t2 = MediaId(ServerId("s"), "t2")
    private var position: Duration = Duration.ZERO

    private fun TestScope.reporter(known: Set<MediaId> = setOf(t1, t2)): PlaybackReporter {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return PlaybackReporter(
            repository = repository,
            streamOf = { id -> FakePlaybackSource.fakeStream(id).takeIf { id in known } },
            scope = backgroundScope,
            playerDispatcher = dispatcher,
            interval = 10.seconds,
        )
    }

    /** Moves virtual time; the fake position follows, like a playing track. */
    private fun TestScope.play(duration: Duration) {
        val steps = duration.inWholeSeconds.toInt()
        repeat(steps) {
            position += 1.seconds
            advanceTimeBy(1.seconds)
            runCurrent() // ticks due exactly now see the position of now
        }
        runCurrent()
    }

    @Test
    fun `a track reports started, then progress every 10 seconds while it plays`() =
        runTest {
            val reporter = reporter()

            reporter.playing(t1) { position }
            runCurrent()
            play(25.seconds)

            assertThat(
                repository.reports,
            ).containsExactly("start t1 @0s", "progress t1 @10s", "progress t1 @20s").inOrder()
        }

    @Test
    fun `pause reports at once and stops the ticker, resume reports again`() =
        runTest {
            val reporter = reporter()
            reporter.playing(t1) { position }
            play(5.seconds)

            reporter.paused(position)
            play(30.seconds)
            reporter.playing(t1) { position }
            runCurrent()

            assertThat(repository.reports)
                .containsExactly("start t1 @0s", "progress t1 @5s paused", "progress t1 @35s")
                .inOrder()
        }

    @Test
    fun `a seek reports the new position`() =
        runTest {
            val reporter = reporter()
            reporter.playing(t1) { position }
            runCurrent()

            reporter.seeked(90.seconds, isPlaying = true)
            runCurrent()

            assertThat(repository.reports.last()).isEqualTo("progress t1 @90s")
        }

    @Test
    fun `the next track stops the previous session first`() =
        runTest {
            val reporter = reporter()
            reporter.playing(t1) { position }
            play(12.seconds)

            reporter.ended(12.seconds)
            position = Duration.ZERO
            reporter.playing(t2) { position }
            runCurrent()

            assertThat(repository.reports)
                .containsExactly("start t1 @0s", "progress t1 @10s", "stopped t1 @12s", "start t2 @0s")
                .inOrder()
        }

    @Test
    fun `a track change without an explicit end still stops the old session`() =
        runTest {
            val reporter = reporter()
            reporter.playing(t1) { position }
            runCurrent()

            reporter.playing(t2) { position }
            runCurrent()

            assertThat(repository.reports).containsExactly("start t1 @0s", "stopped t1 @0s", "start t2 @0s").inOrder()
        }

    @Test
    fun `a failure is reported as a failed stop`() =
        runTest {
            val reporter = reporter()
            reporter.playing(t1) { position }
            runCurrent()

            reporter.ended(3.seconds, failed = true)
            runCurrent()

            assertThat(repository.reports.last()).isEqualTo("stopped t1 @3s failed")
        }

    @Test
    fun `without a resolved stream nothing is reported`() =
        runTest {
            val reporter = reporter(known = emptySet())

            reporter.playing(t1) { position }
            play(30.seconds)
            reporter.ended(30.seconds)
            runCurrent()

            assertThat(repository.reports).isEmpty()
        }

    @Test
    fun `nothing is reported after the session ended`() =
        runTest {
            val reporter = reporter()
            reporter.playing(t1) { position }
            runCurrent()
            reporter.ended(1.seconds)

            play(30.seconds)
            reporter.paused(position)
            runCurrent()

            assertThat(repository.reports).containsExactly("start t1 @0s", "stopped t1 @1s").inOrder()
        }
}
