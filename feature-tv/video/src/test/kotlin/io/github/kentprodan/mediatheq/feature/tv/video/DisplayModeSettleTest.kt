package io.github.kentprodan.mediatheq.feature.tv.video

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DisplayModeSettleTest {
    private val settle = DisplayModeSettle(quietMs = 1_500, maxWaitMs = 8_000, startedAtMs = 1_000)

    @Test
    fun `each change waits for a quiet spell, never past the limit`() {
        assertThat(settle.delayAfterChange(1_000)).isEqualTo(1_500)
        assertThat(settle.delayAfterChange(8_000)).isEqualTo(1_000)
        assertThat(settle.delayAfterChange(9_500)).isEqualTo(0)
    }

    @Test
    fun `a dropped HDMI audio route is waited for, then a quiet spell after it returns`() {
        settle.noteHdmiAudio(present = true)
        assertThat(settle.sawHdmiAudioDrop).isFalse()

        settle.noteHdmiAudio(present = false)
        assertThat(settle.sawHdmiAudioDrop).isTrue()
        assertThat(settle.delayAfterChange(3_000)).isEqualTo(6_000)

        settle.noteHdmiAudio(present = true)
        assertThat(settle.delayAfterChange(5_000)).isEqualTo(1_500)
    }

    @Test
    fun `with no HDMI audio at all (Bluetooth) nothing is waited for`() {
        settle.noteHdmiAudio(present = false)

        assertThat(settle.sawHdmiAudioDrop).isFalse()
        assertThat(settle.delayAfterChange(1_000)).isEqualTo(1_500)
    }
}
