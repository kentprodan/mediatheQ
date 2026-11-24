package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.FadeCurve
import io.github.kentprodan.mediatheq.core.playback.CrossfadePlan.AlbumPlace
import org.junit.Test
import kotlin.math.sqrt

class CrossfadePlanTest {
    @Test
    fun `every curve goes from silent to full, and fading out mirrors it`() {
        FadeCurve.entries.forEach { curve ->
            assertThat(CrossfadePlan.fadeIn(0f, curve)).isWithin(1e-6f).of(0f)
            assertThat(CrossfadePlan.fadeIn(1f, curve)).isWithin(1e-6f).of(1f)
            assertThat(CrossfadePlan.fadeOut(0f, curve)).isWithin(1e-6f).of(1f)
            assertThat(CrossfadePlan.fadeOut(0.3f, curve)).isWithin(1e-6f).of(CrossfadePlan.fadeIn(0.7f, curve))
        }
    }

    @Test
    fun `equal power keeps the loudness, linear keeps the sum, exponential starts slow`() {
        val half = 0.5f
        val inGain = CrossfadePlan.fadeIn(half, FadeCurve.EqualPower)
        val outGain = CrossfadePlan.fadeOut(half, FadeCurve.EqualPower)
        assertThat(inGain).isWithin(1e-6f).of(1 / sqrt(2f))
        assertThat(inGain * inGain + outGain * outGain).isWithin(1e-6f).of(1f)

        assertThat(CrossfadePlan.fadeIn(0.3f, FadeCurve.Linear) + CrossfadePlan.fadeOut(0.3f, FadeCurve.Linear))
            .isWithin(1e-6f)
            .of(1f)
        assertThat(CrossfadePlan.fadeIn(0.5f, FadeCurve.Exponential)).isWithin(1e-6f).of(0.25f)
    }

    @Test
    fun `progress outside the fade is clamped`() {
        assertThat(CrossfadePlan.fadeIn(-1f, FadeCurve.Linear)).isEqualTo(0f)
        assertThat(CrossfadePlan.fadeIn(2f, FadeCurve.Linear)).isEqualTo(1f)
    }

    @Test
    fun `a later track of the same album follows it, anything else does not`() {
        val current = AlbumPlace("a", disc = 1, track = 5)

        assertThat(CrossfadePlan.followsOnAlbum(current, AlbumPlace("a", 1, 6))).isTrue()
        assertThat(CrossfadePlan.followsOnAlbum(current, AlbumPlace("a", 2, 1))).isTrue()
        assertThat(CrossfadePlan.followsOnAlbum(current, AlbumPlace("a", 1, 3))).isFalse()
        assertThat(CrossfadePlan.followsOnAlbum(current, AlbumPlace("b", 1, 6))).isFalse()
        assertThat(CrossfadePlan.followsOnAlbum(current, null)).isFalse()
        assertThat(CrossfadePlan.followsOnAlbum(null, current)).isFalse()
    }

    private fun allowed(
        crossfadeMs: Int = 6_000,
        hasNext: Boolean = true,
        followsOnAlbum: Boolean = false,
        video: Boolean = false,
        bitPerfectWanted: Boolean = false,
        passthrough: Boolean = false,
        repeatOne: Boolean = false,
        sleepTimerAtTrackEnd: Boolean = false,
        outgoingOnDevice: Boolean = true,
    ) = CrossfadePlan.allowed(
        crossfadeMs,
        hasNext,
        followsOnAlbum,
        video,
        bitPerfectWanted,
        passthrough,
        repeatOne,
        sleepTimerAtTrackEnd,
        outgoingOnDevice,
    )

    @Test
    fun `a crossfade runs only when nothing rules it out`() {
        assertThat(allowed()).isTrue()

        assertThat(allowed(crossfadeMs = 0)).isFalse()
        assertThat(allowed(hasNext = false)).isFalse()
        assertThat(allowed(followsOnAlbum = true)).isFalse()
        assertThat(allowed(video = true)).isFalse()
        assertThat(allowed(bitPerfectWanted = true)).isFalse()
        assertThat(allowed(passthrough = true)).isFalse()
        assertThat(allowed(repeatOne = true)).isFalse()
        assertThat(allowed(sleepTimerAtTrackEnd = true)).isFalse()
        assertThat(allowed(outgoingOnDevice = false)).isFalse()
    }

    @Test
    fun `without a waveform the crossfade starts its length before the end`() {
        assertThat(CrossfadePlan.startMs(200_000, 6_000, null)).isEqualTo(194_000)
        assertThat(CrossfadePlan.startMs(4_000, 6_000, null)).isEqualTo(0)
    }

    @Test
    fun `smart fades end the crossfade where the quiet tail starts`() {
        // 10 parts of a 100 s track: the last two are a fade to silence.
        val waveform = listOf(0.5f, 1f, 0.8f, 0.7f, 0.9f, 0.6f, 0.4f, 0.3f, 0.05f, 0.01f)

        assertThat(CrossfadePlan.startMs(100_000, 6_000, waveform)).isEqualTo(74_000)
    }

    @Test
    fun `smart fades skip a silent intro, three seconds at most`() {
        val shortIntro = listOf(0.02f, 0.5f, 1f, 0.8f, 0.7f, 0.9f, 0.6f, 0.4f, 0.3f, 0.2f)
        val longIntro = listOf(0.02f, 0.01f, 0.03f, 0.5f, 1f, 0.8f, 0.7f, 0.9f, 0.6f, 0.4f)

        assertThat(CrossfadePlan.introSkipMs(20_000, shortIntro)).isEqualTo(2_000)
        assertThat(CrossfadePlan.introSkipMs(100_000, longIntro)).isEqualTo(CrossfadePlan.MAX_INTRO_SKIP_MS)
        assertThat(CrossfadePlan.introSkipMs(100_000, null)).isEqualTo(0)
    }

    @Test
    fun `an all-quiet waveform places nothing`() {
        val silent = List(10) { 0.01f }

        assertThat(CrossfadePlan.startMs(100_000, 6_000, silent)).isEqualTo(94_000)
        assertThat(CrossfadePlan.introSkipMs(100_000, silent)).isEqualTo(0)
    }
}
