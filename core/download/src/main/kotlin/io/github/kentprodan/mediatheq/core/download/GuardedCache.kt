package io.github.kentprodan.mediatheq.core.download

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.DefaultContentMetadata
import java.io.File
import java.util.NavigableSet
import java.util.TreeSet

/**
 * The download cache, touched only while its volume is there (#146, ADR 0035). A SimpleCache that misses one file drops
 * every span whose file is gone, which on a removed SD card would erase the whole index; here nothing reaches it then.
 * Opened on first use with the volume present, so a card put in after the app started works without a restart.
 * shortcut: a card pulled between the check and SimpleCache's own read can still drop spans; a lock with the mount
 * broadcast if that shows up.
 */
@UnstableApi
internal class GuardedCache(
    private val root: File,
    private val available: () -> Boolean,
    private val open: () -> Cache,
) : Cache {
    @Volatile private var cache: Cache? = null

    private fun cache(): Cache? {
        if (!available() || !root.exists()) return null
        return cache ?: synchronized(this) { cache ?: open().also { cache = it } }
    }

    private fun required(): Cache = cache() ?: throw Cache.CacheException("The download storage is not available")

    override fun getUid(): Long = cache()?.uid ?: UID_UNSET

    override fun release() {
        synchronized(this) { cache?.release() }
    }

    override fun addListener(
        key: String,
        listener: Cache.Listener,
    ): NavigableSet<CacheSpan> = cache()?.addListener(key, listener) ?: TreeSet()

    override fun removeListener(
        key: String,
        listener: Cache.Listener,
    ) {
        cache?.removeListener(key, listener)
    }

    override fun getCachedSpans(key: String): NavigableSet<CacheSpan> = cache()?.getCachedSpans(key) ?: TreeSet()

    override fun getKeys(): Set<String> = cache()?.keys.orEmpty()

    override fun getCacheSpace(): Long = cache()?.cacheSpace ?: 0

    override fun startReadWrite(
        key: String,
        position: Long,
        length: Long,
    ): CacheSpan = required().startReadWrite(key, position, length)

    override fun startReadWriteNonBlocking(
        key: String,
        position: Long,
        length: Long,
    ): CacheSpan? = required().startReadWriteNonBlocking(key, position, length)

    override fun startFile(
        key: String,
        position: Long,
        length: Long,
    ): File = required().startFile(key, position, length)

    override fun commitFile(
        file: File,
        length: Long,
    ) = required().commitFile(file, length)

    override fun releaseHoleSpan(holeSpan: CacheSpan) {
        // A hole's lock is in memory only: always given back.
        cache?.releaseHoleSpan(holeSpan)
    }

    // shortcut: a download removed while the card is out leaves its bytes on the card; clean up orphans if it matters.
    override fun removeResource(key: String) {
        cache()?.removeResource(key)
    }

    override fun removeSpan(span: CacheSpan) {
        cache()?.removeSpan(span)
    }

    override fun isCached(
        key: String,
        position: Long,
        length: Long,
    ): Boolean = cache()?.isCached(key, position, length) ?: false

    override fun getCachedLength(
        key: String,
        position: Long,
        length: Long,
    ): Long = cache()?.getCachedLength(key, position, length) ?: -(if (length == C.LENGTH_UNSET.toLong()) Long.MAX_VALUE else length)

    override fun getCachedBytes(
        key: String,
        position: Long,
        length: Long,
    ): Long = cache()?.getCachedBytes(key, position, length) ?: 0

    override fun applyContentMetadataMutations(
        key: String,
        mutations: ContentMetadataMutations,
    ) = required().applyContentMetadataMutations(key, mutations)

    override fun getContentMetadata(key: String): ContentMetadata = cache()?.getContentMetadata(key) ?: DefaultContentMetadata.EMPTY

    private companion object {
        const val UID_UNSET = -1L
    }
}
