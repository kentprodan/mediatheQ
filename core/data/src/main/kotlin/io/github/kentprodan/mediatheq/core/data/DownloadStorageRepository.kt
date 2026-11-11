package io.github.kentprodan.mediatheq.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Where downloads and the persistent image cache live (#146, ADR 0035): internal storage or a removable volume (an SD
 * card). A move copies the downloads and applies at the next app start.
 */
interface DownloadStorageRepository {
    /** Internal storage first, then each mounted removable volume; empty without one (the setting is hidden). */
    val locations: Flow<List<StorageLocation>>

    /** False while the downloads' volume is missing: they count as not on the device, nothing is deleted. */
    val available: StateFlow<Boolean>

    /** The name of the removable volume the downloads are on; `null` for internal storage. */
    val locationName: String?

    /** The files folder in use (downloads and the persistent image cache); internal while the volume is missing. */
    val filesDir: File

    val move: StateFlow<StorageMove>

    /** Copies the downloads to [location]; [StorageMove.Done] means the copy is checked and the app must restart. */
    fun moveTo(location: StorageLocation)

    /** Back to [StorageMove.Idle] after a failed move. */
    fun dismissMove()

    /** The files folders not in use, where a leftover copy may be (deleted at start). */
    suspend fun otherFilesDirs(): List<File>
}

/** A place downloads can live: [id] is its files folder, `null` (and no [name]) for internal storage. */
data class StorageLocation(
    val id: String?,
    val name: String?,
    val freeBytes: Long,
    val current: Boolean,
)

sealed interface StorageMove {
    data object Idle : StorageMove

    /** Copying, [fraction] of the bytes done; downloads are paused. */
    data class Moving(
        val fraction: Float,
    ) : StorageMove

    /** Copied and checked, the new location saved: the app restarts to use it. */
    data object Done : StorageMove

    /** Nothing changed; [noSpace] when the location has too little room. */
    data class Failed(
        val noSpace: Boolean,
    ) : StorageMove
}
