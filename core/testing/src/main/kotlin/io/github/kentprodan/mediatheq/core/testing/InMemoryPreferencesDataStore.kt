package io.github.kentprodan.mediatheq.core.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * A preferences DataStore in memory: no files, so tests behave the same on Windows and Linux. Set [failing] to make
 * reads and writes throw `IOException` like a broken disk.
 */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(emptyPreferences())
    var failing = false

    /** What is stored right now, bypassing [failing]. */
    val current: Preferences get() = state.value

    override val data: Flow<Preferences> = state.onEach { if (failing) throw IOException("disk failure") }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            if (failing) throw IOException("disk failure")
            transform(state.value).also { state.value = it }
        }
}
