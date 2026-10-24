package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.server.LiveEvent
import io.github.kentprodan.mediatheq.core.server.LiveEvents
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.SocketMessage
import io.github.kentprodan.mediatheq.provider.jellyfin.mapper.toSocketMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Jellyfin's `/socket` (jellyfin skill, section 9). A WebSocket cannot carry the auth header, so the token goes in
 * `ApiKey` (R1) and the client is the plain one, without [JellyfinAuthInterceptor]. Never log the socket URL.
 */
class JellyfinLiveEvents
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
    ) : LiveEvents {
        override fun events(server: SignedInServer): Flow<LiveEvent> =
            callbackFlow {
                val url =
                    server.connection.baseUrl
                        .toHttpUrlOrNull()
                        ?.newBuilder()
                        ?.addPathSegment("socket")
                        ?.addQueryParameter("ApiKey", server.connection.accessToken.orEmpty())
                        ?.addQueryParameter("deviceId", server.connection.deviceId)
                        ?.build()
                if (url == null) {
                    send(LiveEvent.Closed(MqError.Client(0, "invalid server address")))
                    close()
                    return@callbackFlow
                }
                var keepAlive: Job? = null

                fun closeWith(error: MqError) {
                    keepAlive?.cancel()
                    trySend(LiveEvent.Closed(error))
                    channel.close()
                }

                val listener =
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            trySend(LiveEvent.Connected)
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            when (val message = text.toSocketMessage(json, server)) {
                                is SocketMessage.Event -> {
                                    trySend(message.event)
                                }

                                is SocketMessage.ForceKeepAlive -> {
                                    // Answer now, then at half the server's timeout, as Jellyfin's own clients do.
                                    keepAlive?.cancel()
                                    keepAlive =
                                        launch {
                                            while (isActive) {
                                                webSocket.send(KEEP_ALIVE)
                                                delay((message.seconds / 2.0).seconds)
                                            }
                                        }
                                }

                                null -> {}
                            }
                        }

                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            webSocket.close(NORMAL_CLOSURE, null)
                        }

                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) = closeWith(MqError.Network)

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) = closeWith(response?.code?.let(::httpError) ?: MqError.Network)
                    }
                val socket = okHttpClient.newWebSocket(Request.Builder().url(url).build(), listener)
                awaitClose {
                    keepAlive?.cancel()
                    socket.close(NORMAL_CLOSURE, null)
                }
            }
                // trySend must never drop an event (above all the final Closed) while the collector writes to Room.
                .buffer(Channel.UNLIMITED)

        private companion object {
            const val KEEP_ALIVE = """{"MessageType":"KeepAlive"}"""
            const val NORMAL_CLOSURE = 1000
        }
    }
