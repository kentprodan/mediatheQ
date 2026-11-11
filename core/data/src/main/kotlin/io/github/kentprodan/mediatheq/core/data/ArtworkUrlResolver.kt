package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.ImageType
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns an [Artwork] into an image URL for the image loader (wired in `:app`) and the media session: the kept cover of a
 * download (a `file:` URI, so it shows offline, #154) or the server's image.
 */
@Singleton
class ArtworkUrlResolver
    @Inject
    constructor(
        accountStore: AccountStore,
        private val librarySource: LibrarySource,
        @ApplicationScope scope: CoroutineScope,
        private val downloadedCovers: DownloadedCovers,
        preferences: LibraryPreferencesRepository,
    ) {
        // Kept in memory: covers are resolved on the main thread while scrolling, so this must not touch disk.
        private val baseUrl: StateFlow<String?> = accountStore.baseUrl.stateIn(scope, SharingStarted.Eagerly, null)

        // Settings -> Offline, cache, and download -> Images -> High quality images (#144).
        private val highQuality: StateFlow<Boolean> =
            preferences.preferences.map { it.images.highQuality }.stateIn(scope, SharingStarted.Eagerly, false)

        /** Returns at once when signed in; a cover requested before the account is read waits for it. */
        suspend fun url(
            artwork: Artwork,
            widthPx: Int?,
        ): String {
            downloadedCovers.awaitFirstPass()
            // Downloads keep primary images only; a backdrop shares its item id with the artist's photo.
            val kept = if (artwork.type == ImageType.Primary) downloadedCovers.file(artwork) else null
            return kept?.toURI()?.toString()
                ?: librarySource.imageUrl(
                    baseUrl.filterNotNull().first(),
                    artwork,
                    artworkWidthBucket(widthPx, highQuality.value),
                )
        }
    }

/**
 * Rounds a requested width up to a few fixed sizes, so 2, 3 or 4 columns and list rows share cached images instead of
 * each asking the server for a slightly different size. Unknown sizes get a medium image. [highQuality] (#144) asks one
 * size up, up to 2048 px.
 */
internal fun artworkWidthBucket(
    widthPx: Int?,
    highQuality: Boolean = false,
): Int {
    val widths = if (highQuality) ARTWORK_WIDTHS + HQ_WIDTH else ARTWORK_WIDTHS
    val bucket =
        when {
            widthPx == null || widthPx <= 0 -> 512
            else -> widths.firstOrNull { it >= widthPx } ?: widths.last()
        }
    return if (highQuality) widths.firstOrNull { it > bucket } ?: widths.last() else bucket
}

private val ARTWORK_WIDTHS = listOf(128, 256, 512, 1024)
private const val HQ_WIDTH = 2048
