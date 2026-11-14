package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class NextUpTest {
    private fun episode(
        n: Int,
        played: Boolean = false,
        position: Duration = Duration.ZERO,
    ) = Video(
        MediaId(ServerId("s"), "e$n"),
        VideoKind.Episode,
        "Episode $n",
        episodeNumber = n,
        userData = UserData(played = played, playbackPosition = position),
    )

    @Test
    fun `nothing watched is the first episode`() {
        assertThat(nextUp(listOf(episode(1), episode(2)))?.title).isEqualTo("Episode 1")
    }

    @Test
    fun `mid-season is the one after the last watched`() {
        assertThat(nextUp(listOf(episode(1, true), episode(2, true), episode(3)))?.title).isEqualTo("Episode 3")
    }

    @Test
    fun `everything watched is nothing`() {
        assertThat(nextUp(listOf(episode(1, true), episode(2, true)))).isNull()
    }

    @Test
    fun `a skipped episode before the last watched one is not next up`() {
        assertThat(nextUp(listOf(episode(1, true), episode(2), episode(3, true), episode(4)))?.title)
            .isEqualTo("Episode 4")
    }

    @Test
    fun `an episode left halfway is next up and resumes where it was`() {
        val halfway = episode(2, position = 12.minutes)

        assertThat(nextUp(listOf(episode(1, true), halfway, episode(3)))).isEqualTo(halfway)
        assertThat(halfway.resumePosition()).isEqualTo(12.minutes)
        assertThat(episode(2, played = true, position = 12.minutes).resumePosition()).isEqualTo(Duration.ZERO)
    }

    private fun inSeries(
        series: String,
        season: Int,
        n: Int,
        played: Boolean = false,
        lastPlayedDay: Long? = null,
    ) = Video(
        MediaId(ServerId("s"), "$series-s${season}e$n"),
        VideoKind.Episode,
        "$series $season.$n",
        seriesId = MediaId(ServerId("s"), series),
        seasonNumber = season,
        episodeNumber = n,
        userData =
            UserData(
                played = played,
                lastPlayedAt = lastPlayedDay?.let { kotlin.time.Instant.fromEpochSeconds(it * 86_400) },
            ),
    )

    @Test
    fun `Home's Next up is one episode per started series, the series played last first`() {
        val episodes =
            listOf(
                inSeries("a", 1, 1, played = true, lastPlayedDay = 1),
                inSeries("a", 1, 2),
                inSeries("b", 1, 1, played = true, lastPlayedDay = 5),
                inSeries("b", 1, 2),
                inSeries("c", 1, 1),
            )

        assertThat(nextUpPerSeries(episodes).map { it.id.itemId }).containsExactly("b-s1e2", "a-s1e2").inOrder()
    }

    @Test
    fun `the next episode is the next one, then the next season, never a special, none after the last`() {
        val episodes = listOf(inSeries("a", 1, 1), inSeries("a", 1, 2), inSeries("a", 0, 1), inSeries("a", 2, 1))

        assertThat(nextEpisode(episodes, episodes[0])?.id?.itemId).isEqualTo("a-s1e2")
        assertThat(nextEpisode(episodes, episodes[1])?.id?.itemId).isEqualTo("a-s2e1")
        assertThat(nextEpisode(episodes, episodes[3])).isNull()
    }

    @Test
    fun `Home's Next up leaves out an episode left halfway and the specials`() {
        val episodes =
            listOf(
                inSeries("a", 1, 1, played = true, lastPlayedDay = 1),
                inSeries("a", 1, 2).copy(userData = UserData(playbackPosition = 5.minutes)),
                inSeries("b", 0, 1, played = true, lastPlayedDay = 2),
                inSeries("b", 1, 1, played = true, lastPlayedDay = 1),
                inSeries("b", 1, 2),
            )

        assertThat(nextUpPerSeries(episodes).map { it.id.itemId }).containsExactly("b-s1e2")
    }
}
