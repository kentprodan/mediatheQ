package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.BackupCheck
import io.github.kentprodan.mediatheq.core.data.BackupRepository
import io.github.kentprodan.mediatheq.core.data.BackupRestore

/** [BackupRepository] answering with [writes], [check] and [restoreResult]; records the URIs it was given. */
class FakeBackupRepository : BackupRepository {
    var writes = true
    var check: BackupCheck = BackupCheck.Invalid
    var restoreResult: BackupRestore = BackupRestore.Restored(0)
    val written = mutableListOf<String>()
    val restored = mutableListOf<String>()

    override suspend fun write(uri: String): Boolean = writes.also { written += uri }

    override suspend fun read(uri: String): BackupCheck = check

    override suspend fun restore(uri: String): BackupRestore = restoreResult.also { restored += uri }
}
