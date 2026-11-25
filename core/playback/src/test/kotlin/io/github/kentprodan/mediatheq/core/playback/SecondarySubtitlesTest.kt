package io.github.kentprodan.mediatheq.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecondarySubtitlesTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val secondary = SecondarySubtitles()
    private val selector =
        SecondarySubtitleTrackSelector(context, secondary).apply {
            init(
                object : TrackSelector.InvalidationListener {
                    override fun onTrackSelectionsInvalidated(
                        parameters: androidx.media3.common.TrackSelectionParameters?,
                    ) = Unit
                },
                DefaultBandwidthMeter.getSingletonInstance(context),
            )
        }
    private val renderers = arrayOf(TextRenderer({}, null), TextRenderer({}, null))

    private fun text(
        id: String,
        language: String,
        codec: String = MimeTypes.APPLICATION_SUBRIP,
    ) = TrackGroup(
        id,
        Format
            .Builder()
            .setId(id)
            .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
            .setCodecs(codec)
            .setLanguage(language)
            .build(),
    )

    private val english = text("en", "en")
    private val romanian = text("ro", "ro")
    private val styled = text("ro-ass", "ro", MimeTypes.TEXT_SSA)

    private fun select(vararg groups: TrackGroup) =
        selector.selectTracks(renderers, TrackGroupArray(*groups), MediaSource.MediaPeriodId(Any()), Timeline.EMPTY)

    @Test
    fun `the second renderer plays the asked language`() {
        secondary.choose("ro")

        assertThat(select(english, romanian).selections[1]?.trackGroup).isEqualTo(romanian)
    }

    @Test
    fun `no second track for a styled one or none asked, and its cues go once it is off`() {
        assertThat(select(english, romanian).selections[1]).isNull()

        secondary.choose("ro")
        assertThat(select(english, styled).selections[1]).isNull()

        secondary.publish(
            listOf(
                androidx.media3.common.text.Cue
                    .Builder()
                    .setText("Salut")
                    .build(),
            ),
        )
        assertThat(secondary.cues.value).hasSize(1)
        secondary.choose(null)
        assertThat(secondary.cues.value).isEmpty()
    }
}
