package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.AlbumResumeRepository
import io.github.kentprodan.mediatheq.core.model.AlbumResume
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

/** Resume points set directly per album; [recorded] lists what [record] was called with. */
class FakeAlbumResumeRepository : AlbumResumeRepository {
    val resumes = MutableStateFlow<Map<MediaId, AlbumResume>>(emptyMap())
    val recorded = mutableListOf<Pair<MediaId, Duration>>()

    override fun resume(albumId: MediaId) = resumes.map { it[albumId] }

    override suspend fun record(
        trackId: MediaId,
        position: Duration,
    ) {
        recorded += trackId to position
    }
}
