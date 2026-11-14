package io.github.kentprodan.mediatheq.feature.video

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The PiP set-up and the leave rule (#278). */
class VideoPipTest {
    @Test
    fun `auto-enter only while playing with the setting on`() {
        assertThat(pipSpec(true, isPlaying = true, hasNext = false, aspectRatio = 16 / 9f).autoEnter).isTrue()
        assertThat(pipSpec(true, isPlaying = false, hasNext = false, aspectRatio = 16 / 9f).autoEnter).isFalse()
        assertThat(pipSpec(false, isPlaying = true, hasNext = false, aspectRatio = 16 / 9f).autoEnter).isFalse()
    }

    @Test
    fun `the buttons are pause or play, then next when an episode follows`() {
        assertThat(
            pipSpec(true, true, hasNext = true, 1.78f).actions,
        ).containsExactly(PipAction.Pause, PipAction.Next).inOrder()
        assertThat(pipSpec(true, false, hasNext = false, 1.78f).actions).containsExactly(PipAction.Play)
    }

    @Test
    fun `the ratio stays inside what the platform accepts, 16 to 9 while unknown`() {
        assertThat(pipSpec(true, true, false, 4f).aspectRatio).isEqualTo(2390 to 1000)
        assertThat(pipSpec(true, true, false, 0.1f).aspectRatio).isEqualTo(418 to 1000)
        assertThat(pipSpec(true, true, false, null).aspectRatio).isEqualTo(1777 to 1000)
    }

    @Test
    fun `Back pauses unless keep audio is on`() {
        assertThat(leavePauses(keepAudio = false)).isTrue()
        assertThat(leavePauses(keepAudio = true)).isFalse()
    }
}
