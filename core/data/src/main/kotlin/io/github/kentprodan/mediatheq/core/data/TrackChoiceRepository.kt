package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.dao.VideoDao
import io.github.kentprodan.mediatheq.core.database.entity.VideoTrackChoiceEntity
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.TrackChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The tracks chosen per movie, episode or season (ADR 0050, #274), kept across syncs, cleared on sign-out. */
interface TrackChoiceRepository {
    /** The choice made on [itemId] (a movie, an episode or a season); `null` when none. */
    fun choice(itemId: MediaId): Flow<TrackChoice?>

    suspend fun save(
        itemId: MediaId,
        choice: TrackChoice,
    )

    /** Back to the season's choice or the defaults. */
    suspend fun clear(itemId: MediaId)
}

@Singleton
class DefaultTrackChoiceRepository
    @Inject
    constructor(
        private val dao: VideoDao,
    ) : TrackChoiceRepository {
        override fun choice(itemId: MediaId): Flow<TrackChoice?> =
            dao.trackChoice(itemId.serverId.value, itemId.itemId).map { row ->
                row?.let {
                    TrackChoice(it.audioIndex, it.audioLanguage, it.subtitleIndex, it.subtitleLanguage, it.subtitleOff)
                }
            }

        override suspend fun save(
            itemId: MediaId,
            choice: TrackChoice,
        ) = dao.saveTrackChoice(
            VideoTrackChoiceEntity(
                itemId.serverId.value,
                itemId.itemId,
                choice.audioIndex,
                choice.audioLanguage,
                choice.subtitleIndex,
                choice.subtitleLanguage,
                choice.subtitleOff,
            ),
        )

        override suspend fun clear(itemId: MediaId) = dao.clearTrackChoice(itemId.serverId.value, itemId.itemId)
    }
