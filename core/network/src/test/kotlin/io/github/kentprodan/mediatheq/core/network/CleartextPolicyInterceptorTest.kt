package io.github.kentprodan.mediatheq.core.network

import com.google.common.truth.Truth.assertThat
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class CleartextPolicyInterceptorTest {
    @get:Rule
    val serverRule = MockWebServerRule()

    @Test
    fun `cleartext to a local address is allowed`() {
        serverRule.server.enqueue(MockResponse(body = "ok"))

        val response = mediatheqOkHttpClient().newCall(Request(serverRule.server.url("/"))).execute()

        assertThat(response.body.string()).isEqualTo("ok")
    }

    @Test
    fun `cleartext through a proxy is refused, even when the proxy is local`() {
        val client = mediatheqOkHttpClient().newBuilder().proxy(serverRule.server.proxyAddress).build()

        val call = client.newCall(Request("http://jellyfin.example.com/".toHttpUrl()))

        assertThrows(CleartextNotAllowedException::class.java) { call.execute() }
        assertThat(serverRule.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `cleartext to a non-local address is refused before sending`() {
        val client =
            mediatheqOkHttpClient()
                .newBuilder()
                .apply { networkInterceptors().clear() }
                .addNetworkInterceptor(CleartextPolicyInterceptor(isAllowed = { false }))
                .build()

        val call = client.newCall(Request(serverRule.server.url("/")))

        assertThrows(CleartextNotAllowedException::class.java) { call.execute() }
        assertThat(serverRule.server.requestCount).isEqualTo(0)
    }
}
