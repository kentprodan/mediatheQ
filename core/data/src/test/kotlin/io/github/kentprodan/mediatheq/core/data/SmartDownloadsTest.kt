package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeVideoRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/** #383: the runner, with the planner it applies. */
class SmartDownloadsTest {
    private val videos = FakeVideoRepository()
    private val downloads = FakeDownloadRepository()
    private val series = MediaId(ServerId("s"), "show")
    private val season = MediaId(ServerId("s"), "season1")

    private fun episode(n: Int) =
        Video(
            id = MediaId(ServerId("s"), "e$n"),
            kind = VideoKind.Episode,
            title = "E$n",
            duration = 40.minutes,
            seriesId = series,
            seasonId = season,
            seasonNumber = 1,
            episodeNumber = n,
        )

    private fun TestScope.start() {
        SmartDownloads(videos, downloads, backgroundScope, StandardTestDispatcher(testScheduler)).start()
        advanceTimeBy(3_000)
        runCurrent()
    }

    @Test
    fun `a followed series gets its next three episodes with its own origin`() =
        runTest {
            videos.seasonEpisodes.value = mapOf(season to (1..5).map(::episode))
            videos.setFollowed(series, true)

            start()

            assertThat(downloads.videoDownloads.value.map { it.id.itemId }).containsExactly("e1", "e2", "e3")
            assertThat(
                downloads.videoDownloads.value
                    .first()
                    .origins,
            ).containsExactly(DownloadOrigin.FollowedSeries(series))
        }

    @Test
    fun `signed out (nothing followed any more) the kept episodes stay`() =
        runTest {
            val kept =
                VideoDownload(
                    episode(1).id,
                    "E1",
                    DownloadState.Completed,
                    origins = setOf(DownloadOrigin.FollowedSeries(series)),
                )
            downloads.videoDownloads.value = listOf(kept)

            start()

            assertThat(downloads.videoDownloads.value).containsExactly(kept)
        }

    @Test
    fun `unfollowing lets the kept episodes go`() =
        runTest {
            videos.seasonEpisodes.value = mapOf(season to (1..3).map(::episode))
            videos.setFollowed(series, true)
            start()

            unfollow(videos, downloads, series)
            advanceTimeBy(3_000)
            runCurrent()

            assertThat(videos.followedSeries.value).isEmpty()
            assertThat(downloads.videoDownloads.value).isEmpty()
        }
}
