package io.github.kentprodan.mediatheq.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        private val downloadRepository: DownloadRepository,
        private val playback: PlaybackConnection,
    ) : ViewModel() {
        val uiState: StateFlow<DownloadsUiState> =
            combine(downloadRepository.downloads, downloadRepository.storageUsed) { downloads, storageUsed ->
                if (downloads.isEmpty()) {
                    DownloadsUiState.Empty
                } else {
                    // The repository already sorts by album; consecutive runs keep that order.
                    val albums =
                        downloads
                            .groupBy { it.track.albumId ?: it.track.albumTitle }
                            .values
                            .map { DownloadedAlbum(it.first().track.albumTitle, it) }
                    DownloadsUiState.Content(albums, storageUsed)
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
                    downloadRepository.download(listOf(action.track))
                }

                is DownloadsAction.Remove -> {
                    downloadRepository.remove(listOf(action.track.id))
                }
            }
        }

        // Only finished downloads join the queue: they play anywhere, also offline.
        private fun play(action: DownloadsAction.Play) {
            val content = uiState.value as? DownloadsUiState.Content ?: return
            val finished =
                content.albums
                    .flatMap { it.downloads }
                    .filter { it.state == DownloadState.Completed }
                    .map { it.track }
            val index = finished.indexOfFirst { it.id == action.track.id }
            if (index >= 0) playback.play(finished, index) else playback.play(listOf(action.track))
        }
    }
