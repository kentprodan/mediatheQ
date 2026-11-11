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
 * Offline mode turned on while there is a network (#144): by the switch, or by [AutoOffline.NotOnWifi] on a metered
 * network. The player sees no network then; without one it plays only downloads anyway.
 */
internal fun forcedOffline(
    switchOn: Boolean,
    auto: AutoOffline,
    metered: Boolean,
): Boolean = switchOn || (auto == AutoOffline.NotOnWifi && metered)

/** Offline mode as the library sees it: [forcedOffline], or no network unless automatic offline mode is Never. */
internal fun offlineActive(
    switchOn: Boolean,
    auto: AutoOffline,
    online: Boolean,
    metered: Boolean,
): Boolean = forcedOffline(switchOn, auto, metered) || (!online && auto != AutoOffline.Never)

/**
 * Offline mode (#63, #144): on with the setting, and on by itself per Automatic offline mode (no network, or not on
 * Wi-Fi). While it is on, library reads keep only completed downloads (ADR 0021); turning it off shows the whole
 * library again at once, without a sync.
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
        private val settings = preferences.preferences.map { it.offlineMode to it.autoOffline }.distinctUntilChanged()

        val active: Flow<Boolean> =
            combine(settings, network.online, network.meteredChanges) { (on, auto), online, metered ->
                offlineActive(on, auto, online, metered)
            }.distinctUntilChanged()

        /** [forcedOffline] as it changes: what the player treats as no network, besides having none (#144). */
        val forced: Flow<Boolean> =
            combine(settings, network.meteredChanges) { (on, auto), metered -> forcedOffline(on, auto, metered) }
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
