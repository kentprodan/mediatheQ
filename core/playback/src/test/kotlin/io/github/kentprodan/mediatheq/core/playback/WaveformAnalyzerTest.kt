package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.test.utils.FakeDataSet
import androidx.media3.test.utils.FakeDataSource
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeWaveformRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class WaveformAnalyzerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseProvider = StandaloneDatabaseProvider(context)
    private val downloadCache =
        SimpleCache(Files.createTempDirectory("downloads").toFile(), NoOpCacheEvictor(), databaseProvider)
    private val streamingCache =
        SimpleCache(Files.createTempDirectory("media").toFile(), NoOpCacheEvictor(), databaseProvider)
    private val completed = MutableStateFlow<Set<MediaId>>(emptySet())
    private val files = LocalFiles(completed, downloadCache, streamingCache)
    private val waveforms = FakeWaveformRepository()
    private val server = FakeDataSet().apply { setRandomData("http://nas/file", 100) }

    @After
    fun release() {
        downloadCache.release()
        streamingCache.release()
    }

    private fun id(n: Int) = MediaId(ServerId("s"), "t$n")

    /** Writes the server's 100-byte file under [key], or only its first [bytes] while the length says 100. */
    private fun write(
        cache: SimpleCache,
        key: String,
        bytes: Long = C.LENGTH_UNSET.toLong(),
    ) {
        if (bytes != C.LENGTH_UNSET.toLong()) {
            cache.applyContentMetadataMutations(
                key,
                ContentMetadataMutations().also {
                    ContentMetadataMutations.setContentLength(it, 100)
                },
            )
        }
        val spec =
            DataSpec
                .Builder()
                .setUri(Uri.parse("http://nas/file"))
                .setKey(key)
                .setLength(bytes)
                .build()
        CacheWriter(CacheDataSource(cache, FakeDataSource(server)), spec, null, null).cache()
    }

    /** Records what it decodes; each decode waits for [release] when [hold] is on. */
    private inner class RecordingDecoder(
        var result: Decoded = Decoded.Levels(listOf(0.5f)),
        private val hold: Boolean = false,
    ) : PcmDecoder {
        val decoded = mutableListOf<String>()
        var running = 0
        var mostAtOnce = 0
        val release = CompletableDeferred<Unit>()

        override suspend fun decode(file: LocalFile): Decoded {
            decoded += file.key
            mostAtOnce = maxOf(mostAtOnce, ++running)
            if (hold) release.await()
            running--
            return result
        }
    }

    private fun TestScope.analyzer(decoder: PcmDecoder): Pair<WaveformAnalyzer, Job> {
        val analyzer =
            WaveformAnalyzer(
                scope = CoroutineScope(coroutineContext + Job()),
                worker = StandardTestDispatcher(testScheduler),
                files = files::find,
                decoder = decoder,
                waveforms = waveforms,
                completedDownloads = completed,
            )
        return analyzer to analyzer.start()
    }

    @Test
    fun `a complete download and a whole cached original are found with their size, partial and converted are not`() =
        runTest {
            write(downloadCache, downloadCacheKey(id(1)))
            completed.value = setOf(id(1))
            write(streamingCache, originalCacheKey(id(2)))
            write(streamingCache, originalCacheKey(id(3)), bytes = 50)
            // A converted stream is cached (if at all) under its play session, never the original's key.
            write(streamingCache, "transcode:session-4")
            // A download still running is not in the completed set.
            write(downloadCache, downloadCacheKey(id(5)), bytes = 50)

            assertThat(files.find(id(1))).isEqualTo(LocalFile(downloadCache, downloadCacheKey(id(1)), 100))
            assertThat(files.find(id(2))).isEqualTo(LocalFile(streamingCache, originalCacheKey(id(2)), 100))
            assertThat(files.find(id(3))).isNull()
            assertThat(files.find(id(4))).isNull()
            assertThat(files.find(id(5))).isNull()
        }

    @Test
    fun `a whole file is decoded once per size and kept`() =
        runTest {
            write(streamingCache, originalCacheKey(id(1)))
            val decoder = RecordingDecoder()
            val (analyzer, job) = analyzer(decoder)

            analyzer.offer(listOf(id(1), id(2)))
            advanceUntilIdle()
            analyzer.offer(listOf(id(1)))
            advanceUntilIdle()

            assertThat(decoder.decoded).containsExactly(originalCacheKey(id(1)))
            assertThat(waveforms.stored.value).containsExactly(id(1), 100L to listOf(0.5f))

            // The same track as another file (changed on the server): analyzed again.
            waveforms.save(id(1), 99, listOf(0.1f))
            analyzer.offer(listOf(id(1)))
            advanceUntilIdle()
            assertThat(decoder.decoded).hasSize(2)
            job.cancel()
        }

    @Test
    fun `a format with no decoder is kept as such, a failure is tried again in a later session`() =
        runTest {
            write(streamingCache, originalCacheKey(id(1)))
            write(streamingCache, originalCacheKey(id(2)))
            val decoder = RecordingDecoder(Decoded.Unsupported)
            val (analyzer, job) = analyzer(decoder)

            analyzer.offer(listOf(id(1)))
            advanceUntilIdle()
            decoder.result = Decoded.Failed
            analyzer.offer(listOf(id(1), id(2)))
            advanceUntilIdle()
            // Player events offer it again and again: a failed file is not decoded again this session.
            analyzer.offer(listOf(id(2)))
            advanceUntilIdle()

            assertThat(waveforms.stored.value).containsExactly(id(1), 100L to null)
            assertThat(decoder.decoded).containsExactly(originalCacheKey(id(1)), originalCacheKey(id(2))).inOrder()
            job.cancel()

            // A new session (the service started again) tries it once more.
            val (again, next) = analyzer(decoder)
            again.offer(listOf(id(2)))
            advanceUntilIdle()
            assertThat(decoder.decoded).hasSize(3)
            next.cancel()
        }

    @Test
    fun `one file at a time, and what plays now goes ahead of the rest`() =
        runTest {
            (1..4).forEach { write(streamingCache, originalCacheKey(id(it))) }
            val decoder = RecordingDecoder(hold = true)
            val (analyzer, job) = analyzer(decoder)

            analyzer.offer(listOf(id(1), id(2)))
            runCurrent()
            // While t1 decodes: the current track and its next one jump the queue.
            analyzer.offer(listOf(id(4), id(3)), first = true)
            runCurrent()
            assertThat(decoder.running).isEqualTo(1)
            decoder.release.complete(Unit)
            advanceUntilIdle()

            assertThat(decoder.mostAtOnce).isEqualTo(1)
            assertThat(decoder.decoded.map { it.removeSuffix(":original").substringAfterLast(":") })
                .containsExactly("t1", "t4", "t3", "t2")
                .inOrder()
            job.cancel()
        }

    @Test
    fun `downloads finishing while it runs are analyzed, the ones already there are not`() =
        runTest {
            write(downloadCache, downloadCacheKey(id(1)))
            write(downloadCache, downloadCacheKey(id(2)))
            completed.value = setOf(id(1))
            val decoder = RecordingDecoder()
            val (_, job) = analyzer(decoder)
            advanceUntilIdle()

            completed.value = setOf(id(1), id(2))
            advanceUntilIdle()

            assertThat(decoder.decoded).containsExactly(downloadCacheKey(id(2)))
            job.cancel()
        }

    @Test
    fun `the player offers the current track and the next two`() =
        runTest {
            (1..4).forEach { write(streamingCache, originalCacheKey(id(it))) }
            val decoder = RecordingDecoder()
            val (analyzer, job) = analyzer(decoder)
            val player = TestExoPlayerBuilder(context).setMediaSourceFactory(FakeMediaSourceFactory()).build()
            player.setMediaItems(
                (1..4).map { Track(id(it), "Track $it", emptyList(), 3.minutes).toMediaItem(null) },
                1,
                0,
            )

            analyzer.playerListener.onEvents(
                player,
                Player.Events(FlagSet.Builder().add(Player.EVENT_TIMELINE_CHANGED).build()),
            )
            advanceUntilIdle()

            assertThat(decoder.decoded)
                .containsExactly(
                    originalCacheKey(id(2)),
                    originalCacheKey(id(3)),
                    originalCacheKey(id(4)),
                ).inOrder()
            player.release()
            job.cancel()
        }

    @Test
    fun `one track's crash does not stop the analysis of the next ones`() =
        runTest {
            write(streamingCache, originalCacheKey(id(1)))
            write(streamingCache, originalCacheKey(id(2)))
            val decoder =
                PcmDecoder { file ->
                    if (file.key == originalCacheKey(id(1))) error("broken extractor")
                    Decoded.Levels(listOf(1f))
                }
            val (analyzer, job) = analyzer(decoder)

            analyzer.offer(listOf(id(1), id(2)))
            advanceUntilIdle()

            assertThat(waveforms.stored.value.keys).containsExactly(id(2))
            job.cancel()
        }

    @Test
    fun `a decode that never ends is given up after the limit, and the next track is analyzed`() =
        runTest {
            write(streamingCache, originalCacheKey(id(1)))
            write(streamingCache, originalCacheKey(id(2)))
            val decoded = mutableListOf<String>()
            val decoder =
                PcmDecoder { file ->
                    decoded += file.key
                    if (file.key == originalCacheKey(id(1))) awaitCancellation()
                    Decoded.Levels(listOf(1f))
                }
            val (analyzer, job) = analyzer(decoder)

            analyzer.offer(listOf(id(1), id(2)))
            advanceUntilIdle()
            assertThat(waveforms.stored.value.keys).containsExactly(id(2))

            // Given up for this session: queued again, it is not decoded again.
            analyzer.offer(listOf(id(1)))
            advanceUntilIdle()
            assertThat(decoded).containsExactly(originalCacheKey(id(1)), originalCacheKey(id(2))).inOrder()
            job.cancel()
        }

    @Test
    fun `each decode logs its start and its result with the time it took`() =
        runTest {
            write(streamingCache, originalCacheKey(id(1)))
            val lines = mutableListOf<String>()
            val log =
                object : AppLog {
                    override fun info(
                        tag: String,
                        message: String,
                    ) {
                        lines += "$tag $message"
                    }

                    override fun warn(
                        tag: String,
                        message: String,
                        error: Throwable?,
                    ) = Unit
                }
            val analyzer =
                WaveformAnalyzer(
                    CoroutineScope(coroutineContext + Job()),
                    StandardTestDispatcher(testScheduler),
                    files::find,
                    RecordingDecoder(),
                    waveforms,
                    completed,
                    log,
                )
            val job = analyzer.start()

            analyzer.offer(listOf(id(1)))
            advanceUntilIdle()

            assertThat(lines).hasSize(2)
            assertThat(lines[0]).startsWith("MqWaveform Decoding ${id(1)}")
            assertThat(lines[1]).matches("MqWaveform Decoded .*: levels in [0-9]+ ms")
            job.cancel()
        }
}
