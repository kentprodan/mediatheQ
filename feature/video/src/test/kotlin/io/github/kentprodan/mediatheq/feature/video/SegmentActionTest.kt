package io.github.kentprodan.mediatheq.feature.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.VideoPreferences
import io.github.kentprodan.mediatheq.core.model.MediaSegment
import io.github.kentprodan.mediatheq.core.model.SegmentType
import io.github.kentprodan.mediatheq.core.model.SkipMode
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The skip rule rows (#277): each kind x each setting, already skipped, seeking back, the controls. */
class SegmentActionTest {
    private val intro = MediaSegment(SegmentType.Intro, 30.seconds, 120.seconds)
    private val credits = MediaSegment(SegmentType.Outro, 2400.seconds, 2600.seconds)
    private val recap = MediaSegment(SegmentType.Recap, 0.seconds, 20.seconds)
    private val all = listOf(recap, intro, credits)

    private fun at(
        position: Duration,
        settings: VideoPreferences = VideoPreferences(),
        skipped: Set<MediaSegment> = emptySet(),
        shownAt: Map<MediaSegment, Duration> = emptyMap(),
        controls: Boolean = false,
    ) = segmentAction(position, all, settings, skipped, shownAt, controls)

    @Test
    fun `outside a segment nothing happens`() {
        assertThat(at(25.seconds)).isEqualTo(SegmentAction.None)
        assertThat(at(120.seconds)).isEqualTo(SegmentAction.None)
    }

    @Test
    fun `each kind follows its own setting`() {
        val settings =
            VideoPreferences(skipIntro = SkipMode.Auto, skipCredits = SkipMode.Off, skipRecapPreview = SkipMode.Button)

        assertThat(at(31.seconds, settings)).isEqualTo(SegmentAction.Skip(intro))
        assertThat(at(2401.seconds, settings)).isEqualTo(SegmentAction.None)
        assertThat(at(1.seconds, settings)).isEqualTo(SegmentAction.Button(recap))
    }

    @Test
    fun `a skipped segment does nothing again, also when seeking back into it`() {
        val auto = VideoPreferences(skipIntro = SkipMode.Auto)

        assertThat(at(60.seconds, auto, skipped = setOf(intro))).isEqualTo(SegmentAction.None)
        assertThat(at(31.seconds, skipped = setOf(intro))).isEqualTo(SegmentAction.None)
    }

    @Test
    fun `the button goes 8 s after it showed, stays with the controls, and does not come back by itself`() {
        val shown = mapOf(intro to 30.seconds)

        assertThat(at(37.seconds, shownAt = shown)).isEqualTo(SegmentAction.Button(intro))
        assertThat(at(39.seconds, shownAt = shown)).isEqualTo(SegmentAction.None)
        assertThat(at(39.seconds, shownAt = shown, controls = true)).isEqualTo(SegmentAction.Button(intro))
        assertThat(at(100.seconds, shownAt = shown)).isEqualTo(SegmentAction.None)
    }
}
