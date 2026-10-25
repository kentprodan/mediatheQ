package io.github.kentprodan.mediatheq.feature.downloads

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

@Composable
private fun Downloads(state: DownloadsUiState) =
    MediatheqTheme {
        DownloadsScreen(state) {}
    }

private fun track(
    n: Int,
    title: String,
) = PreviewData.track.copy(id = MediaId(ServerId("preview"), "dl-$n"), title = title, trackNumber = n)

@PreviewTest
@MqPreviews
@Composable
fun DownloadsLoadingScreenshot() = Downloads(DownloadsUiState.Loading)

@PreviewTest
@MqPreviews
@Composable
fun DownloadsEmptyScreenshot() = Downloads(DownloadsUiState.Empty)

/** Every row state: done, downloading (known and unknown size), queued, waiting for Wi-Fi, failed. */
@PreviewTest
@MqPreviews
@Composable
fun DownloadsContentScreenshot() =
    Downloads(
        DownloadsUiState.Content(
            listOf(
                DownloadedAlbum(
                    PreviewData.album.title,
                    listOf(
                        TrackDownload(track(1, "Strobe"), DownloadState.Completed),
                        TrackDownload(track(2, "Ghosts 'n' Stuff"), DownloadState.Downloading(0.42f)),
                        TrackDownload(track(3, "Moar Ghosts"), DownloadState.Downloading(null)),
                        TrackDownload(track(4, "Some Chords"), DownloadState.Queued),
                    ),
                ),
                DownloadedAlbum(
                    null,
                    listOf(
                        TrackDownload(PreviewData.longTrack, DownloadState.WaitingForWifi),
                        TrackDownload(track(6, "Raise Your Weapon"), DownloadState.Failed),
                    ),
                ),
            ),
            storageUsed = 1_234_567_890,
        ),
    )
