package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.VideoRange
import kotlin.time.Duration

/** What the player is doing, for screens (architecture skill, section 7). The position comes separately, sampled. */
data class PlayerUiModel(
    /** The current queue entry, or `null` when nothing is queued. */
    val item: NowPlayingItem? = null,
    /** Shows a pause button (playing or about to play); `false` shows play. */
    val isPlaying: Boolean = false,
    val duration: Duration = Duration.ZERO,
    /** Transcodes cannot be seeked yet (jellyfin skill R12, #38). */
    val isSeekable: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** The queue entry playing, to mark it in [PlaybackConnection.queue]. */
    val currentEntryId: String? = null,
    /** Shuffle on: the queue shows the shuffled order and cannot be reordered. */
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val output: AudioOutputInfo = AudioOutputInfo(),
    /** Loading the stream before it can play (Media3 buffering). */
    val isBuffering: Boolean = false,
    /** Able to play at once (Media3 ready); see [slowStarts]. */
    val isReady: Boolean = false,
    /** The player stopped on an error (Media3 `playerError`) and waits for a new item or a retry. */
    val isFailed: Boolean = false,
    /** Width / height of the playing video's picture once known; `null` for music or before the first frame. */
    val videoAspectRatio: Float? = null,
)

/** The audio output, for the bit-perfect indicator and the Audio sheet (media3 skill, sections 6 and 7). */
data class AudioOutputInfo(
    /** The sample rate sent unchanged to a USB DAC; `null` when not bit-perfect. Phone volume does not apply then. */
    val bitPerfectSampleRateHz: Int? = null,
    val usbDacConnected: Boolean = false,
    /** The DAC cannot take the playing track's format unchanged, so it plays normally. */
    val bitPerfectNoMatch: Boolean = false,
    /** Bit-perfect USB needs Android 14. */
    val bitPerfectSupported: Boolean = true,
    /** The playing track is Dolby Atmos, but this device and output cannot play it: it plays as a stereo conversion. */
    val atmosConverted: Boolean = false,
    /**
     * Dolby Atmos leaves the device untouched over HDMI now (ADR 0039): the AudioTrack the sink opened carries E-AC-3
     * JOC or TrueHD. The only source for an Atmos badge, never the metadata.
     */
    val atmosPassthrough: Boolean = false,
    /** The playing track is Dolby Atmos and plays as it is, but decoded on this device: no Atmos reaches the output. */
    val atmosDecoded: Boolean = false,
    /** The queue item [streamKnown] and [convertedKbps] describe; they arrive apart from the item itself. */
    val playedItemId: String? = null,
    /** The playing track's stream is resolved, so [convertedKbps] is known (#125); until then no quality is shown. */
    val streamKnown: Boolean = false,
    /** The bitrate of the server's conversion that plays (Opus); `null` when the original file plays (#125). */
    val convertedKbps: Int? = null,
    /** What the playing video really plays as (#276); `null` for music or before its first frame. */
    val video: VideoPlayback? = null,
) {
    /** The equalizer is off while bit-perfect: the samples must reach the DAC unchanged. */
    val equalizerBlocked: Boolean get() = bitPerfectSampleRateHz != null
}

/** One queue entry, for the queue screen. */
data class QueueItem(
    /** Unique in the queue (the same track can be queued twice); commands name entries by it. */
    val entryId: String,
    val id: MediaId,
    val title: String,
    val artist: String,
    val artwork: Artwork? = null,
    val duration: Duration = Duration.ZERO,
)

/** The playing track as the queue knows it. */
data class NowPlayingItem(
    val id: MediaId,
    val title: String,
    val artist: String,
    val artwork: Artwork? = null,
    /** A movie or an episode (ADR 0047): the video player shows it, never Now Playing. */
    val isVideo: Boolean = false,
)

/** The player's info line (#276): the video's codec, picture size, range, how it is delivered and the decoder. */
data class VideoPlayback(
    /** `video/hevc`, `video/dolby-vision`, ... */
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val range: VideoRange = VideoRange.Sdr,
    val playMethod: PlayMethod? = null,
    val decoder: String? = null,
)

/** The range a decoded format carries (#276): Dolby Vision by its decoder, else HDR10 (PQ) or HLG by its transfer. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun videoRangeOf(
    mimeType: String?,
    colorTransfer: Int,
): VideoRange =
    when {
        mimeType == "video/dolby-vision" -> VideoRange.DolbyVision
        colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_ST2084 -> VideoRange.Hdr10
        colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_HLG -> VideoRange.Hlg
        else -> VideoRange.Sdr
    }
