package io.github.kentprodan.mediatheq.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = AddToPlaylistViewModel.Factory::class)
class AddToPlaylistViewModel
    @AssistedInject
    constructor(
        @Assisted private val trackIds: List<MediaId>,
        libraryRepository: LibraryRepository,
        private val playlistRepository: PlaylistRepository,
    ) : ViewModel() {
        private val creating = MutableStateFlow(trackIds.isEmpty())
        private val busy = MutableStateFlow(false)
        private val error = MutableStateFlow<MqError?>(null)
        private val done = MutableStateFlow(false)

        val uiState: StateFlow<AddToPlaylistUiState> =
            combine(
                libraryRepository.playlists(),
                creating,
                busy,
                error,
                done,
            ) { playlists, creating, busy, error, done ->
                if (done) {
                    AddToPlaylistUiState.Done
                } else {
                    AddToPlaylistUiState.Content(playlists, creating, busy, error)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddToPlaylistUiState.Loading)

        fun onAction(action: AddToPlaylistAction) {
            if (busy.value) return
            when (action) {
                is AddToPlaylistAction.Pick -> {
                    submit { playlistRepository.add(action.playlistId, trackIds) }
                }

                AddToPlaylistAction.StartCreating -> {
                    creating.value = true
                }

                is AddToPlaylistAction.Create -> {
                    val name = action.name.trim()
                    if (name.isNotEmpty()) submit { playlistRepository.create(name, trackIds) }
                }
            }
        }

        private fun submit(call: suspend () -> MqResult<*>) {
            busy.value = true
            error.value = null
            viewModelScope.launch {
                when (val result = call()) {
                    is MqResult.Success -> done.value = true
                    is MqResult.Failure -> error.value = result.error
                }
                busy.value = false
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(trackIds: List<MediaId>): AddToPlaylistViewModel
        }
    }
