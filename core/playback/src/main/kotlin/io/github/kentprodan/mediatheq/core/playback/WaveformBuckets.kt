package io.github.kentprodan.mediatheq.core.playback

import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Folds decoded float PCM into a waveform (#124, ADR 0027): the loudness (RMS) of each of `count` equal parts of the
 * track, normalized to the loudest part. Keeps one sum per small chunk of frames while decoding, so it never needs the
 * track's length up front (unreliable for VBR MP3 and Opus) nor the whole PCM in memory. The channel count may change
 * between buffers (a decoder switching from mono to stereo); chunks stay the same length in time.
 */
internal class WaveformBuckets(
    private val chunkFrames: Int = CHUNK_FRAMES,
) {
    private val chunkSums = ArrayList<Double>()
    private val chunkSamples = ArrayList<Int>()
    private var sum = 0.0
    private var samples = 0
    private var frames = 0
    private var channel = 0

    /**
     * Adds the remaining samples of [pcm], interleaved [channels]. Values beyond -1..1 (a clipped master) count as full
     * scale; a sample that is not a number (a broken decode) counts as silence.
     */
    fun add(
        pcm: FloatBuffer,
        channels: Int,
    ) {
        require(channels > 0) { "channels must be positive" }
        while (pcm.hasRemaining()) {
            val raw = pcm.get()
            val sample = if (raw.isNaN()) 0f else raw.coerceIn(-1f, 1f)
            sum += sample * sample
            samples++
            if (++channel >= channels) {
                channel = 0
                if (++frames == chunkFrames) {
                    chunkSums += sum
                    chunkSamples += samples
                    sum = 0.0
                    samples = 0
                    frames = 0
                }
            }
        }
    }

    /**
     * The waveform: [count] levels in play order, 0..1, the loudest 1. Silence (or nothing decoded) is all 0. A track
     * with fewer chunks than [count] repeats its chunks; the last, partial chunk counts by its own samples.
     */
    fun levels(count: Int = LEVEL_COUNT): List<Float> {
        val sums = if (samples > 0) chunkSums + sum else chunkSums
        val counts = if (samples > 0) chunkSamples + samples else chunkSamples
        if (sums.isEmpty()) return List(count) { 0f }
        val rms =
            List(count) { bucket ->
                val start = bucket * sums.size / count
                val end = maxOf(start + 1, (bucket + 1) * sums.size / count)
                sqrt(sums.subList(start, end).sum() / counts.subList(start, end).sum())
            }
        val loudest = rms.max()
        return if (loudest == 0.0) List(count) { 0f } else rms.map { (it / loudest).toFloat() }
    }

    companion object {
        /** The values kept per track. */
        const val LEVEL_COUNT = 200

        /** About 46 ms at 44.1 kHz: a track over ~9 s fills every one of the 200 buckets with its own chunks. */
        const val CHUNK_FRAMES = 2048
    }
}
