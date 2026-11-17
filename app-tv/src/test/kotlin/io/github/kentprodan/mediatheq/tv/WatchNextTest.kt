package io.github.kentprodan.mediatheq.tv

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WatchNextTest {
    private fun entry(id: String) =
        WatchNextEntry(Video(MediaId(ServerId("s"), id), VideoKind.Movie, id), continuing = true)

    @Test
    fun `new cards are inserted, kept ones updated by program id, gone ones deleted`() {
        val plan = watchNextPlan(mapOf("a" to 1L, "b" to 2L), listOf(entry("b"), entry("c")))

        assertThat(plan.insert.map { it.video.id.itemId }).containsExactly("c")
        assertThat(plan.update.keys).containsExactly(2L)
        assertThat(plan.delete).containsExactly(1L)
    }

    @Test
    fun `a card's link opens its video at its position, other links are ignored`() {
        val id = MediaId(ServerId("server-1"), "m1")

        assertThat(parseWatchNextUri(watchNextUri(id, 42.minutes))).isEqualTo(id to 42.minutes)
        assertThat(parseWatchNextUri(android.net.Uri.parse("https://example.com/video/s/m1"))).isNull()
        assertThat(parseWatchNextUri(null)).isNull()
    }
}
