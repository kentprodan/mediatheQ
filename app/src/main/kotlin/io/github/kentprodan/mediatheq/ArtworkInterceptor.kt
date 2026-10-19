package io.github.kentprodan.mediatheq

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.pxOrElse
import io.github.kentprodan.mediatheq.core.data.ArtworkUrlResolver
import io.github.kentprodan.mediatheq.core.model.Artwork

/** Lets screens pass an [Artwork] to the image loader; it becomes a sized server URL here. */
class ArtworkInterceptor(
    private val resolver: ArtworkUrlResolver,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val artwork = chain.request.data as? Artwork ?: return chain.proceed()
        val url = resolver.url(artwork, chain.size.width.pxOrElse { -1 })
        return chain
            .withRequest(
                chain.request
                    .newBuilder()
                    .data(url)
                    .build(),
            ).proceed()
    }
}
