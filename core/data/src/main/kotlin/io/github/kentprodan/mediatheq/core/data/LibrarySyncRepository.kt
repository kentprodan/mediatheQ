package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqResult
import kotlinx.coroutines.flow.StateFlow

/** Keeps the local library in step with the server (offline-first, architecture skill section 4). */
interface LibrarySyncRepository {
    val status: StateFlow<SyncStatus>

    /** Runs a full sync now, or joins the one already running. Resumes a sync that was interrupted. */
    suspend fun sync(): MqResult<Unit>
}
