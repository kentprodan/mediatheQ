package io.github.kentprodan.mediatheq.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        private val downloadRepository: DownloadRepository,
        library: LibraryRepository,
        private val playback: PlaybackConnection,
    ) : ViewModel() {
        val uiState: StateFlow<DownloadsUiState> =
            combine(
                downloadRepository.downloads,
                downloadRepository.freeBytes,
                library.albums().map { albums -> albums.associateBy { it.id } },
                library.allPlaylists().map { playlists -> playlists.associateBy { it.id } },
            ) { downloads, freeBytes, albums, playlists ->
                if (downloads.isEmpty()) {
                    DownloadsUiState.Empty
                } else {
                    downloadsContent(downloads, albums, playlists, freeBytes)
                }
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
            }
        }

        // A song plays with the other downloaded songs after it; anything else (still downloading) alone.
        private fun play(action: DownloadsAction.Play) {
            val songs = (uiState.value as? DownloadsUiState.Content)?.songs.orEmpty().map { it.track }
            val index = songs.indexOfFirst { it.id == action.track.id }
            if (index >= 0) playback.play(songs, index) else playback.play(listOf(action.track))
        }
    }
