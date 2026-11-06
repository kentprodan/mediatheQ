package io.github.kentprodan.mediatheq.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AudioOutputRepository
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Output settings and one output's page (#140): the remembered outputs and their profiles. The current output is the
 * one last used, also while nothing plays; a change applies at once when it is the one playing.
 */
@HiltViewModel
class OutputSettingsViewModel
    @Inject
    constructor(
        private val outputs: AudioOutputRepository,
        playback: PlaybackConnection,
    ) : ViewModel() {
        val uiState: StateFlow<OutputSettingsUiState> =
            combine(outputs.outputs, playback.state.map { it.output }.distinctUntilChanged()) { remembered, info ->
                OutputSettingsUiState.Content(remembered.outputs, remembered.current, info)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutputSettingsUiState.Loading)

        fun onAction(action: OutputSettingsAction) {
            viewModelScope.launch {
                when (action) {
                    is OutputSettingsAction.SetProfile -> outputs.setProfile(action.id, action.profile)
                    is OutputSettingsAction.SetEqBand -> outputs.setEqBand(action.id, action.index, action.gainDb)
                    is OutputSettingsAction.Forget -> outputs.forget(action.id)
                }
            }
        }
    }
