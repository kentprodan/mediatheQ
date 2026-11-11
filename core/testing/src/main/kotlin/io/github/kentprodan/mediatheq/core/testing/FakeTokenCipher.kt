package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.datastore.TokenCipher
import java.security.GeneralSecurityException

/** Reversible stand-in for the Keystore cipher; set [failing] to simulate a lost key. */
class FakeTokenCipher : TokenCipher {
    var failing = false

    /** Encrypted values that cannot be decrypted, as if written with another key. */
    var unreadable: Set<String> = emptySet()

    override fun encrypt(plain: String): String = check().let { "enc:${plain.reversed()}" }

    override fun decrypt(encrypted: String): String =
        check().let {
            if (encrypted in unreadable) throw GeneralSecurityException("other key")
            encrypted.removePrefix("enc:").reversed()
        }

    private fun check() {
        if (failing) throw GeneralSecurityException("key invalidated")
    }
}
