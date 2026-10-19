package io.github.kentprodan.mediatheq.core.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device only: the Android Keystore does not exist on the JVM (ADR 0013). */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreTokenCipherTest {
    private val cipher = AndroidKeystoreTokenCipher()

    @Test
    fun encryptThenDecryptReturnsTheToken() {
        assertThat(cipher.decrypt(cipher.encrypt("secret-token"))).isEqualTo("secret-token")
    }

    @Test
    fun everyEncryptionUsesAFreshIv() {
        assertThat(cipher.encrypt("secret-token")).isNotEqualTo(cipher.encrypt("secret-token"))
    }
}
