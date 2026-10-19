package io.github.kentprodan.mediatheq.core.network

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkBindings {
    @Binds
    fun networkMonitor(monitor: ConnectivityNetworkMonitor): NetworkMonitor
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /** The one HTTP client of the app; providers add their own interceptors with `newBuilder()`. */
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = mediatheqOkHttpClient()

    /** JSON settings for server DTOs (jellyfin skill, section 1). */
    @Provides
    @Singleton
    fun json(): Json = MediatheqJson
}

/** Base client: short connect timeout so an unreachable server fails fast, cleartext policy on every connection. */
fun mediatheqOkHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .addNetworkInterceptor(CleartextPolicyInterceptor())
        .build()

/** Lenient JSON for server responses: unknown keys ignored, nulls omitted. */
val MediatheqJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
