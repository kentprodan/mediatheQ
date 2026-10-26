package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * The network as the player sees it: none while the offline mode setting is on (#63), so only downloads and cached
 * tracks play, and the rest is skipped like offline (#61). Prefetch stops too.
 */
internal class OfflineModeNetwork(
    private val network: NetworkMonitor,
    offlineMode: Flow<Boolean>,
    scope: CoroutineScope,
) : NetworkMonitor by network {
    // Read on the loader thread, so kept as a value; off until the setting is read (as before #63).
    private val offlineModeOn = offlineMode.stateIn(scope, SharingStarted.Eagerly, false)

    override fun isOnline(): Boolean = network.isOnline() && !offlineModeOn.value

    override val online: Flow<Boolean> =
        combine(network.online, offlineModeOn) { online, offline -> online && !offline }.distinctUntilChanged()
}
