package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.media.AudioFormat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

/**
 * The service's output wiring (#165, required by #65): the controller deciding on a fake DAC, its state applied to a
 * real player by [OutputStateApplier], the mid-track reopen and the fallback on an AudioSink error.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioOutputWiringTest {
    private val cd = PcmFormat(AudioFormat.ENCODING_PCM_16BIT, 44_100, AudioFormat.CHANNEL_OUT_STEREO)
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext<Context>())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()
    private val port = FakeUsbAudioPort().apply { capabilities = listOf(MixerCapability(cd, bitPerfect = true)) }

    private val offloadModes = mutableListOf<Int>()
    private val equalizer = mutableListOf<Boolean>()
    private val extras = mutableListOf<AudioOutputState>()
    private val devices = mutableListOf<OutputDevice>()
    private var timer: (() -> Unit)? = null

    private lateinit var controller: AudioOutputController
    private var settings = AudioSettings()
    private val normalization = NormalizationListener(player, { settings }, { controller.state.mode })
    private val applier =
        OutputStateApplier(
            player,
            state = { controller.state },
            volume = normalization::apply,
            equalizer = { _, on -> equalizer += on },
            extras = { extras += it },
            device = { devices += it },
        )
    private val reopener =
        AudioTrackReopener(player, { _, block ->
            timer = block
            ({ timer = null })
        })

    init {
        player.addListener(
            object : Player.Listener {
                override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                    val mode = parameters.audioOffloadPreferences.audioOffloadMode
                    if (offloadModes.lastOrNull() != mode) offloadModes += mode
                }
            },
        )
        controller =
            AudioOutputController(
                port,
                { cd },
                applier::apply,
                { false },
                reopen = reopener::reopen,
                tracksReleased = reopener::onTracksReleased,
            )
        player.setMediaItems(
            listOf(
                Track(MediaId(ServerId("s"), "t1"), "Track 1", emptyList(), 3.minutes, albumNormalizationGainDb = -6.0)
                    .toMediaItem(null)
                    .withTrackUri()!!,
            ),
        )
        applier.applyOffload(true)
    }

    private val audioOff get() = C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes

    private fun setSettings(next: AudioSettings) {
        settings = next
        controller.setSettings(next)
    }

    private fun error(cause: Exception) =
        ExoPlaybackException.createForUnexpected(RuntimeException(cause), PlaybackException.ERROR_CODE_UNSPECIFIED)

    @After
    fun release() = player.release()

    @Test
    fun `offload is set again only when it changes`() {
        setSettings(AudioSettings(equalizerOn = true))
        controller.refresh("transition")
        controller.refresh("state")
        setSettings(AudioSettings(equalizerOn = true, bypass = false))

        assertThat(offloadModes)
            .containsExactly(
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
            ).inOrder()
    }

    @Test
    fun `bit-perfect plays at full volume with the equalizer off, and both come back after`() {
        setSettings(AudioSettings(equalizerOn = true))
        assertThat(player.volume).isWithin(0.001f).of(0.501f)
        assertThat(equalizer.last()).isTrue()

        setSettings(AudioSettings(equalizerOn = true, bitPerfectUsb = true))
        assertThat(controller.state.mode).isEqualTo(AudioMode.BitPerfect)
        assertThat(player.volume).isEqualTo(1f)
        assertThat(equalizer.last()).isFalse()

        setSettings(AudioSettings(equalizerOn = true))
        assertThat(player.volume).isWithin(0.001f).of(0.501f)
        assertThat(equalizer.last()).isTrue()
    }

    @Test
    fun `screens get every state, and bit-perfect only once an AudioTrack opened on it`() {
        setSettings(AudioSettings(bitPerfectUsb = true))
        assertThat(extras.last().mode).isEqualTo(AudioMode.BitPerfect)
        assertThat(extras.last().bitPerfectConfirmed).isFalse()

        controller.onAudioTrackOpened(44_100)

        assertThat(extras.last().bitPerfectConfirmed).isTrue()
        assertThat(devices).isNotEmpty()
    }

    @Test
    fun `an AudioSink error while bit-perfect drops the preference and plays on, any other error is not ours`() {
        setSettings(AudioSettings(bitPerfectUsb = true))

        assertThat(
            applier.recover(
                error(AudioSink.WriteException(-32, Format.Builder().build(), false)),
                controller::onBitPerfectFailed,
            ),
        ).isTrue()

        assertThat(port.preferred).isNull()
        assertThat(controller.state.mode).isNotEqualTo(AudioMode.BitPerfect)
        assertThat(player.playbackState).isNotEqualTo(Player.STATE_IDLE)
        assertThat(applier.recover(error(IOException("dropped")), controller::onBitPerfectFailed)).isFalse()
    }

    @Test
    fun `turned on mid-track, the open AudioTrack is replaced and the new one is bit-perfect`() {
        player.playWhenReady = true
        controller.onAudioTrackOpened(44_100)

        setSettings(AudioSettings(bitPerfectUsb = true))

        assertThat(audioOff).isTrue()
        assertThat(player.playWhenReady).isTrue()
        assertThat(extras.last().bitPerfectConfirmed).isFalse()
        // The sink released the old track (the service's analytics listener); then a new one opens.
        controller.onAudioTrackReleased()
        assertThat(audioOff).isFalse()
        controller.onAudioTrackOpened(44_100)

        assertThat(extras.last().bitPerfectConfirmed).isTrue()
        assertThat(player.trackSelectionParameters.audioOffloadPreferences.audioOffloadMode)
            .isEqualTo(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
    }
}
