package io.github.kentprodan.mediatheq.feature.downloads

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.AutoDownloads
import io.github.kentprodan.mediatheq.core.data.CleanupPreview
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.data.VideoDownload
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Composable
private fun Downloads(
    state: DownloadsUiState,
    tab: DownloadsTab = DownloadsTab.Files,
) = MediatheqTheme {
    DownloadsScreen(state, {}, tab)
}

private fun track(
    n: Int,
    title: String,
) = PreviewData.track.copy(id = MediaId(ServerId("preview"), "dl-$n"), title = title, trackNumber = n)

private val totals =
    DownloadTotals(
        3_800_000_000,
        12.hours + 4.minutes,
        41_000_000_000,
        manual = OfflineUsage(812, 3_100_000_000),
        auto = OfflineUsage(146, 700_000_000),
    )

private val downloading =
    listOf(
        TrackDownload(track(2, "Ghosts 'n' Stuff"), DownloadState.Downloading(0.42f)),
        TrackDownload(track(3, "Moar Ghosts"), DownloadState.Downloading(null)),
        TrackDownload(track(4, "Some Chords"), DownloadState.Queued),
        TrackDownload(PreviewData.longTrack, DownloadState.WaitingForWifi),
        TrackDownload(track(6, "Raise Your Weapon"), DownloadState.Failed),
    )

@PreviewTest
@MqPreviews
@Composable
fun DownloadsLoadingScreenshot() = Downloads(DownloadsUiState.Loading)

/** Offline files with nothing finished: the summary, then the empty state. */
@PreviewTest
@MqPreviews
@Composable
fun DownloadsEmptyScreenshot() = Downloads(DownloadsUiState.Content(DownloadTotals(0, 0.minutes, 41_000_000_000)))

/** Offline files: summary (manual / automatic), Cleanup, filter, albums, playlists, songs (one a converted copy, #68). */
@PreviewTest
@MqPreviews
@Composable
fun DownloadsContentScreenshot() =
    Downloads(
        DownloadsUiState.Content(
            totals,
            downloading,
            albums =
                listOf(
                    DownloadedAlbum(PreviewData.album, listOf(track(1, "Strobe")), 42_700_000, 20.minutes, auto = true),
                ),
            playlists =
                listOf(
                    DownloadedPlaylist(
                        Playlist(MediaId(ServerId("preview"), "pl"), "Unplayed", trackCount = 64),
                        listOf(track(1, "Strobe")),
                        1_800_000_000,
                        5.hours,
                    ),
                ),
            songs =
                listOf(
                    TrackDownload(track(1, "Strobe"), DownloadState.Completed),
                    TrackDownload(track(5, "Animal Rights"), DownloadState.Completed, converted = true),
                    TrackDownload(
                        track(7, "Ghosts 'n' Stuff"),
                        DownloadState.Completed,
                        origins = setOf(io.github.kentprodan.mediatheq.core.data.DownloadOrigin.Favorite),
                    ),
                ),
            hasFiles = true,
        ),
    )

/** Download queue: each row state and Pause all. */
@PreviewTest
@MqPreviews
@Composable
fun DownloadsQueueScreenshot() = Downloads(DownloadsUiState.Content(totals, downloading), DownloadsTab.Queue)

/** Download queue paused by the user: waiting rows say Paused, Resume all. */
@PreviewTest
@MqPreviews
@Composable
fun DownloadsQueuePausedScreenshot() =
    Downloads(DownloadsUiState.Content(totals, downloading, paused = true), DownloadsTab.Queue)

@PreviewTest
@MqPreviews
@Composable
fun DownloadsQueueEmptyScreenshot() = Downloads(DownloadsUiState.Content(totals), DownloadsTab.Queue)

/** The Cleanup confirmation, in place (dialogs render blank in screenshot tests). */
@PreviewTest
@MqPreviews
@Composable
fun DownloadsCleanupScreenshot() =
    MediatheqTheme {
        MqDialogContent("Clean up offline files?", "Clean up", {}, "Cancel", {}) {
            CleanupBody(CleanupPreview(failed = 3, bytes = 58_000_000))
        }
    }

/** The SD card holding the downloads is out (#146): the notice above the kept downloads. */
@PreviewTest
@MqPreviews
@Composable
fun DownloadsUnavailableScreenshot() =
    Downloads(
        DownloadsUiState.Content(
            DownloadTotals(42_700_000, 20.minutes, 0),
            albums =
                listOf(
                    DownloadedAlbum(PreviewData.album, listOf(track(1, "Strobe")), 42_700_000, 20.minutes),
                ),
            unavailableOn = "SD card",
            hasFiles = true,
        ),
    )

private fun videoDownload(
    n: Int,
    title: String,
    bytes: Long,
    state: DownloadState = DownloadState.Completed,
    cannot: Boolean = false,
) = VideoDownload(MediaId(ServerId("preview"), "v-$n"), title, state, bytes, cannotDownload = cannot)

/** Video mode's files (#279): a movie and a season with its episodes and sizes. */
@PreviewTest
@MqPreviews
@Composable
fun DownloadsVideosScreenshot() =
    Downloads(
        DownloadsUiState.Content(
            totals,
            movies = listOf(videoDownload(1, "Moșoi: Tribul Mic", 4_200_000_000)),
            seasons =
                listOf(
                    DownloadedSeason(
                        MediaId(ServerId("preview"), "se1"),
                        "Show",
                        2,
                        listOf(videoDownload(2, "Episode 1", 900_000_000), videoDownload(3, "Episode 2", 880_000_000)),
                        1_780_000_000,
                        whole = true,
                    ),
                ),
            hasFiles = true,
        ),
    )

/** The queue with a video that cannot be downloaded on this phone (#279). */
@PreviewTest
@MqPreviews
@Composable
fun DownloadsVideoQueueScreenshot() =
    Downloads(
        DownloadsUiState.Content(
            totals,
            videoQueue =
                listOf(
                    videoDownload(4, "Episode 3", 300_000_000, DownloadState.Downloading(0.3f)),
                    videoDownload(5, "Dolby Vision 5 film", 0, DownloadState.Failed, cannot = true),
                ),
        ),
        DownloadsTab.Queue,
    )
