package io.github.kentprodan.mediatheq.feature.video

import io.github.kentprodan.mediatheq.core.data.VideoTracks
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.isTextSubtitle
import io.github.kentprodan.mediatheq.core.ui.TrackOption
import io.github.kentprodan.mediatheq.core.ui.TracksSheetState

/** The sheet's rows for [tracks]: audio streams, then Off and the subtitles; labels as the server names them. */
internal fun VideoTracks.sheet(
    offLabel: String,
    /** "Track %1${'$'}d" for a stream without a name. */
    trackFormat: String,
): TracksSheetState {
    fun MediaStreamInfo.label() = displayTitle ?: title ?: language ?: trackFormat.format(index)
    val audio = streams.filter { it.type == StreamType.Audio }
    val subtitles = streams.filter { it.type == StreamType.Subtitle }
    return TracksSheetState(
        // No choice: the server plays its default audio stream.
        audio =
            (resolved.audioIndex ?: audio.firstOrNull { it.isDefault }?.index ?: audio.firstOrNull()?.index)
                .let { playing -> audio.map { TrackOption(it.index, it.label(), it.index == playing) } },
        subtitles =
            listOf(TrackOption(null, offLabel, resolved.subtitleIndex == null)) +
                subtitles.map {
                    TrackOption(it.index, it.label(), it.index == resolved.subtitleIndex)
                },
        wholeSeason = wholeSeason,
    )
}

/** Off, then one plain text subtitle per language for the second track (#635); `null` when there is none. */
internal fun VideoTracks.secondSubtitles(
    offLabel: String,
    trackFormat: String,
    language: String?,
): List<TrackOption>? {
    val text = streams.filter { it.isTextSubtitle() && it.language != null }.distinctBy { it.language }
    if (text.isEmpty()) return null
    return listOf(TrackOption(null, offLabel, language == null)) +
        text.map {
            TrackOption(
                it.index,
                it.displayTitle ?: it.title ?: it.language ?: trackFormat.format(it.index),
                it.language == language,
            )
        }
}
