package io.github.kentprodan.mediatheq

import androidx.test.core.app.ApplicationProvider
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.size.Size
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Artwork
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtworkInterceptorTest {
    private var policy = CachePolicy.ENABLED
    private val interceptor =
        ArtworkInterceptor({ artwork, width -> "http://nas/${artwork.itemId}?w=$width" }) { policy }

    /** A chain that keeps the request the interceptor hands on. */
    private class Chain(
        override val request: ImageRequest,
        override val size: Size = Size(300, 300),
    ) : Interceptor.Chain {
        var proceeded: ImageRequest? = null

        override fun withRequest(request: ImageRequest): Interceptor.Chain = Chain(request, size).also { next = it }

        override fun withSize(size: Size): Interceptor.Chain = Chain(request, size)

        override suspend fun proceed(): ImageResult {
            proceeded = request
            return ErrorResult(null, request, IllegalStateException("not loaded in tests"))
        }

        var next: Chain? = null
    }

    private suspend fun handed(data: Any): ImageRequest {
        val chain = Chain(ImageRequest.Builder(ApplicationProvider.getApplicationContext()).data(data).build())
        interceptor.intercept(chain)
        return chain.next!!.proceeded!!
    }

    @Test
    fun `an artwork becomes its sized URL with the network allowed`() =
        runTest {
            val request = handed(Artwork("al1", "tag"))

            assertThat(request.data).isEqualTo("http://nas/al1?w=300")
            assertThat(request.networkCachePolicy).isEqualTo(CachePolicy.ENABLED)
        }

    @Test
    fun `with Wi-Fi only images on mobile data no request may use the network`() =
        runTest {
            policy = CachePolicy.DISABLED

            assertThat(handed(Artwork("al1", "tag")).networkCachePolicy).isEqualTo(CachePolicy.DISABLED)
            assertThat(handed("http://nas/other.jpg").networkCachePolicy).isEqualTo(CachePolicy.DISABLED)
        }
}
