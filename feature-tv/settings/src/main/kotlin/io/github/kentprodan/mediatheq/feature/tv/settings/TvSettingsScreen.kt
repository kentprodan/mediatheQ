package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Surface
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.data.TvCarousel
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvControl
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.SubtitleStyle
import io.github.kentprodan.mediatheq.core.model.TvLogoSize
import io.github.kentprodan.mediatheq.core.model.TvTitleStyle
import io.github.kentprodan.mediatheq.core.ui.tv.title

@Composable
fun TvSettingsRoute(
    versionName: String,
    onOpenLicenses: () -> Unit,
    onOpenHomeRows: () -> Unit = {},
    onOpenParental: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    viewModel: TvSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvSettingsScreen(
        state,
        versionName,
        viewModel::onAction,
        onOpenLicenses,
        onOpenHomeRows,
        onOpenParental,
        onSwitchProfile,
    )
}

/** The levels of Settings (#580): the categories, each category's page, and the pages that pick one value. */
internal enum class SettingsPage {
    Root,
    Sound,
    Video,
    Display,
    Home,
    Library,
    Account,
    About,
    Quality,
    DisplayMatch,
    SubtitleSize,
    SubtitleBackground,
    SubtitleColor,
    Carousel,
    CarouselSources,
    MenuStyle,
    TitleStyle,
    LogoSize,
    AppMode,
    SignOut,
}

/**
 * Settings on TV (#243), laid out like the Google TV system Settings (#580): full screen, a level's rows on the left
 * and a dimmed preview of the focused row's next level on the right. Select or Right opens that level, Back or Left
 * comes back to the row that opened it; a value is picked on its own level, with a radio on the current one.
 */
@Composable
fun TvSettingsScreen(
    state: TvSettingsUiState,
    versionName: String,
    onAction: (TvSettingsAction) -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenHomeRows: () -> Unit = {},
    onOpenParental: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
) = TvSettingsScreen(
    state,
    versionName,
    onAction,
    onOpenLicenses,
    onOpenHomeRows,
    onOpenParental,
    onSwitchProfile,
    start = listOf(SettingsPage.Root),
)

