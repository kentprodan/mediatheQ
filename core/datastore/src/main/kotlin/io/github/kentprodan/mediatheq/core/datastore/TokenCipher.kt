package io.github.kentprodan.mediatheq.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/** Encrypts access tokens before they reach disk. Both functions throw `GeneralSecurityException` on failure. */
interface TokenCipher {
    fun encrypt(plain: String): String

    fun decrypt(encrypted: String): String
}

/**
 * AES-256-GCM with a key that never leaves the Android Keystore (ADR 0013). Output is Base64 of IV + ciphertext; every
 * call uses a fresh IV. The key needs no user authentication, so playback can start without unlocking the phone.
 */
internal class AndroidKeystoreTokenCipher
    @Inject
    constructor() : TokenCipher {
        override fun encrypt(plain: String): String =
            keystore {
                val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
                Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
            }

        override fun decrypt(encrypted: String): String =
            keystore {
                val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
                val cipher =
                    Cipher.getInstance(TRANSFORMATION).apply {
                        init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
                    }
                String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
            }

        /** Keystore failures often surface as `ProviderException` (a RuntimeException); keep the interface contract. */
        private inline fun <T> keystore(block: () -> T): T =
            try {
                block()
            } catch (e: ProviderException) {
                throw GeneralSecurityException(e)
            }

        private fun key(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val spec =
                KeyGenParameterSpec
                    .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            return KeyGenerator
                .getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE,
                ).apply { init(spec) }
                .generateKey()
        }

        private companion object {
            const val KEYSTORE = "AndroidKeyStore"
            const val ALIAS = "mediatheq_access_token"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val IV_BYTES = 12
            const val TAG_BITS = 128
        }
    }
