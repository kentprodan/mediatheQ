package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.AudioPreferencesStore
import io.github.kentprodan.mediatheq.core.datastore.StoredVideoPreferences
import io.github.kentprodan.mediatheq.core.model.SegmentType
import io.github.kentprodan.mediatheq.core.model.SkipMode
import io.github.kentprodan.mediatheq.core.model.SubtitleMode
import io.github.kentprodan.mediatheq.core.model.SubtitleStyle
import io.github.kentprodan.mediatheq.core.model.TrackDefaults
import io.github.kentprodan.mediatheq.core.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

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
    /** Skipping (#277): intros, credits (outros), recaps and previews (with commercials). */
    val skipIntro: SkipMode = SkipMode.Button,
    val skipCredits: SkipMode = SkipMode.Button,
    val skipRecapPreview: SkipMode = SkipMode.Button,
    /** No spoilers (#428, #440): an unwatched episode shows no overview. */
    val hideUnwatchedOverviews: Boolean = false,
    /** How subtitles look (#429); every part follows the system caption style by default. */
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /** Downloads made by hand go once watched, after this delay (#413). */
    val watchedRemoval: WatchedRemoval = WatchedRemoval.Off,
    /** When [watchedRemoval] was turned on: only plays after it count, so turning it on never empties the downloads. */
    val watchedRemovalSince: Instant? = null,
) {
    /** The setting for a segment of [type]. */
    fun skipFor(type: SegmentType): SkipMode =
        when (type) {
            SegmentType.Intro -> skipIntro
            SegmentType.Outro -> skipCredits
            SegmentType.Recap, SegmentType.Preview, SegmentType.Commercial -> skipRecapPreview
        }

    fun quality(metered: Boolean): VideoQuality = if (metered) meteredQuality else unmeteredQuality

    /** The track defaults for items without a choice; [phoneLanguage] stands in for an unset audio language. */
    fun trackDefaults(phoneLanguage: String): TrackDefaults {
        val audio = audioLanguage ?: phoneLanguage
        return TrackDefaults(audio, subtitleMode, subtitleLanguage ?: audio)
    }
}

/** When a watched video download made by hand goes (#413, owner decision). */
enum class WatchedRemoval(
    val delay: Duration?,
) {
    Off(null),
    Now(Duration.ZERO),
    OneDay(1.days),
    OneWeek(7.days),
}

interface VideoPreferencesRepository {
    val preferences: Flow<VideoPreferences>

    /** Applies [change] to the current preferences and stores the result. */
    suspend fun update(change: (VideoPreferences) -> VideoPreferences)

    companion object {
        /** The defaults, never changed: for a class built without Hilt (tests, previews). */
        val Defaults: VideoPreferencesRepository =
            object : VideoPreferencesRepository {
                override val preferences = flowOf(VideoPreferences())

                override suspend fun update(change: (VideoPreferences) -> VideoPreferences) = Unit
            }
    }
}

/** In the audio preferences file, so Backup & restore carries it like the rest (#150). */
@Singleton
class DefaultVideoPreferencesRepository
    @Inject
    constructor(
        private val store: AudioPreferencesStore,
        private val clock: Clock = Clock.System,
    ) : VideoPreferencesRepository {
        override val preferences: Flow<VideoPreferences> = store.values.map { it.video.toPreferences() }

        override suspend fun update(change: (VideoPreferences) -> VideoPreferences) {
            val old = preferences.first()
            val new = change(old)
            // Turned on (#413): from now on, so what was watched and kept stays.
            val since =
                when {
                    new.watchedRemoval == WatchedRemoval.Off -> null
                    old.watchedRemoval == WatchedRemoval.Off -> clock.now()
                    else -> new.watchedRemovalSince
                }
            store.setVideo(new.copy(watchedRemovalSince = since).toStored())
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
        skipIntro = enumOrNull<SkipMode>(skipIntro) ?: defaults.skipIntro,
        skipCredits = enumOrNull<SkipMode>(skipCredits) ?: defaults.skipCredits,
        skipRecapPreview = enumOrNull<SkipMode>(skipRecapPreview) ?: defaults.skipRecapPreview,
        hideUnwatchedOverviews = hideUnwatchedOverviews ?: defaults.hideUnwatchedOverviews,
        watchedRemoval = enumOrNull<WatchedRemoval>(watchedRemoval) ?: defaults.watchedRemoval,
        watchedRemovalSince = watchedRemovalSince?.let(Instant::fromEpochMilliseconds),
        subtitleStyle =
            SubtitleStyle(
                enumOrNull<SubtitleStyle.Size>(subtitleSize) ?: SubtitleStyle.Size.System,
                enumOrNull<SubtitleStyle.Background>(subtitleBackground) ?: SubtitleStyle.Background.System,
                enumOrNull<SubtitleStyle.Color>(subtitleColor) ?: SubtitleStyle.Color.System,
            ),
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
        skipIntro = skipIntro.unlessDefault(defaults.skipIntro)?.name,
        skipCredits = skipCredits.unlessDefault(defaults.skipCredits)?.name,
        skipRecapPreview = skipRecapPreview.unlessDefault(defaults.skipRecapPreview)?.name,
        hideUnwatchedOverviews = hideUnwatchedOverviews.unlessDefault(defaults.hideUnwatchedOverviews),
        watchedRemoval = watchedRemoval.unlessDefault(defaults.watchedRemoval)?.name,
        watchedRemovalSince = watchedRemovalSince?.toEpochMilliseconds(),
        subtitleSize = subtitleStyle.size.unlessDefault(SubtitleStyle.Size.System)?.name,
        subtitleBackground = subtitleStyle.background.unlessDefault(SubtitleStyle.Background.System)?.name,
        subtitleColor = subtitleStyle.color.unlessDefault(SubtitleStyle.Color.System)?.name,
    )
}
