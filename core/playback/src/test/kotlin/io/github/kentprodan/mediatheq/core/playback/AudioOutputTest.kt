package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class AudioOutputTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var mode = AudioMode.Offload
    private val player: ExoPlayer =
        TestExoPlayerBuilder(context).setMediaSourceFactory(FakeMediaSourceFactory()).build()
    private val normalization = NormalizationListener(player, { AudioSettings() }, { mode }).also(player::addListener)

    @After
    fun release() = player.release()

    private fun item(
        n: Int,
        albumDb: Double? = null,
        trackDb: Double? = null,
    ) = Track(
        MediaId(ServerId("s"), "t$n"),
        "Track $n",
        emptyList(),
        3.minutes,
        normalizationGainDb = trackDb,
        albumNormalizationGainDb = albumDb,
    ).toMediaItem(null).withTrackUri()!!

    @Test
    fun `each track plays at its own normalization volume`() {
        player.setMediaItems(listOf(item(1, albumDb = -6.0), item(2, trackDb = -20.0), item(3), item(4, albumDb = 3.0)))
        assertThat(player.volume).isWithin(0.001f).of(0.501f)

        player.seekToNextMediaItem()
        assertThat(player.volume).isWithin(0.001f).of(0.1f)

        player.seekToNextMediaItem()
        assertThat(player.volume).isEqualTo(1f)

        player.seekToNextMediaItem()
        assertThat(player.volume).isEqualTo(1f)
    }

    @Test
    fun `bit-perfect plays at full volume at once, and normalization comes back after`() {
        player.setMediaItems(listOf(item(1, albumDb = -6.0)))

        mode = AudioMode.BitPerfect
        normalization.apply()
        assertThat(player.volume).isEqualTo(1f)

        mode = AudioMode.Offload
        normalization.apply()
        assertThat(player.volume).isWithin(0.001f).of(0.501f)
    }

    @Test
    fun `a controller connects with the default commands, except setting the player volume`() {
        val session =
            MediaSession
                .Builder(context, player)
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ) = acceptWithoutPlayerVolume(session, controller)
                    },
                ).build()
        val future = MediaController.Builder(context, session.token).buildAsync()
        RobolectricUtil.runMainLooperUntil { future.isDone }

        // Regression (#53): the controller was rejected (Media3 1.11's default onConnect result has no commands).
        val controller = future.get()
        assertThat(controller.isConnected).isTrue()
        assertThat(controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)).isTrue()
        assertThat(controller.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM)).isTrue()
        assertThat(controller.isCommandAvailable(Player.COMMAND_SET_VOLUME)).isFalse()
        controller.release()
        session.release()
    }

    @Test
    fun `offload is enabled with gapless required, or disabled`() {
        player.applyOffload(true)
        val offload = player.trackSelectionParameters.audioOffloadPreferences
        assertThat(offload.audioOffloadMode).isEqualTo(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
        assertThat(offload.isGaplessSupportRequired).isTrue()

        player.applyOffload(false)
        assertThat(player.trackSelectionParameters.audioOffloadPreferences.audioOffloadMode)
            .isEqualTo(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
    }
}
