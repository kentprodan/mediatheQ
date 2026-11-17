package io.github.kentprodan.mediatheq.tv

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import androidx.navigation3.runtime.NavKey
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.ArtworkUrlResolver
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.MovieKey
import io.github.kentprodan.mediatheq.core.navigation.SeriesKey
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** One suggestion of the system search (#386). */
data class SearchRow(
    val title: String,
    val subtitle: String?,
    val image: String?,
    val link: Uri,
    val year: Int? = null,
    val durationMs: Long? = null,
)

/** The suggestions as the system search reads them; each opens its link with VIEW. */
fun suggestionCursor(rows: List<SearchRow>): MatrixCursor =
    MatrixCursor(COLUMNS).apply {
        rows.forEachIndexed { index, row ->
            addRow(
                arrayOf<Any?>(
                    index.toLong(),
                    row.title,
                    row.subtitle,
                    row.image,
                    row.year,
                    row.durationMs,
                    row.link.toString(),
                    Intent.ACTION_VIEW,
                ),
            )
        }
    }

private val COLUMNS =
    arrayOf(
        BaseColumns._ID,
        SearchManager.SUGGEST_COLUMN_TEXT_1,
        SearchManager.SUGGEST_COLUMN_TEXT_2,
        SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
        SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,
        SearchManager.SUGGEST_COLUMN_DURATION,
        SearchManager.SUGGEST_COLUMN_INTENT_DATA,
        SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
    )

/** The link a suggestion opens: `mediatheq://open/<movie|series|album>/<server>/<item>`. */
fun openUri(
    kind: String,
    id: MediaId,
): Uri =
    Uri
        .Builder()
        .scheme(SCHEME)
        .authority(OPEN)
        .appendPath(kind)
        .appendPath(id.serverId.value)
        .appendPath(id.itemId)
        .build()

/** The page an [openUri] names; `null` for any other link. */
fun parseOpenUri(uri: Uri?): NavKey? {
    if (uri == null || uri.scheme != SCHEME || uri.host != OPEN) return null
    val (kind, server, item) = uri.pathSegments.takeIf { it.size == 3 } ?: return null
    val id = MediaId(ServerId(server), item)
    return when (kind) {
        MOVIE -> MovieKey(id)
        SERIES -> SeriesKey(id)
        ALBUM -> AlbumKey(id)
        else -> null
    }
}

private const val SCHEME = "mediatheq"
private const val OPEN = "open"
private const val MOVIE = "movie"
private const val SERIES = "series"
private const val ALBUM = "album"

/**
 * The TV's answers to the system search (#386, like the official client's `MediaContentProvider`): movies, series and
 * albums of the local library whose title matches. Only the system may read it (`GLOBAL_SEARCH`).
 */
class TvSearchProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun videos(): VideoRepository

        fun library(): LibraryRepository

        fun artwork(): ArtworkUrlResolver
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val query =
            selectionArgs?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.takeUnless { it == SearchManager.SUGGEST_URI_PATH_QUERY }
        val limit = uri.getQueryParameter(SearchManager.SUGGEST_PARAMETER_LIMIT)?.toIntOrNull() ?: LIMIT
        val context = context ?: return suggestionCursor(emptyList())
        if (query.isNullOrBlank()) return suggestionCursor(emptyList())
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        // The search binder thread waits; a slow database never holds it longer than the timeout.
        val rows = runBlocking { withTimeoutOrNull(TIMEOUT) { rows(deps, query.trim(), limit) } }.orEmpty()
        return suggestionCursor(rows)
    }

    private suspend fun rows(
        deps: Dependencies,
        query: String,
        limit: Int,
    ): List<SearchRow> {
        val artwork = deps.artwork()
        val videos = (deps.videos().search(query, limit) as? MqResult.Success)?.value
        val albums = (deps.library().search(query, limit) as? MqResult.Success)?.value?.albums.orEmpty()
        val movies =
            videos?.movies.orEmpty().map {
                SearchRow(
                    it.title,
                    it.year?.toString(),
                    it.artwork?.let { a -> artwork.url(a, IMAGE_WIDTH) },
                    openUri(MOVIE, it.id),
                    it.year,
                    it.duration.inWholeMilliseconds,
                )
            }
        val series =
            videos?.series.orEmpty().map {
                SearchRow(
                    it.title,
                    it.year?.toString(),
                    it.artwork?.let { a -> artwork.url(a, IMAGE_WIDTH) },
                    openUri(SERIES, it.id),
                    it.year,
                )
            }
        val albumRows =
            albums.map {
                SearchRow(
                    it.title,
                    it.albumArtists.joinToString { a -> a.name }.ifEmpty { null },
                    it.artwork?.let { a -> artwork.url(a, IMAGE_WIDTH) },
                    openUri(ALBUM, it.id),
                    it.year,
                )
            }
        return (movies + series + albumRows).take(limit)
    }

    override fun getType(uri: Uri): String = SearchManager.SUGGEST_MIME_TYPE

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val LIMIT = 15
        const val IMAGE_WIDTH = 320
        val TIMEOUT = 3.seconds
    }
}
