package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioPolicyTest {
    @Test
    fun `nothing needs the samples, so audio is offloaded`() {
        assertThat(AudioPolicy.decide(AudioSettings())).isEqualTo(AudioMode.Offload)
    }

    @Test
    fun `the equalizer needs the samples, so audio is processed`() {
        assertThat(AudioPolicy.decide(AudioSettings(equalizerOn = true))).isEqualTo(AudioMode.Processed)
    }

    @Test
    fun `album normalization uses the album gain`() {
        assertThat(AudioPolicy.volume(ItemGains(albumDb = -6.0, trackDb = -12.0), Normalization.Album))
            .isWithin(0.001f)
            .of(0.501f)
    }

    @Test
    fun `album normalization falls back to the track gain`() {
        assertThat(AudioPolicy.volume(ItemGains(albumDb = null, trackDb = -20.0), Normalization.Album))
            .isWithin(0.001f)
            .of(0.1f)
    }

    @Test
    fun `track normalization prefers the track gain, then the album gain`() {
        assertThat(AudioPolicy.volume(ItemGains(-6.0, -20.0), Normalization.Track)).isWithin(0.001f).of(0.1f)
        assertThat(AudioPolicy.volume(ItemGains(-20.0, null), Normalization.Track)).isWithin(0.001f).of(0.1f)
    }

    @Test
    fun `no gain plays at full volume`() {
        assertThat(AudioPolicy.volume(ItemGains(null, null), Normalization.Album)).isEqualTo(1f)
    }

    @Test
    fun `a positive gain never boosts above full volume`() {
        assertThat(AudioPolicy.volume(ItemGains(albumDb = 4.0, trackDb = null), Normalization.Album)).isEqualTo(1f)
    }

    @Test
    fun `normalization off ignores the gains`() {
        assertThat(AudioPolicy.volume(ItemGains(-6.0, -6.0), Normalization.Off)).isEqualTo(1f)
    }
}
