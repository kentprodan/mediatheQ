package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.database.dao.LibraryDao
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.SearchHit
import io.github.kentprodan.mediatheq.core.model.ServerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultLibraryRepository
    @Inject
    constructor(
        private val libraryDao: LibraryDao,
    ) : LibraryRepository {
        override fun albums(): Flow<List<Album>> = libraryDao.albums().map { rows -> rows.map { it.toDomain() } }

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<SearchHit> {
            val match = ftsMatchExpression(query) ?: return emptyList()
            return libraryDao.search(match, limit).mapNotNull { row ->
                val kind = SearchHit.Kind.entries.firstOrNull { it.name.equals(row.kind, ignoreCase = true) }
                kind?.let { SearchHit(it, MediaId(ServerId(row.serverId), row.itemId), row.name) }
            }
        }
    }
