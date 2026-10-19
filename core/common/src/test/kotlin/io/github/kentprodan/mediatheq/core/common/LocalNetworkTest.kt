package io.github.kentprodan.mediatheq.core.common

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.net.InetAddress

class LocalNetworkTest {
    // IP literals only: getByName does no DNS lookup for them.
    private fun isLocal(ip: String) = InetAddress.getByName(ip).isLocalNetwork()

    @Test
    fun `private LAN ranges are local`() {
        listOf("192.168.178.34", "10.0.0.5", "172.16.0.1", "172.31.255.255").forEach {
            assertWithMessage(it).that(isLocal(it)).isTrue()
        }
    }

    @Test
    fun `loopback and link-local are local`() {
        listOf(
            "127.0.0.1",
            "169.254.1.1",
            "::1",
            "fe80::1",
        ).forEach { assertWithMessage(it).that(isLocal(it)).isTrue() }
    }

    @Test
    fun `Tailscale addresses are local`() {
        listOf("100.64.0.1", "100.101.102.103", "100.127.255.255", "fd7a:115c:a1e0::1").forEach {
            assertWithMessage(it).that(isLocal(it)).isTrue()
        }
    }

    @Test
    fun `public addresses are not local`() {
        listOf("8.8.8.8", "172.32.0.1", "100.128.0.1", "100.63.255.255", "2001:4860:4860::8888").forEach {
            assertWithMessage(it).that(isLocal(it)).isFalse()
        }
    }
}
