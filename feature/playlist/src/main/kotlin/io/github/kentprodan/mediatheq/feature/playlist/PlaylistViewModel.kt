package io.github.kentprodan.mediatheq.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlaylistViewModel.Factory::class)
class PlaylistViewModel
    @AssistedInject
    constructor(
        @Assisted private val playlistId: MediaId,
        private val repository: PlaylistRepository,
        private val playback: PlaybackConnection,
        private val userData: UserDataRepository,
    ) : ViewModel() {
        private val selected = MutableStateFlow(emptySet<String>())
        private val error = MutableStateFlow<MqError?>(null)
        private val deleted = MutableStateFlow(false)

        val uiState: StateFlow<PlaylistUiState> =
            combine(
                repository.playlist(playlistId),
                repository.items(playlistId),
                // Only the track id: position ticks and play/pause must not rebuild the list.
                playback.state.map { it.item?.id }.distinctUntilChanged(),
                combine(selected, error, deleted, ::Triple),
            ) { playlist, items, currentTrackId, (selected, error, deleted) ->
                when {
                    deleted -> {
                        PlaylistUiState.Deleted
                    }

                    playlist == null -> {
                        PlaylistUiState.NotFound
                    }

                    else -> {
                        // An edit may have removed selected items meanwhile.
                        val entryIds = items.mapTo(HashSet()) { it.entryId }
                        PlaylistUiState.Content(playlist, items, currentTrackId, selected intersect entryIds, error)
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistUiState.Loading)

        init {
            // Edits made elsewhere (or a read-back that failed) show up when the screen opens.
            viewModelScope.launch { repository.refresh(playlistId) }
        }

        fun onAction(action: PlaylistAction) {
            val content = uiState.value as? PlaylistUiState.Content ?: return
            val items = content.items

            fun item(position: Int) = items.firstOrNull { it.position == position }
            when (action) {
                PlaylistAction.Play -> {
                    if (items.isNotEmpty()) playback.play(items.map { it.track })
                }

                PlaylistAction.Shuffle -> {
                    if (items.isNotEmpty()) playback.play(items.map { it.track }, shuffle = true)
                }

                is PlaylistAction.PlayItem -> {
                    val index = items.indexOfFirst { it.position == action.position }
                    if (index >= 0) playback.play(items.map { it.track }, startIndex = index)
                }

                is PlaylistAction.PlayNext -> {
                    item(action.position)?.let { playback.playNext(listOf(it.track)) }
                }

                is PlaylistAction.AddToQueue -> {
                    item(action.position)?.let { playback.addToQueue(listOf(it.track)) }
                }

                is PlaylistAction.SetFavorite -> {
                    val track = item(action.position)?.track ?: return
                    viewModelScope.launch { userData.setFavorite(track.id, action.isFavorite) }
                }

                is PlaylistAction.ToggleSelection -> {
                    val entryId = item(action.position)?.takeUnless { it.isPending }?.entryId
                    if (entryId != null) selected.update { if (entryId in it) it - entryId else it + entryId }
                }

                PlaylistAction.ClearSelection -> {
                    selected.value = emptySet()
                }

                PlaylistAction.RemoveSelected -> {
                    val entries = items.filter { it.entryId in content.selected }
                    selected.value = emptySet()
                    remove(content, entries)
                }

                is PlaylistAction.Remove -> {
                    remove(content, listOfNotNull(item(action.position)))
                }

                is PlaylistAction.Move -> {
                    val moved = item(action.position)
                    val target = item(action.targetPosition)
                    if (content.canEdit && moved != null && target != null && moved != target) {
                        edit { repository.move(playlistId, moved, target) }
                    }
                }

                is PlaylistAction.Rename -> {
                    val name = action.name.trim()
                    if (name.isNotEmpty() && name != content.playlist.name) edit { repository.rename(playlistId, name) }
                }

                PlaylistAction.Delete -> {
                    edit { repository.delete(playlistId).also { if (it is MqResult.Success) deleted.value = true } }
                }

                PlaylistAction.DismissError -> {
                    error.value = null
                }
            }
        }

        private fun remove(
            content: PlaylistUiState.Content,
            entries: List<PlaylistItem>,
        ) {
            if (!content.canEdit || entries.isEmpty()) return
            edit { repository.remove(playlistId, entries.mapTo(HashSet()) { it.entryId }) }
        }

        private fun edit(call: suspend () -> MqResult<Unit>) {
            viewModelScope.launch {
                val result = call()
                if (result is MqResult.Failure) error.value = result.error
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(playlistId: MediaId): PlaylistViewModel
        }
    }
