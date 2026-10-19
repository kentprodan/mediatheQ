package io.github.kentprodan.mediatheq.core.network

import io.github.kentprodan.mediatheq.core.common.isLocalNetwork
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy

/**
 * Network interceptor that allows cleartext HTTP only to addresses on the user's own networks (ADR 0013). It checks the
 * IP the socket actually connected to, so LAN host names (`nas`, `jellyfin.lan`) and MagicDNS names work too.
 */
class CleartextPolicyInterceptor(
    private val isAllowed: (InetAddress) -> Boolean = InetAddress::isLocalNetwork,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isHttps) {
            val route = chain.connection()?.route()
            val address = route?.socketAddress?.address
            // Through a proxy the socket address is the proxy's, and the request would travel on in cleartext.
            val direct = route?.proxy?.type() == Proxy.Type.DIRECT
            if (!direct || address == null || !isAllowed(address)) throw CleartextNotAllowedException(request.url.host)
        }
        return chain.proceed(request)
    }
}

/** Cleartext HTTP to [host] was refused because it is not on a local network. */
class CleartextNotAllowedException(
    host: String,
) : IOException("Cleartext HTTP is only allowed on local networks: $host")
