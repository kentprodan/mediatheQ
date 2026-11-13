package io.github.kentprodan.mediatheq.feature.onboarding.appmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The app mode step after the first sign-in (ADR 0051). It is only shown when the server can have video, so Both is
 * preselected; a server without video skips it as Music.
 */
@HiltViewModel
class AppModeViewModel
    @Inject
    constructor(
        private val appMode: AppModeRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(AppModeUiState())
        val uiState: StateFlow<AppModeUiState> = state.asStateFlow()

        fun onAction(action: AppModeAction) {
            when (action) {
                is AppModeAction.Select -> state.update { it.copy(selected = action.mode) }
                AppModeAction.Continue -> viewModelScope.launch { appMode.setMode(state.value.selected) }
            }
        }
    }
