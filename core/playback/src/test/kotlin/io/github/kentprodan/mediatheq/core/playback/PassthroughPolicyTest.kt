package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MimeTypes
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PassthroughPolicyTest {
    @Test
    fun `a bitstream passes only when the route's set (switch on) names it (#377)`() {
        assertThat(passthroughAllowed(MimeTypes.AUDIO_TRUEHD, setOf(MimeTypes.AUDIO_TRUEHD))).isTrue()
        assertThat(passthroughAllowed(MimeTypes.AUDIO_TRUEHD, emptySet())).isFalse()
        assertThat(passthroughAllowed(MimeTypes.AUDIO_DTS, setOf(MimeTypes.AUDIO_E_AC3))).isFalse()
    }

    @Test
    fun `E-AC-3 JOC may still go as plain E-AC-3 where only that is allowed (ADR 0039)`() {
        assertThat(passthroughAllowed(MimeTypes.AUDIO_E_AC3_JOC, setOf(MimeTypes.AUDIO_E_AC3))).isTrue()
        assertThat(passthroughAllowed(MimeTypes.AUDIO_E_AC3, setOf(MimeTypes.AUDIO_E_AC3_JOC))).isFalse()
    }

    @Test
    fun `PCM and offloaded music are never judged`() {
        assertThat(passthroughAllowed(MimeTypes.AUDIO_RAW, emptySet())).isTrue()
        assertThat(passthroughAllowed(MimeTypes.AUDIO_FLAC, emptySet())).isTrue()
        assertThat(passthroughAllowed(null, emptySet())).isTrue()
    }
}
