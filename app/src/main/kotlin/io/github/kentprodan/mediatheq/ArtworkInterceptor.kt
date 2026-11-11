package io.github.kentprodan.mediatheq

import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageResult
import coil3.size.pxOrElse
import io.github.kentprodan.mediatheq.core.model.Artwork

/**
 * Lets screens pass an [Artwork] to the image loader; it becomes a sized server URL here (`ArtworkUrlResolver`). [networkPolicy] says whether
 * the network may be used now (Wi-Fi only image downloads, #144): a cover not cached yet then keeps its placeholder.
 */
class ArtworkInterceptor(
    /** [ArtworkUrlResolver.url]: the URL of an artwork at a width (-1 = unknown). */
    private val url: suspend (Artwork, Int?) -> String,
    private val networkPolicy: () -> CachePolicy = { CachePolicy.ENABLED },
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request.newBuilder().networkCachePolicy(networkPolicy())
        val artwork = chain.request.data as? Artwork
        if (artwork != null) request.data(url(artwork, chain.size.width.pxOrElse { -1 }))
        return chain.withRequest(request.build()).proceed()
    }
}
