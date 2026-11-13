package io.github.kentprodan.mediatheq

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
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import io.github.kentprodan.mediatheq.core.data.ArtworkInterceptor
import io.github.kentprodan.mediatheq.core.data.ArtworkUrlResolver
import io.github.kentprodan.mediatheq.core.data.DefaultLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.DownloadStorageRepository
import io.github.kentprodan.mediatheq.core.data.DownloadedCovers
import io.github.kentprodan.mediatheq.core.data.FavoriteDownloads
import io.github.kentprodan.mediatheq.core.data.ImagePreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LiveSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
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

    @Inject
    lateinit var favoriteDownloads: FavoriteDownloads

    @Inject
    lateinit var libraryPreferences: LibraryPreferencesRepository

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var downloadStorage: DownloadStorageRepository

    @Inject
    lateinit var accounts: AccountRepository

    @Inject
    lateinit var log: FileAppLog

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    // Images settings (#144), kept in memory: the image loader asks on every request.
    private lateinit var images: StateFlow<ImagePreferences?>
    private lateinit var metered: StateFlow<Boolean>

    override fun onCreate() {
        super.onCreate()
        // A crash goes into the app's log before the process ends (#169); nothing is sent anywhere.
        val crash = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { log.crash("Crash", "Uncaught on ${thread.name}", error) }
            crash?.uncaughtException(thread, error)
        }
        log.info("App", "Started")
        // Syncs after login and when a day has passed; wipes the library on sign-out (issue #9).
        librarySync.start()
        // Live updates while the app is visible (or music plays, told by the playback service), issue #18.
        liveSync.start()
        // Keeps the covers of downloaded albums on the device, so they show offline (#154).
        downloadedCovers.start()
        // Downloads the favorites by themselves, per Settings -> Offline, cache, and download -> Media (#145).
        favoriteDownloads.start()
        // The database of an account removed while it was open goes now that another one is open (#147).
        applicationScope.launch { accounts.cleanUp() }
        images =
            libraryPreferences.preferences.map { it.images }.stateIn(applicationScope, SharingStarted.Eagerly, null)
        metered =
            networkMonitor.meteredChanges.stateIn(applicationScope, SharingStarted.Eagerly, networkMonitor.isMetered())
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = liveSync.setForeground(true)

                override fun onStop(owner: LifecycleOwner) = liveSync.setForeground(false)
            },
        )
    }

    /**
     * Covers load through the app OkHttp client, so the local-network cleartext rule applies (ADR 0013). The service
     * loader is off: otherwise Coil would add its own client, which skips that rule. The disk cache's place follows
     * Persistent image cache as it was when the loader was made (#144): a change applies at the next app start. A
     * persistent cache lives with the downloads (#146: internal storage or the SD card), internal while the card is out.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .serviceLoaderEnabled(false)
            .diskCache {
                // Coil asks for it on a fetch thread, once; the settings are read by then.
                val persistent = runBlocking { images.filterNotNull().first() }.persistentCache
                val files = downloadStorage.filesDir
                unusedImageCacheDir(persistent, files, cacheDir).deleteRecursively()
                // What a move left behind (#146); never the card's copy while the card is out.
                runBlocking { downloadStorage.otherFilesDirs() }
                    .filter { it != files }
                    .forEach { imageCacheDir(persistent = true, it, cacheDir).deleteRecursively() }
                DiskCache
                    .Builder()
                    .directory(imageCacheDir(persistent, files, cacheDir).toOkioPath())
                    .build()
            }.components {
                // On the main thread for every cover: values in memory only, no system call.
                add(
                    ArtworkInterceptor(artworkUrls::url) {
                        imageNetworkPolicy(images.value?.wifiOnly == true, metered.value)
                    },
                )
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }.build()
}
