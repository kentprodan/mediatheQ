package io.github.kentprodan.mediatheq.core.playback

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import io.github.kentprodan.mediatheq.core.data.sameLanguage
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.SubtitleDelivery
import io.github.kentprodan.mediatheq.core.model.SubtitleMethod

/** The id a side-loaded subtitle's track carries: how the selection finds it again. */
internal fun sideLoadedId(index: Int) = "sub:$index"

/**
 * The server's external subtitle files as side-loaded tracks of a direct play (ADR 0050, #274), fetched through the
 * item's authenticated data source; [chosen] is flagged default. Formats Media3 cannot side-load are left out.
 */
internal fun sideLoadedSubtitles(
    deliveries: List<SubtitleDelivery>,
    chosen: Int?,
): List<MediaItem.SubtitleConfiguration> =
    deliveries.filter { it.method == SubtitleMethod.External && it.url != null }.mapNotNull { delivery ->
        val mime = subtitleMime(delivery.codec) ?: return@mapNotNull null
        MediaItem.SubtitleConfiguration
            .Builder(delivery.url!!.toUri())
            .setId(sideLoadedId(delivery.index))
            .setMimeType(mime)
            .setLanguage(delivery.language)
            .setLabel(delivery.title)
            .setSelectionFlags(if (delivery.index == chosen) C.SELECTION_FLAG_DEFAULT else 0)
            .build()
    }

private fun subtitleMime(codec: String?): String? =
    when (codec?.lowercase()) {
        "subrip", "srt" -> MimeTypes.APPLICATION_SUBRIP
        "webvtt", "vtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "ttml" -> MimeTypes.APPLICATION_TTML
        else -> null
    }

/** A text track group of the player, as the selection sees it. */
internal data class TextGroup(
    val id: String?,
    val language: String?,
)

/**
 * Which of the player's text [groups] shows the [chosen] subtitle stream (ADR 0050), `null` for none (none chosen, burned
 * into the picture, or not found). A side-loaded file is found by its id; an embedded one by its place among the
 * embedded subtitles (the file's order), checked against its language; one inside a conversion's playlist by language.
 */
internal fun pickTextGroup(
    deliveries: List<SubtitleDelivery>,
    chosen: Int?,
    groups: List<TextGroup>,
): Int? {
    val delivery = deliveries.firstOrNull { it.index == chosen } ?: return null
    val embeddedGroups = groups.indices.filterNot { groups[it].id?.startsWith("sub:") == true }

    fun byLanguage() = embeddedGroups.firstOrNull { sameLanguage(groups[it].language, delivery.language) }
    return when (delivery.method) {
        SubtitleMethod.External -> {
            groups.indexOfFirst { it.id == sideLoadedId(delivery.index) }.takeIf { it >= 0 }
        }

        SubtitleMethod.Embed -> {
            // Every subtitle inside the file counts (also burned-in and dropped ones): the player lists them all.
            val place = deliveries.filter { it.method != SubtitleMethod.External }.indexOf(delivery)
            val atPlace = embeddedGroups.getOrNull(place)
            val language = atPlace?.let { groups[it].language }
            if (atPlace != null &&
                (language == null || delivery.language == null || sameLanguage(language, delivery.language))
            ) {
                atPlace
            } else {
                byLanguage()
            }
        }

        SubtitleMethod.Hls -> {
            byLanguage()
        }

        SubtitleMethod.Encode, SubtitleMethod.Drop -> {
            null
        }
    }
}

/**
 * Which of the player's audio [groups] (their languages, in the file's order) is the [chosen] audio stream of a direct
 * play, by its place among the file's [audio] streams, checked against the language; `null` leaves the player's own.
 */
internal fun pickAudioGroup(
    audio: List<MediaStreamInfo>,
    chosen: Int?,
    groups: List<String?>,
): Int? {
    val place = audio.indexOfFirst { it.index == chosen }.takeIf { it >= 0 } ?: return null
    val language = audio[place].language
    val atPlace = groups.getOrNull(place)
    if (place < groups.size && (atPlace == null || language == null || sameLanguage(atPlace, language))) return place
    return groups.indexOfFirst { sameLanguage(it, language) }.takeIf { it >= 0 }
}

/**
 * Keeps the text track of a playing video on its chosen subtitle (#274): when the tracks are known, the one
 * [pickTextGroup] names is forced on, or text is turned off. Music is left alone.
 */
@OptIn(UnstableApi::class)
internal class VideoTextSelector(
    private val player: Player,
    private val resolver: StreamResolver,
) : Player.Listener {
    override fun onTracksChanged(tracks: Tracks) {
        val item = player.currentMediaItem?.takeIf { it.isVideo() } ?: return
        val id = item.localConfiguration?.uri?.videoId() ?: return
        val stream = resolver.current(id) ?: return
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val picked =
            pickTextGroup(
                stream.subtitles,
                stream.subtitleStreamIndex,
                textGroups.map {
                    TextGroup(
                        it.mediaTrackGroup.getFormat(0).id,
                        it.mediaTrackGroup.getFormat(0).language,
                    )
                },
            )
        val builder =
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        if (picked == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .addOverride(TrackSelectionOverride(textGroups[picked].mediaTrackGroup, 0))
        }
        // A file played as it is has all its audio streams: the chosen one is selected here, not by the server.
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val audio =
            pickAudioGroup(
                stream.audioStreams,
                stream.audioStreamIndex,
                audioGroups.map { it.mediaTrackGroup.getFormat(0).language },
            )
        if (audio != null && audioGroups.size > 1) {
            builder.addOverride(TrackSelectionOverride(audioGroups[audio].mediaTrackGroup, 0))
        }
        val parameters = builder.build()
        if (parameters != player.trackSelectionParameters) player.trackSelectionParameters = parameters
    }
}
