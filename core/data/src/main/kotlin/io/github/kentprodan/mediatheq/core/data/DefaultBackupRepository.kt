package io.github.kentprodan.mediatheq.core.data

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.database.AccountDatabases
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.SavedAccount
import io.github.kentprodan.mediatheq.core.datastore.SettingsBackupStore
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
internal class DefaultBackupRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val settings: SettingsBackupStore,
        private val accounts: AccountStore,
        private val databases: AccountDatabases,
        private val client: ClientInfo,
        private val clock: Clock,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : BackupRepository {
        override suspend fun write(uri: String): Boolean =
            withContext(ioDispatcher) {
                try {
                    val backup =
                        BackupFile(
                            format = BACKUP_FORMAT,
                            appVersion = client.version,
                            created = clock.now(),
                            settings = settings.snapshot(),
                            accounts =
                                accounts.saved.first().map {
                                    BackupAccount(
                                        JELLYFIN,
                                        it.serverId,
                                        it.serverName,
                                        it.baseUrl,
                                        it.userId,
                                        it.userName,
                                    )
                                },
                        )
                    val stream = context.contentResolver.openOutputStream(uri.toUri(), "wt") ?: return@withContext false
                    stream.use { it.write(encodeBackup(backup).encodeToByteArray()) }
                    true
                } catch (_: IOException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }

        override suspend fun read(uri: String): BackupCheck {
            val loaded = load(uri) ?: return BackupCheck.Unreadable
            return loaded.backup?.let { BackupCheck.Valid(it.summary()) } ?: BackupCheck.Invalid
        }

        override suspend fun restore(uri: String): BackupRestore {
            val loaded = load(uri) ?: return BackupRestore.Failed(BackupCheck.Unreadable)
            val backup = loaded.backup ?: return BackupRestore.Failed(BackupCheck.Invalid)
            return try {
                settings.replace(backup.settings)
                val saved = accounts.saved.first()
                backup.accounts
                    .filter { it.provider == JELLYFIN }
                    .filter { new -> saved.none { it.serverId == new.serverId && it.userId == new.userId } }
                    .distinctBy { it.serverId to it.userId }
                    .forEach {
                        // A new database name, never the open one: the active account stays as it is.
                        accounts.saveSignedOut(
                            SavedAccount(
                                databases.newName(),
                                it.address,
                                it.serverId,
                                it.serverName,
                                it.userId,
                                it.userName,
                            ),
                        )
                    }
                BackupRestore.Restored(accounts.saved.first().count { !it.signedIn })
            } catch (_: IOException) {
                BackupRestore.NotSaved
            }
        }

        /** The file's backup (`null` inside when it is not one), or `null` when it cannot be read. */
        private suspend fun load(uri: String): Loaded? =
            withContext(ioDispatcher) {
                try {
                    val stream = context.contentResolver.openInputStream(uri.toUri()) ?: return@withContext null
                    val bytes = stream.use { it.readAtMost(MAX_BYTES + 1) }
                    // A settings backup is a few kB; anything this big is not one, and is not read whole.
                    if (bytes.size > MAX_BYTES) Loaded(null) else Loaded(decodeBackup(bytes.decodeToString()))
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }

        private class Loaded(
            val backup: BackupFile?,
        )

        private fun BackupFile.summary() =
            BackupSummary(
                created = created,
                appVersion = appVersion,
                settingsCount = settings.library.size + settings.audio.size,
                accounts = accounts.map { BackupAccountSummary(it.serverName, it.userName, it.address) },
            )

        private companion object {
            const val JELLYFIN = "jellyfin"
            const val MAX_BYTES = 1_000_000
        }
    }

/** Up to [max] bytes (a little more at most): never the whole of a file far too big to be a backup. */
private fun InputStream.readAtMost(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (out.size() < max) {
        val read = read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
