package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.SearchHit
import kotlinx.coroutines.flow.MutableStateFlow

/** [LibraryRepository] with settable contents; search matches names case-insensitively. */
class FakeLibraryRepository : LibraryRepository {
    val albums = MutableStateFlow<List<Album>>(emptyList())
    var hits: List<SearchHit> = emptyList()

    override fun albums() = albums

    override suspend fun search(
        query: String,
        limit: Int,
    ) = hits.filter { it.name.contains(query, ignoreCase = true) }.take(limit)
}
