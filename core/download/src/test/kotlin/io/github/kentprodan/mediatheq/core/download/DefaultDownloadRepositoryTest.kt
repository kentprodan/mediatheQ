package io.github.kentprodan.mediatheq.core.download

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.test.utils.FakeDataSet
import androidx.media3.test.utils.FakeDataSource
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.CONVERTED_DOWNLOAD
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeAudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes

/** The repository over a real DownloadManager whose index the test fills; nothing is ever downloaded. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class DefaultDownloadRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = StandaloneDatabaseProvider(context)
    private val index = DefaultDownloadIndex(database)
    private val cache = SimpleCache(Files.createTempDirectory("downloads").toFile(), NoOpCacheEvictor(), database)
    private val library = FakeLibraryRepository()
    private val audioPreferences = FakeAudioPreferencesRepository()
    private lateinit var manager: DownloadManager

    private val done = track("t1", "Album A", 2)
    private val failed = track("t2", "Album A", 1)
    private val waiting = track("t3", "Album B", 1)

    @After
    fun release() {
        cache.release()
    }

    @Test
    fun `downloads join the library tracks, in album order`() =
        runTest {
            val repository = repository(Download.STATE_COMPLETED to done, Download.STATE_FAILED to failed)

            repository.downloads.test {
                assertThat(expectMostRecentItem())
                    .containsExactly(
                        TrackDownload(failed, DownloadState.Failed),
                        TrackDownload(done, DownloadState.Completed),
                    ).inOrder()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a download whose track left the library keeps its title`() =
        runTest {
            val repository = repository(Download.STATE_COMPLETED to done)
            library.tracks.value = emptyList()

            repository.downloads.test {
                val row = expectMostRecentItem().single()
                assertThat(row.track.id).isEqualTo(done.id)
                assertThat(row.track.title).isEqualTo(done.title)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `only completed downloads count as on the device`() =
        runTest {
            val repository = repository(Download.STATE_COMPLETED to done, Download.STATE_QUEUED to waiting)

            assertThat(repository.isDownloaded(done.id)).isTrue()
            assertThat(repository.isDownloaded(waiting.id)).isFalse()
        }

    @Test
    fun `completed ids hold only finished downloads, and only once the stored downloads are read`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository =
                repository(Download.STATE_COMPLETED to done, Download.STATE_QUEUED to waiting, dispatcher = dispatcher)

            repository.completedIds.test {
                expectNoEvents()
                testScheduler.runCurrent()
                assertThat(awaitItem()).containsExactly(done.id)
            }
        }

    @Test
    fun `downloading again skips tracks finished or under way, and retries failed ones`() =
        runTest {
            val fresh = track("t4", "Album C", 1)
            val repository =
                repository(
                    Download.STATE_COMPLETED to done,
                    Download.STATE_FAILED to failed,
                    Download.STATE_QUEUED to waiting,
                )

            repository.download(listOf(done, failed, waiting, fresh))

            val requests = startedIntents().map { it.request() }
            assertThat(requests.map { it.id }).containsExactly(failed.id.toString(), fresh.id.toString()).inOrder()
            assertThat(requests.map { it.customCacheKey }).containsExactly(failed.id.toString(), fresh.id.toString())
        }

    /** Puts [size] bytes of [track]'s download in the cache, as the server's conversion or its original. */
    private fun partial(
        track: Track,
        converted: Boolean,
        size: Int = 100,
    ) {
        val key = track.id.toString()
        val server = FakeDataSet().apply { newDefaultData().appendReadData(size) }
        val spec =
            DataSpec
                .Builder()
                .setUri("mediatheq://download")
                .setKey(key)
                .setLength(size.toLong())
                .build()
        CacheWriter(CacheDataSource(cache, FakeDataSource(server)), spec, null, null).cache()
        cache.applyContentMetadataMutations(
            key,
            ContentMetadataMutations().set(CONVERTED_DOWNLOAD, if (converted) 1L else 0L),
        )
    }

    @Test
    fun `a converted download says so`() =
        runTest {
            partial(done, converted = true)
            val repository = repository(Download.STATE_COMPLETED to done, Download.STATE_FAILED to failed)

            repository.downloads.test {
                assertThat(expectMostRecentItem().associate { it.track.id to it.converted })
                    .containsExactly(done.id, true, failed.id, false)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `downloading a failed download again starts over, whether it was converted or not`() =
        runTest {
            partial(failed, converted = true)
            partial(waiting, converted = false)
            val repository = repository(Download.STATE_FAILED to failed, Download.STATE_FAILED to waiting)

            repository.download(listOf(failed, waiting))

            assertThat(cache.getCachedBytes(failed.id.toString(), 0, C.LENGTH_UNSET.toLong())).isEqualTo(0)
            assertThat(cache.getCachedBytes(waiting.id.toString(), 0, C.LENGTH_UNSET.toLong())).isEqualTo(0)
            assertThat(
                startedIntents().map { it.request().id },
            ).containsExactly(failed.id.toString(), waiting.id.toString())
        }

    @Test
    fun `a second tap while a failed download starts over does not touch it again`() =
        runTest {
            val repository = repository(Download.STATE_FAILED to failed)

            repository.download(listOf(failed))
            // What the new run already wrote.
            partial(failed, converted = true)
            repository.download(listOf(failed))

            assertThat(cache.getCachedBytes(failed.id.toString(), 0, C.LENGTH_UNSET.toLong())).isEqualTo(100)
            assertThat(startedIntents()).hasSize(1)
        }

    @Test
    fun `R5 removing a download never calls the server`() =
        runTest {
            // The repository has no server dependency at all; removal is one local intent to the download service.
            val repository = repository(Download.STATE_COMPLETED to done)

            repository.remove(listOf(done.id))

            val intent = startedIntents().single()
            assertThat(intent.component?.className).isEqualTo(MediatheqDownloadService::class.java.name)
            assertThat(intent.action).isEqualTo(DownloadService.ACTION_REMOVE_DOWNLOAD)
            assertThat(intent.getStringExtra(DownloadService.KEY_CONTENT_ID)).isEqualTo(done.id.toString())
        }

    @Test
    fun `downloads wait for Wi-Fi, or take any network when the setting allows it`() =
        runTest {
            repository()
            RobolectricUtil.runMainLooperUntil {
                manager.requirements ==
                    DownloadModule.requirements(unmeteredOnly = true)
            }

            audioPreferences.setDownloadsUnmeteredOnly(false)

            RobolectricUtil.runMainLooperUntil {
                manager.requirements ==
                    DownloadModule.requirements(unmeteredOnly = false)
            }
        }

    @Test
    fun `before the stored downloads are read, a completed download already counts as on the device`() =
        runTest {
            // The index read is queued but not run yet: the player started the process (e.g. offline, cold start).
            val repository =
                repository(Download.STATE_COMPLETED to done, dispatcher = StandardTestDispatcher(testScheduler))

            assertThat(repository.isDownloaded(done.id)).isTrue()
            assertThat(repository.isDownloaded(waiting.id)).isFalse()
        }

    private fun TestScope.repository(
        vararg stored: Pair<Int, Track>,
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
    ): DefaultDownloadRepository {
        library.tracks.value = listOf(done, failed, waiting)
        stored.forEach { (state, track) -> index.putDownload(download(track, state)) }
        manager =
            DownloadManager(context, index) { error("nothing is downloaded in these tests") }
                .apply { pauseDownloads() }
        RobolectricUtil.runMainLooperUntil { manager.isInitialized }
        return DefaultDownloadRepository(
            context,
            manager,
            cache,
            library,
            audioPreferences,
            backgroundScope,
            dispatcher,
            dispatcher,
        )
    }

    private fun startedIntents(): List<Intent> = generateSequence { shadowOf(context).nextStartedService }.toList()

    @Suppress("DEPRECATION")
    private fun Intent.request(): DownloadRequest = getParcelableExtra(DownloadService.KEY_DOWNLOAD_REQUEST)!!

    private fun download(
        track: Track,
        state: Int,
    ) = Download(
        DownloadRequest
            .Builder(track.id.toString(), Uri.parse("mediatheq://download"))
            .setCustomCacheKey(track.id.toString())
            .setData(track.title.encodeToByteArray())
            .build(),
        state,
        0,
        0,
        0,
        0,
        if (state == Download.STATE_FAILED) Download.FAILURE_REASON_UNKNOWN else Download.FAILURE_REASON_NONE,
    )

    private fun track(
        id: String,
        album: String,
        number: Int,
    ) = Track(
        id = MediaId(ServerId("server-1"), id),
        title = "Track $id",
        artists = emptyList(),
        duration = 3.minutes,
        albumId = MediaId(ServerId("server-1"), album),
        albumTitle = album,
        trackNumber = number,
    )
}
