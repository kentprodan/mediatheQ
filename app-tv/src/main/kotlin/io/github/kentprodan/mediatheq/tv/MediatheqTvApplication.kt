package io.github.kentprodan.mediatheq.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.kentprodan.mediatheq.core.common.FileAppLog
import io.github.kentprodan.mediatheq.core.data.DefaultLibrarySyncRepository
import javax.inject.Inject

/**
 * The TV app (ADR 0038). Starts the library sync (after sign-in, then daily; a wipe on sign-out), like the phone. Live
 * sync, cover downloads and favorite downloads start with the TV screens that need them.
 */
@HiltAndroidApp
class MediatheqTvApplication : Application() {
    @Inject
    lateinit var librarySync: DefaultLibrarySyncRepository

    @Inject
    lateinit var log: FileAppLog

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
}
