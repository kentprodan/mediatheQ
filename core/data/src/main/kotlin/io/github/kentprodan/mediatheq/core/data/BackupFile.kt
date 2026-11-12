package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.SettingsSnapshot
import io.github.kentprodan.mediatheq.core.model.ServerId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

/** A settings backup (#150): settings and the accounts' addresses, never a token, password or DeviceId. */
internal data class BackupFile(
    val format: Int,
    val appVersion: String,
    val created: Instant,
    val settings: SettingsSnapshot,
    val accounts: List<BackupAccount>,
)

/** An account as a backup keeps it: where and who, never how it signed in. */
internal data class BackupAccount(
    val provider: String,
    val serverId: ServerId,
    val serverName: String,
    val address: String,
    val userId: String,
    val userName: String,
)

internal const val BACKUP_FORMAT = 1

private val json = Json { prettyPrint = true }

/** The file's text: readable JSON, every setting with its type so it comes back as the same type. */
internal fun encodeBackup(backup: BackupFile): String =
    json.encodeToString(
        JsonElement.serializer(),
        JsonObject(
            mapOf(
                "format" to JsonPrimitive(backup.format),
                "app" to JsonPrimitive(backup.appVersion),
                "created" to JsonPrimitive(backup.created.toString()),
                "settings" to
                    JsonObject(
                        mapOf("library" to backup.settings.library.encode(), "audio" to backup.settings.audio.encode()),
                    ),
                "accounts" to
                    JsonArray(
                        backup.accounts.map {
                            JsonObject(
                                mapOf(
                                    "provider" to JsonPrimitive(it.provider),
                                    "serverId" to JsonPrimitive(it.serverId.value),
                                    "serverName" to JsonPrimitive(it.serverName),
                                    "address" to JsonPrimitive(it.address),
                                    "userId" to JsonPrimitive(it.userId),
                                    "userName" to JsonPrimitive(it.userName),
                                ),
                            )
                        },
                    ),
            ),
        ),
    )

/**
 * The backup in [text], or `null` when it is not one (not JSON, no format, a broken part). A newer format is read as far
 * as it is understood: unknown keys, sections and value types are left out.
 */
internal fun decodeBackup(text: String): BackupFile? =
    runCatching {
        val root = Json.parseToJsonElement(text).jsonObject
        val format = root.getValue("format").jsonPrimitive.intOrNull ?: return null
        if (format < 1) return null
        val settings = root["settings"]?.jsonObject
        BackupFile(
            format = format,
            appVersion = root["app"]?.jsonPrimitive?.content.orEmpty(),
            created = Instant.parse(root.getValue("created").jsonPrimitive.content),
            settings =
                SettingsSnapshot(
                    settings
                        ?.get("library")
                        ?.jsonObject
                        ?.decodeValues()
                        .orEmpty(),
                    settings
                        ?.get("audio")
                        ?.jsonObject
                        ?.decodeValues()
                        .orEmpty(),
                ),
            accounts =
                root["accounts"]?.jsonArray.orEmpty().mapNotNull { element ->
                    val account = element.jsonObject

                    fun field(name: String) = account[name]?.jsonPrimitive?.content
                    BackupAccount(
                        provider = field("provider") ?: return@mapNotNull null,
                        serverId = ServerId(field("serverId")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null),
                        serverName = field("serverName").orEmpty(),
                        address = field("address")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                        userId = field("userId")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                        userName = field("userName").orEmpty(),
                    )
                },
        )
    }.getOrNull()

private fun Map<String, Any>.encode(): JsonObject =
    JsonObject(
        mapNotNull { (key, value) ->
            val (type, element) =
                when (value) {
                    is String -> {
                        "string" to JsonPrimitive(value)
                    }

                    is Int -> {
                        "int" to JsonPrimitive(value)
                    }

                    is Long -> {
                        "long" to JsonPrimitive(value)
                    }

                    is Float -> {
                        "float" to JsonPrimitive(value)
                    }

                    is Double -> {
                        "double" to JsonPrimitive(value)
                    }

                    is Boolean -> {
                        "boolean" to JsonPrimitive(value)
                    }

                    is Set<*> -> {
                        "stringSet" to
                            JsonArray(value.filterIsInstance<String>().sorted().map(::JsonPrimitive))
                    }

                    else -> {
                        return@mapNotNull null
                    }
                }
            key to JsonObject(mapOf("type" to JsonPrimitive(type), "value" to element))
        }.toMap(),
    )

/** Values of a type this version does not know, or that do not match their type, are left out. */
private fun JsonObject.decodeValues(): Map<String, Any> =
    mapNotNull { (key, element) ->
        runCatching {
            val entry = element.jsonObject
            val value = entry.getValue("value")
            val decoded: Any? =
                when (entry.getValue("type").jsonPrimitive.content) {
                    "string" -> value.jsonPrimitive.takeIf { it.isString }?.content
                    "int" -> value.jsonPrimitive.intOrNull
                    "long" -> value.jsonPrimitive.longOrNull
                    "float" -> value.jsonPrimitive.floatOrNull
                    "double" -> value.jsonPrimitive.doubleOrNull
                    "boolean" -> value.jsonPrimitive.booleanOrNull
                    "stringSet" -> value.jsonArray.map { it.jsonPrimitive.content }.toSet()
                    else -> null
                }
            decoded?.let { key to it }
        }.getOrNull()
    }.toMap()
