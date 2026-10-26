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
 * The open-source licenses (#70). [report] reads the build's licenses report, which lives in the app (ADR 0022); it is
 * read and parsed once, off the main thread.
 */
class LicensesViewModel(
    report: () -> String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val openId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LicensesUiState> =
        combine(flow { emit(licenseEntries(report())) }.flowOn(dispatcher), openId) { entries, open ->
            LicensesUiState.Content(entries, open)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LicensesUiState.Loading)

    fun onAction(action: LicensesAction) {
        when (action) {
            is LicensesAction.Toggle -> openId.update { if (it == action.id) null else action.id }
        }
    }
}
