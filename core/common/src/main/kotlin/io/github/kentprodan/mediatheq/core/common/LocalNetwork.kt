package io.github.kentprodan.mediatheq.core.common

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * True for addresses on the user's own networks, where cleartext HTTP is allowed (ADR 0013): loopback, private LAN
 * ranges, link-local, IPv6 unique local (includes Tailscale's fd7a:115c:a1e0::/48) and 100.64.0.0/10 (Tailscale; also
 * carrier-grade NAT).
 */
fun InetAddress.isLocalNetwork(): Boolean {
    if (isLoopbackAddress || isSiteLocalAddress || isLinkLocalAddress) return true
    val bytes = address
    return when (this) {
        is Inet4Address -> bytes[0].toInt() == 100 && (bytes[1].toInt() and 0xC0) == 64
        is Inet6Address -> (bytes[0].toInt() and 0xFE) == 0xFC
        else -> false
    }
}