/** Settings opened at the levels [start] (the screenshots of the deeper levels). */
@Composable
internal fun TvSettingsScreen(
    state: TvSettingsUiState,
    versionName: String,
    onAction: (TvSettingsAction) -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenHomeRows: () -> Unit,
    onOpenParental: () -> Unit,
    onSwitchProfile: () -> Unit,
    start: List<SettingsPage>,
) {
    Surface(Modifier.fillMaxSize()) {
        if (state !is TvSettingsUiState.Content) return@Surface
        // Kept with the screen's saved state, so coming back from the licenses or Home rows finds the same level and row.
        var stack by rememberSaveable { mutableStateOf(start) }
        var focused by rememberSaveable { mutableStateOf(mapOf<SettingsPage, String>()) }
        val open: (SettingsPage) -> Unit = { child ->
            // Back lands on the row that opened it, however it was opened.
            focused = focused + (stack.last() to child.name)
            stack = stack + child
        }
        val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
        BackHandler(enabled = stack.size > 1, onBack = back)
        val pages =
            Pages(
                state,
                versionName,
                onAction,
                open,
                back,
                onOpenLicenses,
                onOpenHomeRows,
                onOpenParental,
                onSwitchProfile,
            )
        AnimatedContent(
            stack,
            transitionSpec = {
                // Deeper slides in from the right, back from the left, as the system Settings do.
                val deeper = targetState.size > initialState.size
                (slideInHorizontally { if (deeper) it / SLIDE else -it / SLIDE } + fadeIn()) togetherWith fadeOut()
            },
            contentKey = { it.size to it.last() },
            label = "settingsLevel",
        ) { levels ->
            val page = levels.last()
            val content = pages.of(page)
            // The current value on a level that picks one, else the row focused last here, else the first.
            val initial =
                remember {
                    content.rows.firstOrNull { it.control == MqTvControl.Radio && it.checked }?.key
                        ?: focused[page]?.takeIf { key -> content.rows.any { it.key == key } }
                        ?: content.rows.firstOrNull { it.enabled }?.key
                }
            Row(Modifier.fillMaxSize().padding(start = START, top = TOP)) {
                SettingsColumn(content.title) {
                    content.rows.forEach { entry ->
                        key(entry.key) {
                            val requester = remember { FocusRequester() }
                            val child = content.children[entry.key]
                            SettingRow(
                                entry,
                                Modifier.focusRequester(requester).onPreviewKeyEvent { event ->
                                    val down = event.type == KeyEventType.KeyDown
                                    when {
                                        event.key == Key.DirectionRight && child != null -> {
                                            if (down) open(child)
                                            true
                                        }

                                        event.key == Key.DirectionLeft && levels.size > 1 -> {
                                            if (down) back()
                                            true
                                        }

                                        else -> {
                                            false
                                        }
                                    }
                                },
                                onFocus = { focused = focused + (page to entry.key) },
                            )
                            if (entry.key == initial) {
                                LaunchedEffect(Unit) {
                                    withFrameNanos {}
                                    runCatching { requester.requestFocus() }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(PANE_GAP))
                content.children[focused[page]]?.let { child ->
                    val preview = pages.of(child)
                    // Only a look ahead: not read out, never focused.
                    SettingsColumn(preview.title, Modifier.alpha(PREVIEW_ALPHA).clearAndSetSemantics {}) {
                        preview.rows.forEach { PreviewRow(it) }
                    }
                }
            }
        }
    }
}

/** One level: its title, its rows and which rows open which level. */
private data class Level(
    val title: String,
    val rows: List<SettingEntry>,
    val children: Map<String, SettingsPage>,
)

/** Collects a level's rows; a [section] set before a row becomes that row's overline. */
private class LevelBuilder(
    private val open: (SettingsPage) -> Unit,
) {
    val rows = mutableListOf<SettingEntry>()
    val children = mutableMapOf<String, SettingsPage>()
    var section: String? = null

    fun row(entry: SettingEntry) {
        rows += entry.copy(section = section)
        section = null
    }

    fun opens(
        page: SettingsPage,
        title: String,
        summary: String? = null,
        icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    ) {
        children[page.name] = page
        row(SettingEntry(page.name, title, summary, icon, onClick = { open(page) }))
    }
}

/** Builds each level from the state; the same level shows as the current one or as the preview. */
private class Pages(
    val state: TvSettingsUiState.Content,
    val versionName: String,
    val onAction: (TvSettingsAction) -> Unit,
    val open: (SettingsPage) -> Unit,
    val back: () -> Unit,
    val onOpenLicenses: () -> Unit,
    val onOpenHomeRows: () -> Unit,
    val onOpenParental: () -> Unit,
    val onSwitchProfile: () -> Unit,
) {
    @Composable
    fun of(page: SettingsPage): Level {
        val b = LevelBuilder(open)
        val title =
            when (page) {
                SettingsPage.Root -> {
                    b.opens(SettingsPage.Sound, stringResource(R.string.tv_settings_sound), icon = MqTvIcons.Playing)
                    if (state.appMode?.video == true) {
                        b.opens(
                            SettingsPage.Video,
                            stringResource(R.string.tv_settings_video_subtitles),
                            icon = MqTvIcons.Subtitles,
                        )
                    }
                    b.opens(
                        SettingsPage.Display,
                        stringResource(R.string.tv_settings_display),
                        icon = MqTvIcons.Palette,
                    )
                    b.opens(SettingsPage.Home, stringResource(R.string.tv_settings_home), icon = MqTvIcons.Home)
                    b.opens(SettingsPage.Library, stringResource(R.string.tv_settings_library), icon = MqTvIcons.Sync)
                    b.opens(
                        SettingsPage.Account,
                        stringResource(R.string.tv_settings_account),
                        state.userName,
                        MqTvIcons.Person,
                    )
                    b.opens(SettingsPage.About, stringResource(R.string.tv_settings_about), versionName, MqTvIcons.Info)
                    stringResource(R.string.tv_settings_title)
                }

                SettingsPage.Sound -> {
                    b.section = stringResource(R.string.tv_settings_output)
                    val hdmi = state.hdmi
                    b.row(
                        SettingEntry(
                            "passthrough",
                            stringResource(R.string.tv_settings_passthrough),
                            // Nothing to switch yet: the player remembers the HDMI output on its first play there.
                            if (hdmi != null) {
                                stringResource(R.string.tv_settings_passthrough_detail, hdmi.name)
                            } else {
                                stringResource(R.string.tv_settings_passthrough_unknown)
                            },
                            control = MqTvControl.Switch.takeIf { hdmi != null },
                            checked = hdmi?.atmosPassthrough == true,
                            enabled = hdmi != null,
                            onClick = { onAction(TvSettingsAction.TogglePassthrough) },
                        ),
                    )
                    b.section = stringResource(R.string.tv_settings_streaming)
                    b.opens(
                        SettingsPage.Quality,
                        stringResource(R.string.tv_settings_quality),
                        qualityLabel(state.quality),
                    )
                    stringResource(R.string.tv_settings_sound)
                }

                SettingsPage.Video -> {
                    b.section = stringResource(R.string.tv_settings_video)
                    b.opens(
                        SettingsPage.DisplayMatch,
                        stringResource(R.string.tv_settings_display_match),
                        stringResource(state.displayMatch.label),
                    )
                    b.section = stringResource(R.string.tv_settings_subtitles)
                    val style = state.subtitleStyle
                    b.opens(
                        SettingsPage.SubtitleSize,
                        stringResource(R.string.tv_settings_subtitle_size),
                        stringResource(style.size.label),
                    )
                    b.opens(
                        SettingsPage.SubtitleBackground,
                        stringResource(R.string.tv_settings_subtitle_background),
                        stringResource(style.background.label),
                    )
                    b.opens(
                        SettingsPage.SubtitleColor,
                        stringResource(R.string.tv_settings_subtitle_color),
                        stringResource(style.color.label),
                    )
                    stringResource(R.string.tv_settings_video_subtitles)
                }

                SettingsPage.Display -> {
                    b.opens(
                        SettingsPage.MenuStyle,
                        stringResource(R.string.tv_settings_menu_style),
                        stringResource(state.menuStyle.label),
                    )
                    b.opens(
                        SettingsPage.TitleStyle,
                        stringResource(R.string.tv_settings_title_style),
                        stringResource(state.titleStyle.label),
                    )
                    // Only while pages show a logo (#578).
                    if (state.titleStyle != TvTitleStyle.Text) {
                        b.opens(
                            SettingsPage.LogoSize,
                            stringResource(R.string.tv_settings_logo_size),
                            stringResource(state.logoSize.label),
                        )
                    }
                    b.row(
                        SettingEntry(
                            "posterAccent",
                            stringResource(R.string.tv_settings_poster_accent),
                            stringResource(R.string.tv_settings_poster_accent_detail),
                            control = MqTvControl.Switch,
                            checked = state.posterAccent,
                            onClick = { onAction(TvSettingsAction.TogglePosterAccent) },
                        ),
                    )
                    b.row(
                        SettingEntry(
                            "transitions",
                            stringResource(R.string.tv_settings_simple_transitions),
                            stringResource(R.string.tv_settings_simple_transitions_detail),
                            control = MqTvControl.Switch,
                            checked = state.simpleTransitions,
                            onClick = { onAction(TvSettingsAction.ToggleSimpleTransitions) },
                        ),
                    )
                    stringResource(R.string.tv_settings_display)
                }

                SettingsPage.Home -> {
                    b.opens(
                        SettingsPage.Carousel,
                        stringResource(R.string.tv_settings_carousel),
                        stringResource(state.carousel.label),
                    )
                    if (state.carousel != TvCarousel.Off) {
                        b.opens(
                            SettingsPage.CarouselSources,
                            stringResource(R.string.tv_settings_carousel_source),
                            state.carouselPicked.map { stringResource(it.title) }.joinToString(),
                        )
                        b.row(
                            SettingEntry(
                                "carouselSkipWatched",
                                stringResource(R.string.tv_settings_carousel_skip_watched),
                                control = MqTvControl.Switch,
                                checked = state.carouselSkipWatched,
                                onClick = { onAction(TvSettingsAction.ToggleCarouselSkipWatched) },
                            ),
                        )
                    }
                    b.row(
                        SettingEntry(
                            "homeRows",
                            stringResource(R.string.tv_settings_home_rows),
                            stringResource(R.string.tv_settings_home_rows_detail),
                            onClick = onOpenHomeRows,
                        ),
                    )
                    stringResource(R.string.tv_settings_home)
                }

                SettingsPage.Library -> {
                    state.appMode?.let { mode ->
                        b.opens(
                            SettingsPage.AppMode,
                            stringResource(R.string.tv_settings_app_mode),
                            stringResource(mode.label),
                        )
                    }
                    b.row(
                        SettingEntry(
                            "sync",
                            stringResource(R.string.tv_settings_sync),
                            syncLabel(state.sync),
                            onClick = { onAction(TvSettingsAction.SyncNow) },
                        ),
                    )
                    stringResource(R.string.tv_settings_library)
                }

                SettingsPage.Account -> {
                    // "Who's watching?" (#433): switch to or add a profile.
                    b.row(
                        SettingEntry(
                            "account",
                            state.serverName.orEmpty(),
                            listOfNotNull(state.userName, stringResource(R.string.tv_settings_switch_profile))
                                .joinToString(" · "),
                            MqTvIcons.Person,
                            onClick = onSwitchProfile,
                        ),
                    )
                    if (state.appMode?.video == true) {
                        b.row(
                            SettingEntry(
                                "parental",
                                stringResource(R.string.tv_settings_parental),
                                stringResource(R.string.tv_settings_parental_detail),
                                onClick = onOpenParental,
                            ),
                        )
                    }
                    b.opens(SettingsPage.SignOut, stringResource(R.string.tv_settings_sign_out))
                    stringResource(R.string.tv_settings_account)
                }

                SettingsPage.About -> {
                    b.row(SettingEntry("version", stringResource(R.string.tv_settings_version), versionName))
                    b.row(
                        SettingEntry(
                            "licenses",
                            stringResource(R.string.tv_settings_licenses),
                            onClick = onOpenLicenses,
                        ),
                    )
                    stringResource(R.string.tv_settings_about)
                }

                SettingsPage.Quality -> {
                    StreamingQuality.entries.forEach {
                        b.pick(
                            it.name,
                            qualityLabel(it),
                            it == state.quality,
                        ) { onAction(TvSettingsAction.SetQuality(it)) }
                    }
                    stringResource(R.string.tv_settings_quality)
                }

                SettingsPage.DisplayMatch -> {
                    io.github.kentprodan.mediatheq.core.data.DisplayMatch.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.displayMatch) {
                            onAction(TvSettingsAction.SetDisplayMatch(it))
                        }
                    }
                    stringResource(R.string.tv_settings_display_match)
                }

                SettingsPage.SubtitleSize -> {
                    SubtitleStyle.Size.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.subtitleStyle.size) {
                            onAction(TvSettingsAction.SetSubtitleStyle(state.subtitleStyle.copy(size = it)))
                        }
                    }
                    stringResource(R.string.tv_settings_subtitle_size)
                }

                SettingsPage.SubtitleBackground -> {
                    SubtitleStyle.Background.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.subtitleStyle.background) {
                            onAction(TvSettingsAction.SetSubtitleStyle(state.subtitleStyle.copy(background = it)))
                        }
                    }
                    stringResource(R.string.tv_settings_subtitle_background)
                }

                SettingsPage.SubtitleColor -> {
                    SubtitleStyle.Color.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.subtitleStyle.color) {
                            onAction(TvSettingsAction.SetSubtitleStyle(state.subtitleStyle.copy(color = it)))
                        }
                    }
                    stringResource(R.string.tv_settings_subtitle_color)
                }

                SettingsPage.Carousel -> {
                    TvCarousel.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.carousel) {
                            onAction(TvSettingsAction.SetCarousel(it))
                        }
                    }
                    stringResource(R.string.tv_settings_carousel)
                }

                SettingsPage.CarouselSources -> {
                    // Several at once (#575): checkboxes, the level stays open while they change.
                    state.carouselSources.forEach { source ->
                        b.row(
                            SettingEntry(
                                source.name,
                                stringResource(source.title),
                                control = MqTvControl.Checkbox,
                                checked = source in state.carouselPicked,
                                onClick = { onAction(TvSettingsAction.ToggleCarouselSource(source)) },
                            ),
                        )
                    }
                    stringResource(R.string.tv_settings_carousel_source)
                }

                SettingsPage.MenuStyle -> {
                    io.github.kentprodan.mediatheq.core.data.TvMenuStyle.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.menuStyle) {
                            onAction(TvSettingsAction.SetMenuStyle(it))
                        }
                    }
                    stringResource(R.string.tv_settings_menu_style)
                }

                SettingsPage.TitleStyle -> {
                    TvTitleStyle.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.titleStyle) {
                            onAction(TvSettingsAction.SetTitleStyle(it))
                        }
                    }
                    stringResource(R.string.tv_settings_title_style)
                }

                SettingsPage.LogoSize -> {
                    TvLogoSize.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.logoSize) {
                            onAction(TvSettingsAction.SetLogoSize(it))
                        }
                    }
                    stringResource(R.string.tv_settings_logo_size)
                }

                SettingsPage.AppMode -> {
                    AppMode.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.appMode) {
                            onAction(TvSettingsAction.SetAppMode(it))
                        }
                    }
                    stringResource(R.string.tv_settings_app_mode)
                }

                SettingsPage.SignOut -> {
                    b.row(
                        SettingEntry(
                            "signOut",
                            stringResource(R.string.tv_settings_sign_out),
                            onClick = { onAction(TvSettingsAction.SignOut) },
                        ),
                    )
                    b.row(SettingEntry("cancel", stringResource(R.string.tv_settings_cancel), onClick = back))
                    stringResource(R.string.tv_settings_sign_out_question, state.serverName.orEmpty())
                }
            }
        return Level(title, b.rows.toList(), b.children.toMap())
    }

    /** A value of a level that picks one: a radio, and choosing it comes back to the level before. */
    private fun LevelBuilder.pick(
        key: String,
        title: String,
        checked: Boolean,
        set: () -> Unit,
    ) = row(
        SettingEntry(key, title, control = MqTvControl.Radio, checked = checked) {
            set()
            back()
        },
    )
}

