package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The TV's PIN and each profile's age limit (#434), device-wide (set by the main profile for all). The PIN is stored
 * salted and hashed, never as typed.
 */
@Singleton
class ParentalControls
    @Inject
    constructor(
        private val store: LibraryPreferencesStore,
    ) {
        val hasPin: Flow<Boolean> = store.parentalPin.map { it != null }

        /** Age limits by account id; a profile without one has no limit. */
        val limits: Flow<Map<String, Int>> = store.ageLimits

        /** This profile's age limit, `null` for none. */
        val limit: Flow<Int?> = store.ageLimits.map { it[store.activeAccount] }

        /** Whether [pin] is the PIN; true while none is set. */
        suspend fun checkPin(pin: String): Boolean {
            val stored = store.parentalPin.first() ?: return true
            val (salt, hash) = stored.split(SEPARATOR, limit = 2).takeIf { it.size == 2 } ?: return false
            return MessageDigest.isEqual(hash(salt, pin).toByteArray(), hash.toByteArray())
        }

        /** Sets the PIN, `null` removes it. */
        suspend fun setPin(pin: String?) {
            val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes).let(Base64.getEncoder()::encodeToString)
            store.setParentalPin(pin?.let { salt + SEPARATOR + hash(salt, it) })
        }

        suspend fun setLimit(
            accountId: String,
            limit: Int?,
        ) = store.setAgeLimit(accountId, limit)

        private fun hash(
            salt: String,
            pin: String,
        ): String =
            Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()))

        private companion object {
            const val SEPARATOR = ":"
            const val SALT_BYTES = 16
        }
    }
