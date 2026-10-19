package io.github.kentprodan.mediatheq.provider.jellyfin

import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URLEncoder

/**
 * Adds the `Authorization: MediaBrowser ...` header (R1) to every request tagged with a [ServerConnection]. The only
 * place that authenticates Jellyfin requests: never `api_key` or legacy `X-Emby-*` headers.
 */
internal class JellyfinAuthInterceptor(
    private val clientInfo: ClientInfo,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val connection = request.tag(ServerConnection::class) ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header("Authorization", authorizationHeader(clientInfo, connection)).build(),
        )
    }
}

/** The MediaBrowser header; values are URL-encoded, which Jellyfin decodes (quotes and commas in a device name). */
internal fun authorizationHeader(
    clientInfo: ClientInfo,
    connection: ServerConnection,
): String {
    val parts =
        listOfNotNull(
            "Client" to clientInfo.client,
            "Device" to clientInfo.device,
            "DeviceId" to connection.deviceId,
            "Version" to clientInfo.version,
            connection.accessToken?.let { "Token" to it },
        )
    return "MediaBrowser " + parts.joinToString(", ") { (key, value) -> "$key=\"${encode(value)}\"" }
}

private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
