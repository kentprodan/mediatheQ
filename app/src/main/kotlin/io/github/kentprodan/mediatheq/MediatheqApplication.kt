package io.github.kentprodan.mediatheq

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.kentprodan.mediatheq.core.data.DefaultLibrarySyncRepository
import javax.inject.Inject

@HiltAndroidApp
class MediatheqApplication : Application() {
    @Inject
    lateinit var librarySync: DefaultLibrarySyncRepository

    override fun onCreate() {
        super.onCreate()
        // Syncs after login and when a day has passed; wipes the library on sign-out (issue #9).
        librarySync.start()
    }
}
