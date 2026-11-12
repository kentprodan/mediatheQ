package io.github.kentprodan.mediatheq.feature.settings

/** Settings -> About -> Changelog (#151). */
sealed interface ChangelogUiState {
    data object Loading : ChangelogUiState

    /** Every version, newest first; [open] are the ones showing their sections. */
    data class Content(
        val versions: List<ChangelogVersion>,
        val open: Set<String>,
    ) : ChangelogUiState
}

sealed interface ChangelogAction {
    data class Toggle(
        val name: String,
    ) : ChangelogAction
}
