package io.github.kentprodan.mediatheq.provider.jellyfin

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.VideoCodecLimit
import org.junit.Test

class VideoCodecProfileTest {
    @Test
    fun `a decoder's limits become per-codec conditions the server converts beyond (#636)`() {
        val profile = VideoCodecLimit("hevc", 3840, 2160, 153, listOf("main", "main 10")).toCodecProfile()

        assertThat(profile.type).isEqualTo("Video")
        assertThat(profile.codec).isEqualTo("hevc")
        assertThat(profile.conditions.map { Triple(it.condition, it.property, it.value) })
            .containsExactly(
                Triple("LessThanEqual", "Width", "3840"),
                Triple("LessThanEqual", "Height", "2160"),
                Triple("LessThanEqual", "VideoLevel", "153"),
                Triple("EqualsAny", "VideoProfile", "main|main 10"),
            ).inOrder()
    }

    @Test
    fun `unknown limits add no condition`() {
        assertThat(VideoCodecLimit("av1", maxWidth = 3840).toCodecProfile().conditions.map { it.property })
            .containsExactly("Width")
    }
}
