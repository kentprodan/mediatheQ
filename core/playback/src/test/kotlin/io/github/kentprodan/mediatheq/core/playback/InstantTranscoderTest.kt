package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class InstantTranscoderTest {
    // Counts the sources the player makes: a new one for an entry means its stream is chosen again.
    private val created = mutableListOf<String>()
    private val sources =
        object : MediaSource.Factory by FakeMediaSourceFactory() {
            private val fake = FakeMediaSourceFactory()

            override fun createMediaSource(mediaItem: MediaItem): MediaSource =
                fake.createMediaSource(mediaItem).also { created += mediaItem.mediaId }
        }
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).setMediaSourceFactory(sources).build()
    private var enabled = true
    private var needed = true
    private val transcoder = InstantTranscoder(player, { enabled }, { needed })

    private val items =
        (1..4).map {
            Track(
                MediaId(ServerId("s"), "t$it"),
                "Track $it",
                emptyList(),
                3.minutes,
            ).toMediaItem(null).withTrackUri()!!
        }

    @After
    fun release() = player.release()

    private fun playSecondAt5Seconds() {
        player.setMediaItems(items, 1, 5_000)
        player.shuffleModeEnabled = true
        player.prepare()
        player.play()
        advance(player).untilPendingCommandsAreFullyHandled()
        created.clear()
    }

    @Test
    fun `losing Wi-Fi with the setting on restarts the current track at the same place, in a new stream`() {
        playSecondAt5Seconds()
        val order = player.playOrder()
        runBlocking { transcoder.onNetwork(metered = false) }

        runBlocking { transcoder.onNetwork(metered = true) }
        advance(player).untilPendingCommandsAreFullyHandled()

        assertThat(created).containsExactly(items[1].mediaId)
        assertThat(player.currentMediaItemIndex).isEqualTo(1)
        assertThat(player.currentPosition).isAtLeast(5_000)
        assertThat(player.playOrder()).isEqualTo(order)
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun `nothing restarts with the setting off, for a local or same-quality stream, or without a Wi-Fi to lose`() {
        playSecondAt5Seconds()
        runBlocking { transcoder.onNetwork(metered = true) } // started metered: no Wi-Fi lost
        enabled = false
        runBlocking {
            transcoder.onNetwork(metered = false)
            transcoder.onNetwork(metered = true)
        }
        enabled = true
        needed = false
        runBlocking {
            transcoder.onNetwork(metered = false)
            transcoder.onNetwork(metered = true)
        }
        advance(player).untilPendingCommandsAreFullyHandled()

        assertThat(created).isEmpty()
    }

    @Test
    fun `only a streaming track in another quality needs a new stream`() {
        assertThat(wantsNewStream(StreamingQuality.Original, StreamingQuality.Kbps256, local = false)).isTrue()
        assertThat(wantsNewStream(StreamingQuality.Kbps256, StreamingQuality.Kbps256, local = false)).isFalse()
        assertThat(wantsNewStream(StreamingQuality.Original, StreamingQuality.Kbps256, local = true)).isFalse()
        assertThat(wantsNewStream(null, StreamingQuality.Kbps256, local = false)).isFalse()
    }
}
