package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
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
import io.github.kentprodan.mediatheq.core.data.AutoActionsPreferences
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
class AutoActionsControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val player: ExoPlayer =
        TestExoPlayerBuilder(context).setMediaSourceFactory(FakeMediaSourceFactory()).build()
    private var settings =
        AutoActionsPreferences(volumePause = true, bluetoothPlay = true, pauseOnTaskRemoval = true)
    private lateinit var actions: AutoActionsController

    @After
    fun release() {
        if (::actions.isInitialized) actions.release()
        player.release()
    }

    /** A queue in the player, playing or paused, then the controller starts (as in the service). */
    private fun start(
        playing: Boolean,
        queued: Boolean = true,
    ) {
        if (queued) {
            player.setMediaItem(
                Track(MediaId(ServerId("s"), "t1"), "T1", emptyList(), 3.minutes).toMediaItem(null).withTrackUri()!!,
            )
            player.prepare()
        }
        player.playWhenReady = playing
        idle()
        actions = AutoActionsController(context, audioManager, player) { settings }
        actions.start()
        idle()
    }

    private fun idle() {
        advance(player).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** What the device callback hands over (Robolectric's devices are no sinks and share one id). */
    private fun connect(vararg types: Int) {
        actions.onOutputsAdded(types.toList())
        idle()
    }

    private fun volume(level: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        context.sendBroadcast(Intent(VOLUME_CHANGED_ACTION).setPackage(context.packageName))
        idle()
    }

    @Test
    fun `connecting wired or USB headphones plays the paused queue`() {
        start(playing = false)
        connect(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        assertThat(player.playWhenReady).isTrue()

        player.pause()
        connect(AudioDeviceInfo.TYPE_USB_HEADSET)
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun `connecting a Bluetooth device plays only with its setting on`() {
        settings = settings.copy(bluetoothPlay = false)
        start(playing = false)
        connect(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        assertThat(player.playWhenReady).isFalse()

        settings = settings.copy(bluetoothPlay = true)
        connect(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun `a call-only Bluetooth output or the phone's own outputs do not play`() {
        start(playing = false)
        connect(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `nothing plays without a queue or with the setting off`() {
        start(playing = false, queued = false)
        connect(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        assertThat(player.playWhenReady).isFalse()
        assertThat(player.playbackState).isEqualTo(Player.STATE_IDLE)
    }

    @Test
    fun `headphone auto play off does nothing`() {
        settings = settings.copy(headphonePlay = false)
        start(playing = false)
        connect(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `a stopped queue is prepared and played`() {
        start(playing = false)
        player.stop()
        idle()
        connect(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        assertThat(player.playWhenReady).isTrue()
        assertThat(player.playbackState).isNotEqualTo(Player.STATE_IDLE)
    }

    @Test
    fun `volume 0 pauses and raising it plays on`() {
        volume(5)
        start(playing = true)
        volume(0)
        assertThat(player.playWhenReady).isFalse()

        volume(3)
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun `raising the volume does not play what the user paused`() {
        volume(5)
        start(playing = true)
        volume(0)
        player.play()
        player.pause()
        idle()

        volume(3)
        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `volume 0 while paused resumes nothing later`() {
        volume(5)
        start(playing = false)
        volume(0)
        volume(3)
        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `the volume setting off keeps playing at volume 0`() {
        settings = settings.copy(volumePause = false)
        volume(5)
        start(playing = true)
        volume(0)
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun `task removal pauses only with its setting on`() {
        start(playing = true)
        settings = settings.copy(pauseOnTaskRemoval = false)
        actions.on(AutoEvent.TaskRemoved)
        assertThat(player.playWhenReady).isTrue()

        settings = settings.copy(pauseOnTaskRemoval = true)
        actions.on(AutoEvent.TaskRemoved)
        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `a stop pauses instead, except a dismissed media card or with the setting off`() {
        start(playing = true)
        assertThat(actions.stopPauses(dismissal = false)).isTrue()
        assertThat(actions.stopPauses(dismissal = true)).isFalse()

        settings = settings.copy(ignoreRemoteStop = false)
        assertThat(actions.stopPauses(dismissal = false)).isFalse()
    }
}
