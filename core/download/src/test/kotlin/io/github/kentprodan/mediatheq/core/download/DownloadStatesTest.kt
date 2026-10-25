package io.github.kentprodan.mediatheq.core.download

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.scheduler.Requirements
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.DownloadState
import org.junit.Test

@UnstableApi
class DownloadStatesTest {
    private val wifiMissing = Requirements.NETWORK_UNMETERED

    @Test
    fun `queued waits its turn`() {
        assertThat(downloadState(Download.STATE_QUEUED, 0f, 0)).isEqualTo(DownloadState.Queued)
    }

    @Test
    fun `queued without Wi-Fi waits for Wi-Fi`() {
        assertThat(downloadState(Download.STATE_QUEUED, 0f, wifiMissing)).isEqualTo(DownloadState.WaitingForWifi)
    }

    @Test
    fun `stopped and restarting show as queued`() {
        assertThat(downloadState(Download.STATE_STOPPED, 0f, 0)).isEqualTo(DownloadState.Queued)
        assertThat(downloadState(Download.STATE_RESTARTING, 0f, 0)).isEqualTo(DownloadState.Queued)
    }

    @Test
    fun `downloading carries its progress from 0 to 1`() {
        assertThat(downloadState(Download.STATE_DOWNLOADING, 42f, 0)).isEqualTo(DownloadState.Downloading(0.42f))
    }

    @Test
    fun `downloading with an unknown size has no progress`() {
        assertThat(
            downloadState(Download.STATE_DOWNLOADING, C.PERCENTAGE_UNSET.toFloat(), 0),
        ).isEqualTo(DownloadState.Downloading(null))
    }

    @Test
    fun `completed and failed map one to one`() {
        assertThat(downloadState(Download.STATE_COMPLETED, 100f, wifiMissing)).isEqualTo(DownloadState.Completed)
        assertThat(downloadState(Download.STATE_FAILED, 10f, 0)).isEqualTo(DownloadState.Failed)
    }

    @Test
    fun `a download being removed is already gone for the user`() {
        assertThat(downloadState(Download.STATE_REMOVING, 0f, 0)).isNull()
    }
}
