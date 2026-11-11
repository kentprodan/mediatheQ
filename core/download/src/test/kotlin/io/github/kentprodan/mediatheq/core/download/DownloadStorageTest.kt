package io.github.kentprodan.mediatheq.core.download

import android.app.Application
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.test.utils.FakeDataSet
import androidx.media3.test.utils.FakeDataSource
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.CONVERTED_DOWNLOAD
import io.github.kentprodan.mediatheq.core.data.StorageLocation
import io.github.kentprodan.mediatheq.core.data.StorageMove
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** The download location (#146): the folder move, the reopened cache, the guard and the missing card. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class DownloadStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = StandaloneDatabaseProvider(context)
    private val preferences = context.getSharedPreferences(DownloadStorage.PREFERENCES, Context.MODE_PRIVATE)
    private val card = Files.createTempDirectory("card").toFile()
    private val volumes = FakeVolumes()
    private val caches = mutableListOf<Cache>()
    private val manager by lazy {
        DownloadManager(context, DefaultDownloadIndex(database)) { error("nothing is downloaded in these tests") }
            .also { RobolectricUtil.runMainLooperUntil { it.isInitialized } }
    }

    @After
    fun release() {
        caches.forEach(Cache::release)
    }

    @Test
    fun `the cache reopened on the copy keeps every download, its metadata and its bytes`() {
        val from = Files.createTempDirectory("internal").toFile()
        val to = File(card, "downloads")
        val old = open(from)
        write(old, "s1:t1", converted = true)
        old.release()
        caches -= old

        copyDownloads(from, to, Long.MAX_VALUE) {}
        val moved = open(to)

        assertThat(moved.keys).containsExactly("s1:t1")
        assertThat(moved.getContentMetadata("s1:t1").get(CONVERTED_DOWNLOAD, 0L)).isEqualTo(1L)
        assertThat(read(moved, "s1:t1")).hasLength(SIZE)
    }

    @Test
    fun `a copy reports its progress up to the whole`() {
        val from =
            Files
                .createTempDirectory(
                    "internal",
                ).toFile()
                .apply { File(this, "a.exo").writeBytes(ByteArray(10)) }
        val progress = mutableListOf<Float>()

        copyDownloads(from, File(card, "downloads"), Long.MAX_VALUE) { progress += it }

        assertThat(progress.last()).isEqualTo(1f)
        assertThat(File(card, "downloads/a.exo").length()).isEqualTo(10)
    }

    @Test
    fun `too little room copies nothing`() {
        val from =
            Files
                .createTempDirectory(
                    "internal",
                ).toFile()
                .apply { File(this, "a.exo").writeBytes(ByteArray(10)) }

        assertThrows(NotEnoughSpaceException::class.java) { copyDownloads(from, File(card, "downloads"), 5) {} }

        assertThat(File(card, "downloads").exists()).isFalse()
    }

    @Test
    fun `a failed copy leaves the downloads where they were`() {
        val from =
            Files
                .createTempDirectory(
                    "internal",
                ).toFile()
                .apply { File(this, "a.exo").writeBytes(ByteArray(10)) }
        val blocked = File(card, "file").apply { writeText("not a folder") }

        assertThrows(IOException::class.java) { copyDownloads(from, File(blocked, "downloads"), Long.MAX_VALUE) {} }

        assertThat(File(from, "a.exo").length()).isEqualTo(10)
    }

    @Test
    fun `with the card out nothing reaches the cache, and the downloads are there when it is back`() {
        var mounted = true
        val folder = File(card, "downloads")
        val guarded = GuardedCache(card, { mounted }) { open(folder) }
        write(guarded, "s1:t1", converted = false)
        val away = File(card.parentFile, "${card.name}-away")

        // The card goes: its files are gone, and a read would make SimpleCache drop every span.
        mounted = false
        folder.renameTo(away)
        assertThrows(Cache.CacheException::class.java) { guarded.startReadWrite("s1:t1", 0, SIZE.toLong()) }
        assertThat(guarded.keys).isEmpty()
        assertThat(guarded.isCached("s1:t1", 0, SIZE.toLong())).isFalse()

        away.renameTo(folder)
        mounted = true
        assertThat(guarded.isCached("s1:t1", 0, SIZE.toLong())).isTrue()
    }

    @Test
    fun `a cache on a card that is out opens only once the card is there`() {
        var mounted = false
        var opened = 0
        val guarded = GuardedCache(card, { mounted }) { opened++.let { open(File(card, "downloads")) } }

        guarded.keys
        assertThat(opened).isEqualTo(0)

        mounted = true
        guarded.keys
        guarded.keys
        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun `the setting lists internal storage and each card, and nothing without a card`() =
        runBlocking {
            val storage = storage()
            assertThat(storage.locations.first()).isEmpty()

            volumes.removable = listOf(Volume(card, "SD card"))

            assertThat(storage.locations.first().map { it.id to it.current })
                .containsExactly(null to true, card.path to false)
                .inOrder()
        }

    @Test
    fun `downloads on a card that is out are unavailable and paused, and come back with it`() {
        preferences
            .edit()
            .putString("path", card.path)
            .putString("name", "SD card")
            .commit()
        val storage = storage()
        RobolectricUtil.runMainLooperUntil { manager.downloadsPaused }

        assertThat(storage.available.value).isFalse()
        assertThat(storage.locationName).isEqualTo("SD card")
        assertThat(storage.filesDir).isEqualTo(context.filesDir)

        volumes.mounted += card
        volumes.changes.tryEmit(Unit)
        RobolectricUtil.runMainLooperUntil { !manager.downloadsPaused }
        assertThat(storage.available.value).isTrue()
        assertThat(storage.root).isEqualTo(card)
    }

    @Test
    fun `a move copies the downloads, saves the card, and the next start uses it and clears the old folder`() {
        volumes.mounted += card
        volumes.removable = listOf(Volume(card, "SD card"))
        ShadowStatFs.registerStats(card, 1_000, 1_000, 1_000)
        val storage = storage()
        File(storage.downloadDir, "a.exo").apply { parentFile!!.mkdirs() }.writeBytes(ByteArray(10))

        storage.moveTo(StorageLocation(card.path, "SD card", Long.MAX_VALUE, current = false))
        RobolectricUtil.runMainLooperUntil { storage.move.value == StorageMove.Done }

        assertThat(File(card, "downloads/a.exo").length()).isEqualTo(10)
        assertThat(manager.downloadsPaused).isTrue()
        val next = storage()
        assertThat(next.downloadDir).isEqualTo(File(card, "downloads"))
        assertThat(next.locationName).isEqualTo("SD card")
        assertThat(File(context.filesDir, "downloads").exists()).isFalse()
    }

    @Test
    fun `a move that fails keeps the location and resumes the downloads`() {
        val blocked = File(card, "file").apply { writeText("not a folder") }
        val storage = storage()
        File(storage.downloadDir, "a.exo").apply { parentFile!!.mkdirs() }.writeBytes(ByteArray(10))

        storage.moveTo(StorageLocation(blocked.path, "SD card", 0, current = false))
        RobolectricUtil.runMainLooperUntil { storage.move.value is StorageMove.Failed }
        RobolectricUtil.runMainLooperUntil { !manager.downloadsPaused }

        assertThat(preferences.getString("path", null)).isNull()
        assertThat(File(storage.downloadDir, "a.exo").length()).isEqualTo(10)
        storage.dismissMove()
        assertThat(storage.move.value).isEqualTo(StorageMove.Idle)
    }

    private fun storage() =
        DownloadStorage(
            context,
            preferences,
            volumes,
            { manager },
            CoroutineScope(Dispatchers.Unconfined),
            Dispatchers.Unconfined,
        )

    private fun open(folder: File): Cache = SimpleCache(folder, NoOpCacheEvictor(), database).also { caches += it }

    private fun write(
        cache: Cache,
        key: String,
        converted: Boolean,
    ) {
        val server = FakeDataSet().apply { newDefaultData().appendReadData(SIZE) }
        val spec =
            DataSpec
                .Builder()
                .setUri("mediatheq://download")
                .setKey(key)
                .setLength(SIZE.toLong())
                .build()
        CacheWriter(CacheDataSource(cache, FakeDataSource(server)), spec, null, null).cache()
        cache.applyContentMetadataMutations(
            key,
            ContentMetadataMutations().set(CONVERTED_DOWNLOAD, if (converted) 1L else 0L),
        )
    }

    private fun read(
        cache: Cache,
        key: String,
    ): ByteArray {
        val source = CacheDataSource(cache, null)
        source.open(
            DataSpec
                .Builder()
                .setUri("mediatheq://download")
                .setKey(key)
                .setLength(SIZE.toLong())
                .build(),
        )
        return DataSourceUtil.readToEnd(source).also { source.close() }
    }

    private class FakeVolumes : Volumes {
        var removable: List<Volume> = emptyList()
        val mounted = mutableSetOf<File>()
        override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override fun removable(): List<Volume> = removable

        override fun isMounted(dir: File): Boolean = dir in mounted
    }

    private companion object {
        const val SIZE = 100
    }
}
