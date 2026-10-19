package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Turns an [Artwork] into an image URL for the image loader (wired in `:app`). */
@Singleton
class ArtworkUrlResolver
    @Inject
    constructor(
        accountStore: AccountStore,
        private val librarySource: LibrarySource,
        @ApplicationScope scope: CoroutineScope,
    ) {
        // Kept in memory: covers are resolved on the main thread while scrolling, so this must not touch disk.
        private val baseUrl: StateFlow<String?> = accountStore.baseUrl.stateIn(scope, SharingStarted.Eagerly, null)

        /** Returns at once when signed in; a cover requested before the account is read waits for it. */
        suspend fun url(
            artwork: Artwork,
            widthPx: Int?,
        ): String = librarySource.imageUrl(baseUrl.filterNotNull().first(), artwork, artworkWidthBucket(widthPx))
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
