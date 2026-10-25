package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

// Robolectric: the extras are an android.os.Bundle.
@RunWith(RobolectricTestRunner::class)
class TrackMediaItemsTest {
    private val track = Track(MediaId(ServerId("s"), "t1"), "Track", emptyList(), 3.minutes)

    @Test
    fun `queue items carry the album and track gains, also through the session`() {
        val item = track.copy(normalizationGainDb = -6.0, albumNormalizationGainDb = -7.5).toMediaItem(null)

        assertThat(item.withTrackUri()!!.gains()).isEqualTo(ItemGains(albumDb = -7.5, trackDb = -6.0))
    }

    @Test
    fun `a track without gains carries none`() {
        assertThat(track.toMediaItem(null).gains()).isEqualTo(ItemGains(null, null))
    }
}
