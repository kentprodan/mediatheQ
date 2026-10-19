package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * The one server account and the per-install DeviceId (jellyfin skill, section 2). Multiple servers come later; the
 * token is stored only encrypted.
 */
@Singleton
class AccountStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val cipher: TokenCipher,
    ) {
        /**
         * The saved account, `null` when signed out. Fails with [MqError.Storage] when the token cannot be decrypted
         * (e.g. the Keystore key is gone); the caller treats that as signed out.
         */
        val account: Flow<MqResult<StoredAccount?>> =
            dataStore.data
                .map { prefs ->
                    val encrypted = prefs[TOKEN] ?: return@map MqResult.Success(null)
                    try {
                        MqResult.Success(
                            StoredAccount(
                                baseUrl = prefs[BASE_URL].orEmpty(),
                                serverId = ServerId(prefs[SERVER_ID].orEmpty()),
                                serverName = prefs[SERVER_NAME].orEmpty(),
                                userId = prefs[USER_ID].orEmpty(),
                                userName = prefs[USER_NAME].orEmpty(),
                                accessToken = cipher.decrypt(encrypted),
                            ),
                        )
                    } catch (_: GeneralSecurityException) {
                        MqResult.Failure(MqError.Storage)
                    } catch (_: IllegalArgumentException) {
                        // A corrupt value: token not Base64, or an empty ServerId.
                        MqResult.Failure(MqError.Storage)
                    }
                }.catch { if (it is IOException) emit(MqResult.Failure(MqError.Storage)) else throw it }

        /** Replaces the saved account (R9: one token per server, the newest wins). Throws on IO or crypto failure. */
        suspend fun save(account: StoredAccount) {
            val encrypted = cipher.encrypt(account.accessToken)
            dataStore.edit {
                it[BASE_URL] = account.baseUrl
                it[SERVER_ID] = account.serverId.value
                it[SERVER_NAME] = account.serverName
                it[USER_ID] = account.userId
                it[USER_NAME] = account.userName
                it[TOKEN] = encrypted
            }
        }

        /** Forgets the account and its token; the DeviceId stays. Throws `IOException` when the disk fails. */
        suspend fun clear() {
            dataStore.edit { prefs ->
                listOf(BASE_URL, SERVER_ID, SERVER_NAME, USER_ID, USER_NAME, TOKEN).forEach { prefs.remove(it) }
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

        private companion object {
            val BASE_URL = stringPreferencesKey("base_url")
            val SERVER_ID = stringPreferencesKey("server_id")
            val SERVER_NAME = stringPreferencesKey("server_name")
            val USER_ID = stringPreferencesKey("user_id")
            val USER_NAME = stringPreferencesKey("user_name")
            val TOKEN = stringPreferencesKey("access_token_encrypted")
            val DEVICE_ID = stringPreferencesKey("device_id")
        }
    }
