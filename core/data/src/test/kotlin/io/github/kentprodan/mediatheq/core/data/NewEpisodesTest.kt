package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import org.junit.Test

class NewEpisodesTest {
    private val show = MediaId(ServerId("s"), "sr")

    private fun episode(
        n: Int,
        played: Boolean = false,
    ) = Video(MediaId(ServerId("s"), "e$n"), VideoKind.Episode, "E$n", userData = UserData(played = played))

    @Test
    fun `a series met for the first time is only recorded, a later episode is told once`() {
        val first = episodeNotices(emptySet(), mapOf(show to listOf(episode(1), episode(2))))
        assertThat(first.new).isEmpty()

        val second = episodeNotices(first.seen, mapOf(show to listOf(episode(1), episode(2), episode(3))))
        assertThat(second.new.map { it.title }).containsExactly("E3")

        val third = episodeNotices(second.seen, mapOf(show to listOf(episode(1), episode(2), episode(3))))
        assertThat(third.new).isEmpty()
    }

    @Test
    fun `an episode already watched when it arrives is not told`() {
        val first = episodeNotices(emptySet(), mapOf(show to listOf(episode(1))))
        assertThat(
            episodeNotices(first.seen, mapOf(show to listOf(episode(1), episode(2, played = true)))).new,
        ).isEmpty()
    }
}
