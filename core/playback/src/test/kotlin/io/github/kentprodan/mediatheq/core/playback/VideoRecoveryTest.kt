package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.PlaybackException
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.VideoQuality
import org.junit.Test

class VideoRecoveryTest {
    @Test
    fun `a reclaimed decoder is asked for again twice, other errors are not`() {
        val reclaimed = PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED
        assertThat(VideoRecoveryRules.retryReclaimed(reclaimed, retries = 0)).isTrue()
        assertThat(VideoRecoveryRules.retryReclaimed(reclaimed, retries = 1)).isTrue()
        assertThat(VideoRecoveryRules.retryReclaimed(reclaimed, retries = 2)).isFalse()
        assertThat(VideoRecoveryRules.retryReclaimed(PlaybackException.ERROR_CODE_DECODING_FAILED, 0)).isFalse()
    }

    @Test
    fun `a resume is stuck only while meant to play with 10 s loaded`() {
        assertThat(VideoRecoveryRules.wedged(buffering = true, playWhenReady = true, bufferedAheadMs = 12_000)).isTrue()
        assertThat(VideoRecoveryRules.wedged(buffering = true, playWhenReady = true, bufferedAheadMs = 4_000)).isFalse()
        assertThat(
            VideoRecoveryRules.wedged(buffering = true, playWhenReady = false, bufferedAheadMs = 12_000),
        ).isFalse()
        assertThat(
            VideoRecoveryRules.wedged(buffering = false, playWhenReady = true, bufferedAheadMs = 12_000),
        ).isFalse()
    }

    @Test
    fun `only a video decoder failing before its first frame means the device cannot play the stream`() {
        val init = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        val decoding = PlaybackException.ERROR_CODE_DECODING_FAILED
        assertThat(VideoRecoveryRules.decoderCannotStart(init, "video/dolby-vision", firstFrameShown = false)).isTrue()
        assertThat(VideoRecoveryRules.decoderCannotStart(decoding, "video/hevc", firstFrameShown = false)).isTrue()
        assertThat(VideoRecoveryRules.decoderCannotStart(decoding, "video/hevc", firstFrameShown = true)).isFalse()
        assertThat(VideoRecoveryRules.decoderCannotStart(init, "audio/eac3", firstFrameShown = false)).isFalse()
        val network = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        assertThat(VideoRecoveryRules.decoderCannotStart(network, "video/hevc", firstFrameShown = false)).isFalse()
    }

    @Test
    fun `a ready video with no video selected is unplayable`() {
        assertThat(
            VideoRecoveryRules.videoUnplayable(ready = true, hasVideoTrack = true, videoSelected = false),
        ).isTrue()
        assertThat(
            VideoRecoveryRules.videoUnplayable(ready = true, hasVideoTrack = false, videoSelected = false),
        ).isTrue()
        assertThat(
            VideoRecoveryRules.videoUnplayable(ready = true, hasVideoTrack = true, videoSelected = true),
        ).isFalse()
        assertThat(
            VideoRecoveryRules.videoUnplayable(ready = false, hasVideoTrack = false, videoSelected = false),
        ).isFalse()
    }

    @Test
    fun `an audio clock an hour past the player's age is corrupted, a rejected HAL timestamp is not`() {
        val age = 600_000L
        assertThat(
            VideoRecoveryRules.clockCorrupted(reportedPositionUs = 26L * 3_600_000_000, playerAgeMs = age),
        ).isTrue()
        assertThat(VideoRecoveryRules.clockCorrupted(reportedPositionUs = 590_000_000, playerAgeMs = age)).isFalse()
    }

    @Test
    fun `the head position is the fourth value of Media3's frame position mismatch warning`() {
        val warning = "Spurious audio timestamp (frame position mismatch): 1200, 3400, 5600, 89478485333, 7800"
        assertThat(spuriousHeadPositionUs(warning)).isEqualTo(89_478_485_333)
        assertThat(spuriousHeadPositionUs("Spurious audio timestamp (system clock mismatch): 1, 2, 3, 4, 5")).isNull()
    }

    @Test
    fun `a converted video asks H264 SDR with no direct play`() {
        val profile =
            DeviceProfileBuilder.video(
                emptySet(),
                setOf("video/hevc", "video/dolby-vision"),
                VideoQuality.Original,
                videoRangeTypes = listOf("SDR", "HDR10", "DOVI"),
                converted = true,
            )
        assertThat(profile.directPlayContainers).isEmpty()
        assertThat(profile.videoCodecs).containsExactly("h264")
        assertThat(profile.videoRangeTypes).containsExactly("SDR")
    }
}
