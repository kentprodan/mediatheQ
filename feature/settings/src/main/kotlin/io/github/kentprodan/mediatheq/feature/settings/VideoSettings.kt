package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.data.VideoPreferences
import io.github.kentprodan.mediatheq.core.model.SkipMode
import io.github.kentprodan.mediatheq.core.model.SubtitleMode
import io.github.kentprodan.mediatheq.core.model.VideoQuality
import java.util.Locale

/**
 * Settings -> Playback -> Video (#276): the quality per network kind, the audio and subtitle defaults for items without
 * a choice, burn-in of styled subtitles, auto-play of the next episode, picture-in-picture and keeping the audio.
 */
internal fun LazyListScope.videoGroups(
    video: VideoPreferences,
    onAction: (SettingsAction) -> Unit,
    onRestore: () -> Unit,
) {
    fun set(change: VideoPreferences.() -> VideoPreferences) = onAction(SettingsAction.SetVideo { it.change() })
    item(key = "video_quality") {
        Group(R.string.settings_video_quality) {
            listOf(false to R.string.settings_quality_unmetered, true to R.string.settings_quality_metered)
                .forEach { (metered, title) ->
                    item { shapes ->
                        ChoiceItem(
                            shapes,
                            stringResource(title),
                            null,
                            VideoQuality.entries,
                            selected = video.quality(metered),
                            label = { it.label() },
                            onSelect = { quality ->
                                set {
                                    if (metered) {
                                        copy(
                                            meteredQuality = quality,
                                        )
                                    } else {
                                        copy(unmeteredQuality = quality)
                                    }
                                }
                            },
                        )
                    }
                }
        }
    }
    item(key = "video_tracks") {
        Group(R.string.settings_video_tracks) {
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_video_audio_language),
                    null,
                    LANGUAGES,
                    selected = video.audioLanguage,
                    label = { languageLabel(it) },
                    onSelect = { set { copy(audioLanguage = it) } },
                )
            }
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_video_subtitles),
                    null,
                    SubtitleMode.entries,
                    selected = video.subtitleMode,
                    label = { stringResource(it.label) },
                    onSelect = { set { copy(subtitleMode = it) } },
                )
            }
            item { shapes ->
                ChoiceItem(
                    shapes,
                    stringResource(R.string.settings_video_subtitle_language),
                    stringResource(R.string.settings_video_subtitle_language_summary),
                    LANGUAGES,
                    selected = video.subtitleLanguage,
                    label = { languageLabel(it) },
                    onSelect = { set { copy(subtitleLanguage = it) } },
                    enabled = video.subtitleMode == SubtitleMode.Language,
                )
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_video_burn_in),
                    stringResource(R.string.settings_video_burn_in_summary),
                    video.burnInStyled,
                ) { set { copy(burnInStyled = it) } }
            }
        }
    }
    item(key = "video_skipping") {
        Group(R.string.settings_video_skipping) {
            listOf(
                R.string.settings_video_skip_intro to VideoPreferences::skipIntro,
                R.string.settings_video_skip_credits to VideoPreferences::skipCredits,
                R.string.settings_video_skip_recap to VideoPreferences::skipRecapPreview,
            ).forEach { (title, current) ->
                item { shapes ->
                    ChoiceItem(
                        shapes,
                        stringResource(title),
                        null,
                        SkipMode.entries,
                        selected = current(video),
                        label = { stringResource(it.label) },
                        onSelect = { mode ->
                            set {
                                when (title) {
                                    R.string.settings_video_skip_intro -> copy(skipIntro = mode)
                                    R.string.settings_video_skip_credits -> copy(skipCredits = mode)
                                    else -> copy(skipRecapPreview = mode)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    item(key = "video_player") {
        Group(R.string.settings_video_player) {
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_video_auto_play),
                    stringResource(R.string.settings_video_auto_play_summary),
                    video.autoPlayNext,
                ) { set { copy(autoPlayNext = it) } }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_video_pip),
                    stringResource(R.string.settings_video_pip_summary),
                    video.pictureInPicture,
                ) { set { copy(pictureInPicture = it) } }
            }
            item { shapes ->
                SwitchItem(
                    shapes,
                    stringResource(R.string.settings_video_keep_audio),
                    stringResource(R.string.settings_video_keep_audio_summary),
                    video.keepAudio,
                ) { set { copy(keepAudio = it) } }
            }
        }
    }
    restoreDefaultsGroup(onRestore)
}

/** `null` = the phone's language; then the languages most libraries carry. */
private val LANGUAGES = listOf(null, "en", "ro", "fr", "de", "es", "it")

@Composable
private fun languageLabel(code: String?): String =
    code?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()).replaceFirstChar(Char::titlecase) }
        ?: stringResource(R.string.settings_video_phone_language)

@Composable
private fun VideoQuality.label(): String =
    maxHeight?.let { stringResource(R.string.settings_video_quality_height, it) }
        ?: stringResource(R.string.settings_video_quality_original)

private val SubtitleMode.label: Int
    get() =
        when (this) {
            SubtitleMode.Off -> R.string.settings_video_subtitles_off
            SubtitleMode.ForcedOnly -> R.string.settings_video_subtitles_forced
            SubtitleMode.Language -> R.string.settings_video_subtitles_language
        }

private val SkipMode.label: Int
    get() =
        when (this) {
            SkipMode.Off -> R.string.settings_video_skip_off
            SkipMode.Button -> R.string.settings_video_skip_button
            SkipMode.Auto -> R.string.settings_video_skip_auto
        }
