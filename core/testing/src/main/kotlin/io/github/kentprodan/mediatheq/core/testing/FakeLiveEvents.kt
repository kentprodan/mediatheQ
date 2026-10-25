package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.server.LiveEvent
import io.github.kentprodan.mediatheq.core.server.LiveEvents
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * [LiveEvents] as a fake socket: each collection is one connection. [opened] counts connections ever made, [open] the
 * ones collected right now; [send] pushes an event into the newest connection.
 */
class FakeLiveEvents : LiveEvents {
    val opened = MutableStateFlow(0)
    val open = MutableStateFlow(0)
    private val sockets = mutableListOf<Channel<LiveEvent>>()

    override fun events(server: SignedInServer): Flow<LiveEvent> =
        flow {
            val socket = Channel<LiveEvent>(Channel.UNLIMITED)
            sockets += socket
            opened.value++
            open.value++
            try {
                for (event in socket) {
                    emit(event)
                    if (event is LiveEvent.Closed) break
                }
            } finally {
                open.value--
            }
        }

    suspend fun send(event: LiveEvent) = sockets.last().send(event)
}