// Between the rows and the preview of the next level.
private val PANE_GAP = 124.dp
private const val PREVIEW_ALPHA = 0.5f
private const val SLIDE = 4

private val SubtitleStyle.Size.label: Int
    get() =
        when (this) {
            SubtitleStyle.Size.System -> R.string.tv_settings_subtitle_system
            SubtitleStyle.Size.Small -> R.string.tv_settings_subtitle_small
            SubtitleStyle.Size.Normal -> R.string.tv_settings_subtitle_normal
            SubtitleStyle.Size.Large -> R.string.tv_settings_subtitle_large
        }

private val SubtitleStyle.Background.label: Int
    get() =
        when (this) {
            SubtitleStyle.Background.System -> R.string.tv_settings_subtitle_system
            SubtitleStyle.Background.None -> R.string.tv_settings_subtitle_none
            SubtitleStyle.Background.Shadow -> R.string.tv_settings_subtitle_shadow
            SubtitleStyle.Background.Box -> R.string.tv_settings_subtitle_box
        }

private val SubtitleStyle.Color.label: Int
    get() =
        when (this) {
            SubtitleStyle.Color.System -> R.string.tv_settings_subtitle_system
            SubtitleStyle.Color.White -> R.string.tv_settings_subtitle_white
            SubtitleStyle.Color.Yellow -> R.string.tv_settings_subtitle_yellow
        }

