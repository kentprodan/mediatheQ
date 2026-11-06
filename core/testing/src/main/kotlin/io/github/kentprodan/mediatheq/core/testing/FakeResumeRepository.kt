package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.ResumeRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ResumePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

/** Resume points set directly per album or playlist id; [recorded] lists what [record] was called with. */
class FakeResumeRepository : ResumeRepository {
    val resumes = MutableStateFlow<Map<MediaId, ResumePoint>>(emptyMap())
    val recorded = mutableListOf<Pair<MediaId, Duration>>()

    override fun resume(albumId: MediaId) = resumes.map { it[albumId] }

    override fun playlistResume(playlistId: MediaId) = resumes.map { it[playlistId] }

    override suspend fun record(
        trackId: MediaId,
        position: Duration,
        playlistId: MediaId?,
    ) {
        recorded += trackId to position
    }
}
