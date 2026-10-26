package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Offline mode (#63): on with the setting, and on by itself without a network. While it is on, library reads keep only
 * completed downloads (ADR 0021); turning it off shows the whole library again at once, without a sync.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OfflineFilter
    @Inject
    constructor(
        preferences: LibraryPreferencesRepository,
        network: NetworkMonitor,
        // A Provider: the download repository reads the library's tracks itself.
        private val downloads: Provider<DownloadRepository>,
    ) {
        val active: Flow<Boolean> =
            combine(preferences.preferences.map { it.offlineMode }, network.online) { on, online -> on || !online }
                .distinctUntilChanged()

        /** [query] over everything, or over the item ids of completed downloads while offline mode is on. */
        fun <T> of(query: (all: Boolean, downloaded: List<String>) -> Flow<T>): Flow<T> =
            active.flatMapLatest { on ->
                if (on) {
                    downloads.get().completedIds.flatMapLatest { ids -> query(false, ids.map { it.itemId }) }
                } else {
                    query(true, emptyList())
                }
            }

        /** [of] for a one-shot read, with the state of now. */
        suspend fun <T> once(query: suspend (all: Boolean, downloaded: List<String>) -> T): T =
            if (active.first()) {
                query(
                    false,
                    downloads
                        .get()
                        .completedIds
                        .first()
                        .map { it.itemId },
                )
            } else {
                query(true, emptyList())
            }
    }
