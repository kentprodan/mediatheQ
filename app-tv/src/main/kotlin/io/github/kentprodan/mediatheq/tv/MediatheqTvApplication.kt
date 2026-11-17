package io.github.kentprodan.mediatheq.tv

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import dagger.hilt.android.HiltAndroidApp
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.FileAppLog
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.ArtworkInterceptor
import io.github.kentprodan.mediatheq.core.data.ArtworkUrlResolver
import io.github.kentprodan.mediatheq.core.data.DefaultLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.LiveSync
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.playback.RemoteControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var appMode: AppModeRepository

    @Inject
    lateinit var liveSync: LiveSync

    @Inject
    lateinit var remoteControl: RemoteControl

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

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
        // Live updates and remote commands (#389) while the TV app is open or plays: Jellyfin web and the phone can
        // then control it.
        liveSync.start()
        remoteControl.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = liveSync.setForeground(true)

                override fun onStop(owner: LifecycleOwner) = liveSync.setForeground(false)
            },
        )
        // A TV with no mode chosen shows music and video when the server has video (#426); Settings changes it.
        applicationScope.launch {
            if (appMode.videoAvailable && !appMode.chosen.first()) appMode.setMode(AppMode.Both)
        }
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
