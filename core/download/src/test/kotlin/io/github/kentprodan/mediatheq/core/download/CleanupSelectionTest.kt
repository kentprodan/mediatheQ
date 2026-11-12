package io.github.kentprodan.mediatheq.core.download

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Test

@UnstableApi
class CleanupSelectionTest {
    private val home = ServerId("home")
    private val office = ServerId("office")

    private fun key(
        server: ServerId,
        item: String,
    ) = MediaId(server, item).toString()

    private val index =
        mapOf(
            key(home, "done") to Download.STATE_COMPLETED,
            key(home, "failed") to Download.STATE_FAILED,
            key(home, "queued") to Download.STATE_QUEUED,
            key(home, "running") to Download.STATE_DOWNLOADING,
            key(home, "stopped") to Download.STATE_STOPPED,
            key(office, "done") to Download.STATE_COMPLETED,
            key(office, "failed") to Download.STATE_FAILED,
        )

    @Test
    fun `only the active server's failed downloads are chosen, never completed or waiting ones`() {
        val selection = cleanupSelection(index, emptySet(), home)

        assertThat(selection.failed).containsExactly(MediaId(home, "failed"))
    }

    @Test
    fun `signed out, every server's failed downloads are chosen`() {
        val selection = cleanupSelection(index, emptySet(), null)

        assertThat(selection.failed).containsExactly(MediaId(home, "failed"), MediaId(office, "failed"))
    }

    @Test
    fun `an orphan is cache data no download of any server owns`() {
        val keys = index.keys + key(home, "left-behind")

        val selection = cleanupSelection(index, keys, home)

        // Another account's completed download is in the same index (ADR 0036): never an orphan.
        assertThat(selection.orphans).containsExactly(key(home, "left-behind"))
    }
}
