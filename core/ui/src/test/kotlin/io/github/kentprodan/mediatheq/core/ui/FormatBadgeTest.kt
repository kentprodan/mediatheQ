package io.github.kentprodan.mediatheq.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import org.junit.Test

class FormatBadgeTest {
    private fun badge(
        channels: Int?,
        spatial: SpatialFormat? = null,
    ) = AudioFormat("flac", channels = channels, spatialFormat = spatial).formatBadge()

    @Test
    fun `Dolby Atmos wins over its channel count`() {
        assertThat(badge(6, SpatialFormat.DolbyAtmos)).isEqualTo(FormatBadge.DolbyAtmos)
        assertThat(badge(null, SpatialFormat.DolbyAtmos)).isEqualTo(FormatBadge.DolbyAtmos)
    }

    @Test
    fun `DTS X has its own badge, even without a channel count`() {
        assertThat(badge(null, SpatialFormat.DtsX)).isEqualTo(FormatBadge.DtsX)
    }

    @Test
    fun `more than two channels is surround`() {
        assertThat(badge(6)).isEqualTo(FormatBadge.Surround(6))
        assertThat(badge(8)).isEqualTo(FormatBadge.Surround(8))
        assertThat(badge(3)).isEqualTo(FormatBadge.Surround(3))
    }

    @Test
    fun `two channels is stereo, one is mono`() {
        assertThat(badge(2)).isEqualTo(FormatBadge.Stereo)
        assertThat(badge(1)).isEqualTo(FormatBadge.Mono)
    }

    @Test
    fun `an unknown channel count has no badge`() {
        assertThat(badge(null)).isNull()
        assertThat(badge(0)).isNull()
    }
}
