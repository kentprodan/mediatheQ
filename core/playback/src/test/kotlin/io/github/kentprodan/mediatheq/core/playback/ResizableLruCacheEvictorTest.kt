package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files

// Robolectric: the cache index lives in Media3's SQLite database.
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class ResizableLruCacheEvictorTest {
    private val evictor = ResizableLruCacheEvictor(maxBytes = 250)
    private val cache =
        SimpleCache(
            Files.createTempDirectory("media").toFile(),
            evictor,
            StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext<Context>()),
        )

    @After
    fun release() = cache.release()

    /** Caches 100 bytes under [key], like a finished streaming read. */
    private fun write(key: String) {
        val hole = cache.startReadWrite(key, 0, 100)!!
        val file = cache.startFile(key, 0, 100)
        file.writeBytes(ByteArray(100))
        cache.commitFile(file, 100)
        cache.releaseHoleSpan(hole)
    }

    private fun cached(key: String) = cache.getCachedBytes(key, 0, 100) == 100L

    @Test
    fun `a full cache drops an older file so a new one fits`() {
        write("a")
        write("b")

        write("c")

        assertThat(cache.cacheSpace).isEqualTo(200)
        assertThat(cached("c")).isTrue()
    }

    @Test
    fun `a smaller size applies at the next write`() {
        write("a")
        write("b")

        evictor.maxBytes = 150
        write("c")

        assertThat(cache.cacheSpace).isAtMost(150)
        assertThat(cached("c")).isTrue()
    }

    @Test
    fun `a larger size keeps more`() {
        evictor.maxBytes = 400
        listOf("a", "b", "c").forEach(::write)

        assertThat(cache.cacheSpace).isEqualTo(300)
    }
}
