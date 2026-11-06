package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow

/** [NetworkMonitor] tests flip: [metered] and [online]. */
class FakeNetworkMonitor(
    metered: Boolean = false,
    online: Boolean = true,
) : NetworkMonitor {
    override val online = MutableStateFlow(online)

    override val meteredChanges = MutableStateFlow(metered)

    var metered: Boolean
        get() = meteredChanges.value
        set(value) {
            meteredChanges.value = value
        }

    override fun isMetered() = metered

    override fun isOnline() = online.value
}
