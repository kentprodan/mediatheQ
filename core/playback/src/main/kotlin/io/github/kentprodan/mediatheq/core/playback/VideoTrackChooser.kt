package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.data.TrackChoiceRepository
import io.github.kentprodan.mediatheq.core.data.VideoPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.resolveChoice
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ResolvedTracks
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject

/** The audio and subtitle streams a video plays with (ADR 0050, #274); `null` leaves them to the server. */
fun interface VideoTrackChooser {
    suspend fun tracks(videoId: MediaId): ResolvedTracks?

    companion object {
        val None = VideoTrackChooser { null }
    }
}

/**
 * [VideoTrackChooser] from Room: the video's own choice, else its season's, else Settings -> Video's defaults (#276;
 * forced subtitles only and the phone's language unless set).
 */
internal class RepositoryVideoTrackChooser
    @Inject
    constructor(
        private val videos: VideoRepository,
        private val choices: TrackChoiceRepository,
        private val preferences: VideoPreferencesRepository,
    ) : VideoTrackChooser {
        override suspend fun tracks(videoId: MediaId): ResolvedTracks? {
            val video = videos.video(videoId).first() ?: return null
            val streams = videos.streams(videoId).first()
            val season = video.seasonId?.let { choices.choice(it).first() }
            val defaults = preferences.preferences.first().trackDefaults(Locale.getDefault().language)
            return resolveChoice(streams, choices.choice(videoId).first(), season, defaults)
        }
    }
