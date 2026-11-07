package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.AudioOutputStore
import io.github.kentprodan.mediatheq.core.datastore.StoredOutputProfile
import io.github.kentprodan.mediatheq.core.model.OutputId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAudioOutputRepository
    @Inject
    constructor(
        private val store: AudioOutputStore,
    ) : AudioOutputRepository {
        // updateProfile reads the profile, then writes it: one at a time.
        private val lock = Mutex()

        override val outputs: Flow<RememberedOutputs> =
            store.values.map { stored ->
                val outputs =
                    stored.outputs
                        .mapNotNull { output ->
                            OutputId.parseOrNull(output.id)?.let {
                                RememberedOutput(it, output.name.orEmpty(), profile(output.profile))
                            }
                        }.sortedWith(compareBy({ it.id != OutputId.Phone }, { it.name.lowercase() }))
                val current =
                    stored.current
                        ?.let(OutputId::parseOrNull)
                        ?.takeIf { id -> outputs.any { it.id == id } } ?: OutputId.Phone
                RememberedOutputs(outputs, current)
            }

        override suspend fun use(
            id: OutputId,
            name: String,
        ) = store.use(id.toString(), name)

        override suspend fun setProfile(
            id: OutputId,
            profile: OutputProfile?,
        ) = store.setProfile(id.toString(), profile?.toStored() ?: StoredOutputProfile())

        override suspend fun updateProfile(
            id: OutputId,
            change: (OutputProfile) -> OutputProfile,
        ) {
            lock.withLock { setProfile(id, change(outputs.first().profileOf(id))) }
        }

        override suspend fun setEqBand(
            id: OutputId,
            index: Int,
            gainDb: Float,
        ) = updateProfile(id) { profile ->
            withEqBand(
                profile.eqGainsDb,
                index,
                gainDb,
            )?.let { profile.copy(eqPreset = EqPreset.Custom, customGainsDb = it) }
                ?: profile
        }

        override suspend fun forget(id: OutputId) = store.forget(id.toString())

        private fun profile(stored: StoredOutputProfile): OutputProfile {
            val defaults = OutputProfile()
            return OutputProfile(
                normalization = enumOrNull<Normalization>(stored.normalization) ?: defaults.normalization,
                bypass = stored.bypass ?: defaults.bypass,
                equalizerOn = stored.equalizerOn ?: defaults.equalizerOn,
                eqPreset = enumOrNull<EqPreset>(stored.eqPreset) ?: defaults.eqPreset,
                customGainsDb = stored.eqBands?.toGains() ?: defaults.customGainsDb,
                bitPerfectUsb = stored.bitPerfectUsb ?: defaults.bitPerfectUsb,
            )
        }

        private fun OutputProfile.toStored() =
            StoredOutputProfile(
                normalization = normalization.name,
                bypass = bypass,
                equalizerOn = equalizerOn,
                eqPreset = eqPreset.name,
                eqBands = customGainsDb.joinToString(","),
                bitPerfectUsb = bitPerfectUsb,
            )
    }
