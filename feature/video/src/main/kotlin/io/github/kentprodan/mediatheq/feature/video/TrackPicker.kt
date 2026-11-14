package io.github.kentprodan.mediatheq.feature.video

import io.github.kentprodan.mediatheq.core.data.TrackChoiceRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.resolveChoice
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.ResolvedTracks
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.SubtitleMode
import io.github.kentprodan.mediatheq.core.model.TrackChoice
import io.github.kentprodan.mediatheq.core.model.TrackDefaults
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.ui.TrackOption
import io.github.kentprodan.mediatheq.core.ui.TracksSheetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.util.Locale
import javax.inject.Inject

/** A video's tracks for the sheet (#274): its streams, what plays now, and whether its season's choice applies. */
data class VideoTracks(
    val streams: List<MediaStreamInfo>,
    val resolved: ResolvedTracks,
    /** For an episode: the season's choice is the one in force (the episode has none of its own). */
    val wholeSeason: Boolean?,
)

/**
 * Reads and saves the tracks chosen for a video (ADR 0050, #274), shared by the movie page, the series page and the
 * player: the choice resolves as the player's will (item, then season, then the defaults).
 */
class TrackPicker
    @Inject
    constructor(
        private val videos: VideoRepository,
        private val choices: TrackChoiceRepository,
    ) {
        fun tracks(video: Video): Flow<VideoTracks> =
            combine(
                videos.streams(video.id),
                choices.choice(video.id),
                video.seasonId?.let { choices.choice(it) } ?: flowOf(null),
            ) { streams, own, season ->
                VideoTracks(
                    streams,
                    resolveChoice(streams, own, season, DEFAULTS),
                    wholeSeason = video.seasonId?.let { own == null && season != null },
                )
            }

        /**
         * Saves the audio [audioIndex] / subtitle [subtitleIndex] (`null` = off) on [video], or on its season with
         * [wholeSeason] (the episode's own choice then goes, so the season's applies to it too).
         */
        suspend fun choose(
            video: Video,
            audioIndex: Int?,
            subtitleIndex: Int?,
            wholeSeason: Boolean,
        ) {
            val streams = videos.streams(video.id).first()
            val choice =
                TrackChoice(
                    audioIndex = audioIndex,
                    audioLanguage = streams.firstOrNull { it.index == audioIndex }?.language,
                    subtitleIndex = subtitleIndex,
                    subtitleLanguage = streams.firstOrNull { it.index == subtitleIndex }?.language,
                    subtitleOff = subtitleIndex == null,
                )
            val season = video.seasonId
            if (wholeSeason && season != null) {
                choices.save(season, choice)
                choices.clear(video.id)
            } else {
                choices.save(video.id, choice)
            }
        }

        private companion object {
            // shortcut: Settings -> Video (#276) sets these; until then forced subtitles in the phone's language.
            val DEFAULTS =
                TrackDefaults(audioLanguage = Locale.getDefault().language, subtitleMode = SubtitleMode.ForcedOnly)
        }
    }

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
