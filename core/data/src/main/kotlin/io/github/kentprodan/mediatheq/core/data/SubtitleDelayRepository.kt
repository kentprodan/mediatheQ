package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.dao.VideoDao
import io.github.kentprodan.mediatheq.core.database.entity.VideoSubtitleDelayEntity
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How much earlier (negative) or later each movie's or episode's subtitles show (#456); cleared on sign-out. */
interface SubtitleDelayRepository {
    /** [itemId]'s delay, zero when none was set. */
    fun delay(itemId: MediaId): Flow<Duration>

    /** Zero forgets it. */
    suspend fun save(
        itemId: MediaId,
        delay: Duration,
    )
}

@Singleton
class DefaultSubtitleDelayRepository
    @Inject
    constructor(
        private val dao: VideoDao,
    ) : SubtitleDelayRepository {
        override fun delay(itemId: MediaId): Flow<Duration> =
            dao.subtitleDelay(itemId.serverId.value, itemId.itemId).map { (it ?: 0L).milliseconds }

        override suspend fun save(
            itemId: MediaId,
            delay: Duration,
        ) = if (delay == Duration.ZERO) {
            dao.clearSubtitleDelay(itemId.serverId.value, itemId.itemId)
        } else {
            dao.saveSubtitleDelay(
                VideoSubtitleDelayEntity(itemId.serverId.value, itemId.itemId, delay.inWholeMilliseconds),
            )
        }
    }

/** [current] one 0.1 s step [later] or earlier, 5 s at most either way (#456): further ahead is not buffered yet. */
fun subtitleDelayStep(
    current: Duration,
    later: Boolean,
): Duration =
    (current + if (later) SUBTITLE_DELAY_STEP else -SUBTITLE_DELAY_STEP).coerceIn(
        -SUBTITLE_DELAY_MAX,
        SUBTITLE_DELAY_MAX,
    )

private val SUBTITLE_DELAY_STEP = 100.milliseconds
private val SUBTITLE_DELAY_MAX = 5.seconds
