package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.sin

class WaveformBucketsTest {
    private fun sine(
        frames: Int,
        amplitude: Float,
        channels: Int = 1,
    ) = FloatArray(frames * channels) { amplitude * sin(2 * PI * 440 * (it / channels) / 44_100).toFloat() }

    /** The channel count the next [feed] uses. */
    private var channels = 1

    private fun WaveformBuckets.feed(vararg parts: FloatArray) = parts.forEach { add(FloatBuffer.wrap(it), channels) }

    @Test
    fun `silence and an empty file give a flat zero line`() {
        val silent = WaveformBuckets().also { channels = 2 }.apply { feed(FloatArray(44_100 * 2)) }

        assertThat(silent.levels()).hasSize(WaveformBuckets.LEVEL_COUNT)
        assertThat(silent.levels().toSet()).containsExactly(0f)
        assertThat(WaveformBuckets().also { channels = 1 }.levels().toSet()).containsExactly(0f)
    }

    @Test
    fun `a steady sine is flat at the top`() {
        val buckets = WaveformBuckets().also { channels = 1 }.apply { feed(sine(44_100 * 20, 0.5f)) }

        buckets.levels().forEach { assertThat(it).isWithin(0.02f).of(1f) }
    }

    @Test
    fun `a quiet half then a loud half shows both, normalized to the loud one`() {
        val frames = 2048 * 400
        val buckets =
            WaveformBuckets().also { channels = 1 }.apply {
                feed(
                    sine(frames / 2, 0.1f),
                    sine(frames / 2, 0.4f),
                )
            }

        val levels = buckets.levels()
        assertThat(levels.first()).isWithin(0.02f).of(0.25f)
        assertThat(levels.last()).isWithin(0.02f).of(1f)
    }

    @Test
    fun `clipped samples count as full scale`() {
        val clipped = WaveformBuckets().also { channels = 1 }.apply { feed(FloatArray(2048 * 200) { 3f }) }
        val full =
            WaveformBuckets().also { channels = 1 }.apply {
                feed(
                    FloatArray(2048 * 100) { 1f },
                    FloatArray(2048 * 100) { 0.5f },
                )
            }

        assertThat(clipped.levels().toSet()).containsExactly(1f)
        // Full scale against half scale: the second half is half as loud.
        assertThat(full.levels().last()).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `a track shorter than the bucket count still fills every bucket, odd tail included`() {
        // 3.5 chunks: three whole, one half chunk at a quarter of the level.
        val buckets =
            WaveformBuckets().also { channels = 1 }.apply {
                feed(
                    FloatArray(2048 * 3) { 0.8f },
                    FloatArray(1024) { 0.2f },
                )
            }

        val levels = buckets.levels()
        assertThat(levels).hasSize(WaveformBuckets.LEVEL_COUNT)
        assertThat(levels.first()).isEqualTo(1f)
        assertThat(levels.last()).isWithin(0.001f).of(0.25f)
    }

    @Test
    fun `stereo counts both channels, pieces of any size add up the same`() {
        val pcm = sine(2048 * 300, 0.3f, channels = 2)
        val whole = WaveformBuckets().also { channels = 2 }.apply { feed(pcm) }
        val pieces =
            WaveformBuckets().also { channels = 2 }.apply {
                pcm
                    .toList()
                    .chunked(
                        777,
                    ).forEach { feed(it.toFloatArray()) }
            }

        assertThat(pieces.levels()).isEqualTo(whole.levels())
    }

    @Test
    fun `samples that are not a number count as silence`() {
        val buckets =
            WaveformBuckets().also { channels = 1 }.apply {
                feed(
                    FloatArray(2048 * 200) {
                        if (it % 2 ==
                            0
                        ) {
                            Float.NaN
                        } else {
                            0.5f
                        }
                    },
                )
            }

        buckets.levels().forEach { assertThat(it).isEqualTo(1f) }
    }

    @Test
    fun `a switch from mono to stereo keeps chunks the same length in time`() {
        val buckets = WaveformBuckets()
        // 100 chunks of mono, then 100 chunks of stereo at the same level: 200 equal buckets.
        buckets.add(FloatBuffer.wrap(FloatArray(2048 * 100) { 0.5f }), 1)
        buckets.add(FloatBuffer.wrap(FloatArray(2048 * 100 * 2) { 0.5f }), 2)

        assertThat(buckets.levels().toSet()).containsExactly(1f)
    }
}
