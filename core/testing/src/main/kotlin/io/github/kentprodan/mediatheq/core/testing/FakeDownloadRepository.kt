package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** [DownloadRepository] in memory: [download] queues, tests move states with [setState]. */
class FakeDownloadRepository(
    initial: List<TrackDownload> = emptyList(),
) : DownloadRepository {
    override val downloads = MutableStateFlow(initial)
    override val storageUsed = MutableStateFlow(0L)

    override fun download(tracks: List<Track>) {
        downloads.update { current ->
            current + tracks.filter { track -> current.none { it.track.id == track.id } }.map { TrackDownload(it, DownloadState.Queued) }
        }
    }

    override fun remove(ids: List<MediaId>) {
        downloads.update { current -> current.filterNot { it.track.id in ids } }
    }

    override fun isDownloaded(id: MediaId) = downloads.value.any { it.track.id == id && it.state == DownloadState.Completed }

    fun setState(
        id: MediaId,
        state: DownloadState,
    ) {
        downloads.update { current -> current.map { if (it.track.id == id) it.copy(state = state) else it } }
    }
}
