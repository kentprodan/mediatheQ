package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Turns an [Artwork] into an image URL for the image loader (wired in `:app`). */
class ArtworkUrlResolver
    @Inject
    constructor(
        private val accountStore: AccountStore,
        private val librarySource: LibrarySource,
    ) {
        /** Waits for a signed-in server (covers can be requested a moment before the account is read), then builds the URL. */
        suspend fun url(
            artwork: Artwork,
            widthPx: Int?,
        ): String {
            val baseUrl =
                accountStore.account
                    .map { (it as? MqResult.Success)?.value?.baseUrl }
                    .filterNotNull()
                    .first()
            return librarySource.imageUrl(baseUrl, artwork, artworkWidthBucket(widthPx))
        }
    }

/**
 * Rounds a requested width up to a few fixed sizes, so 2, 3 or 4 columns and list rows share cached images instead of
 * each asking the server for a slightly different size. Unknown sizes get a medium image.
 */
internal fun artworkWidthBucket(widthPx: Int?): Int =
    when {
        widthPx == null || widthPx <= 0 -> 512
        else -> ARTWORK_WIDTHS.firstOrNull { it >= widthPx } ?: ARTWORK_WIDTHS.last()
    }

private val ARTWORK_WIDTHS = listOf(128, 256, 512, 1024)
