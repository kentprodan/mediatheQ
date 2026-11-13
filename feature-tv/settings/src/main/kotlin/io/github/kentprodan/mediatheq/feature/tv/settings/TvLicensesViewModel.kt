package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kentprodan.mediatheq.core.common.licenseEntries
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The open-source licenses on TV (#243). [report] and [notices] read the TV app's licenses report and the libraries'
 * NOTICE files (ADR 0022); parsed once for the screen's life, off the main thread.
 */
class TvLicensesViewModel(
    report: () -> String,
    notices: () -> String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val uiState: StateFlow<TvLicensesUiState> =
        flow { emit(licenseEntries(report(), notices())) }
            .flowOn(dispatcher)
            .map { TvLicensesUiState.Content(it) }
            .stateIn(viewModelScope, SharingStarted.Lazily, TvLicensesUiState.Loading)
}
