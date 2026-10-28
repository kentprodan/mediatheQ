package io.github.kentprodan.mediatheq

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import dagger.hilt.android.HiltAndroidApp
import io.github.kentprodan.mediatheq.core.data.ArtworkUrlResolver
import io.github.kentprodan.mediatheq.core.data.DefaultLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.DownloadedCovers
import io.github.kentprodan.mediatheq.core.data.LiveSync
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MediatheqApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var librarySync: DefaultLibrarySyncRepository

    @Inject
    lateinit var liveSync: LiveSync

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var artworkUrls: ArtworkUrlResolver

    @Inject
    lateinit var downloadedCovers: DownloadedCovers

    override fun onCreate() {
        super.onCreate()
        // Syncs after login and when a day has passed; wipes the library on sign-out (issue #9).
        librarySync.start()
        // Live updates while the app is visible (or music plays, told by the playback service), issue #18.
        liveSync.start()
        // Keeps the covers of downloaded albums on the device, so they show offline (#154).
        downloadedCovers.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = liveSync.setForeground(true)

                override fun onStop(owner: LifecycleOwner) = liveSync.setForeground(false)
            },
        )
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
