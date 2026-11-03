package io.github.kentprodan.mediatheq.core.playback

import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Folds decoded float PCM (interleaved [channels]) into a waveform (#124, ADR 0027): the loudness (RMS) of each of
 * `count` equal parts of the track, normalized to the loudest part. Keeps one sum per small chunk while decoding, so
 * it never needs the track's length up front (unreliable for VBR MP3 and Opus) nor the whole PCM in memory.
 */
internal class WaveformBuckets(
    private val channels: Int,
    chunkFrames: Int = CHUNK_FRAMES,
) {
    private val chunkSamples = chunkFrames * channels
    private val chunkSums = ArrayList<Double>()
    private var sum = 0.0
    private var samples = 0

    init {
        require(channels > 0) { "channels must be positive" }
    }

    /** Adds the remaining samples of [pcm] (values beyond -1..1, a clipped master, count as full scale). */
    fun add(pcm: FloatBuffer) {
        while (pcm.hasRemaining()) {
            val sample = pcm.get().coerceIn(-1f, 1f)
            sum += sample * sample
            if (++samples == chunkSamples) {
                chunkSums += sum
                sum = 0.0
                samples = 0
            }
        }
    }

    /**
     * The waveform: [count] levels in play order, 0..1, the loudest 1. Silence (or nothing decoded) is all 0. A track
     * with fewer chunks than [count] repeats its chunks; the last, partial chunk counts by its own length.
     */
    fun levels(count: Int = LEVEL_COUNT): List<Float> {
        // The partial chunk as a mean square of its own samples, scaled to a whole chunk so it weighs alike.
        val chunks = if (samples > 0) chunkSums + sum / samples * chunkSamples else chunkSums
        if (chunks.isEmpty()) return List(count) { 0f }
        val rms =
            List(count) { bucket ->
                val start = bucket * chunks.size / count
                val end = maxOf(start + 1, (bucket + 1) * chunks.size / count)
                sqrt(chunks.subList(start, end).sum() / ((end - start) * chunkSamples))
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
