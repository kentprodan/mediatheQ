package io.github.kentprodan.mediatheq.tv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import dagger.hilt.android.HiltAndroidApp
import io.github.kentprodan.mediatheq.core.common.FileAppLog
import io.github.kentprodan.mediatheq.core.data.ArtworkInterceptor
import io.github.kentprodan.mediatheq.core.data.ArtworkUrlResolver
import io.github.kentprodan.mediatheq.core.data.DefaultLibrarySyncRepository
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

/**
 * The TV app (ADR 0038). Starts the library sync (after sign-in, then daily; a wipe on sign-out), like the phone. Live
 * sync and favorite downloads start with the TV screens that need them.
 */
@HiltAndroidApp
class MediatheqTvApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var librarySync: DefaultLibrarySyncRepository

    @Inject
    lateinit var log: FileAppLog

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var artworkUrls: ArtworkUrlResolver

    override fun onCreate() {
        super.onCreate()
        // A crash goes into the app's log before the process ends (#169); nothing is sent anywhere.
        val crash = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { log.crash("Crash", "Uncaught on ${thread.name}", error) }
            crash?.uncaughtException(thread, error)
        }
        log.info("App", "Started (TV)")
        librarySync.start()
    }

    /**
     * Covers (#236) load through the app's OkHttp client, so the local-network cleartext rule applies (ADR 0013); the
     * service loader is off so Coil adds no client of its own. A TV is on a fixed network: no Wi-Fi-only rule, and the
     * cache stays in the cache directory.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .serviceLoaderEnabled(false)
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache").toOkioPath()).build() }
            .components {
                add(ArtworkInterceptor(artworkUrls::url))
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }.build()
}
