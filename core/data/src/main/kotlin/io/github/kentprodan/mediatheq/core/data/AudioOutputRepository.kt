package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.OutputId
import kotlinx.coroutines.flow.Flow

/** Remembered audio outputs and their profiles (#140). They survive sign-out like the other audio settings. */
interface AudioOutputRepository {
    val outputs: Flow<RememberedOutputs>

    /** Makes [id] the current output; remembers it first, named [name] with a copy of the phone's profile, if new. */
    suspend fun use(
        id: OutputId,
        name: String,
    )

    /** Sets [id]'s whole profile; `null` restores the defaults. Outputs that are not remembered are ignored. */
    suspend fun setProfile(
        id: OutputId,
        profile: OutputProfile?,
    )

    /** Sets one equalizer band of [id] (switching to [EqPreset.Custom]), clamped; other bands keep their gains. */
    suspend fun setEqBand(
        id: OutputId,
        index: Int,
        gainDb: Float,
    )

    /** Forgets [id]; the phone stays. A forgotten current output makes the phone current until the next change. */
    suspend fun forget(id: OutputId)
}
