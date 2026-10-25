package io.github.kentprodan.mediatheq.core.server

import kotlinx.coroutines.flow.Flow

/** Changes the server pushes while the app listens (jellyfin skill, section 9). */
interface LiveEvents {
    /**
     * Opens one connection to [server] while collected and closes it when collection stops. The flow ends after a
     * [LiveEvent.Closed]; reconnecting is the caller's job.
     */
    fun events(server: SignedInServer): Flow<LiveEvent>
}
