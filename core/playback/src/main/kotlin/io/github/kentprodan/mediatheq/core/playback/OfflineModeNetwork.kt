package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * The network as the player sees it: none while the offline mode setting is on (#63), so only downloads and cached
 * tracks play, and the rest is skipped like offline (#61). Prefetch stops too.
 */
internal class OfflineModeNetwork(
    private val network: NetworkMonitor,
    offlineMode: Flow<Boolean>,
    scope: CoroutineScope,
) : NetworkMonitor by network {
    // `null` until the setting is read: a queue resumed right at process start must not stream before that.
    private val offlineModeOn = offlineMode.stateIn<Boolean?>(scope, SharingStarted.Eagerly, null)

    // Called on loader threads, and by prefetch only once playing (long after the read): waiting once is fine.
    override fun isOnline(): Boolean =
        network.isOnline() && !(offlineModeOn.value ?: runBlocking { offlineModeOn.filterNotNull().first() })

    override val online: Flow<Boolean> =
        combine(network.online, offlineModeOn.filterNotNull()) { online, offline -> online && !offline }
            .distinctUntilChanged()
}
