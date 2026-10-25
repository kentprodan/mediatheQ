package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import javax.inject.Qualifier

/**
 * The LRU cache of streamed tracks (media3 skill, section 9; ADR 0018), separate from the never-evicted downloads.
 * Only original files are kept: they are the same bytes on every request, so a replay or seek can mix cached and fresh
 * ranges. Live transcodes have no length and differ per conversion, so they are never written.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class StreamingCache

/** The key of a track's original file in the streaming cache: stable across sessions and URLs. */
internal fun originalCacheKey(itemId: MediaId): String = "$itemId:original"

/**
 * The key a resolved stream is cached under: the original file's for a direct play; anything else (a converted or
 * remuxed stream) gets a key of its own play session, so its bytes never mix with another stream's.
 */
internal fun streamCacheKey(stream: PlaybackStream): String =
    when (stream.playMethod) {
        PlayMethod.DirectPlay -> originalCacheKey(stream.itemId)
        PlayMethod.DirectStream, PlayMethod.Transcode -> "transcode:${stream.playSessionId}"
    }

/**
 * True when the whole content of [key] is cached. The index may be ahead of the files (the system can clear the cache
 * folder); a read then fails once and the retry resolves the stream normally.
 */
@UnstableApi
internal fun Cache.isFullyCached(key: String): Boolean {
    val length = ContentMetadata.getContentLength(getContentMetadata(key))
    return length != C.LENGTH_UNSET.toLong() && isCached(key, 0, length)
}

/** Drops the cached copy of [key] when the server's file has another size (replaced or re-tagged). */
@UnstableApi
internal fun Cache.dropIfChanged(
    key: String,
    sizeBytes: Long?,
) {
    val cached = ContentMetadata.getContentLength(getContentMetadata(key))
    if (sizeBytes != null && cached != C.LENGTH_UNSET.toLong() && cached != sizeBytes) removeResource(key)
}
