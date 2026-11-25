package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.SubtitleDelayRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

class FakeSubtitleDelayRepository : SubtitleDelayRepository {
    val delays = MutableStateFlow<Map<MediaId, Duration>>(emptyMap())

    override fun delay(itemId: MediaId): Flow<Duration> = delays.map { it[itemId] ?: Duration.ZERO }

    override suspend fun save(
        itemId: MediaId,
        delay: Duration,
    ) {
        delays.value = if (delay == Duration.ZERO) delays.value - itemId else delays.value + (itemId to delay)
    }
}
