package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * Least-recently-used eviction like Media3's own, but with a size that can change while the app runs (the streaming
 * cache setting). A smaller size takes effect at the next write into the cache. Called by the cache on its own thread.
 */
@UnstableApi
internal class ResizableLruCacheEvictor(
    @Volatile var maxBytes: Long,
) : CacheEvictor {
    private val spans = TreeSet(compareBy<CacheSpan> { it.lastTouchTimestamp }.thenBy { it })
    private var currentBytes = 0L

    override fun requiresCacheSpanTouches() = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(
        cache: Cache,
        key: String,
        position: Long,
        length: Long,
    ) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(
        cache: Cache,
        span: CacheSpan,
    ) {
        spans += span
        currentBytes += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(
        cache: Cache,
        span: CacheSpan,
    ) {
        spans -= span
        currentBytes -= span.length
    }

    override fun onSpanTouched(
        cache: Cache,
        oldSpan: CacheSpan,
        newSpan: CacheSpan,
    ) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /** Removes the least recently used spans until [required] more bytes fit; removal calls [onSpanRemoved]. */
    private fun evict(
        cache: Cache,
        required: Long,
    ) {
        while (currentBytes + required > maxBytes && spans.isNotEmpty()) cache.removeSpan(spans.first())
    }
}

/** Bytes in [gb] gibibytes. */
internal fun gibibytes(gb: Int): Long = gb * 1024L * 1024 * 1024
