package io.github.kentprodan.mediatheq.core.network

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.ServerAddressSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/** #382: home address down, the other one answers. */
class AddressFailoverTest {
    private val home = MockWebServer().apply { start() }
    private val away = MockWebServer().apply { start() }
    private val homeUrl = home.url("/jf").toString()
    private val awayUrl = away.url("/").toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val addresses = MutableStateFlow(listOf(homeUrl, awayUrl))

    private val probes = OkHttpClient.Builder().connectTimeout(1, TimeUnit.SECONDS).build()

    private fun failover() =
        AddressFailover(
            object : ServerAddressSource {
                override val addresses = this@AddressFailoverTest.addresses
            },
            flowOf(true),
            scope,
            probes,
            recheck = 200.milliseconds,
        )

    @After
    fun close() {
        scope.cancel()
        away.close()
        runCatching { home.close() }
    }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out" }
            Thread.sleep(20)
        }
    }

    @Test
    fun `with home down, requests to home go to the other address, path prefix replaced`() {
        home.close()
        repeat(20) { away.enqueue(MockResponse(body = "ok")) }
        val failover = failover()
        waitFor { failover.active?.port == away.port }

        val client = OkHttpClient.Builder().addInterceptor(failover).build()
        val body = client.newCall(Request(home.url("/jf/Items?x=1"))).execute().use { it.body.string() }

        assertThat(body).isEqualTo("ok")
        // Probes ask for the root; the request comes among them.
        val request = generateSequence { away.takeRequest() }.first { it.url.encodedPath != "/" }
        assertThat(request.url.encodedPath).isEqualTo("/Items")
        assertThat(request.url.queryParameter("x")).isEqualTo("1")
    }

    @Test
    fun `a request that cannot connect tries the next address at once`() {
        // Probes take answers too.
        repeat(20) { away.enqueue(MockResponse(body = "away")) }
        addresses.value =
            listOf(
                away
                    .url("/")
                    .newBuilder()
                    .port(1)
                    .build()
                    .toString(),
                awayUrl,
            )
        val failover = failover()
        // The addresses arrive; whether the probe already moved away or not, the request must reach the other one.
        waitFor { failover.active != null }
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(failover)
                .connectTimeout(1, TimeUnit.SECONDS)
                .build()

        val body =
            client
                .newCall(
                    Request(
                        away
                            .url("/a")
                            .newBuilder()
                            .port(1)
                            .build(),
                    ),
                ).execute()
                .use { it.body.string() }

        assertThat(body).isEqualTo("away")
    }

    @Test
    fun `other servers are left alone`() {
        val other = MockWebServer().apply { start() }
        other.enqueue(MockResponse(body = "lrclib"))
        val client = OkHttpClient.Builder().addInterceptor(failover()).build()

        val body = client.newCall(Request(other.url("/api/get"))).execute().use { it.body.string() }

        assertThat(body).isEqualTo("lrclib")
        other.close()
    }

    @Test
    fun `any answer, even an error, makes the home address active`() {
        home.enqueue(MockResponse(code = 503))
        val failover = failover()
        waitFor { failover.active?.port == home.port }
    }
}
