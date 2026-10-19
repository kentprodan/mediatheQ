package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

/** Drives a real ExoPlayer (fake media) through the listener: the events the reports depend on come from Media3. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class ReportingListenerTest {
    private val repository = FakePlaybackRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()
            .also { player ->
                val reporter =
                    PlaybackReporter(repository, { FakePlaybackSource.fakeStream(it) }, scope, Dispatchers.Unconfined)
                player.addListener(ReportingListener(player, reporter))
            }

    private fun item(id: String): MediaItem =
        Track(MediaId(ServerId("s"), id), id, emptyList(), 3.minutes).toMediaItem(null).withTrackUri()!!

    @After
    fun release() {
        player.release()
        scope.cancel()
    }

    /** The reports without positions (fake media durations are not the point here). */
    private fun reports() =
        repository.reports.map {
            it.substringBefore(" @") +
                if (it.endsWith("failed")) " failed" else ""
        }

    @Test
    fun `every track of an album gets started and stopped, also when the next one starts gaplessly`() {
        player.playQueue(listOf(item("t1"), item("t2"), item("t3")), startIndex = 0, shuffle = false)

        advance(player).untilState(Player.STATE_ENDED)

        assertThat(reports().filterNot { it.startsWith("progress") })
            .containsExactly("start t1", "stopped t1", "start t2", "stopped t2", "start t3", "stopped t3")
            .inOrder()
    }

    @Test
    fun `repeat one starts a new session every time the track plays again`() {
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.playQueue(listOf(item("t1")), startIndex = 0, shuffle = false)

        advance(player).untilPositionDiscontinuityWithReason(Player.DISCONTINUITY_REASON_AUTO_TRANSITION)

        assertThat(
            reports().filterNot {
                it.startsWith("progress")
            },
        ).containsExactly("start t1", "stopped t1", "start t1").inOrder()
    }

    @Test
    fun `a new queue starting with the playing track ends the old session and starts a new one`() {
        player.playQueue(listOf(item("t1"), item("t2")), startIndex = 0, shuffle = false)
        advance(player).untilPositionAtLeast(100)

        player.playQueue(listOf(item("t1")), startIndex = 0, shuffle = false)
        advance(player).untilPositionAtLeast(100)

        assertThat(
            reports().filterNot {
                it.startsWith("progress")
            },
        ).containsExactly("start t1", "stopped t1", "start t1").inOrder()
    }

    @Test
    fun `skipping to the next track stops the old session and starts the new one`() {
        player.playQueue(listOf(item("t1"), item("t2")), startIndex = 0, shuffle = false)
        advance(player).untilPositionAtLeast(100)

        player.seekToNextMediaItem()
        advance(player).untilState(Player.STATE_READY)
        advance(player).untilPendingCommandsAreFullyHandled()

        assertThat(
            reports().filterNot {
                it.startsWith("progress")
            },
        ).containsExactly("start t1", "stopped t1", "start t2").inOrder()
    }

    @Test
    fun `pausing reports a paused position`() {
        player.playQueue(listOf(item("t1")), startIndex = 0, shuffle = false)
        advance(player).untilPositionAtLeast(100)

        player.pause()
        advance(player).untilPendingCommandsAreFullyHandled()

        assertThat(repository.reports.last()).endsWith("paused")
    }
}
