package io.github.kentprodan.mediatheq.feature.settings

import io.github.kentprodan.mediatheq.core.common.LicenseEntry

/** What the licenses screen shows. */
sealed interface LicensesUiState {
    /** The build's report is being read (a few milliseconds). */
    data object Loading : LicensesUiState

    /** Every entry; [openId] is the one whose license text shows. */
    data class Content(
        val entries: List<LicenseEntry>,
        val openId: String? = null,
    ) : LicensesUiState
}

sealed interface LicensesAction {
    /** Shows the license of [id], or hides it when it is the open one. */
    data class Toggle(
        val id: String,
    ) : LicensesAction
}
