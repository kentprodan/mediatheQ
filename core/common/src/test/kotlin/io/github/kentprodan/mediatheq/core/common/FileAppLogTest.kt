package io.github.kentprodan.mediatheq.core.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Instant

class FileAppLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-11-12T10:15:03.123Z")
        }

    private fun TestScope.log(
        dir: File = folder.root,
        maxBytes: Long = 1_000_000,
    ): FileAppLog {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return FileAppLog(dir, clock, backgroundScope, dispatcher, maxBytes, ZoneOffset.UTC)
    }

    private fun file(name: String = "mediatheq.log") = File(folder.root, name)

    @Test
    fun `lines are written in order with time, level, tag and the cause chain`() =
        runTest {
            val log = log()

            log.info("Playback", "Started Moșoi (abc)")
            log.warn("Sync", "Failed", IOException("reset", IllegalStateException("closed")))
            runCurrent()

            assertThat(file().readLines())
                .containsExactly(
                    "2026-11-12 10:15:03.123 I Playback: Started Moșoi (abc)",
                    "2026-11-12 10:15:03.123 W Sync: Failed | IOException: reset <- IllegalStateException: closed",
                ).inOrder()
        }

    @Test
    fun `no secret reaches the file, from a message or an error`() =
        runTest {
            val log = log()

            log.info("Http", "GET http://nas:8096/Items?ApiKey=s3cret")
            log.warn("Auth", "Sign-in failed", IOException("""bad JSON {"AccessToken":"s3cret"}"""))
            runCurrent()

            assertThat(file().readText()).doesNotContain("s3cret")
        }

    @Test
    fun `at the size limit the file rotates, and only two are kept`() =
        runTest {
            val log = log(maxBytes = 200)

            repeat(30) {
                log.info("Tag", "line $it")
                runCurrent()
            }

            assertThat(folder.root.list()!!.toList()).containsExactly("mediatheq.log", "mediatheq.1.log")
            assertThat(file().readText()).contains("line 29")
            assertThat(file("mediatheq.1.log").readText()).doesNotContain("line 0\n")
            assertThat(file("mediatheq.1.log").length()).isAtMost(200L + 60)
        }

    @Test
    fun `a line that cannot be written is dropped, and later ones still go through`() =
        runTest {
            // The log folder's path is taken by a file: nothing can be written there.
            val blocked = folder.newFile("blocked")
            val log = log(dir = blocked)

            log.info("Tag", "lost")
            runCurrent()
            blocked.delete()
            log.info("Tag", "kept")
            runCurrent()

            assertThat(File(blocked, "mediatheq.log").readText()).contains("kept")
            assertThat(File(blocked, "mediatheq.log").readText()).doesNotContain("lost")
        }

    @Test
    fun `logging never blocks or throws, even with nothing draining the queue`() =
        runTest {
            val log = log()

            repeat(5_000) { log.info("Tag", "line $it") }
            runCurrent()

            // The oldest went; the newest are kept.
            assertThat(file().readText()).contains("line 4999")
            assertThat(file().readLines()).doesNotContain("2026-11-12 10:15:03.123 I Tag: line 0")
        }

    @Test
    fun `an export joins the older and the current file, lines still queued included`() =
        runTest {
            val log = log(maxBytes = 120)
            repeat(4) {
                log.info("Tag", "line $it")
                runCurrent()
            }
            log.info("Tag", "queued")

            val target = File(folder.root, "export/out.txt")
            assertThat(log.export(target)).isTrue()

            // The older file first, then the current one, then what was still queued.
            assertThat(file("mediatheq.1.log").exists()).isTrue()
            assertThat(target.readLines().map { it.substringAfter(": ") })
                .containsExactly("line 0", "line 1", "line 2", "line 3", "queued")
                .inOrder()
        }

    @Test
    fun `a crash is written at once`() =
        runTest {
            val log = log()

            log.crash("Crash", "Uncaught on main", IllegalStateException("boom"))

            assertThat(file().readText()).contains("E Crash: Uncaught on main | IllegalStateException: boom")
        }
}
