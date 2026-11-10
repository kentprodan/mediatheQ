package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioDeviceInfo
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AutoActionsPreferences
import org.junit.Test

class AutoActionsTest {
    private val allOn =
        AutoActionsPreferences(
            volumePause = true,
            headphonePlay = true,
            bluetoothPlay = true,
            pauseOnTaskRemoval = true,
            ignoreRemoteStop = true,
        )
    private val allOff =
        AutoActionsPreferences(
            volumePause = false,
            headphonePlay = false,
            bluetoothPlay = false,
            pauseOnTaskRemoval = false,
            ignoreRemoteStop = false,
        )
    private val paused = AutoState(playing = false, hasQueue = true)
    private val playing = AutoState(playing = true, hasQueue = true)
    private val empty = AutoState(playing = false, hasQueue = false)

    @Test
    fun `with every setting off no event does anything`() {
        for (event in AutoEvent.entries) {
            for (state in listOf(paused, playing, empty, paused.copy(pausedByVolume = true))) {
                assertThat(autoAction(event, allOff, state)).isEqualTo(AutoAction.Nothing)
            }
        }
    }

    @Test
    fun `headphones play a paused queue, only with their own setting`() {
        val event = AutoEvent.HeadphonesConnected
        assertThat(autoAction(event, allOn, paused)).isEqualTo(AutoAction.Play)
        assertThat(autoAction(event, allOn, playing)).isEqualTo(AutoAction.Nothing)
        assertThat(autoAction(event, allOn, empty)).isEqualTo(AutoAction.Nothing)
        assertThat(autoAction(event, allOff.copy(bluetoothPlay = true), paused)).isEqualTo(AutoAction.Nothing)
        assertThat(autoAction(event, AutoActionsPreferences(), paused)).isEqualTo(AutoAction.Play)
    }

    @Test
    fun `Bluetooth plays a paused queue, only with its own setting`() {
        val event = AutoEvent.BluetoothConnected
        assertThat(autoAction(event, allOn, paused)).isEqualTo(AutoAction.Play)
        assertThat(autoAction(event, allOn, playing)).isEqualTo(AutoAction.Nothing)
        assertThat(autoAction(event, allOn, empty)).isEqualTo(AutoAction.Nothing)
        assertThat(autoAction(event, allOff.copy(headphonePlay = true), paused)).isEqualTo(AutoAction.Nothing)
        assertThat(autoAction(event, AutoActionsPreferences(), paused)).isEqualTo(AutoAction.Nothing)
    }

    @Test
    fun `volume 0 pauses what plays`() {
        assertThat(autoAction(AutoEvent.VolumeZero, allOn, playing)).isEqualTo(AutoAction.Pause)
        assertThat(autoAction(AutoEvent.VolumeZero, allOn, paused)).isEqualTo(AutoAction.Nothing)
    }

    @Test
    fun `raising the volume plays only what volume 0 paused`() {
        val byVolume = paused.copy(pausedByVolume = true)
        assertThat(autoAction(AutoEvent.VolumeRaised, allOn, byVolume)).isEqualTo(AutoAction.Play)
        assertThat(autoAction(AutoEvent.VolumeRaised, allOn, paused)).isEqualTo(AutoAction.Nothing)
        assertThat(autoAction(AutoEvent.VolumeRaised, allOn, playing)).isEqualTo(AutoAction.Nothing)
        assertThat(autoAction(AutoEvent.VolumeRaised, allOn, empty.copy(pausedByVolume = true)))
            .isEqualTo(AutoAction.Nothing)
    }

    @Test
    fun `task removal pauses what plays`() {
        assertThat(autoAction(AutoEvent.TaskRemoved, allOn, playing)).isEqualTo(AutoAction.Pause)
        assertThat(autoAction(AutoEvent.TaskRemoved, allOn, paused)).isEqualTo(AutoAction.Nothing)
        assertThat(autoAction(AutoEvent.TaskRemoved, AutoActionsPreferences(), playing)).isEqualTo(AutoAction.Nothing)
    }

    @Test
    fun `a remote stop becomes a pause in any state, by default`() {
        for (state in listOf(paused, playing, empty)) {
            assertThat(autoAction(AutoEvent.RemoteStop, AutoActionsPreferences(), state)).isEqualTo(AutoAction.Pause)
        }
    }

    @Test
    fun `added devices map to their event`() {
        assertThat(connectEvent(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)).isEqualTo(AutoEvent.HeadphonesConnected)
        assertThat(connectEvent(AudioDeviceInfo.TYPE_WIRED_HEADSET)).isEqualTo(AutoEvent.HeadphonesConnected)
        assertThat(connectEvent(AudioDeviceInfo.TYPE_USB_HEADSET)).isEqualTo(AutoEvent.HeadphonesConnected)
        assertThat(connectEvent(AudioDeviceInfo.TYPE_USB_DEVICE)).isEqualTo(AutoEvent.HeadphonesConnected)
        assertThat(connectEvent(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)).isEqualTo(AutoEvent.BluetoothConnected)
        assertThat(connectEvent(AudioDeviceInfo.TYPE_BLE_HEADSET)).isEqualTo(AutoEvent.BluetoothConnected)
        assertThat(connectEvent(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)).isNull()
        assertThat(connectEvent(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)).isNull()
        assertThat(connectEvent(AudioDeviceInfo.TYPE_HDMI)).isNull()
    }
}
