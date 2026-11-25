package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioFormat
import androidx.media3.common.C
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BitPerfectOutputTest {
    private val stereo = AudioFormat.CHANNEL_OUT_STEREO
    private val dac24 = PcmFormat(C.ENCODING_PCM_24BIT, 96_000, stereo)

    @Test
    fun `16-bit or float PCM at the mixer's rate and channels opens in the mixer's encoding`() {
        assertThat(bitPerfectEncoding(C.ENCODING_PCM_FLOAT, 96_000, stereo, direct = true, dac24))
            .isEqualTo(C.ENCODING_PCM_24BIT)
        assertThat(bitPerfectEncoding(C.ENCODING_PCM_16BIT, 96_000, stereo, direct = true, dac24))
            .isEqualTo(C.ENCODING_PCM_24BIT)
    }

    @Test
    fun `anything else opens as the sink configured it`() {
        assertThat(bitPerfectEncoding(C.ENCODING_PCM_FLOAT, 96_000, stereo, direct = true, null)).isNull()
        assertThat(bitPerfectEncoding(C.ENCODING_PCM_FLOAT, 48_000, stereo, direct = true, dac24)).isNull()
        assertThat(bitPerfectEncoding(C.ENCODING_PCM_FLOAT, 96_000, AudioFormat.CHANNEL_OUT_MONO, true, dac24)).isNull()
        assertThat(bitPerfectEncoding(C.ENCODING_PCM_FLOAT, 96_000, stereo, direct = false, dac24)).isNull()
        assertThat(bitPerfectEncoding(C.ENCODING_E_AC3, 96_000, stereo, direct = true, dac24)).isNull()
        val dac16 = dac24.copy(encoding = C.ENCODING_PCM_16BIT)
        assertThat(bitPerfectEncoding(C.ENCODING_PCM_16BIT, 96_000, stereo, direct = true, dac16)).isNull()
    }

    @Test
    fun `float holding 24-bit samples reaches a 24-bit container unchanged`() {
        val samples = intArrayOf(0, 1, -1, 8_388_607, -8_388_608, 1_234_567)
        val input = buffer(samples.size * 4) { samples.forEach { putFloat(it / 8_388_608f) } }

        val out = convert(input, C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, samples.size * 3)

        assertThat(
            (0 until samples.size).map { read24(out, it * 3) },
        ).containsExactlyElementsIn(samples.toList()).inOrder()
        assertThat(input.position()).isEqualTo(0)
    }

    @Test
    fun `16-bit samples move to the top of a 32-bit container, and float holding them back to 16 bits`() {
        val input =
            buffer(4) {
                putShort(-32_768)
                putShort(12_345)
            }

        val out = convert(input, C.ENCODING_PCM_16BIT, C.ENCODING_PCM_32BIT, 8)
        assertThat(out.getInt(0)).isEqualTo(-32_768 shl 16)
        assertThat(out.getInt(4)).isEqualTo(12_345 shl 16)

        val floats =
            buffer(8) {
                putFloat(-1f)
                putFloat(12_345 / 32_768f)
            }
        val back = convert(floats, C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_16BIT, 4)
        assertThat(back.getShort(0)).isEqualTo((-32_768).toShort())
        assertThat(back.getShort(2)).isEqualTo(12_345.toShort())
    }

    @Test
    fun `full-scale float is clipped to the container`() {
        val out = convert(buffer(4) { putFloat(1f) }, C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, 3)

        assertThat(read24(out, 0)).isEqualTo(8_388_607)
    }

    @Test
    fun `rates above 192 kHz are halved until they fit, the others untouched`() {
        assertThat(cappedRate(384_000)).isEqualTo(192_000)
        assertThat(cappedRate(352_800)).isEqualTo(176_400)
        assertThat(cappedRate(768_000)).isEqualTo(192_000)
        assertThat(cappedRate(192_000)).isNull()
        assertThat(cappedRate(44_100)).isNull()
    }

    private fun buffer(
        size: Int,
        fill: ByteBuffer.() -> Unit,
    ) = ByteBuffer
        .allocate(size)
        .order(ByteOrder.nativeOrder())
        .apply(fill)
        .apply { flip() }

    private fun convert(
        input: ByteBuffer,
        from: Int,
        to: Int,
        size: Int,
    ): ByteBuffer {
        val out = ByteBuffer.allocate(size)
        pcmToContainer(input, from, out, to)
        assertThat(out.position()).isEqualTo(size)
        return out
    }

    private fun read24(
        out: ByteBuffer,
        at: Int,
    ): Int =
        (
            (out.get(at).toInt() and 0xFF) or ((out.get(at + 1).toInt() and 0xFF) shl 8) or
                (out.get(at + 2).toInt() shl 16)
        )

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test
    fun `the rate cap flushes like any processor, so tracks play (every track failed before)`() {
        val cap = HiResRateCap()
        cap.configure(
            androidx.media3.common.audio.AudioProcessor.AudioFormat(
                48_000,
                2,
                androidx.media3.common.C.ENCODING_PCM_16BIT,
            ),
        )

        cap.flush(
            androidx.media3.common.audio.AudioProcessor
                .StreamMetadata(androidx.media3.common.C.TIME_UNSET),
        )
    }
}
