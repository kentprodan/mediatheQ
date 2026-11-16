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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
    networkChanges: Flow<Any>,
    scope: CoroutineScope,
    private val probeClient: OkHttpClient,
    private val recheck: Duration,
) : Interceptor {
    @Inject
    constructor(
        source: ServerAddressSource,
        network: NetworkMonitor,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        source,
        // Wi-Fi to mobile data keeps "online" true but changes "metered": both count as a new network.
        combine(network.online, network.meteredChanges, ::Pair),
        scope,
        probeClient(),
        30.seconds,
    )

    @Volatile private var candidates: List<HttpUrl> = emptyList()

    private val current = MutableStateFlow<HttpUrl?>(null)

    /** Where requests go now; `null` before the addresses are known (requests then keep their own address). */
    val active: HttpUrl? get() = current.value

    init {
        scope.launch {
            combine(source.addresses, networkChanges) { addresses, _ -> addresses }.collectLatest { addresses ->
                candidates = addresses.mapNotNull { it.trimEnd('/').toHttpUrlOrNull() }.distinct()
                // The current address stays until a probe decides: a new network must not stall every request.
                if (current.value !in candidates) current.value = candidates.firstOrNull()
                if (candidates.size < 2) return@collectLatest
                probe()
                var wait = recheck
                while (true) {
                    // On the preferred address: nothing to do until a failed request moves away from it.
                    if (current.value == candidates.first()) {
                        current.first { it != candidates.first() }
                        wait = recheck
                    }
                    delay(wait)
                    probe()
                    // Away for long (no home network around): ask less and less often, up to every 15 minutes.
                    wait = (wait * 2).coerceAtMost(MAX_RECHECK)
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
        list.zip(answered).firstOrNull { it.second }?.let { current.value = it.first }
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
        val now = current.value
        val order = listOfNotNull(now) + list.filter { it != now }
        var failure: IOException? = null
        for (target in order) {
            try {
                val response = chain.proceed(request.newBuilder().url(request.url.moved(from, target)).build())
                if (target != current.value) current.value = target
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
        val MAX_RECHECK = 15.minutes

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
                e is ConnectException || e is UnknownHostException || e is NoRouteToHostException ||
                // A connect timeout ("failed to connect to ... after 10000ms"): the request was never sent.
                (e is SocketTimeoutException && e.message.orEmpty().contains("connect", ignoreCase = true))

        fun HttpUrl.isUnder(base: HttpUrl): Boolean {
            val prefix = base.encodedPath.trimEnd('/')
            return scheme == base.scheme && host == base.host && port == base.port &&
                (prefix.isEmpty() || encodedPath == prefix || encodedPath.startsWith(prefix + "/"))
        }

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
                .encodedPath((to.encodedPath.trimEnd('/') + rest).ifEmpty { "/" })
                .build()
        }
    }
}
