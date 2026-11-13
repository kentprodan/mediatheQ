package io.github.kentprodan.mediatheq.feature.tv.settings

import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.model.OutputId

/** The HDMI output whose Dolby Atmos passthrough switch Settings shows (ADR 0039). */
data class TvHdmiOutput(
    val id: OutputId,
    val name: String,
    val atmosPassthrough: Boolean,
)

/** The dialog open over the settings list. */
enum class TvSettingsDialog { Quality, SignOut }

/** Settings on TV (#243). */
sealed interface TvSettingsUiState {
    data object Loading : TvSettingsUiState

    data class Content(
        /**
         * The HDMI output the switch edits: the current one, else the last one remembered (the player names it on
         * its first play over HDMI); `null` before that.
         */
        val hdmi: TvHdmiOutput?,
        /** The streaming quality on unmetered networks, where the TV plays. */
        val quality: StreamingQuality,
        val sync: SyncStatus,
        val serverName: String?,
        val userName: String?,
        val dialog: TvSettingsDialog? = null,
    ) : TvSettingsUiState
}

sealed interface TvSettingsAction {
    /** Turns the HDMI output's Dolby Atmos passthrough on or off; the next track follows it (#241). */
    data object TogglePassthrough : TvSettingsAction

    data object ChooseQuality : TvSettingsAction

    data class SetQuality(
        val quality: StreamingQuality,
    ) : TvSettingsAction

    data object SyncNow : TvSettingsAction

    /** Asks before signing out. */
    data object AskSignOut : TvSettingsAction

    data object SignOut : TvSettingsAction

    /** Closes the open dialog. */
    data object Dismiss : TvSettingsAction
}
