package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class WatchedDownloadsTest {
    private val day0 = Instant.fromEpochSeconds(1_700_000_000)
    private val now = day0 + 10.days
    private val since = day0
    private val id = MediaId(ServerId("s"), "m1")

    private fun download(
        origins: Set<DownloadOrigin> = setOf(DownloadOrigin.Movie(id)),
        completedAt: Instant = day0 + 1.days,
        state: DownloadState = DownloadState.Completed,
    ) = VideoDownload(id, "M", state, origins = origins, completedAt = completedAt)

    private fun video(
        played: Boolean = true,
        lastPlayed: Instant? = day0 + 2.days,
        favorite: Boolean = false,
    ) = Video(
        id,
        VideoKind.Movie,
        "M",
        userData = UserData(played = played, lastPlayedAt = lastPlayed, isFavorite = favorite),
    )

    private fun plan(
        download: VideoDownload = download(),
        video: Video = video(),
        removal: WatchedRemoval = WatchedRemoval.OneDay,
        since: Instant? = this.since,
        at: Instant = now,
        playing: MediaId? = null,
    ) = watchedRemovals(listOf(download), mapOf(id to video), removal, since, at, playing)

    @Test
    fun `a movie watched after it downloaded and after the delay loses its manual origin`() {
        assertThat(plan()).containsExactly(id, setOf(DownloadOrigin.Movie(id)))
        assertThat(plan(at = day0 + 2.days + 23.hours)).isEmpty()
        assertThat(plan(removal = WatchedRemoval.Now, at = day0 + 2.days)).isNotEmpty()
    }

    @Test
    fun `off, not watched, favorite, playing or not finished are kept`() {
        assertThat(plan(removal = WatchedRemoval.Off)).isEmpty()
        assertThat(plan(since = null)).isEmpty()
        assertThat(plan(video = video(played = false))).isEmpty()
        assertThat(plan(video = video(favorite = true))).isEmpty()
        assertThat(plan(playing = id)).isEmpty()
        assertThat(plan(download = download(state = DownloadState.Queued))).isEmpty()
    }

    @Test
    fun `watched before the download or before the setting was on is kept`() {
        assertThat(plan(download = download(completedAt = day0 + 3.days))).isEmpty()
        assertThat(plan(since = day0 + 5.days)).isEmpty()
    }

    @Test
    fun `only manual origins go, a followed series keeps its own`() {
        val series = DownloadOrigin.FollowedSeries(MediaId(ServerId("s"), "sr"))
        assertThat(plan(download = download(origins = setOf(series)))).isEmpty()
        assertThat(plan(download = download(origins = setOf(series, DownloadOrigin.Single))))
            .containsExactly(id, setOf(DownloadOrigin.Single))
    }
}
