package io.github.kentprodan.mediatheq.feature.onboarding.appmode

import io.github.kentprodan.mediatheq.core.model.AppMode

/** The "What do you use mediatheQ for?" step (ADR 0051): the card chosen so far. */
data class AppModeUiState(
    val selected: AppMode = AppMode.Both,
)

sealed interface AppModeAction {
    data class Select(
        val mode: AppMode,
    ) : AppModeAction

    /** Stores [AppModeUiState.selected]; the app then leaves the step. */
    data object Continue : AppModeAction
}
