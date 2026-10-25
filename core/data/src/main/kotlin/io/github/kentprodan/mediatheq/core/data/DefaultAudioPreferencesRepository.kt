package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.AudioPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAudioPreferencesRepository
    @Inject
    constructor(
        private val store: AudioPreferencesStore,
    ) : AudioPreferencesRepository {
        // setEqBand reads the gains, then writes them: one at a time.
        private val eqLock = Mutex()

        override val preferences: Flow<AudioPreferences> =
            store.values.map { stored ->
                val defaults = AudioPreferences()
                AudioPreferences(
                    bitPerfectUsb = stored.bitPerfectUsb ?: defaults.bitPerfectUsb,
                    equalizerOn = stored.equalizerOn ?: defaults.equalizerOn,
                    eqPreset = EqPreset.entries.firstOrNull { it.name == stored.eqPreset } ?: defaults.eqPreset,
                    customGainsDb = stored.eqBands?.toGains() ?: defaults.customGainsDb,
                    normalization = enumOrNull<Normalization>(stored.normalization) ?: defaults.normalization,
                    unmeteredQuality =
                        enumOrNull<StreamingQuality>(stored.unmeteredQuality) ?: defaults.unmeteredQuality,
                    meteredQuality = enumOrNull<StreamingQuality>(stored.meteredQuality) ?: defaults.meteredQuality,
                    streamingCacheGb =
                        stored.streamingCacheGb?.takeIf { it in AudioPreferences.CACHE_SIZES_GB }
                            ?: defaults.streamingCacheGb,
                    downloadsUnmeteredOnly = stored.downloadsUnmeteredOnly ?: defaults.downloadsUnmeteredOnly,
                )
            }

        override suspend fun setBitPerfectUsb(on: Boolean) = store.setBitPerfectUsb(on)

        override suspend fun setEqualizerOn(on: Boolean) = store.setEqualizerOn(on)

        override suspend fun setEqPreset(preset: EqPreset) = store.setEq(preset.name, bands = null)

        override suspend fun setNormalization(normalization: Normalization) = store.setNormalization(normalization.name)

        override suspend fun setStreamingQuality(
            metered: Boolean,
            quality: StreamingQuality,
        ) = store.setStreamingQuality(metered, quality.name)

        override suspend fun setStreamingCacheGb(gb: Int) {
            if (gb in AudioPreferences.CACHE_SIZES_GB) store.setStreamingCacheGb(gb)
        }

        override suspend fun setDownloadsUnmeteredOnly(on: Boolean) = store.setDownloadsUnmeteredOnly(on)

        override suspend fun setEqBand(
            index: Int,
            gainDb: Float,
        ) {
            eqLock.withLock {
                val gains = withEqBand(preferences.first().eqGainsDb, index, gainDb) ?: return
                store.setEq(EqPreset.Custom.name, gains.joinToString(","))
            }
        }

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
            enumValues<E>().firstOrNull { it.name == name }

        // Anything but one valid gain per band is dropped: an old or damaged value means the defaults.
        private fun String.toGains(): List<Float>? =
            split(",")
                .map { it.toFloatOrNull()?.takeIf { gain -> gain in AudioPreferences.EQ_GAIN_DB } ?: return null }
                .takeIf { it.size == AudioPreferences.EQ_BANDS_HZ.size }
    }
