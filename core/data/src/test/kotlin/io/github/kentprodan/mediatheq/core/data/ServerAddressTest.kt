package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerAddressTest {
    @Test
    fun `an address without scheme gets http`() {
        assertThat(normalizeServerAddress("nas:8096")).isEqualTo("http://nas:8096")
    }

    @Test
    fun `whitespace and trailing slashes are removed`() {
        assertThat(normalizeServerAddress("  https://media.example.com/  ")).isEqualTo("https://media.example.com")
    }

    @Test
    fun `a base path is kept`() {
        assertThat(
            normalizeServerAddress("HTTPS://example.com:8920/jellyfin/"),
        ).isEqualTo("https://example.com:8920/jellyfin")
    }

    @Test
    fun `IPv6 literals keep their brackets`() {
        assertThat(
            normalizeServerAddress("http://[fd7a:115c:a1e0::1]:8096"),
        ).isEqualTo("http://[fd7a:115c:a1e0::1]:8096")
    }

    @Test
    fun `unusable input is rejected`() {
        listOf("", "   ", "ftp://nas", "http://", "http://nas name", "://").forEach {
            assertThat(normalizeServerAddress(it)).isNull()
        }
    }
}
