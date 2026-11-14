package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.VideoPreferences
import io.github.kentprodan.mediatheq.core.data.VideoPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [VideoPreferencesRepository]. */
class FakeVideoPreferencesRepository : VideoPreferencesRepository {
    override val preferences = MutableStateFlow(VideoPreferences())

    override suspend fun update(change: (VideoPreferences) -> VideoPreferences) {
        preferences.value = change(preferences.value)
    }
}
