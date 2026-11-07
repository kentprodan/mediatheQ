package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.AudioOutputRepository
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.data.OutputProfile
import io.github.kentprodan.mediatheq.core.data.RememberedOutput
import io.github.kentprodan.mediatheq.core.data.RememberedOutputs
import io.github.kentprodan.mediatheq.core.data.withEqBand
import io.github.kentprodan.mediatheq.core.model.OutputId
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [AudioOutputRepository] with the same rules as the real one (new outputs copy the phone). */
class FakeAudioOutputRepository : AudioOutputRepository {
    override val outputs = MutableStateFlow(RememberedOutputs())

    override suspend fun use(
        id: OutputId,
        name: String,
    ) {
        val value = outputs.value
        val known = value.outputs.any { it.id == id }
        val list =
            when {
                id == OutputId.Phone -> value.outputs
                known -> value.outputs.map { if (it.id == id) it.copy(name = name) else it }
                else -> value.outputs + RememberedOutput(id, name, value.profileOf(OutputId.Phone))
            }
        outputs.value = RememberedOutputs(list, id)
    }

    override suspend fun setProfile(
        id: OutputId,
        profile: OutputProfile?,
    ) {
        val value = outputs.value
        outputs.value =
            value.copy(
                outputs = value.outputs.map { if (it.id == id) it.copy(profile = profile ?: OutputProfile()) else it },
            )
    }

    override suspend fun updateProfile(
        id: OutputId,
        change: (OutputProfile) -> OutputProfile,
    ) = setProfile(id, change(outputs.value.profileOf(id)))

    override suspend fun setEqBand(
        id: OutputId,
        index: Int,
        gainDb: Float,
    ) {
        val profile = outputs.value.profileOf(id)
        val gains = withEqBand(profile.eqGainsDb, index, gainDb) ?: return
        setProfile(id, profile.copy(eqPreset = EqPreset.Custom, customGainsDb = gains))
    }

    override suspend fun forget(id: OutputId) {
        if (id == OutputId.Phone) return
        val value = outputs.value
        outputs.value =
            RememberedOutputs(
                value.outputs.filter { it.id != id },
                if (value.current == id) OutputId.Phone else value.current,
            )
    }
}
