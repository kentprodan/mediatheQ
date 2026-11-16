package io.github.kentprodan.mediatheq.core.network

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.common.ServerAddressSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Several addresses for one server (#382): home on the LAN, away over Tailscale or the internet. Every request to any
 * of them goes to the [active] one, so the API, streams, covers and the socket follow without knowing; cache keys keep
 * the URLs the app built. The addresses are probed together (any HTTP answer counts) when they or the network change,
 * and every 30 s while a fallback is active, to come back to the preferred one. A request that cannot connect tries
 * the others at once (any I/O failure for a GET or HEAD, only a failed connection otherwise, so nothing is sent twice).
 */
@Singleton
class AddressFailover internal constructor(
    source: ServerAddressSource,
    online: Flow<Boolean>,
    scope: CoroutineScope,
    private val probeClient: OkHttpClient,
    private val recheck: Duration,
) : Interceptor {
    @Inject
    constructor(
        source: ServerAddressSource,
        network: NetworkMonitor,
        @ApplicationScope scope: CoroutineScope,
    ) : this(source, network.online, scope, probeClient(), 30.seconds)

    @Volatile private var candidates: List<HttpUrl> = emptyList()

    /** Where requests go now; `null` before the first probe (requests then keep their own address). */
    @Volatile var active: HttpUrl? = null
        private set

    init {
        scope.launch {
            combine(source.addresses, online) { addresses, _ -> addresses }.collectLatest { addresses ->
                candidates = addresses.mapNotNull { it.trimEnd('/').toHttpUrlOrNull() }.distinct()
                active = candidates.firstOrNull()
                if (candidates.size < 2) return@collectLatest
                probe()
                while (active != candidates.first()) {
                    delay(recheck)
                    probe()
                }
            }
        }
    }

    /** Asks every address at once and makes the first one (in preference order) that answered active. */
    internal suspend fun probe() {
        val list = candidates
        val answered =
            withContext(Dispatchers.IO) {
                list.map { url -> async { answers(url) } }.awaitAll()
            }
        list.zip(answered).firstOrNull { it.second }?.let { active = it.first }
    }

    private fun answers(url: HttpUrl): Boolean =
        try {
            probeClient.newCall(Request.Builder().url(url).build()).execute().use { true }
        } catch (_: IOException) {
            false
        }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val list = candidates
        val from = list.firstOrNull { request.url.isUnder(it) }
        if (list.size < 2 || from == null) return chain.proceed(request)
        val order = listOfNotNull(active) + list.filter { it != active }
        var failure: IOException? = null
        for (target in order) {
            try {
                val response = chain.proceed(request.newBuilder().url(request.url.moved(from, target)).build())
                if (target != active) active = target
                return response
            } catch (e: IOException) {
                if (!request.retriable(e)) throw e
                failure = e
            }
        }
        throw failure ?: IOException("No address of the server answered")
    }

    private companion object {
        val PROBE_TIMEOUT = 4.seconds

        fun probeClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(PROBE_TIMEOUT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .callTimeout(PROBE_TIMEOUT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .addNetworkInterceptor(CleartextPolicyInterceptor())
                .build()

        /** A failure worth another address: anything for a read, only a connection that never opened otherwise. */
        fun Request.retriable(e: IOException): Boolean =
            method == "GET" || method == "HEAD" ||
                e is ConnectException || e is UnknownHostException || e is NoRouteToHostException

        fun HttpUrl.isUnder(base: HttpUrl): Boolean =
            scheme == base.scheme && host == base.host && port == base.port &&
                encodedPath.startsWith(base.encodedPath.trimEnd('/'))

        /** This URL with [from]'s scheme, host, port and path prefix replaced by [to]'s. */
        fun HttpUrl.moved(
            from: HttpUrl,
            to: HttpUrl,
        ): HttpUrl {
            if (from == to) return this
            val rest = encodedPath.removePrefix(from.encodedPath.trimEnd('/'))
            return newBuilder()
                .scheme(to.scheme)
                .host(to.host)
                .port(to.port)
                .encodedPath(to.encodedPath.trimEnd('/') + rest)
                .build()
        }
    }
}
