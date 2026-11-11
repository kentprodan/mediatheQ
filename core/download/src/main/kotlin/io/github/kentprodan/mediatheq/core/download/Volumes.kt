package io.github.kentprodan.mediatheq.core.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject

/** Removable volumes the app can keep files on (#146); behind an interface so tests need no SD card. */
internal interface Volumes {
    /** Each mounted removable volume's app files folder (no storage permission needed) and its name. */
    fun removable(): List<Volume>

    fun isMounted(dir: File): Boolean

    /** A volume was mounted, ejected or removed. */
    val changes: Flow<Unit>
}

internal data class Volume(
    val filesDir: File,
    val name: String,
)

internal class AndroidVolumes
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : Volumes {
        private val storage = context.getSystemService(StorageManager::class.java)

        override fun removable(): List<Volume> =
            context.getExternalFilesDirs(null).filterNotNull().mapNotNull { dir ->
                storage
                    .getStorageVolume(dir)
                    ?.takeIf { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
                    ?.let { Volume(dir, it.getDescription(context)) }
            }

        /** Volumes being ejected: still mounted, but their files are left alone from then on. */
        private val ejecting =
            java.util.concurrent.ConcurrentHashMap
                .newKeySet<String>()

        override fun isMounted(dir: File): Boolean =
            Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED &&
                ejecting.none { dir.path.startsWith(it) }

        override val changes: Flow<Unit> =
            callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            intent.data?.path?.let { volume ->
                                if (intent.action == Intent.ACTION_MEDIA_EJECT) {
                                    ejecting += volume
                                } else if (intent.action == Intent.ACTION_MEDIA_MOUNTED) {
                                    ejecting -= volume
                                }
                            }
                            trySend(Unit)
                        }
                    }
                val filter =
                    IntentFilter().apply {

                        MOUNT_ACTIONS.forEach(::addAction)
                        addDataScheme("file")
                    }
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                awaitClose { context.unregisterReceiver(receiver) }
            }

        private companion object {
            val MOUNT_ACTIONS =
                listOf(
                    Intent.ACTION_MEDIA_MOUNTED,
                    Intent.ACTION_MEDIA_EJECT,
                    Intent.ACTION_MEDIA_UNMOUNTED,
                    Intent.ACTION_MEDIA_REMOVED,
                    Intent.ACTION_MEDIA_BAD_REMOVAL,
                )
        }
    }

/** Copying failed for lack of room on the target. */
internal class NotEnoughSpaceException : java.io.IOException("Not enough space for the downloads")

/**
 * Copies the download folder [from] to [to] (#146), checking each copy's length; on any failure the partial copy is
 * deleted and [from] is left as it was. [onProgress] gets the fraction of bytes copied.
 */
internal fun copyDownloads(
    from: File,
    to: File,
    freeBytes: Long,
    onProgress: (Float) -> Unit,
) {
    to.deleteRecursively()
    val files = from.walkTopDown().filter { it.isFile }.toList()
    val total = files.sumOf { it.length() }
    if (total > freeBytes) throw NotEnoughSpaceException()
    var copied = 0L
    try {
        files.forEach { file ->
            val copy = File(to, file.relativeTo(from).path).apply { parentFile?.mkdirs() }
            file.inputStream().use { input ->
                java.io.FileOutputStream(copy).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            if (copy.length() != file.length()) throw java.io.IOException("Incomplete copy of ${file.name}")
            copied += file.length()
            onProgress(copied.toFloat() / total)
        }
    } catch (e: java.io.IOException) {
        to.deleteRecursively()
        throw e
    }
}
