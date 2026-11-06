package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AudioFocusPreferences
import io.github.kentprodan.mediatheq.core.data.ShortFocusAction
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioFocusControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val player: ExoPlayer =
        TestExoPlayerBuilder(context).setMediaSourceFactory(FakeMediaSourceFactory()).build()
    private var settings = AudioFocusPreferences()
    private var bitPerfect = false
    private val ducks = mutableListOf<Boolean>()
    private val focus = AudioFocusController(audioManager, player, { settings }, { bitPerfect }) { ducks += it }

    @After
    fun release() = player.release()

    private fun play() {
        player.addListener(focus)
        player.setMediaItem(
            Track(MediaId(ServerId("s"), "t1"), "T1", emptyList(), 3.minutes).toMediaItem(null).withTrackUri()!!,
        )
        player.prepare()
        player.play()
        advance(player).untilPendingCommandsAreFullyHandled()
    }

    /** The system's change, through the listener of the last request (Robolectric sends none itself). */
    private fun focusChange(change: Int) {
        shadowOf(audioManager).lastAudioFocusRequest.listener.onAudioFocusChange(change)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `the decision table`() {
        val duck = AudioFocusPreferences()
        val pause = AudioFocusPreferences(ShortFocusAction.Pause)
        val nothing = AudioFocusPreferences(ShortFocusAction.Nothing)
        val noResume = AudioFocusPreferences(noResumeAfterLongLoss = true)
        val canDuck = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK

        assertThat(focusReaction(canDuck, duck, bitPerfect = false)).isEqualTo(FocusReaction.Duck)
        assertThat(focusReaction(canDuck, duck, bitPerfect = true)).isEqualTo(FocusReaction.Pause(resume = true))
        assertThat(focusReaction(canDuck, pause, bitPerfect = false)).isEqualTo(FocusReaction.Pause(resume = true))
        assertThat(focusReaction(canDuck, nothing, bitPerfect = false)).isEqualTo(FocusReaction.Nothing)
        assertThat(focusReaction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, duck, false))
            .isEqualTo(FocusReaction.Pause(resume = true))
        assertThat(focusReaction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, noResume, false))
            .isEqualTo(FocusReaction.Pause(resume = false))
        assertThat(focusReaction(AudioManager.AUDIOFOCUS_LOSS, duck, false)).isEqualTo(FocusReaction.Pause(false))
        assertThat(focusReaction(AudioManager.AUDIOFOCUS_GAIN, duck, false)).isEqualTo(FocusReaction.Regain)
    }

    @Test
    fun `playing asks for focus that never lets the system duck by itself`() {
        play()

        val request = shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest
        assertThat(request.willPauseWhenDucked()).isTrue()
        assertThat(request.acceptsDelayedFocusGain()).isTrue()
    }

    @Test
    fun `a short interruption ducks and comes back to full volume`() {
        play()

        focusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertThat(ducks).containsExactly(true)
        assertThat(player.playWhenReady).isTrue()

        focusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertThat(ducks).containsExactly(true, false).inOrder()
    }

    @Test
    fun `a short interruption pauses and plays on, or is ignored`() {
        settings = AudioFocusPreferences(ShortFocusAction.Pause)
        play()
        focusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertThat(player.playWhenReady).isFalse()
        focusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertThat(player.playWhenReady).isTrue()

        settings = AudioFocusPreferences(ShortFocusAction.Nothing)
        focusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertThat(player.playWhenReady).isTrue()
        assertThat(ducks).isEmpty()
    }

    @Test
    fun `a call pauses and plays on afterwards, unless that is turned off`() {
        play()
        focusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertThat(player.playWhenReady).isFalse()
        focusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertThat(player.playWhenReady).isTrue()

        settings = AudioFocusPreferences(noResumeAfterLongLoss = true)
        focusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        focusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `pressing pause during a call keeps the music paused when it ends`() {
        play()
        focusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        player.play()
        player.pause()
        focusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `another player taking the focus pauses for good and gives the focus back`() {
        play()

        focusChange(AudioManager.AUDIOFOCUS_LOSS)

        assertThat(player.playWhenReady).isFalse()
        assertThat(shadowOf(audioManager).lastAbandonedAudioFocusRequest).isNotNull()
    }

    @Test
    fun `a stop gives the focus back`() {
        play()

        player.stop()
        advance(player).untilPendingCommandsAreFullyHandled()

        assertThat(shadowOf(audioManager).lastAbandonedAudioFocusRequest).isNotNull()
    }

    @Test
    fun `a new queue after the end asks for focus again`() {
        play()
        player.seekTo(player.duration)
        advance(player).untilState(Player.STATE_ENDED)
        assertThat(shadowOf(audioManager).lastAbandonedAudioFocusRequest).isNotNull()

        // Refused, so a request shows as a pause: playWhenReady itself never changed.
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        player.setMediaItem(
            Track(MediaId(ServerId("s"), "t2"), "T2", emptyList(), 3.minutes).toMediaItem(null).withTrackUri()!!,
        )
        player.prepare()
        advance(player).untilPendingCommandsAreFullyHandled()

        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `refused focus does not play, delayed focus plays when it comes`() {
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        play()
        assertThat(player.playWhenReady).isFalse()

        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
        player.play()
        assertThat(player.playWhenReady).isFalse()
        focusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertThat(player.playWhenReady).isTrue()
    }
}
