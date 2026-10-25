package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

// Robolectric: android.net.Uri in the load event.
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class StreamLoadErrorPolicyTest {
    private val policy = StreamLoadErrorPolicy()

    private fun retryDelay(exception: IOException) =
        policy.getRetryDelayMsFor(
            LoadErrorInfo(
                LoadEventInfo(0, DataSpec(Uri.EMPTY), 0),
                MediaLoadData(C.DATA_TYPE_MEDIA),
                exception,
                1,
            ),
        )

    @Test
    fun `a dropped connection is retried (e g a Wi-Fi to cellular handover)`() {
        assertThat(retryDelay(IOException("connection reset"))).isNotEqualTo(C.TIME_UNSET)
        assertThat(retryDelay(StreamUnavailableException(MqError.Network))).isNotEqualTo(C.TIME_UNSET)
        assertThat(retryDelay(StreamUnavailableException(MqError.Server(503)))).isNotEqualTo(C.TIME_UNSET)
    }

    @Test
    fun `answers that will not change are not retried`() {
        assertThat(retryDelay(StreamUnavailableException(MqError.Unauthorized))).isEqualTo(C.TIME_UNSET)
        assertThat(retryDelay(StreamUnavailableException(MqError.Client(0, "no stream")))).isEqualTo(C.TIME_UNSET)
    }

    @Test
    fun `a track that cannot load offline is not retried`() {
        assertThat(retryDelay(OfflineException())).isEqualTo(C.TIME_UNSET)
    }
}
