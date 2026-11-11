package io.github.kentprodan.mediatheq.core.download

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.StatFs
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.data.DownloadStorageRepository
import io.github.kentprodan.mediatheq.core.data.StorageLocation
import io.github.kentprodan.mediatheq.core.data.StorageMove
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Where the downloads live (#146, ADR 0035). The location is read once per process (SharedPreferences, read before the
 * cache opens, like ADR 0020); a move copies the folder, checks it, saves the new place and the app restarts, so the
 * open cache never changes under its users. While the location's volume is missing, downloads are paused and count as
 * not on the device.
 */
@UnstableApi
@Singleton
internal class DownloadStorage
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @Named(PREFERENCES) private val preferences: SharedPreferences,
        private val volumes: Volumes,
        private val manager: Lazy<DownloadManager>,
        @ApplicationScope private val scope: CoroutineScope,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : DownloadStorageRepository {
        private val path: String? = preferences.getString(KEY_PATH, null)

        override val locationName: String? = path?.let { preferences.getString(KEY_NAME, null) }

        /** The location's files folder, fixed for the process. */
        val root: File = path?.let(::File) ?: context.filesDir

        /** The download cache's folder. */
        val downloadDir: File = File(root, DOWNLOADS)

        private val availableState = MutableStateFlow(isMounted())
        override val available: StateFlow<Boolean> = availableState.asStateFlow()

        override val filesDir: File get() = if (available.value) root else context.filesDir

        private val moveState = MutableStateFlow<StorageMove>(StorageMove.Idle)
        override val move: StateFlow<StorageMove> = moveState.asStateFlow()

        private fun isMounted() = path == null || volumes.isMounted(root)

        init {
            scope.launch { volumes.changes.collect { availableState.value = isMounted() } }
            // Dispatched, not immediate: the manager may be under construction right now (it needs this class).
            scope.launch(Dispatchers.Main) {
                combine(available, move) { _, _ -> holdsDownloads() }
                    .distinctUntilChanged()
                    .collect { hold ->
                        // A new manager starts paused, and the service resumes it when it starts (after the local
                        // network check): resumed here only while the service runs.
                        if (hold) {
                            manager.get().pauseDownloads()
                        } else if (serviceRunning) {
                            manager.get().resumeDownloads()
                        }
                    }
            }
        }

        // A copy left by a move that did not finish, or the folder a finished move left behind.
        private val cleanup =
            scope.launch(ioDispatcher) {
                otherFilesDirs().forEach { File(it, DOWNLOADS).deleteRecursively() }
            }

        @Volatile private var serviceRunning = false

        /** Downloads stay paused: the card is out, or a move is copying or done (the app restarts). */
        private fun holdsDownloads() =
            !available.value || move.value is StorageMove.Moving || move.value == StorageMove.Done

        /** The download service started (it has just resumed the manager) or stopped; main thread. */
        fun onServiceRunning(running: Boolean) {
            serviceRunning = running
            if (running && holdsDownloads()) manager.get().pauseDownloads()
        }

        override val locations: Flow<List<StorageLocation>> =
            volumes.changes
                .onStart { emit(Unit) }
                .map {
                    val removable = volumes.removable()
                    if (removable.isEmpty()) return@map emptyList()
                    listOf(StorageLocation(null, null, freeBytes(context.filesDir), path == null)) +
                        removable.map {
                            StorageLocation(
                                it.filesDir.path,
                                it.name,
                                freeBytes(it.filesDir),
                                it.filesDir.path == path,
                            )
                        }
                }.flowOn(ioDispatcher)

        override fun moveTo(location: StorageLocation) {
            if (location.id == path || moveState.value != StorageMove.Idle) return
            moveState.value = StorageMove.Moving(0f)
            scope.launch {
                moveState.value =
                    try {
                        if (!available.value) throw IOException("The downloads' volume is missing")
                        cleanup.join()
                        withContext(Dispatchers.Main) { manager.get().awaitPaused() }
                        val target = location.id?.let(::File) ?: context.filesDir
                        withContext(ioDispatcher) {
                            copyDownloads(downloadDir, File(target, DOWNLOADS), freeBytes(target)) {
                                moveState.value = StorageMove.Moving(it)
                            }
                            val saved =
                                preferences
                                    .edit()
                                    .putString(
                                        KEY_PATH,
                                        location.id,
                                    ).putString(KEY_NAME, location.name)
                                    .commit()
                            if (!saved) {
                                File(target, DOWNLOADS).deleteRecursively()
                                throw IOException("The new location was not saved")
                            }
                        }
                        StorageMove.Done
                    } catch (e: IOException) {
                        StorageMove.Failed(noSpace = e is NotEnoughSpaceException)
                    }
            }
        }

        override fun dismissMove() {
            if (moveState.value is StorageMove.Failed) moveState.value = StorageMove.Idle
        }

        override fun restart() {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            context.startActivity(Intent.makeRestartActivityTask(launch.component))
            Runtime.getRuntime().exit(0)
        }

        override suspend fun otherFilesDirs(): List<File> =
            withContext(ioDispatcher) {
                (listOf(context.filesDir) + volumes.removable().map { it.filesDir }).filter {
                    it !=
                        root
                }
            }

        private fun freeBytes(dir: File) = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0)

        companion object {
            const val PREFERENCES = "download_storage"
            private const val KEY_PATH = "path"
            private const val KEY_NAME = "name"
            private const val DOWNLOADS = "downloads"
        }
    }

/** Pauses the downloads and waits until none is running, so the folder can be copied (main thread). */
@UnstableApi
private suspend fun DownloadManager.awaitPaused() {
    pauseDownloads()
    if (isIdle) return
    suspendCancellableCoroutine { continuation ->
        val listener =
            object : DownloadManager.Listener {
                override fun onIdle(downloadManager: DownloadManager) {
                    removeListener(this)
                    continuation.resume(Unit)
                }
            }
        addListener(listener)
        continuation.invokeOnCancellation { removeListener(listener) }
    }
}
