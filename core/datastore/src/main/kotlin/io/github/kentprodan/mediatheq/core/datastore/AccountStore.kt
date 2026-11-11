package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.kentprodan.mediatheq.core.common.ActiveDatabase
import io.github.kentprodan.mediatheq.core.common.DEFAULT_DATABASE
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.ServerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The saved server accounts in the user's order (#147) and the per-install DeviceId (jellyfin skill, section 2). Each
 * account's id is the file name of its database; the active one is the account whose database this process opened
 * ([activeId], ADR 0036). Tokens are stored only encrypted (ADR 0013).
 */
@Singleton
class AccountStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val cipher: TokenCipher,
        @param:ActiveDatabase private val activeId: String = DEFAULT_DATABASE,
    ) {
        /**
         * Every saved account whose token can be decrypted (one that cannot, e.g. after the Keystore key was lost, is
         * left out until signed in again). Fails with [MqError.Storage] when the disk cannot be read.
         */
        val accounts: Flow<MqResult<List<StoredAccount>>> =
            dataStore.data
                .map { prefs -> MqResult.Success(prefs.ids().mapNotNull { prefs.readOrNull(it) }) }
                .catch<MqResult<List<StoredAccount>>> {
                    if (it is IOException) emit(MqResult.Failure(MqError.Storage)) else throw it
                }

        /**
         * The active account, `null` when signed out. Fails with [MqError.Storage] when the disk cannot be read or its
         * token cannot be decrypted; the caller treats that as signed out.
         */
        val account: Flow<MqResult<StoredAccount?>> =
            dataStore.data
                .map<Preferences, MqResult<StoredAccount?>> { prefs ->
                    if (activeId !in prefs.ids()) {
                        MqResult.Success(null)
                    } else {
                        prefs.readOrNull(activeId)?.let { MqResult.Success(it) } ?: MqResult.Failure(MqError.Storage)
                    }
                }.catch { if (it is IOException) emit(MqResult.Failure(MqError.Storage)) else throw it }

        /** Every saved account's id, also those whose token cannot be read; `null` when the disk cannot be read. */
        suspend fun ids(): List<String>? =
            try {
                dataStore.data.first().ids()
            } catch (_: IOException) {
                null
            }

        /** The active account's base URL, `null` when signed out. Cheap: no token decryption. */
        val baseUrl: Flow<String?> =
            dataStore.data
                .map { prefs -> prefs[key(BASE_URL, activeId)]?.takeIf { activeId in prefs.ids() } }
                .catch { if (it is IOException) emit(null) else throw it }

        /**
         * Writes [account] under its id: replaces a saved one (R9: one token per server, the newest wins) or adds it at
         * the end. Throws on IO or crypto failure.
         */
        suspend fun save(account: StoredAccount) {
            require(',' !in account.id && account.id.isNotEmpty())
            val encrypted = cipher.encrypt(account.accessToken)
            dataStore.edit { prefs ->
                prefs[key(BASE_URL, account.id)] = account.baseUrl
                prefs[key(SERVER_ID, account.id)] = account.serverId.value
                prefs[key(SERVER_NAME, account.id)] = account.serverName
                prefs[key(USER_ID, account.id)] = account.userId
                prefs[key(USER_NAME, account.id)] = account.userName
                prefs[key(TOKEN, account.id)] = encrypted
                val ids = prefs.ids()
                if (account.id !in ids) prefs.setIds(ids + account.id)
            }
        }

        /** The last sync of every saved account, by account id (#148); empty when the disk cannot be read. */
        val syncRecords: Flow<Map<String, StoredSyncRecord>> =
            dataStore.data
                .map { prefs ->
                    prefs.ids().mapNotNull { id -> prefs[key(SYNC, id)]?.let(::decodeSyncRecord)?.let { id to it } }.toMap()
                }.catch { if (it is IOException) emit(emptyMap()) else throw it }

        /**
         * Records how the active account's sync ended. Skipped when the account is no longer saved (signed out meanwhile),
         * so a removed account's record never comes back. Throws `IOException` when the disk fails.
         */
        suspend fun saveSyncRecord(record: StoredSyncRecord) {
            dataStore.edit { prefs ->
                if (activeId in prefs.ids()) prefs[key(SYNC, activeId)] = record.encode()
            }
        }

        /** Forgets the account [id] and its token; the DeviceId stays. Throws `IOException` when the disk fails. */
        suspend fun remove(id: String) {
            dataStore.edit { prefs ->
                FIELDS.forEach { prefs.remove(key(it, id)) }
                prefs.setIds(prefs.ids() - id)
            }
        }

        /** Forgets the active account. */
        suspend fun clear() = remove(activeId)

        /** Puts the accounts in the order of [ids]; ids not saved are ignored, saved ones left out keep their place last. */
        suspend fun reorder(ids: List<String>) {
            dataStore.edit { prefs ->
                val saved = prefs.ids()
                prefs.setIds(ids.filter { it in saved } + saved.filter { it !in ids })
            }
        }

        /** The random per-install DeviceId, created on first use. Never a hardware id. */
        suspend fun deviceId(): String =
            try {
                dataStore.data.first()[DEVICE_ID] ?: run {
                    var id = ""
                    dataStore.edit {
                        id =
                            it[DEVICE_ID] ?: UUID.randomUUID().toString().also { new -> it[DEVICE_ID] = new }
                    }
                    id
                }
            } catch (_: IOException) {
                // Unreadable storage: a stable id for this process beats a crash; saving a login fails anyway.
                fallbackDeviceId
            }

        private val fallbackDeviceId by lazy { UUID.randomUUID().toString() }

        private fun Preferences.readOrNull(id: String): StoredAccount? =
            try {
                read(id)
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                // A corrupt value: token not Base64, or an empty ServerId.
                null
            }

        private fun Preferences.read(id: String): StoredAccount? {
            val encrypted = this[key(TOKEN, id)] ?: return null
            return StoredAccount(
                baseUrl = this[key(BASE_URL, id)].orEmpty(),
                serverId = ServerId(this[key(SERVER_ID, id)].orEmpty()),
                serverName = this[key(SERVER_NAME, id)].orEmpty(),
                userId = this[key(USER_ID, id)].orEmpty(),
                userName = this[key(USER_NAME, id)].orEmpty(),
                accessToken = cipher.decrypt(encrypted),
                id = id,
            )
        }

        companion object {
            private const val BASE_URL = "base_url"
            private const val SERVER_ID = "server_id"
            private const val SERVER_NAME = "server_name"
            private const val USER_ID = "user_id"
            private const val USER_NAME = "user_name"
            private const val TOKEN = "access_token_encrypted"
            private const val SYNC = "sync_record"
            private val FIELDS = listOf(BASE_URL, SERVER_ID, SERVER_NAME, USER_ID, USER_NAME, TOKEN, SYNC)
            private val IDS = stringPreferencesKey("account_ids")
            private val DEVICE_ID = stringPreferencesKey("device_id")

            private fun key(
                field: String,
                id: String,
            ) = stringPreferencesKey("$field@$id")

            private fun Preferences.ids(): List<String> = this[IDS]?.split(',')?.filter { it.isNotEmpty() }.orEmpty()

            private fun MutablePreferences.setIds(ids: List<String>) {
                this[IDS] = ids.joinToString(",")
            }

            /** The single account of the app before #147 becomes the first account, on the default database. */
            val SingleToListMigration: DataMigration<Preferences> =
                object : DataMigration<Preferences> {
                    override suspend fun shouldMigrate(currentData: Preferences) =
                        FIELDS.any { stringPreferencesKey(it) in currentData }

                    override suspend fun migrate(currentData: Preferences): Preferences =
                        currentData.toMutablePreferences().apply {
                            val hadToken = stringPreferencesKey(TOKEN) in this
                            FIELDS.forEach { field ->
                                val old = stringPreferencesKey(field)
                                this[old]?.let { if (hadToken) this[key(field, DEFAULT_DATABASE)] = it }
                                remove(old)
                            }
                            if (hadToken && DEFAULT_DATABASE !in ids()) setIds(ids() + DEFAULT_DATABASE)
                        }

                    override suspend fun cleanUp() = Unit
                }
        }
    }
