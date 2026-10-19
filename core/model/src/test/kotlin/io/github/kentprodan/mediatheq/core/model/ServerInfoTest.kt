package io.github.kentprodan.mediatheq.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerInfoTest {
    private fun info(version: String) = ServerInfo(ServerId("s"), "Server", version)

    @Test
    fun `12 and newer are supported`() {
        assertThat(info("12.2.0").isSupported).isTrue()
        assertThat(info("12.0.0-rc1").isSupported).isTrue()
        assertThat(info("13.0.0").isSupported).isTrue()
    }

    @Test
    fun `10 is not supported`() {
        assertThat(info("10.11.6").majorVersion).isEqualTo(10)
        assertThat(info("10.11.6").isSupported).isFalse()
    }

    @Test
    fun `an unreadable version is not supported`() {
        assertThat(info("unstable").majorVersion).isNull()
        assertThat(info("").isSupported).isFalse()
    }
}
