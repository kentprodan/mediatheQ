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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A real ExoPlayer on fake media: no stream is ever resolved, as for a download played offline. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class ResumeListenerTest {
    private val recorded = mutableListOf<Pair<String, Duration>>()
    private val playlists = mutableListOf<String?>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()
    private val listener =
        ResumeListener(
            player,
            { id, playlist, position ->
                recorded += id.itemId to position
                playlists += playlist?.itemId
            },
            scope,
            interval = 1.seconds,
        ).also(player::addListener)

    private fun item(
        id: String,
        playlist: String? = null,
    ): MediaItem =
        Track(MediaId(ServerId("s"), id), id, emptyList(), 3.minutes)
            .toMediaItem(null, playlistId = playlist?.let { MediaId(ServerId("s"), it) })
            .withTrackUri()!!

    @After
    fun release() {
        player.release()
        scope.cancel()
    }

    @Test
    fun `pausing records the track and where it stopped`() {
        player.playQueue(listOf(item("t1"), item("t2")), startIndex = 0, shuffle = false)
        advance(player).untilPositionAtLeast(100)

        player.pause()
        advance(player).untilPendingCommandsAreFullyHandled()

        val (id, position) = recorded.last()
        assertThat(id).isEqualTo("t1")
        assertThat(position.inWholeMilliseconds).isAtLeast(100)
    }

    @Test
    fun `a track played through is recorded at its end, and so is the last one`() {
        player.playQueue(listOf(item("t1"), item("t2")), startIndex = 0, shuffle = false)

        advance(player).untilState(Player.STATE_ENDED)

        val duration = player.duration
        assertThat(recorded.map { it.first }).containsAtLeast("t1", "t2").inOrder()
        assertThat(recorded.first { it.first == "t1" }.second.inWholeMilliseconds).isAtLeast(duration - 100)
        assertThat(recorded.last().first).isEqualTo("t2")
    }

    @Test
    fun `skipping ahead records where the skipped track was left`() {
        player.playQueue(listOf(item("t1"), item("t2")), startIndex = 0, shuffle = false)
        advance(player).untilPositionAtLeast(100)

        player.seekToNextMediaItem()
        advance(player).untilPendingCommandsAreFullyHandled()

        assertThat(recorded.last().first).isEqualTo("t1")
    }

    @Test
    fun `stopping records where playback was`() {
        player.playQueue(listOf(item("t1"), item("t2")), startIndex = 0, shuffle = false)
        advance(player).untilPositionAtLeast(100)

        player.stop()
        advance(player).untilPendingCommandsAreFullyHandled()

        val (id, position) = recorded.last()
        assertThat(id).isEqualTo("t1")
        assertThat(position.inWholeMilliseconds).isAtLeast(100)
    }

    @Test
    fun `release stops the ticker and records once more`() {
        player.playQueue(listOf(item("t1"), item("t2")), startIndex = 0, shuffle = false)
        advance(player).untilPositionAtLeast(100)

        listener.release()
        val count = recorded.size
        assertThat(recorded.last().first).isEqualTo("t1")

        // Past several ticker intervals: nothing more is recorded by the stopped ticker.
        player.removeListener(listener)
        advance(player).untilPositionAtLeast(2_500)
        assertThat(recorded).hasSize(count)
    }

    @Test
    fun `a track played from a playlist is recorded for that playlist, others for none`() {
        player.playQueue(listOf(item("t1", playlist = "p1")), startIndex = 0, shuffle = false)
        advance(player).untilPositionAtLeast(100)
        player.pause()
        advance(player).untilPendingCommandsAreFullyHandled()
        assertThat(playlists.last()).isEqualTo("p1")

        player.playQueue(listOf(item("t2")), startIndex = 0, shuffle = false)
        advance(player).untilPositionAtLeast(100)
        player.pause()
        advance(player).untilPendingCommandsAreFullyHandled()
        assertThat(recorded.last().first).isEqualTo("t2")
        assertThat(playlists.last()).isNull()
    }
}
