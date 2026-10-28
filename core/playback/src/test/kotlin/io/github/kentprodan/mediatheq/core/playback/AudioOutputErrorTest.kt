package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.audio.AudioSink
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class AudioOutputErrorTest {
    private fun rendererError(cause: Exception) =
        ExoPlaybackException.createForUnexpected(RuntimeException(cause), PlaybackException.ERROR_CODE_UNSPECIFIED)

    @Test
    fun `an AudioTrack the output refused or lost is an audio output error`() {
        val format = Format.Builder().build()

        assertThat(rendererError(AudioSink.WriteException(-32, format, false)).isAudioOutputError()).isTrue()
        assertThat(
            rendererError(AudioSink.InitializationException("start failed", -32, format, false, null))
                .isAudioOutputError(),
        ).isTrue()
    }

    @Test
    fun `a stream or decoder error is not`() {
        assertThat(rendererError(IOException("connection dropped")).isAudioOutputError()).isFalse()
    }
}
