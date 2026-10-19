package io.github.kentprodan.mediatheq

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import dagger.hilt.android.HiltAndroidApp
import io.github.kentprodan.mediatheq.core.data.ArtworkUrlResolver
import io.github.kentprodan.mediatheq.core.data.DefaultLibrarySyncRepository
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MediatheqApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var librarySync: DefaultLibrarySyncRepository

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var artworkUrls: ArtworkUrlResolver

    override fun onCreate() {
        super.onCreate()
        // Syncs after login and when a day has passed; wipes the library on sign-out (issue #9).
        librarySync.start()
    }

    /**
     * Covers load through the app OkHttp client, so the local-network cleartext rule applies (ADR 0013). The service
     * loader is off: otherwise Coil would add its own client, which skips that rule.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .serviceLoaderEnabled(false)
            .components {
                add(ArtworkInterceptor(artworkUrls))
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }.build()
}
