package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.SearchHit
import kotlinx.coroutines.flow.Flow

/** Reads of the local library. Screens add what they need in their own issues (#10, #11, #15-#17). */
interface LibraryRepository {
    /** All albums by sort name; emits again after each completed sync, never with a half-synced library. */
    fun albums(): Flow<List<Album>>

    /** Diacritic-insensitive search over names; the last word matches as a prefix. Any typed text is safe. */
    suspend fun search(
        query: String,
        limit: Int = 50,
    ): List<SearchHit>
}
