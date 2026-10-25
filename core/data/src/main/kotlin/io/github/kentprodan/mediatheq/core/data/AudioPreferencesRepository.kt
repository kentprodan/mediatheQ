package io.github.kentprodan.mediatheq.core.data

import kotlinx.coroutines.flow.Flow

/** Audio preferences (bit-perfect USB, equalizer), kept across restarts. */
interface AudioPreferencesRepository {
    val preferences: Flow<AudioPreferences>

    suspend fun setBitPerfectUsb(on: Boolean)

    suspend fun setEqualizerOn(on: Boolean)

    suspend fun setEqPreset(preset: EqPreset)

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
