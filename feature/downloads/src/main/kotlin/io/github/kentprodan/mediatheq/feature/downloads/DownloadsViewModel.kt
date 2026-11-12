package io.github.kentprodan.mediatheq.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.CleanupPreview
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.DownloadStorageRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        private val downloadRepository: DownloadRepository,
        library: LibraryRepository,
        private val playback: PlaybackConnection,
        private val trackTaps: TrackTaps,
        storage: DownloadStorageRepository,
        audioPreferences: AudioPreferencesRepository,
    ) : ViewModel() {
        private val filter = MutableStateFlow("")
        private val cleanup = MutableStateFlow<CleanupPreview?>(null)

        private val grouped =
            combine(
                downloadRepository.downloads,
                downloadRepository.freeBytes,
                library.albums().map { albums -> albums.associateBy { it.id } },
                library.allPlaylists().map { playlists -> playlists.associateBy { it.id } },
                storage.available,
            ) { downloads, freeBytes, albums, playlists, available ->
                downloadsContent(downloads, albums, playlists, freeBytes)
                    .copy(unavailableOn = if (available) null else storage.locationName.orEmpty())
            }

        private val rules =
            audioPreferences.preferences
                .map {
                    AutoRules(
                        it.autoDownloads,
                        it.removeUnfavorited,
                    )
                }.distinctUntilChanged()

        val uiState: StateFlow<DownloadsUiState> =
            combine(
                grouped,
                downloadRepository.paused,
                rules,
                filter,
                cleanup,
            ) { content, paused, rules, filter, cleanup ->
                val hasFiles =
                    content.albums.isNotEmpty() || content.playlists.isNotEmpty() || content.songs.isNotEmpty()
                filterDownloads(content, filter)
                    .copy(paused = paused, rules = rules, filter = filter, hasFiles = hasFiles, cleanup = cleanup)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState.Loading)

        fun onAction(action: DownloadsAction) {
            when (action) {
                is DownloadsAction.Play -> {
                    play(action)
                }

                is DownloadsAction.PlayNext -> {
                    playback.playNext(listOf(action.track))
                }

                is DownloadsAction.AddToQueue -> {
                    playback.addToQueue(listOf(action.track))
                }

                is DownloadsAction.Retry -> {
                    downloadRepository.download(listOf(action.track), origin = null)
                }

                is DownloadsAction.Remove -> {
                    downloadRepository.remove(listOf(action.track.id))
                }

                is DownloadsAction.RemoveSong -> {
                    downloadRepository.removeSong(action.track.id)
                }

                is DownloadsAction.PlayCollection -> {
                    if (action.tracks.isNotEmpty()) playback.play(action.tracks, shuffle = action.shuffle)
                }

                is DownloadsAction.RemoveCollection -> {
                    downloadRepository.removeCollection(action.origin)
                }

                DownloadsAction.PauseAll -> {
                    downloadRepository.pauseAll()
                }

                DownloadsAction.ResumeAll -> {
                    downloadRepository.resumeAll()
                }

                is DownloadsAction.Filter -> {
                    filter.value = action.text
                }

                DownloadsAction.AskCleanup -> {
                    viewModelScope.launch { cleanup.value = downloadRepository.cleanupPreview() }
                }

                DownloadsAction.ConfirmCleanup -> {
                    cleanup.value = null
                    viewModelScope.launch { downloadRepository.cleanup() }
                }

                DownloadsAction.DismissCleanup -> {
                    cleanup.value = null
                }
            }
        }

        // A song plays with the other downloaded songs after it; anything else (still downloading) alone.
        private fun play(action: DownloadsAction.Play) {
            val songs = (uiState.value as? DownloadsUiState.Content)?.songs.orEmpty().map { it.track }
            val index = songs.indexOfFirst { it.id == action.track.id }
            viewModelScope.launch {
                if (index >= 0) trackTaps.tap(songs, index) else trackTaps.tap(listOf(action.track), 0)
            }
        }
    }
