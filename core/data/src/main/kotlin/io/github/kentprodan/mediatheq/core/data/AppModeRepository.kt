package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.server.ServerCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The app mode (ADR 0051): one app-wide preference, in the backup like every other. */
interface AppModeRepository {
    /** The mode in effect: the stored one, missing = [AppMode.Music]; always Music when the server has no video. */
    val mode: Flow<AppMode>

    /** Whether a mode was ever stored: onboarding asks once. */
    val chosen: Flow<Boolean>

    /** The server can have video (`ServerCapabilities.video`): only then can the mode be changed. */
    val videoAvailable: Boolean

    suspend fun setMode(mode: AppMode)
}

@Singleton
class DefaultAppModeRepository
    @Inject
    constructor(
        private val store: LibraryPreferencesStore,
        capabilities: ServerCapabilities,
    ) : AppModeRepository {
        override val videoAvailable = capabilities.video

        override val mode: Flow<AppMode> =
            store.values
                .map { stored ->
                    if (videoAvailable) enumOrNull<AppMode>(stored.appMode) ?: AppMode.Music else AppMode.Music
                }.distinctUntilChanged()

        override val chosen: Flow<Boolean> = store.values.map { it.appMode != null }.distinctUntilChanged()

        override suspend fun setMode(mode: AppMode) = store.setAppMode(mode.name)
    }
