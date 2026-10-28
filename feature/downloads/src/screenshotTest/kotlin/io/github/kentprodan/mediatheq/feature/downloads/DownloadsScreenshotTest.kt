package io.github.kentprodan.mediatheq.feature.downloads

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.TrackDownload
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
private fun Downloads(state: DownloadsUiState) =
    MediatheqTheme {
        DownloadsScreen(state, {})
    }

private fun track(
    n: Int,
    title: String,
) = PreviewData.track.copy(id = MediaId(ServerId("preview"), "dl-$n"), title = title, trackNumber = n)

private val totals = DownloadTotals(3_800_000_000, 12.hours + 4.minutes, 41_000_000_000)

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

@PreviewTest
@MqPreviews
@Composable
fun DownloadsEmptyScreenshot() = Downloads(DownloadsUiState.Empty)

/** Every section: totals, downloading (each row state), albums, playlists, songs (one a converted copy, #68). */
@PreviewTest
@MqPreviews
@Composable
fun DownloadsContentScreenshot() =
    Downloads(
        DownloadsUiState.Content(
            totals,
            downloading,
            albums = listOf(DownloadedAlbum(PreviewData.album, listOf(track(1, "Strobe")), 42_700_000, 20.minutes)),
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
                ),
        ),
    )

/** Nothing finished yet: only the downloading section under the totals. */
@PreviewTest
@MqPreviews
@Composable
fun DownloadsOnlyDownloadingScreenshot() =
    Downloads(DownloadsUiState.Content(DownloadTotals(0, 0.minutes, 41_000_000_000), downloading))
