// The track selector is adapted from Moonfin (github.com/Moonfin-Client/Moonfin-Core, packages/moonfin_native_video:
// subtitle/DualSubtitleTrackSelector), GPL-3.0. See THIRD_PARTY.md.
package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.FixedTrackSelection
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelectorResult
import androidx.media3.exoplayer.upstream.BandwidthMeter
import io.github.kentprodan.mediatheq.core.data.sameLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A second subtitle track at once (#635, for language learners): the [language] asked for, kept across the queue so the
 * next episode shows it too, and the [cues] its renderer shows, which the video screens draw at the top of the picture.
 * Text formats only (SubRip, WebVTT, TTML, MP4 timed text): bitmap and styled subtitles would cover the first track.
 * One per process: the service's players and the screens share it.
 */
@OptIn(UnstableApi::class)
@Singleton
class SecondarySubtitles
    @Inject
    constructor() {
        private val wanted = MutableStateFlow<String?>(null)
        private val shown = MutableStateFlow<List<Cue>>(emptyList())
        private val selectors = CopyOnWriteArrayList<SecondarySubtitleTrackSelector>()

        /** The language of the second track, `null` for none. */
        val language: StateFlow<String?> = wanted.asStateFlow()

        /** The second track's cues now. */
        val cues: StateFlow<List<Cue>> = shown.asStateFlow()

        fun choose(language: String?) {
            if (wanted.value == language) return
            wanted.value = language
            if (language == null) shown.value = emptyList()
            selectors.forEach { it.invalidateNow() }
        }

        internal fun publish(cues: List<Cue>) {
            shown.value = if (wanted.value == null) emptyList() else cues
        }

        internal fun register(selector: SecondarySubtitleTrackSelector) = selectors.add(selector)

        internal fun unregister(selector: SecondarySubtitleTrackSelector) = selectors.remove(selector)
    }

/** A text format the second renderer shows as text, parsed during extraction (cues carry the source format). */
@OptIn(UnstableApi::class)
internal fun Format.isPlainText(): Boolean =
    when (if (sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) codecs else sampleMimeType) {
        MimeTypes.APPLICATION_SUBRIP, MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_MP4VTT,
        MimeTypes.APPLICATION_TTML, MimeTypes.APPLICATION_TX3G,
        -> true

        else -> false
    }

/**
 * Media3's selection for every renderer but the last, which is kept for the second subtitle track ([secondary]): the
 * first text track in its language that the first track does not already play. A media period cannot hand one sample
 * queue to two renderers, so the same track is never both.
 */
@UnstableApi
internal class SecondarySubtitleTrackSelector(
    context: Context,
    private val secondary: SecondarySubtitles,
) : TrackSelector() {
    private val primary = DefaultTrackSelector(context)

    private data class SelectionInfo(
        val primary: Any?,
    )

    fun invalidateNow() = invalidate(null)

    override fun init(
        listener: InvalidationListener,
        bandwidthMeter: BandwidthMeter,
    ) {
        super.init(listener, bandwidthMeter)
        primary.init(listener, bandwidthMeter)
        secondary.register(this)
    }

    override fun release() {
        secondary.unregister(this)
        primary.release()
        super.release()
    }

    override fun getParameters(): TrackSelectionParameters = primary.parameters

    override fun setParameters(parameters: TrackSelectionParameters) = primary.setParameters(parameters)

    override fun isSetParametersSupported() = true

    override fun setAudioAttributes(audioAttributes: AudioAttributes) = primary.setAudioAttributes(audioAttributes)

    override fun getRendererCapabilitiesListener() = primary.rendererCapabilitiesListener

    override fun onSelectionActivated(info: Any?) = primary.onSelectionActivated((info as? SelectionInfo)?.primary)

    override fun selectTracks(
        rendererCapabilities: Array<out RendererCapabilities>,
        trackGroups: TrackGroupArray,
        periodId: MediaSource.MediaPeriodId,
        timeline: Timeline,
    ): TrackSelectorResult {
        val last = rendererCapabilities.lastIndex
        val result = primary.selectTracks(rendererCapabilities.copyOfRange(0, last), trackGroups, periodId, timeline)
        val configurations = result.rendererConfigurations.copyOf(rendererCapabilities.size)
        val selections = result.selections.copyOf(rendererCapabilities.size)
        secondary.language.value?.let { language ->
            val chosen =
                (0 until trackGroups.length).map(trackGroups::get).firstOrNull { group ->
                    val format = group.getFormat(0)
                    group.type == C.TRACK_TYPE_TEXT &&
                        format.isPlainText() &&
                        sameLanguage(format.language, language) &&
                        result.selections.none { it?.trackGroup == group } &&
                        RendererCapabilities.getFormatSupport(rendererCapabilities[last].supportsFormat(format)) ==
                        C.FORMAT_HANDLED
                }
            if (chosen != null) {
                configurations[last] = RendererConfiguration.DEFAULT
                selections[last] = FixedTrackSelection(chosen, 0)
            }
        }
        // The second track stays out of the reported tracks: the first track's choice and libass read those.
        return TrackSelectorResult(configurations, selections, result.tracks, SelectionInfo(result.info))
    }
}
