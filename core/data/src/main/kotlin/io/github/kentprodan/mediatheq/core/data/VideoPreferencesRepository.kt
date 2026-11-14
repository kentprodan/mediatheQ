package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.AudioPreferencesStore
import io.github.kentprodan.mediatheq.core.datastore.StoredVideoPreferences
import io.github.kentprodan.mediatheq.core.model.SubtitleMode
import io.github.kentprodan.mediatheq.core.model.TrackDefaults
import io.github.kentprodan.mediatheq.core.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Settings -> Playback -> Video (#276, ADR 0050). */
data class VideoPreferences(
    /** Wi-Fi and Ethernet. */
    val unmeteredQuality: VideoQuality = VideoQuality.Original,
    /** Mobile data. */
    val meteredQuality: VideoQuality = VideoQuality.P480,
    /** ISO 639 code; `null` = the phone's language. */
    val audioLanguage: String? = null,
    val subtitleMode: SubtitleMode = SubtitleMode.ForcedOnly,
    /** For [SubtitleMode.Language]; `null` = the audio language. */
    val subtitleLanguage: String? = null,
    /** ASS / SSA keep their styling, converted into the picture by the server. */
    val burnInStyled: Boolean = false,
    /** The next episode starts after the countdown (#275). */
    val autoPlayNext: Boolean = true,
    /** Picture-in-picture when leaving the player (#278). */
    val pictureInPicture: Boolean = true,
    /** Leaving the player keeps the sound going (#278). */
    val keepAudio: Boolean = false,
) {
    fun quality(metered: Boolean): VideoQuality = if (metered) meteredQuality else unmeteredQuality

    /** The track defaults for items without a choice; [phoneLanguage] stands in for an unset audio language. */
    fun trackDefaults(phoneLanguage: String): TrackDefaults {
        val audio = audioLanguage ?: phoneLanguage
        return TrackDefaults(audio, subtitleMode, subtitleLanguage ?: audio)
    }
}

interface VideoPreferencesRepository {
    val preferences: Flow<VideoPreferences>

    /** Applies [change] to the current preferences and stores the result. */
    suspend fun update(change: (VideoPreferences) -> VideoPreferences)
}

/** In the audio preferences file, so Backup & restore carries it like the rest (#150). */
@Singleton
class DefaultVideoPreferencesRepository
    @Inject
    constructor(
        private val store: AudioPreferencesStore,
    ) : VideoPreferencesRepository {
        override val preferences: Flow<VideoPreferences> = store.values.map { it.video.toPreferences() }

        override suspend fun update(change: (VideoPreferences) -> VideoPreferences) {
            store.setVideo(change(preferences.first()).toStored())
        }
    }

/** Unknown or missing values fall back to the defaults. */
internal fun StoredVideoPreferences.toPreferences(): VideoPreferences {
    val defaults = VideoPreferences()
    return VideoPreferences(
        unmeteredQuality = enumOrNull<VideoQuality>(unmeteredQuality) ?: defaults.unmeteredQuality,
        meteredQuality = enumOrNull<VideoQuality>(meteredQuality) ?: defaults.meteredQuality,
        audioLanguage = audioLanguage?.ifBlank { null },
        subtitleMode = enumOrNull<SubtitleMode>(subtitleMode) ?: defaults.subtitleMode,
        subtitleLanguage = subtitleLanguage?.ifBlank { null },
        burnInStyled = burnInStyled ?: defaults.burnInStyled,
        autoPlayNext = autoPlayNext ?: defaults.autoPlayNext,
        pictureInPicture = pictureInPicture ?: defaults.pictureInPicture,
        keepAudio = keepAudio ?: defaults.keepAudio,
    )
}

/** Only what differs from the defaults is stored, so a later default change reaches users who never set it. */
internal fun VideoPreferences.toStored(): StoredVideoPreferences {
    val defaults = VideoPreferences()

    fun <T> T.unlessDefault(default: T): T? = takeIf { it != default }
    return StoredVideoPreferences(
        unmeteredQuality = unmeteredQuality.unlessDefault(defaults.unmeteredQuality)?.name,
        meteredQuality = meteredQuality.unlessDefault(defaults.meteredQuality)?.name,
        audioLanguage = audioLanguage,
        subtitleMode = subtitleMode.unlessDefault(defaults.subtitleMode)?.name,
        subtitleLanguage = subtitleLanguage,
        burnInStyled = burnInStyled.unlessDefault(defaults.burnInStyled),
        autoPlayNext = autoPlayNext.unlessDefault(defaults.autoPlayNext),
        pictureInPicture = pictureInPicture.unlessDefault(defaults.pictureInPicture),
        keepAudio = keepAudio.unlessDefault(defaults.keepAudio),
    )
}
