package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.withEqBand
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [AudioPreferencesRepository] with the same rules as the real one. */
class FakeAudioPreferencesRepository : AudioPreferencesRepository {
    override val preferences = MutableStateFlow(AudioPreferences())

    override suspend fun setBitPerfectUsb(on: Boolean) {
        preferences.value = preferences.value.copy(bitPerfectUsb = on)
    }

    override suspend fun setEqualizerOn(on: Boolean) {
        preferences.value = preferences.value.copy(equalizerOn = on)
    }

    override suspend fun setEqPreset(preset: EqPreset) {
        preferences.value = preferences.value.copy(eqPreset = preset)
    }

    override suspend fun setNormalization(normalization: Normalization) {
        preferences.value = preferences.value.copy(normalization = normalization)
    }

    override suspend fun setStreamingQuality(
        metered: Boolean,
        quality: StreamingQuality,
    ) {
        preferences.value =
            if (metered) {
                preferences.value.copy(meteredQuality = quality)
            } else {
                preferences.value.copy(unmeteredQuality = quality)
            }
    }

    override suspend fun setStreamingCacheGb(gb: Int) {
        if (gb in AudioPreferences.CACHE_SIZES_GB) preferences.value = preferences.value.copy(streamingCacheGb = gb)
    }

    override suspend fun setDownloadsUnmeteredOnly(on: Boolean) {
        preferences.value = preferences.value.copy(downloadsUnmeteredOnly = on)
    }

    override suspend fun setEqBand(
        index: Int,
        gainDb: Float,
    ) {
        val gains = withEqBand(preferences.value.eqGainsDb, index, gainDb) ?: return
        preferences.value = preferences.value.copy(eqPreset = EqPreset.Custom, customGainsDb = gains)
    }
}
