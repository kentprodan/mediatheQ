package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.model.AppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [AppModeRepository] in memory; [stored] `null` = never chosen. Like the real one, Music without [videoAvailable]. */
class FakeAppModeRepository(
    initial: AppMode? = AppMode.Music,
    override val videoAvailable: Boolean = true,
) : AppModeRepository {
    val stored = MutableStateFlow(initial)

    override val mode: Flow<AppMode> = stored.map { if (videoAvailable) it ?: AppMode.Music else AppMode.Music }
    override val chosen: Flow<Boolean> = stored.map { it != null }

    override suspend fun setMode(mode: AppMode) {
        stored.value = mode
    }
}
