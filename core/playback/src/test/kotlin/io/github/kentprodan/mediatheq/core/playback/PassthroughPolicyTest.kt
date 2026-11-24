package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.reflect.Proxy

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

    @Test
    fun `float reaches the decoders only while the bit-perfect route allows it (#629)`() {
        val float =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                .build()
        // A sink below that writes float: it takes float directly.
        val sink =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(AudioSink::class.java)) { _, method, _ ->
                if (method.name == "getFormatSupport") AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY else null
            } as AudioSink
        var allowed = false
        val policy = PassthroughPolicyAudioSink(sink, { emptySet() }, { allowed })

        assertThat(policy.getFormatSupport(float)).isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING)
        allowed = true
        assertThat(policy.getFormatSupport(float)).isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)
    }
}
