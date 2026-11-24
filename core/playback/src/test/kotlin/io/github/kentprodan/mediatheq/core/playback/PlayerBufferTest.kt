package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.exoplayer.DefaultLoadControl
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerBufferTest {
    @Test
    fun `the byte budget is a third of the heap (#626)`() {
        assertThat(targetBufferBytes(MIB * 384)).isEqualTo((MIB * 128).toInt())
    }

    @Test
    fun `a large heap is capped at 384 MiB, a tiny one keeps Media3's floor (#626)`() {
        assertThat(targetBufferBytes(MIB * 4096)).isEqualTo((MIB * 384).toInt())
        assertThat(targetBufferBytes(MIB * 6)).isEqualTo(DefaultLoadControl.DEFAULT_MIN_BUFFER_SIZE)
    }

    private companion object {
        const val MIB = 1024L * 1024
    }
}
