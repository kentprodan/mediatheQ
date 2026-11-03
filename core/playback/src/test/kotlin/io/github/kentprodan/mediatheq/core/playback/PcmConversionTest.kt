package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmConversionTest {
    private fun read(
        encoding: Int,
        vararg bytes: Int,
    ): List<Float> {
        val pcm = ByteBuffer.wrap(ByteArray(bytes.size) { bytes[it].toByte() }).order(ByteOrder.LITTLE_ENDIAN)
        val count = bytes.size / bytesPerSample(encoding)!!
        return FloatArray(count).also { toFloats(pcm, encoding, it, count) }.toList()
    }

    @Test
    fun `16-bit samples map to -1 to 1`() {
        assertThat(read(AudioFormat.ENCODING_PCM_16BIT, 0x00, 0x80, 0x00, 0x40, 0x00, 0x00))
            .containsExactly(-1f, 0.5f, 0f)
            .inOrder()
    }

    @Test
    fun `24-bit packed samples keep their sign (a WAV original)`() {
        assertThat(read(AudioFormat.ENCODING_PCM_24BIT_PACKED, 0x00, 0x00, 0x80, 0x00, 0x00, 0x40))
            .containsExactly(-1f, 0.5f)
            .inOrder()
    }

    @Test
    fun `32-bit, float and 8-bit samples`() {
        assertThat(read(AudioFormat.ENCODING_PCM_32BIT, 0x00, 0x00, 0x00, 0x40)).containsExactly(0.5f)
        val half = java.lang.Float.floatToIntBits(0.5f)
        assertThat(
            read(
                AudioFormat.ENCODING_PCM_FLOAT,
                half and 0xFF,
                half shr 8 and 0xFF,
                half shr 16 and 0xFF,
                half ushr 24,
            ),
        ).containsExactly(0.5f)
        assertThat(read(AudioFormat.ENCODING_PCM_8BIT, 0, 128, 192)).containsExactly(-1f, 0f, 0.5f).inOrder()
    }

    @Test
    fun `other encodings are not read`() {
        assertThat(bytesPerSample(AudioFormat.ENCODING_AC3)).isNull()
    }
}
