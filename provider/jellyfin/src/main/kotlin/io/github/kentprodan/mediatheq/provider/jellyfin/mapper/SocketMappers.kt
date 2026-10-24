package io.github.kentprodan.mediatheq.provider.jellyfin.mapper

import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.server.LiveEvent
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.UserDataChangeInfoDto
import io.github.kentprodan.mediatheq.provider.jellyfin.dto.WebSocketMessageDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** What a `/socket` message asks of us. */
internal sealed interface SocketMessage {
    /** The server drops the connection after [seconds] without a `KeepAlive` from us. */
    data class ForceKeepAlive(
        val seconds: Int,
    ) : SocketMessage

    data class Event(
        val event: LiveEvent,
    ) : SocketMessage
}

/**
 * Reads one `/socket` text frame. Unknown types, other users' data and unreadable frames give `null`: one bad frame
 * must not end the connection.
 */
internal fun String.toSocketMessage(
    json: Json,
    server: SignedInServer,
): SocketMessage? =
    try {
        val message = json.decodeFromString(WebSocketMessageDto.serializer(), this)
        when (message.messageType) {
            "ForceKeepAlive" -> {
                message.data
                    ?.jsonPrimitive
                    ?.int
                    ?.takeIf { it > 0 }
                    ?.let { SocketMessage.ForceKeepAlive(it) }
            }

            "LibraryChanged" -> {
                SocketMessage.Event(LiveEvent.LibraryChanged)
            }

            "UserDataChanged" -> {
                message.data
                    ?.let { json.decodeFromJsonElement(UserDataChangeInfoDto.serializer(), it) }
                    ?.takeIf { it.userId != null && it.userId.sameId(server.userId) }
                    ?.userDataList
                    ?.mapNotNull { dto ->
                        val itemId = dto.itemId?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                        MediaId(server.serverId, itemId.toItemId()) to dto.toDomain()
                    }?.takeIf { it.isNotEmpty() }
                    ?.let { SocketMessage.Event(LiveEvent.UserDataChanged(it.toMap())) }
            }

            else -> {
                null
            }
        }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        // kotlinx.serialization reports some malformed JSON (and a non-number ForceKeepAlive) this way.
        null
    }

/** Jellyfin writes GUIDs with or without dashes; the library stores them as 32 lowercase hex digits. */
private fun String.toItemId(): String = replace("-", "").lowercase()

private fun String.sameId(other: String): Boolean = toItemId() == other.toItemId()
