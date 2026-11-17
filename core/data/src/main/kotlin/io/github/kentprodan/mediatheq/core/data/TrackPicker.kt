package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.ResolvedTracks
import io.github.kentprodan.mediatheq.core.model.TrackChoice
import io.github.kentprodan.mediatheq.core.model.Video
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
 * player, on the phone and the TV (#427): the choice resolves as the player's will (item, then season, then the defaults).
 */
class TrackPicker
    @Inject
    constructor(
        private val videos: VideoRepository,
        private val choices: TrackChoiceRepository,
        private val preferences: VideoPreferencesRepository = VideoPreferencesRepository.Defaults,
    ) {
        fun tracks(video: Video): Flow<VideoTracks> =
            combine(
                videos.streams(video.id),
                choices.choice(video.id),
                video.seasonId?.let { choices.choice(it) } ?: flowOf(null),
                preferences.preferences,
            ) { streams, own, season, settings ->
                VideoTracks(
                    streams,
                    resolveChoice(streams, own, season, settings.trackDefaults(Locale.getDefault().language)),
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
            /** The user picked Off (not merely no subtitle in force now). */
            subtitleOff: Boolean = false,
        ) {
            val streams = videos.streams(video.id).first()
            val choice =
                TrackChoice(
                    audioIndex = audioIndex,
                    audioLanguage = streams.firstOrNull { it.index == audioIndex }?.language,
                    subtitleIndex = subtitleIndex,
                    subtitleLanguage = streams.firstOrNull { it.index == subtitleIndex }?.language,
                    subtitleOff = subtitleOff,
                )
            val season = video.seasonId
            if (wholeSeason && season != null) {
                choices.save(season, choice)
                choices.clear(video.id)
            } else {
                choices.save(video.id, choice)
            }
        }
    }
