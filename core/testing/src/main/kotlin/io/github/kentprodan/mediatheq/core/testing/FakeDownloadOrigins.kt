package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.DownloadOrigin
import io.github.kentprodan.mediatheq.core.data.DownloadOrigins
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** [DownloadOrigins] in memory. */
class FakeDownloadOrigins : DownloadOrigins {
    override val origins = MutableStateFlow<Map<MediaId, Set<DownloadOrigin>>>(emptyMap())

    override suspend fun add(
        tracks: List<MediaId>,
        origin: DownloadOrigin,
    ) = origins.update { current -> current + tracks.associateWith { current[it].orEmpty() + origin } }

    override suspend fun removeCollection(origin: DownloadOrigin) =
        origins.update { current ->
            current.mapValues { (_, origins) -> origins - origin }.filterValues { it.isNotEmpty() }
        }

    override suspend fun remove(
        tracks: List<MediaId>,
        origin: DownloadOrigin,
    ) = origins.update { current ->
        current
            .mapValues { (track, origins) -> if (track in tracks) origins - origin else origins }
            .filterValues { it.isNotEmpty() }
    }

    override suspend fun forget(tracks: List<MediaId>) = origins.update { it - tracks.toSet() }
}
