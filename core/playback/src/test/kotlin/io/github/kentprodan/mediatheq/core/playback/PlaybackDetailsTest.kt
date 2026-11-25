package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackDetailsTest {
    @Test
    fun `one log line says what plays and how it leaves`() {
        val details =
            EngineDetails(
                audioDecoder = "OMX.dolby.eac3",
                audioFormat = "audio/eac3-joc 6 ch 48000 Hz",
                audioPath = AudioPath.Passthrough,
                passthroughEncoding = encodingName(C.ENCODING_E_AC3_JOC),
                droppedFrames = 3,
                recoveries = 1,
                subtitle = "ass · Embed",
                subtitleLibass = true,
                subtitleDelayMs = -300,
            )

        assertThat(details.logLine()).isEqualTo(
            "audio audio/eac3-joc 6 ch 48000 Hz via OMX.dolby.eac3 · out Passthrough E-AC-3 JOC · dropped 3 · " +
                "recoveries 1 · sub ass · Embed libass -300ms",
        )
    }

    @Test
    fun `without recoveries or subtitles the line stays short`() {
        assertThat(EngineDetails().logLine()).isEqualTo("audio ? via ? · out Pcm · dropped 0")
    }
}