private val AppMode.label: Int
    get() =
        when (this) {
            AppMode.Music -> R.string.tv_settings_app_mode_music
            AppMode.Video -> R.string.tv_settings_app_mode_video
            AppMode.Both -> R.string.tv_settings_app_mode_both
        }

@Composable
private fun qualityLabel(quality: StreamingQuality): String =
    quality.bitrate?.let { stringResource(R.string.tv_settings_kbps, it / BITS_PER_KBIT) }
        ?: stringResource(R.string.tv_settings_original)

@Composable
private fun syncLabel(status: SyncStatus): String =
    when (status) {
        is SyncStatus.Idle -> {
            stringResource(R.string.tv_settings_sync_idle)
        }

        is SyncStatus.Running -> {
            stringResource(R.string.tv_settings_sync_running, stepLabel(status.step), status.fetched, status.total)
        }

        is SyncStatus.Failed -> {
            stringResource(
                if (status.error ==
                    MqError.Network
                ) {
                    R.string.tv_settings_sync_unreachable
                } else {
                    R.string.tv_settings_sync_failed
                },
            )
        }
    }

@Composable
private fun stepLabel(step: SyncStep): String =
    stringResource(
        when (step) {
            SyncStep.AlbumArtists -> R.string.tv_settings_step_artists
            SyncStep.Albums -> R.string.tv_settings_step_albums
            SyncStep.Tracks -> R.string.tv_settings_step_tracks
            SyncStep.Playlists, SyncStep.PlaylistEntries -> R.string.tv_settings_step_playlists
            SyncStep.Movies -> R.string.tv_settings_step_movies
            SyncStep.Series, SyncStep.Seasons -> R.string.tv_settings_step_series
            SyncStep.Episodes -> R.string.tv_settings_step_episodes
            SyncStep.Collections, SyncStep.CollectionItems -> R.string.tv_settings_step_collections
        },
    )

