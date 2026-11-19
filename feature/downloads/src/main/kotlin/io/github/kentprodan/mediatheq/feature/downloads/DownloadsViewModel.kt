package io.github.kentprodan.mediatheq.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.CleanupPreview
import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.DownloadStorageRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.unfollow
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
        appMode: AppModeRepository,
        private val videos: VideoRepository,
    ) : ViewModel() {
        private val filter = MutableStateFlow("")
        private val cleanup = MutableStateFlow<CleanupPreview?>(null)
        private var preview: Job? = null

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

        // The downloaded videos and what the library knows of them (their seasons, #279).
        private val videoLists =
            downloadRepository.videoDownloads.flatMapLatest { list ->
                if (list.isEmpty()) {
                    flowOf(Triple(emptyList(), emptyList(), emptyList()))
                } else {
                    combine(list.map { videos.video(it.id) }) { found ->
                        videoDownloadsContent(list, found.filterNotNull().associateBy { it.id })
                    }
                }
            }

        val uiState: StateFlow<DownloadsUiState> =
            combine(
                // Video mode lists no music download (ADR 0051): they are kept and counted in the totals.
                combine(grouped, appMode.mode, videoLists) { content, mode, (movies, seasons, videoQueue) ->
                    val withVideos =
                        if (mode.video) {
                            content.copy(
                                movies = movies,
                                seasons = seasons,
                                videoQueue = videoQueue,
                            )
                        } else {
                            content
                        }
                    if (mode.music) {
                        withVideos
                    } else {
                        withVideos.copy(
                            downloading = emptyList(),
                            albums = emptyList(),
                            playlists = emptyList(),
                            songs = emptyList(),
                        )
                    }
                },
                downloadRepository.paused,
                filter,
                cleanup,
            ) { content, paused, filter, cleanup ->
                val hasFiles =
                    content.albums.isNotEmpty() || content.playlists.isNotEmpty() || content.songs.isNotEmpty() ||
                        content.movies.isNotEmpty() || content.seasons.isNotEmpty()
                filterDownloads(content, filter)
                    .copy(paused = paused, filter = filter, hasFiles = hasFiles, cleanup = cleanup)
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

                is DownloadsAction.RemoveVideos -> {
                    viewModelScope.launch {
                        unfollowSeriesOf(action.ids)
                        if (action.origin == DownloadOrigin.Single) {
                            downloadRepository.removeOrigin(action.ids, action.origin)
                        } else {
                            downloadRepository.removeCollection(action.origin)
                        }
                    }
                }

                // From the queue: an unfinished or failed video goes, whatever it was asked through.
                is DownloadsAction.CancelVideo -> {
                    viewModelScope.launch {
                        unfollowSeriesOf(listOf(action.id))
                        downloadRepository.remove(listOf(action.id))
                    }
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
                    preview?.cancel()
                    preview = viewModelScope.launch { cleanup.value = downloadRepository.cleanupPreview() }
                }

                DownloadsAction.ConfirmCleanup -> {
                    preview?.cancel()
                    cleanup.value = null
                    viewModelScope.launch { downloadRepository.cleanup() }
                }

                DownloadsAction.DismissCleanup -> {
                    // A preview still on its way must not open the dialog again.
                    preview?.cancel()
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

        /**
         * A followed series' episode removed or cancelled by hand (#383) stops following that series: otherwise smart
         * downloads would fetch it again a moment later.
         */
        private suspend fun unfollowSeriesOf(ids: List<MediaId>) {
            downloadRepository.videoDownloads
                .first()
                .filter { it.id in ids }
                .flatMap { it.origins.filterIsInstance<DownloadOrigin.FollowedSeries>() }
                .toSet()
                .forEach { unfollow(videos, downloadRepository, it.id) }
        }
    }
