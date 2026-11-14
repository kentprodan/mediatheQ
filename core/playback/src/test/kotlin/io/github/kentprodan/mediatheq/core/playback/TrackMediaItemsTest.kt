package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
    fun `Atmos tracks are marked, also through the session`() {
        val atmos = AudioFormat("eac3", channels = 6, spatialFormat = SpatialFormat.DolbyAtmos)
        val dtsX = atmos.copy(spatialFormat = SpatialFormat.DtsX)

        assertThat(
            track
                .copy(audioFormat = atmos)
                .toMediaItem(null)
                .withTrackUri()!!
                .isDolbyAtmos(),
        ).isTrue()
        assertThat(track.copy(audioFormat = dtsX).toMediaItem(null).isDolbyAtmos()).isFalse()
        assertThat(track.toMediaItem(null).isDolbyAtmos()).isFalse()
    }

    @Test
    fun `a track without gains carries none`() {
        assertThat(track.toMediaItem(null).gains()).isEqualTo(ItemGains(null, null))
    }

    @Test
    fun `queue items carry the format they reach the sink in, also through the session`() {
        val item = track.copy(audioFormat = AudioFormat("flac", sampleRateHz = 96_000, channels = 2)).toMediaItem(null)

        assertThat(item.withTrackUri()!!.outputFormat())
            .isEqualTo(
                PcmFormat(
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    96_000,
                    android.media.AudioFormat.CHANNEL_OUT_STEREO,
                ),
            )
    }

    @Test
    fun `a track without a known format carries none`() {
        assertThat(track.toMediaItem(null).outputFormat()).isNull()
    }

    @Test
    fun `queue items carry the playlist they are played from, also through the session`() {
        val playlist = MediaId(ServerId("s"), "p1")

        assertThat(track.toMediaItem(null, playlistId = playlist).withTrackUri()!!.playlistId()).isEqualTo(playlist)
        assertThat(track.toMediaItem(null).playlistId()).isNull()
    }

    @Test
    fun `the shuffle weight travels in the item, and Play next marks it`() {
        val played = Instant.parse("2026-11-01T10:00:00Z")
        val track =
            Track(
                MediaId(ServerId("s"), "w1"),
                "Weighted",
                emptyList(),
                3.minutes,
                userData = UserData(isFavorite = true, rating = 8.0, lastPlayedAt = played),
            )
        val item = track.toMediaItem(null).withTrackUri()!!

        assertThat(item.shuffleWeight()).isEqualTo(ShuffleWeight(played, isFavorite = true, halfStars = 8))
        assertThat(item.isPlayedNext()).isFalse()
        assertThat(item.markedPlayedNext().isPlayedNext()).isTrue()
        assertThat(item.markedPlayedNext().entryId()).isEqualTo(item.entryId())
        assertThat(
            Track(MediaId(ServerId("s"), "w2"), "Plain", emptyList(), 3.minutes).toMediaItem(null).shuffleWeight(),
        ).isEqualTo(ShuffleWeight())
    }

    @Test
    fun `a video item resolves through the video scheme and is no track to the music listeners`() {
        val id = MediaId(ServerId("s"), "m1")
        val item =
            Video(id, VideoKind.Episode, "Pilot", seriesName = "Show", seasonNumber = 2, episodeNumber = 4)
                .toMediaItem(null, startPosition = 90.seconds)
                .withTrackUri()!!

        assertThat(item.isVideo()).isTrue()
        assertThat(item.localConfiguration?.uri?.videoId()).isEqualTo(id)
        assertThat(item.localConfiguration?.uri?.trackId()).isNull()
        assertThat(item.queuedTrackId()).isNull()
        assertThat(item.videoStart()).isEqualTo(90.seconds)
        assertThat(item.mediaMetadata.artist.toString()).isEqualTo("Show · S2 E4")
    }
}