private const val BITS_PER_KBIT = 1000

private val io.github.kentprodan.mediatheq.core.data.DisplayMatch.label: Int
    get() =
        when (this) {
            io.github.kentprodan.mediatheq.core.data.DisplayMatch.Off -> {
                R.string.tv_settings_display_match_off
            }

            io.github.kentprodan.mediatheq.core.data.DisplayMatch.Rate -> {
                R.string.tv_settings_display_match_rate
            }

            io.github.kentprodan.mediatheq.core.data.DisplayMatch.RateAndResolution -> {
                R.string.tv_settings_display_match_resolution
            }
        }

private val TvLogoSize.label: Int
    get() =
        when (this) {
            TvLogoSize.Small -> R.string.tv_settings_logo_small
            TvLogoSize.Medium -> R.string.tv_settings_logo_medium
            TvLogoSize.Large -> R.string.tv_settings_logo_large
        }

private val TvTitleStyle.label: Int
    get() =
        when (this) {
            TvTitleStyle.Logo -> R.string.tv_settings_title_logo
            TvTitleStyle.LogoAndText -> R.string.tv_settings_title_logo_text
            TvTitleStyle.Text -> R.string.tv_settings_title_text
        }

private val io.github.kentprodan.mediatheq.core.data.TvMenuStyle.label: Int
    get() =
        when (this) {
            io.github.kentprodan.mediatheq.core.data.TvMenuStyle.Modal -> R.string.tv_settings_menu_style_modal
            io.github.kentprodan.mediatheq.core.data.TvMenuStyle.Standard -> R.string.tv_settings_menu_style_standard
        }

private val TvCarousel.label: Int
    get() =
        when (this) {
            TvCarousel.Off -> R.string.tv_settings_carousel_off
            TvCarousel.Card -> R.string.tv_settings_carousel_card
            TvCarousel.Immersive -> R.string.tv_settings_carousel_immersive
        }
