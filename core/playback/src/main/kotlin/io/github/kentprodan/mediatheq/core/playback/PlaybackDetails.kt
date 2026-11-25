package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How the audio leaves the app (#670). */
enum class AudioPath { Pcm, Offload, BitPerfect, Passthrough }

/**
 * What the engine decided for the playing item (#670), beside the video facts of [VideoPlayback]: the audio decoder and
 * format, how the audio leaves, dropped frames and recoveries, and the chosen subtitle's delivery.
 */
data class EngineDetails(
    val audioDecoder: String? = null,
    /** `audio/eac3-joc 6 ch 48000 Hz`. */
    val audioFormat: String? = null,
    val audioPath: AudioPath = AudioPath.Pcm,
    /** The bitstream sent over HDMI on [AudioPath.Passthrough]: `E-AC-3 JOC`, `TrueHD`, ... */
    val passthroughEncoding: String? = null,
    val droppedFrames: Int = 0,
    /** Decoder reclaims, stuck resumes and conversions after a failure (#632) since the item started. */
    val recoveries: Int = 0,
    /** The chosen subtitle: codec and delivery (`subrip · External`), `null` when off. */
    val subtitle: String? = null,
    /** Styled subtitles drawn by libass (#635). */
    val subtitleLibass: Boolean = false,
    val subtitleDelayMs: Long = 0,
) {
    /** One line for the app log under `MqEngine`. */
    fun logLine(): String =
        listOfNotNull(
            "audio ${audioFormat ?: "?"} via ${audioDecoder ?: "?"}",
            "out ${audioPath.name}${passthroughEncoding?.let { " $it" }.orEmpty()}",
            "dropped $droppedFrames",
            "recoveries $recoveries".takeIf { recoveries > 0 },
            subtitle?.let {
                "sub $it${if (subtitleLibass) " libass" else ""}${if (subtitleDelayMs != 0L) " ${subtitleDelayMs}ms" else ""}"
            },
        ).joinToString(" · ")
}

/** The playing item's [EngineDetails], written by the service, read by the player's details overlay (#670). */
@Singleton
class PlaybackDetails
    @Inject
    constructor() {
        private val state = MutableStateFlow(EngineDetails())
        val details: StateFlow<EngineDetails> = state.asStateFlow()

        internal fun update(change: (EngineDetails) -> EngineDetails) {
            state.value = change(state.value)
        }

        internal fun reset() {
            state.value = EngineDetails()
        }
    }

/** The audio decoder, its input format and dropped video frames, into [details] (#670); a new item starts over. */
@UnstableApi
internal class EngineDetailsListener(
    private val details: PlaybackDetails,
) : AnalyticsListener {
    override fun onMediaItemTransition(
        eventTime: AnalyticsListener.EventTime,
        mediaItem: androidx.media3.common.MediaItem?,
        reason: Int,
    ) = details.update { it.copy(droppedFrames = 0, recoveries = 0) }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) = details.update { it.copy(audioDecoder = decoderName) }

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) = details.update { it.copy(audioFormat = format.describe()) }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long,
    ) = details.update { it.copy(droppedFrames = it.droppedFrames + droppedFrames) }
}

internal fun Format.describe(): String =
    listOfNotNull(
        sampleMimeType,
        channelCount.takeIf { it > 0 }?.let { "$it ch" },
        sampleRate.takeIf { it > 0 }?.let { "$it Hz" },
    ).joinToString(" ")

/** An AudioTrack encoding by name, for a passthrough bitstream. */
internal fun encodingName(encoding: Int?): String? =
    when (encoding) {
        null -> null
        C.ENCODING_AC3 -> "AC-3"
        C.ENCODING_E_AC3 -> "E-AC-3"
        C.ENCODING_E_AC3_JOC -> "E-AC-3 JOC"
        C.ENCODING_AC4 -> "AC-4"
        C.ENCODING_DOLBY_TRUEHD -> "TrueHD"
        C.ENCODING_DTS -> "DTS"
        C.ENCODING_DTS_HD -> "DTS-HD"
        else -> "encoding $encoding"
    }
