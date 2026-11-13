package io.github.kentprodan.mediatheq.feature.tv.settings

import io.github.kentprodan.mediatheq.core.common.LicenseEntry

/** The open-source licenses on TV (#243): the same entries as the phone (ADR 0022). */
sealed interface TvLicensesUiState {
    /** The build's report is being read (a few milliseconds). */
    data object Loading : TvLicensesUiState

    data class Content(
        val entries: List<LicenseEntry>,
    ) : TvLicensesUiState
}
