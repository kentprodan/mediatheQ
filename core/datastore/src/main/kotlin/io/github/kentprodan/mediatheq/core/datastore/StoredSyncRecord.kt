package io.github.kentprodan.mediatheq.core.datastore

import io.github.kentprodan.mediatheq.core.common.MqError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** How an account's last library sync ended (#148): [error] `null` when it succeeded. */
data class StoredSyncRecord(
    val finishedAt: Instant,
    val error: MqError?,
    val trackCount: Int,
    val duration: Duration,
)

/** "finishedAt|tracks|millis|error", the error as [encode] writes it (empty on success). */
internal fun StoredSyncRecord.encode(): String =
    listOf(finishedAt.toEpochMilliseconds(), trackCount, duration.inWholeMilliseconds, error?.encode().orEmpty())
        .joinToString("|")

/** `null` for a value this version cannot read. */
internal fun decodeSyncRecord(value: String): StoredSyncRecord? {
    val parts = value.split('|', limit = 4)
    if (parts.size != 4) return null
    return StoredSyncRecord(
        finishedAt = Instant.fromEpochMilliseconds(parts[0].toLongOrNull() ?: return null),
        error = parts[3].takeIf { it.isNotEmpty() }?.let(::decodeError),
        trackCount = parts[1].toIntOrNull() ?: return null,
        duration = (parts[2].toLongOrNull() ?: return null).milliseconds,
    )
}

private fun MqError.encode(): String =
    when (this) {
        MqError.Network -> "Network"
        MqError.Unauthorized -> "Unauthorized"
        MqError.Forbidden -> "Forbidden"
        MqError.NotFound -> "NotFound"
        is MqError.Server -> "Server:$code"
        is MqError.Client -> "Client:$code"
        MqError.Storage -> "Storage"
        is MqError.Unknown -> "Unknown"
    }

/** Anything not recognized reads as [MqError.Unknown]: the record still shows a failure. */
private fun decodeError(code: String): MqError {
    val number = code.substringAfter(':', "").toIntOrNull()
    return when {
        code == "Network" -> MqError.Network
        code == "Unauthorized" -> MqError.Unauthorized
        code == "Forbidden" -> MqError.Forbidden
        code == "NotFound" -> MqError.NotFound
        code == "Storage" -> MqError.Storage
        code.startsWith("Server:") && number != null -> MqError.Server(number)
        code.startsWith("Client:") && number != null -> MqError.Client(number)
        else -> MqError.Unknown(IllegalStateException("Saved sync error: $code"))
    }
}
