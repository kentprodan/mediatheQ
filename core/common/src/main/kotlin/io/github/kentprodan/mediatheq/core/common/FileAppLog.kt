package io.github.kentprodan.mediatheq.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock

/**
 * [AppLog] into `dir/mediatheq.log` (#169): lines queue without blocking (the oldest go when the queue is full) and one
 * coroutine on [dispatcher] appends them, a batch at a time. At [maxBytes] the file becomes `mediatheq.1.log`, so at
 * most two files are kept. A line that cannot be written is dropped: logging never breaks the app. [sink] also gets
 * each line (logcat in debug builds).
 */
class FileAppLog(
    private val dir: File,
    private val clock: Clock,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val maxBytes: Long = MAX_BYTES,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val sink: (level: Char, tag: String, line: String) -> Unit = { _, _, _ -> },
) : AppLog {
    private val queue = Channel<String>(QUEUE, BufferOverflow.DROP_OLDEST)
    private val fileLock = Mutex()
    private val current = File(dir, CURRENT)
    private val older = File(dir, OLDER)

    init {
        scope.launch(dispatcher) {
            for (line in queue) {
                val batch = mutableListOf(line)
                while (true) batch += queue.tryReceive().getOrNull() ?: break
                fileLock.withLock { write(batch) }
            }
        }
    }

    override fun info(
        tag: String,
        message: String,
    ) = add('I', tag, message, null)

    override fun warn(
        tag: String,
        message: String,
        error: Throwable?,
    ) = add('W', tag, message, error)

    /** Writes [message] at once, on the calling thread: for a crash, when the process is about to end. */
    fun crash(
        tag: String,
        message: String,
        error: Throwable,
    ) {
        val line = line('E', tag, message, error)
        sink('E', tag, line)
        // Not under the lock (it may be held by the dying writer): one line, appended whole.
        write(listOf(line))
    }

    /** Joins the older and the current file into [target], once the lines queued so far are written. */
    suspend fun export(target: File): Boolean =
        withContext(dispatcher) {
            // Lines still queued are written first.
            val pending = generateSequence { queue.tryReceive().getOrNull() }.toList()
            fileLock.withLock {
                if (pending.isNotEmpty()) write(pending)
                try {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        listOf(older, current).filter(File::exists).forEach { file ->
                            file.inputStream().use { it.copyTo(out) }
                        }
                    }
                    true
                } catch (_: IOException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }
        }

    private fun add(
        level: Char,
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        val line = line(level, tag, message, error)
        sink(level, tag, line)
        queue.trySend(line)
    }

    private fun line(
        level: Char,
        tag: String,
        message: String,
        error: Throwable?,
    ): String {
        val time = TIME.format(Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone))
        val cause = error?.let { " | ${causeChain(it)}" }.orEmpty()
        return redact("$time $level $tag: $message$cause").replace('\n', ' ') + "\n"
    }

    private fun write(lines: List<String>) {
        try {
            dir.mkdirs()
            if (current.length() >= maxBytes) {
                older.delete()
                current.renameTo(older)
            }
            current.appendText(lines.joinToString(""))
        } catch (_: IOException) {
            // Dropped: the log must never break playback.
        } catch (_: SecurityException) {
            // Dropped, as above.
        }
    }

    private companion object {
        const val CURRENT = "mediatheq.log"
        const val OLDER = "mediatheq.1.log"
        const val MAX_BYTES = 1_000_000L
        const val QUEUE = 1024
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }
}

/** "IOException: reset <- SocketTimeoutException: timeout": each cause's type and message, no stack frames. */
fun causeChain(error: Throwable): String =
    generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }
        .take(MAX_CAUSES)
        .joinToString(
            " <- ",
        ) { listOfNotNull(it::class.simpleName ?: it::class.java.name, it.message).joinToString(": ") }

private const val MAX_CAUSES = 8
