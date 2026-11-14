package io.github.kentprodan.mediatheq.feature.video

import io.github.kentprodan.mediatheq.core.data.VideoPreferences
import io.github.kentprodan.mediatheq.core.model.MediaSegment
import io.github.kentprodan.mediatheq.core.model.SkipMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What the player does at a position (#277). */
sealed interface SegmentAction {
    data object None : SegmentAction

    /** "Skip intro" / "Skip credits" ... shows for [segment]. */
    data class Button(
        val segment: MediaSegment,
    ) : SegmentAction

    /** [segment] is skipped by itself, once. */
    data class Skip(
        val segment: MediaSegment,
    ) : SegmentAction
}

/**
 * The action at [position] (#277), pure: inside a segment its setting decides. Automatic skips happen once ([skipped]);
 * a skipped segment never shows its button again, also when seeking back into it. The button shows from where it first
 * showed ([buttonShownAt]) for [BUTTON_TIME], longer while the controls are shown, and not again once it went.
 */
fun segmentAction(
    position: Duration,
    segments: List<MediaSegment>,
    settings: VideoPreferences,
    skipped: Set<MediaSegment>,
    buttonShownAt: Map<MediaSegment, Duration>,
    controlsShown: Boolean,
): SegmentAction {
    val segment = segments.firstOrNull { position >= it.start && position < it.end } ?: return SegmentAction.None
    if (segment in skipped) return SegmentAction.None
    return when (settings.skipFor(segment.type)) {
        SkipMode.Off -> {
            SegmentAction.None
        }

        SkipMode.Auto -> {
            SegmentAction.Skip(segment)
        }

        SkipMode.Button -> {
            val shownAt = buttonShownAt[segment]
            val showing = shownAt == null || position in shownAt..<(shownAt + BUTTON_TIME)
            if (showing || controlsShown) SegmentAction.Button(segment) else SegmentAction.None
        }
    }
}

/** How long the skip button stays without the controls (#277). */
val BUTTON_TIME = 8.seconds
