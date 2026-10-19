package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow

/** [LibrarySyncRepository] whose status the test sets; [sync] counts calls and returns [result]. */
class FakeLibrarySyncRepository : LibrarySyncRepository {
    override val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle(null))
    var result: MqResult<Unit> = MqResult.Success(Unit)
    var syncCalls = 0
        private set

    override suspend fun sync(): MqResult<Unit> = result.also { syncCalls++ }
}
