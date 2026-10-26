package io.github.kentprodan.mediatheq.core.datastore

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Whether a play queue is saved, readable at once: a media button with the app closed must decide on the spot whether
 * the playback service may start (it has to play, or the system kills the app).
 */
interface SavedQueueFlag {
    var isSet: Boolean
}

internal class SharedPreferencesSavedQueueFlag
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : SavedQueueFlag {
        private val preferences = context.getSharedPreferences("saved_queue", Context.MODE_PRIVATE)

        override var isSet: Boolean
            get() = preferences.getBoolean(KEY, false)
            set(value) = preferences.edit { putBoolean(KEY, value) }

        private companion object {
            const val KEY = "is_set"
        }
    }
