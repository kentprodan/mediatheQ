package io.github.kentprodan.mediatheq.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The open-source licenses (#70). [report] and [notices] read the build's licenses report and the libraries' NOTICE
 * files, which live in the app (ADR 0022); they are read and parsed once, off the main thread.
 */
class LicensesViewModel(
    report: () -> String,
    notices: () -> String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val openId = MutableStateFlow<String?>(null)

    // Lazily: the report is read and parsed once for the screen's life, not again after each 5 s away.
    val uiState: StateFlow<LicensesUiState> =
        combine(flow { emit(licenseEntries(report(), notices())) }.flowOn(dispatcher), openId) { entries, open ->
            LicensesUiState.Content(entries, open)
        }.stateIn(viewModelScope, SharingStarted.Lazily, LicensesUiState.Loading)

    fun onAction(action: LicensesAction) {
        when (action) {
            is LicensesAction.Toggle -> openId.update { if (it == action.id) null else action.id }
        }
    }
}
