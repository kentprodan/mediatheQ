package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.DownloadStorageRepository
import io.github.kentprodan.mediatheq.core.data.StorageLocation
import io.github.kentprodan.mediatheq.core.data.StorageMove
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class FakeDownloadStorageRepository(
    override val filesDir: File = File("files"),
) : DownloadStorageRepository {
    override val locations = MutableStateFlow<List<StorageLocation>>(emptyList())
    override val available = MutableStateFlow(true)
    override var locationName: String? = null
    override val move = MutableStateFlow<StorageMove>(StorageMove.Idle)
    val moves = mutableListOf<StorageLocation>()
    var restarts = 0

    override fun moveTo(location: StorageLocation) {
        moves += location
    }

    override fun dismissMove() {
        move.value = StorageMove.Idle
    }

    override fun restart() {
        restarts++
    }

    override suspend fun otherFilesDirs(): List<File> = emptyList()
}
