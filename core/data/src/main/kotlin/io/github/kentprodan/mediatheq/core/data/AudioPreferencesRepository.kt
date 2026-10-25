package io.github.kentprodan.mediatheq.core.data

import kotlinx.coroutines.flow.Flow

/** Audio, streaming and storage preferences, kept across restarts. */
interface AudioPreferencesRepository {
    val preferences: Flow<AudioPreferences>

    suspend fun setBitPerfectUsb(on: Boolean)

    suspend fun setEqualizerOn(on: Boolean)

    suspend fun setEqPreset(preset: EqPreset)

    suspend fun setNormalization(normalization: Normalization)

    /** The quality on [metered] networks, or on unmetered ones. */
    suspend fun setStreamingQuality(
        metered: Boolean,
        quality: StreamingQuality,
    )

    /** Ignored outside [AudioPreferences.CACHE_SIZES_GB]. */
    suspend fun setStreamingCacheGb(gb: Int)

    suspend fun setDownloadsUnmeteredOnly(on: Boolean)

    /**
     * Sets band [index] to [gainDb] (clamped to [AudioPreferences.EQ_GAIN_DB]), starting from the gains heard now and
     * switching to [EqPreset.Custom]. Ignored for an index outside the bands.
     */
    suspend fun setEqBand(
        index: Int,
        gainDb: Float,
    )
}

/** [gains] with band [index] set to [gainDb], clamped; `null` for an index outside the bands. */
fun withEqBand(
    gains: List<Float>,
    index: Int,
    gainDb: Float,
): List<Float>? =
    if (index !in gains.indices) {
        null
    } else {
        gains.toMutableList().apply { this[index] = gainDb.coerceIn(AudioPreferences.EQ_GAIN_DB) }
    }
