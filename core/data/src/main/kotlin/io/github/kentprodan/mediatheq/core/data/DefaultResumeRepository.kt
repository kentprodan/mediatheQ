package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.dao.ResumeDao
import io.github.kentprodan.mediatheq.core.database.entity.AlbumResumeEntity
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ResumePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class DefaultResumeRepository
    @Inject
    constructor(
        private val dao: ResumeDao,
    ) : ResumeRepository {
        override fun resume(albumId: MediaId): Flow<ResumePoint?> =
            dao.resume(albumId.serverId.value, albumId.itemId).map { row ->
                row?.let { ResumePoint(MediaId(albumId.serverId, it.trackId), it.positionMillis.milliseconds) }
            }

        override suspend fun record(
            trackId: MediaId,
            position: Duration,
        ) {
            val albumId = dao.albumOf(trackId.serverId.value, trackId.itemId) ?: return
            dao.save(AlbumResumeEntity(trackId.serverId.value, albumId, trackId.itemId, position.inWholeMilliseconds))
        }
    }
