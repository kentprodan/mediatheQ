package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/** #383: the next episodes of a followed series stay downloaded. */
class SmartDownloadPlanTest {
    private fun id(item: String) = MediaId(ServerId("s"), item)

    private val series = id("show")

    private fun episode(
        n: Int,
        played: Boolean = false,
        season: Int = 1,
    ) = Video(
        id = id("e$n"),
        title = "E$n",
        kind = VideoKind.Episode,
        duration = 40.minutes,
        seriesId = series,
        seasonNumber = season,
        episodeNumber = n,
        userData = UserData(played = played),
    )

    private fun downloaded(
        n: Int,
        vararg origins: DownloadOrigin,
    ) = VideoDownload(id("e$n"), "E$n", DownloadState.Completed, origins = origins.toSet())

    private val followed = DownloadOrigin.FollowedSeries(series)

    @Test
    fun `a new follow downloads the next three unwatched episodes, from Next up on`() {
        val episodes = listOf(episode(1, played = true), episode(2), episode(3), episode(4), episode(5))

        val plan = smartDownloadPlan(mapOf(series to episodes), emptyList())

        assertThat(plan.download.getValue(series).map { it.id }).containsExactly(id("e2"), id("e3"), id("e4")).inOrder()
        assertThat(plan.release).isEmpty()
    }

    @Test
    fun `watching one releases it and queues the next`() {
        val episodes = listOf(episode(1, played = true), episode(2, played = true), episode(3), episode(4), episode(5))
        val downloads = listOf(downloaded(2, followed), downloaded(3, followed), downloaded(4, followed))

        val plan = smartDownloadPlan(mapOf(series to episodes), downloads)

        assertThat(plan.download.getValue(series).map { it.id }).containsExactly(id("e5"))
        assertThat(plan.release.getValue(series)).containsExactly(id("e2"))
    }

    @Test
    fun `nothing to do when the next ones are there, and specials never count`() {
        val episodes = listOf(episode(0, season = 0), episode(1), episode(2), episode(3), episode(4))
        val downloads = listOf(downloaded(1, followed), downloaded(2, followed), downloaded(3, followed))

        assertThat(smartDownloadPlan(mapOf(series to episodes), downloads)).isEqualTo(SmartDownloadPlan())
    }

    @Test
    fun `an unfollowed series lets its episodes go, a manual download is not the series' own`() {
        val downloads = listOf(downloaded(1, followed, DownloadOrigin.Single), downloaded(2, DownloadOrigin.Single))

        val plan = smartDownloadPlan(emptyMap(), downloads)

        assertThat(plan.unfollowed).containsExactly(series)
        assertThat(plan.download).isEmpty()
    }

    @Test
    fun `everything watched keeps nothing`() {
        assertThat(nextEpisodes(listOf(episode(1, played = true), episode(2, played = true)))).isEmpty()
    }
}
