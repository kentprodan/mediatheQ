package io.github.kentprodan.mediatheq.feature.settings

import io.github.kentprodan.mediatheq.core.data.OutputProfile
import io.github.kentprodan.mediatheq.core.data.RememberedOutput
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo

/** Settings -> Playback -> Output settings and one output's page (#140). */
sealed interface OutputSettingsUiState {
    data object Loading : OutputSettingsUiState

    data class Content(
        /** The phone first, then the others by name. */
        val outputs: List<RememberedOutput>,
        val current: OutputId,
        /** What the output does now (bit-perfect, its reason): only about [current]. */
        val info: AudioOutputInfo = AudioOutputInfo(),
    ) : OutputSettingsUiState {
        /** The output [id] names, the current one for `null`; `null` when it was forgotten. */
        fun output(id: OutputId?): RememberedOutput? = outputs.firstOrNull { it.id == (id ?: current) }
    }
}

sealed interface OutputSettingsAction {
    /** Sets [id]'s whole profile; `null` restores its defaults. */
    data class SetProfile(
        val id: OutputId,
        val profile: OutputProfile?,
    ) : OutputSettingsAction

    /** Changes [id]'s profile from its stored value (one setting); the repository applies it to the stored profile. */
    data class UpdateProfile(
        val id: OutputId,
        val change: (OutputProfile) -> OutputProfile,
    ) : OutputSettingsAction

    data class SetEqBand(
        val id: OutputId,
        val index: Int,
        val gainDb: Float,
    ) : OutputSettingsAction

    data class Forget(
        val id: OutputId,
    ) : OutputSettingsAction
}
