package io.github.kentprodan.mediatheq.core.datastore

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kentprodan.mediatheq.core.common.ActiveDatabase
import io.github.kentprodan.mediatheq.core.common.DEFAULT_DATABASE
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
        @ActiveDatabase database: String,
    ) : SavedQueueFlag {
        private val preferences = context.getSharedPreferences("saved_queue", Context.MODE_PRIVATE)

        // The queue lives in the active account's database (#147); the first account keeps the old key.
        private val key = if (database == DEFAULT_DATABASE) KEY else "$KEY@$database"

        override var isSet: Boolean
            get() = preferences.getBoolean(key, false)
            set(value) = preferences.edit { putBoolean(key, value) }

        private companion object {
            const val KEY = "is_set"
        }
    }
