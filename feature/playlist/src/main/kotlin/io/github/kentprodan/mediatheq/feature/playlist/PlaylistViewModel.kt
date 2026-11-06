package io.github.kentprodan.mediatheq.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.PlaylistItem
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.data.ResumeRepository
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.resumeTarget
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackCommands
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import io.github.kentprodan.mediatheq.core.ui.SelectionCommand
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
        private val trackTaps: TrackTaps,
        private val commands: TrackCommands,
        private val userData: UserDataRepository,
        private val downloads: DownloadRepository,
        private val preferences: LibraryPreferencesRepository,
        private val resumes: ResumeRepository,
        private val library: LibraryRepository,
    ) : ViewModel() {
        private val selected = MutableStateFlow(emptySet<String>())
        private val error = MutableStateFlow<MqError?>(null)
        private val deleted = MutableStateFlow(false)
        private val editing = MutableStateFlow(false)

        /** The page settings, where the playlist was left and whether it is all downloaded. */
        private val extras =
            combine(
                preferences.preferences.map { it.playlistPage }.distinctUntilChanged(),
                resumes.playlistResume(playlistId),
                library.fullyDownloaded.map { playlistId in it }.distinctUntilChanged(),
                ::Triple,
            )

        val uiState: StateFlow<PlaylistUiState> =
            combine(
                repository.playlist(playlistId),
                repository.items(playlistId),
                // Only the track id: position ticks and play/pause must not rebuild the list.
                playback.state.map { it.item?.id }.distinctUntilChanged(),
                combine(selected, error, deleted, editing) { s, e, d, ed -> Selection(s, e, d, ed) },
                extras,
            ) { playlist, items, currentTrackId, (selected, error, deleted, editing), (page, resume, downloaded) ->
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
                        PlaylistUiState.Content(
                            playlist,
                            items,
                            currentTrackId,
                            selected intersect entryIds,
                            error,
                            page,
                            // shortcut: a track in the playlist twice resumes at its first copy; entry ids if it matters.
                            // After a shuffled session "finished, so the next one" follows the playlist's order.
                            resumeTarget(resume, items.map { it.track }),
                            downloaded,
                            editing,
                        )
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
                    if (items.isNotEmpty()) playback.play(items.map { it.track }, playlistId = playlistId)
                }

                PlaylistAction.Shuffle -> {
                    if (items.isNotEmpty()) {
                        playback.play(
                            items.map { it.track },
                            shuffle = true,
                            playlistId = playlistId,
                        )
                    }
                }

                PlaylistAction.Resume -> {
                    val tracks = items.map { it.track }
                    val resume = content.resume
                    val index = tracks.indexOfFirst { it.id == resume?.trackId }
                    if (resume != null && index >= 0) {
                        playback.play(tracks, index, startPosition = resume.position, playlistId = playlistId)
                    }
                }

                // Queued from the header, not played from the playlist: they do not move its Resume.
                PlaylistAction.PlayAllNext -> {
                    playback.playNext(items.map { it.track })
                }

                PlaylistAction.AddAllToQueue -> {
                    playback.addToQueue(items.map { it.track })
                }

                is PlaylistAction.SetPlaylistFavorite -> {
                    viewModelScope.launch { userData.setFavorite(playlistId, action.isFavorite) }
                }

                PlaylistAction.StartEditing -> {
                    editing.value = true
                }

                is PlaylistAction.PlayItem -> {
                    val index = items.indexOfFirst { it.position == action.position }
                    viewModelScope.launch { trackTaps.tap(items.map { it.track }, index, playlistId) }
                }

                is PlaylistAction.Download -> {
                    val tracks =
                        if (action.position ==
                            null
                        ) {
                            items.map { it.track }
                        } else {
                            listOfNotNull(item(action.position)?.track)
                        }
                    val origin =
                        if (action.position ==
                            null
                        ) {
                            DownloadOrigin.Playlist(playlistId)
                        } else {
                            DownloadOrigin.Single
                        }
                    downloads.download(tracks, origin)
                }

                is PlaylistAction.PlayNext -> {
                    item(action.position)?.let { playback.playNext(listOf(it.track)) }
                }

                is PlaylistAction.AddToQueue -> {
                    item(action.position)?.let { playback.addToQueue(listOf(it.track)) }
                }

                is PlaylistAction.SwipeTrack -> {
                    viewModelScope.launch {
                        commands.swipe(action.track, action.right)
                    }
                }

                is PlaylistAction.RateTrack -> {
                    viewModelScope.launch {
                        commands.rate(action.track, action.halfStars)
                    }
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
                    endSelection()
                }

                PlaylistAction.SelectAll -> {
                    selected.value = items.filterNot { it.isPending }.map { it.entryId }.toSet()
                }

                is PlaylistAction.OnSelection -> {
                    val picked = items.filter { it.entryId in content.selected }.map { it.track }
                    endSelection()
                    when (action.command) {
                        SelectionCommand.Play -> commands.run(TopButton.Play, picked)

                        SelectionCommand.PlayNext -> commands.run(TopButton.PlayNext, picked)

                        SelectionCommand.AddToQueue -> commands.run(TopButton.AddToQueue, picked)

                        SelectionCommand.Download -> commands.download(picked)

                        SelectionCommand.Favorite -> viewModelScope.launch { commands.toggleFavorite(picked) }

                        // The screen opens the playlist picker with the tracks it shows.
                        SelectionCommand.AddToPlaylist -> Unit
                    }
                }

                PlaylistAction.RemoveSelected -> {
                    val entries = items.filter { it.entryId in content.selected }
                    endSelection()
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

        private fun endSelection() {
            selected.value = emptySet()
            editing.value = false
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

private data class Selection(
    val selected: Set<String>,
    val error: MqError?,
    val deleted: Boolean,
    val editing: Boolean,
)
