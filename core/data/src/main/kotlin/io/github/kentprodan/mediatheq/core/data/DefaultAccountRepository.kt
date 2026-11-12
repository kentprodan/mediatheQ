package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.getOrNull
import io.github.kentprodan.mediatheq.core.database.AccountDatabases
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.server.AuthSource
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
internal class DefaultAccountRepository
    @Inject
    constructor(
        private val store: AccountStore,
        private val databases: AccountDatabases,
        private val session: SessionRepository,
        private val authSource: AuthSource,
        private val downloads: DownloadRepository,
        private val restarter: AppRestarter,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AccountRepository {
        override val accounts: Flow<List<Account>> =
            combine(store.saved, store.syncRecords) { saved, records ->
                saved.map {
                    val record =
                        records[it.id]?.let { r ->
                            SyncRecord(r.finishedAt, r.error, r.trackCount, r.duration)
                        }
                    Account(
                        it.id,
                        it.serverName,
                        it.userName,
                        it.baseUrl,
                        active = it.id == databases.open,
                        lastSync = record,
                        signedIn = it.signedIn,
                    )
                }
            }

        override suspend fun makeActive(id: String) {
            if (id == databases.open || saved().none { it.id == id }) return
            if (databases.select(id)) restarter.restart()
        }

        override suspend fun reorder(ids: List<String>) {
            try {
                store.reorder(ids)
            } catch (_: IOException) {
                // The order stays as it was on disk.
            }
        }

        override suspend fun editAddress(
            id: String,
            address: String,
        ): ServerCheck {
            val account = saved().firstOrNull { it.id == id } ?: return ServerCheck.Failed(MqError.Storage)
            val check = session.checkServer(address)
            if (check !is ServerCheck.Supported) return check
            if (check.info.id != account.serverId) return ServerCheck.OtherServer
            return try {
                store.save(account.copy(baseUrl = check.baseUrl))
                check
            } catch (_: IOException) {
                ServerCheck.Failed(MqError.Storage)
            }
        }

        override suspend fun remove(
            id: String,
            deleteDownloads: Boolean,
        ) {
            val accounts = saved()
            val account = accounts.firstOrNull { it.id == id } ?: return removeSignedOut(id, deleteDownloads)
            if (deleteDownloads && accounts.none { it.id != id && it.serverId == account.serverId }) {
                downloads.removeServer(account.serverId)
            }
            if (id == databases.open) {
                val next = accounts.firstOrNull { it.id != id }
                // The last one: today's sign-out, the library goes and the app shows onboarding.
                if (next == null) return session.logout()
                // The next account first: should that fail, the active one stays as it is.
                if (!databases.select(next.id)) return
                signOut(account)
                forget(id)
                // Its database is deleted at the next start (cleanUp), once no longer open.
                restarter.restart()
            } else {
                signOut(account)
                if (forget(id)) withContext(ioDispatcher) { databases.delete(id) }
            }
        }

        /** A signed-out account (#150): nothing to sign out of; it is forgotten with any database it had. */
        private suspend fun removeSignedOut(
            id: String,
            deleteDownloads: Boolean,
        ) {
            val all = store.saved.first()
            val account = all.firstOrNull { it.id == id && !it.signedIn } ?: return
            if (id == databases.open) return
            if (deleteDownloads && all.none { it.id != id && it.serverId == account.serverId }) {
                downloads.removeServer(account.serverId)
            }
            if (forget(id)) withContext(ioDispatcher) { databases.delete(id) }
        }

        override suspend fun cleanUp() {
            // Only after a good read: an unreadable store must never look like "no accounts" and delete every library.
            val owned = store.ids()?.toSet() ?: return
            withContext(ioDispatcher) {
                databases.names().filter { it != databases.open && it !in owned }.forEach(databases::delete)
            }
        }

        /** `false` when the disk failed and the account is still saved. */
        private suspend fun forget(id: String): Boolean =
            try {
                store.remove(id)
                true
            } catch (_: IOException) {
                false
            }

        private suspend fun saved(): List<StoredAccount> =
            store.accounts
                .first()
                .getOrNull()
                .orEmpty()

        /** Best effort: the server may be offline or may have revoked the token already. */
        private suspend fun signOut(account: StoredAccount) =
            withContext(NonCancellable) {
                withTimeoutOrNull(5.seconds) {
                    authSource.logout(ServerConnection(account.baseUrl, store.deviceId(), account.accessToken))
                }
            }
    }
