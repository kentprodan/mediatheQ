package io.github.kentprodan.mediatheq.feature.tv.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.CARDS_PER_SCREEN
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.data.TvCardStyle
import io.github.kentprodan.mediatheq.core.data.TvCarousel
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvControl
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.HomeKind
import io.github.kentprodan.mediatheq.core.model.SubtitleStyle
import io.github.kentprodan.mediatheq.core.model.TvLogoSize
import io.github.kentprodan.mediatheq.core.model.TvTitlePage
import io.github.kentprodan.mediatheq.core.model.TvTitleStyle
import io.github.kentprodan.mediatheq.core.ui.tv.title

@Composable
fun TvSettingsRoute(
    versionName: String,
    onOpenLicenses: () -> Unit,
    onOpenParental: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    viewModel: TvSettingsViewModel = hiltViewModel(),
    homeRows: TvHomeRowsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rows by homeRows.state.collectAsStateWithLifecycle()
    TvSettingsScreen(
        state,
        versionName,
        viewModel::onAction,
        onOpenLicenses,
        onOpenParental,
        onSwitchProfile,
        rows,
        homeRows::onAction,
    )
}

/** The levels of Settings (#580): the categories, each category's page, and the pages that pick one value. */
internal enum class SettingsPage {
    Root,
    Account,
    Sound,
    Video,
    Display,
    Screens,
    Library,
    About,
    Quality,
    DisplayMatch,
    SubtitleSize,
    SubtitleBackground,
    SubtitleColor,
    MenuStyle,
    MenuBehavior,
    ScreenHome,
    ScreenMovies,
    ScreenSeries,
    ScreenMusic,
    Carousel,
    HomeContent,
    CarouselDesign,
    CarouselSources,
    HomeRows,
    RowOrder,
    Row,
    RowDesign,
    RowSize,
    Genres,
    TitlePages,
    TitleStyle,
    LogoSize,
    AppMode,
    SignOut,
}

/** A level on the stack: a page, with the row or detail page it is about for the levels of one (#621). */
internal data class SettingsLevel(
    val page: SettingsPage,
    val arg: String? = null,
) {
    /** Saved on the stack, and the key of the row that opens it. */
    val key: String get() = arg?.let { "${page.name}$ARG$it" } ?: page.name

    companion object {
        fun of(key: String) =
            SettingsLevel(SettingsPage.valueOf(key.substringBefore(ARG)), key.substringAfter(ARG, "").ifEmpty { null })

        private const val ARG = '|'
    }
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
    onOpenParental: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    homeRows: TvHomeRowsState? = null,
    onHomeRowsAction: (TvHomeRowsAction) -> Unit = {},
) = TvSettingsScreen(
    state,
    versionName,
    onAction,
    onOpenLicenses,
    onOpenParental,
    onSwitchProfile,
    homeRows,
    onHomeRowsAction,
    start = listOf(SettingsLevel(SettingsPage.Root)),
)

/** Settings opened at the levels [start] (the screenshots of the deeper levels). */
@Composable
internal fun TvSettingsScreen(
    state: TvSettingsUiState,
    versionName: String,
    onAction: (TvSettingsAction) -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenParental: () -> Unit,
    onSwitchProfile: () -> Unit,
    homeRows: TvHomeRowsState?,
    onHomeRowsAction: (TvHomeRowsAction) -> Unit,
    start: List<SettingsLevel>,
) {
    Surface(Modifier.fillMaxSize()) {
        if (state !is TvSettingsUiState.Content) return@Surface
        // Kept with the screen's saved state, so coming back from the licenses finds the same level and row.
        var stack by rememberSaveable { mutableStateOf(start.map { it.key }) }
        var focused by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
        // The Home row picked up to move (#621).
        var lifted by rememberSaveable { mutableStateOf<String?>(null) }
        val open: (SettingsLevel) -> Unit = { child ->
            // Back lands on the row that opened it, however it was opened.
            focused = focused + (stack.last() to child.key)
            stack = stack + child.key
        }
        val back: () -> Unit = {
            lifted = null
            if (stack.size > 1) stack = stack.dropLast(1)
        }
        BackHandler(enabled = stack.size > 1, onBack = back)
        val context = LocalContext.current
        val pages =
            Pages(
                state,
                versionName,
                onAction,
                open,
                back,
                onOpenLicenses,
                onOpenParental,
                onSwitchProfile,
                onOpenCaptions = { openCaptionSettings(context) },
                homeRows,
                onHomeRowsAction,
                lifted,
            ) { lifted = it }
        AnimatedContent(
            stack,
            transitionSpec = {
                // Deeper slides in from the right, back from the left, as the system Settings do.
                val deeper = targetState.size > initialState.size
                (slideInHorizontally { if (deeper) it / SLIDE else -it / SLIDE } + fadeIn()) togetherWith fadeOut()
            },
            contentKey = { it.size to it.last() },
            label = "settingsLevel",
        ) { keys ->
            val page = keys.last()
            val content = pages.of(SettingsLevel.of(page))
            // The current value on a level that picks one, else the row focused last here, else the first.
            val initial =
                remember {
                    content.rows.firstOrNull { it.control == MqTvControl.Radio && it.checked }?.key
                        ?: focused[page]?.takeIf { key -> content.rows.any { it.key == key } }
                        ?: content.rows.firstOrNull { it.enabled }?.key
                }
            val panel = MaterialTheme.colorScheme.surfaceVariant.takeIf { content.children.isEmpty() }
            Row(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val x = (START + PANE_WIDTH + LEAF_PANEL_GAP).toPx()
                        panel?.let { drawRect(it, Offset(x, 0f), Size(size.width - x, size.height)) }
                    }.padding(start = START, top = TOP),
            ) {
                SettingsColumn(content.title, summary = content.summary) {
                    content.rows.forEach { entry ->
                        key(entry.key) {
                            val requester = remember { FocusRequester() }
                            val child = content.children[entry.key]
                            SettingRow(
                                entry,
                                Modifier.focusRequester(requester).onPreviewKeyEvent { event ->
                                    val down = event.type == KeyEventType.KeyDown
                                    val move = entry.onMove
                                    val vertical = event.key == Key.DirectionUp || event.key == Key.DirectionDown
                                    when {
                                        // A row picked up moves instead of the focus (#621).
                                        move != null && vertical -> {
                                            if (down) move(event.key == Key.DirectionUp)
                                            true
                                        }

                                        event.key == Key.DirectionRight && child != null -> {
                                            if (down) open(child)
                                            true
                                        }

                                        event.key == Key.DirectionLeft && keys.size > 1 -> {
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
    val children: Map<String, SettingsLevel>,
    val summary: String? = null,
)

/** Collects a level's rows; a [section] set before a row becomes that row's overline. */
private class LevelBuilder(
    private val open: (SettingsLevel) -> Unit,
) {
    val rows = mutableListOf<SettingEntry>()
    val children = mutableMapOf<String, SettingsLevel>()
    var section: String? = null

    /** The level's own line under its title. */
    var summary: String? = null

    fun row(entry: SettingEntry) {
        rows += entry.copy(section = section)
        section = null
    }

    fun opens(
        page: SettingsPage,
        title: String,
        summary: String? = null,
        icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    ) = opens(SettingsLevel(page), title, summary, icon)

    fun opens(
        level: SettingsLevel,
        title: String,
        summary: String? = null,
        icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    ) {
        children[level.key] = level
        row(SettingEntry(level.key, title, summary, icon, onClick = { open(level) }))
    }
}

/** Builds each level from the state; the same level shows as the current one or as the preview. */
private class Pages(
    val state: TvSettingsUiState.Content,
    val versionName: String,
    val onAction: (TvSettingsAction) -> Unit,
    val open: (SettingsLevel) -> Unit,
    val back: () -> Unit,
    val onOpenLicenses: () -> Unit,
    val onOpenParental: () -> Unit,
    val onSwitchProfile: () -> Unit,
    val onOpenCaptions: () -> Unit,
    val homeRows: TvHomeRowsState?,
    val onHomeRowsAction: (TvHomeRowsAction) -> Unit,
    val lifted: String?,
    val lift: (String?) -> Unit,
) {
    @Composable
    fun of(level: SettingsLevel): Level {
        val page = level.page
        val arg = level.arg
        val b = LevelBuilder(open)
        val rows = homeRows?.rows.orEmpty()
        val video = state.appMode?.video == true
        val music = state.appMode?.music != false
        val title =
            when (page) {
                SettingsPage.Root -> {
                    b.opens(
                        SettingsPage.Account,
                        stringResource(R.string.tv_settings_account),
                        state.userName,
                        MqTvIcons.Person,
                    )
                    b.opens(SettingsPage.Sound, stringResource(R.string.tv_settings_sound), icon = MqTvIcons.Playing)
                    if (video) {
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
                    b.opens(SettingsPage.Screens, stringResource(R.string.tv_settings_screens), icon = MqTvIcons.Home)
                    b.opens(SettingsPage.Library, stringResource(R.string.tv_settings_library), icon = MqTvIcons.Sync)
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
                    b.row(
                        SettingEntry(
                            "playbackDetails",
                            stringResource(R.string.tv_settings_playback_details),
                            stringResource(R.string.tv_settings_playback_details_detail),
                            control = MqTvControl.Switch,
                            checked = state.playbackDetails,
                            onClick = { onAction(TvSettingsAction.TogglePlaybackDetails) },
                        ),
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
                    // Only the mode on this level; what each one does is on the levels inside.
                    b.opens(
                        SettingsPage.MenuStyle,
                        stringResource(R.string.tv_settings_menu_style),
                        stringResource(state.menuStyle.label),
                    )
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

                SettingsPage.MenuStyle -> {
                    // The menu's shape (#619): floating is the only one so far, its behavior is on the level inside.
                    b.opens(
                        SettingsPage.MenuBehavior,
                        stringResource(R.string.tv_settings_menu_floating),
                        stringResource(R.string.tv_settings_menu_floating_detail),
                    )
                    stringResource(R.string.tv_settings_menu_style)
                }

                SettingsPage.MenuBehavior -> {
                    io.github.kentprodan.mediatheq.core.data.TvMenuStyle.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.menuStyle, stringResource(it.detail)) {
                            onAction(TvSettingsAction.SetMenuStyle(it))
                        }
                    }
                    stringResource(R.string.tv_settings_menu_floating)
                }

                SettingsPage.Screens -> {
                    b.opens(
                        SettingsPage.ScreenHome,
                        stringResource(R.string.tv_settings_home),
                        stringResource(R.string.tv_settings_screens_home),
                    )
                    if (video) {
                        b.opens(
                            SettingsPage.ScreenMovies,
                            stringResource(R.string.tv_settings_movies),
                            stringResource(R.string.tv_settings_movies_detail),
                        )
                        b.opens(
                            SettingsPage.ScreenSeries,
                            stringResource(R.string.tv_settings_series),
                            stringResource(R.string.tv_settings_series_detail),
                        )
                    }
                    if (music) {
                        b.opens(
                            SettingsPage.ScreenMusic,
                            stringResource(R.string.tv_settings_music),
                            stringResource(R.string.tv_settings_music_detail),
                        )
                    }
                    stringResource(R.string.tv_settings_screens)
                }

                SettingsPage.ScreenHome -> {
                    // Home without music (#621) once the TV has both.
                    if (state.appMode == AppMode.Both) {
                        b.opens(
                            SettingsPage.HomeContent,
                            stringResource(R.string.tv_settings_home_content),
                            stringResource(
                                if (state.homeMusic) {
                                    R.string.tv_settings_home_content_all
                                } else {
                                    R.string.tv_settings_home_content_video
                                },
                            ),
                        )
                    }
                    b.opens(
                        SettingsPage.Carousel,
                        stringResource(R.string.tv_settings_carousel),
                        stringResource(state.carousel.label),
                    )
                    b.opens(
                        SettingsPage.HomeRows,
                        stringResource(R.string.tv_settings_home_rows),
                        stringResource(R.string.tv_settings_home_rows_detail),
                    )
                    stringResource(R.string.tv_settings_home)
                }

                SettingsPage.HomeContent -> {
                    b.pick(
                        "video",
                        stringResource(R.string.tv_settings_home_content_video),
                        !state.homeMusic,
                        stringResource(R.string.tv_settings_home_content_video_detail),
                    ) { onAction(TvSettingsAction.SetHomeMusic(false)) }
                    b.pick(
                        "all",
                        stringResource(R.string.tv_settings_home_content_all),
                        state.homeMusic,
                        stringResource(R.string.tv_settings_home_content_all_detail),
                    ) { onAction(TvSettingsAction.SetHomeMusic(true)) }
                    stringResource(R.string.tv_settings_home_content)
                }

                SettingsPage.HomeRows -> {
                    b.opens(
                        SettingsPage.RowOrder,
                        stringResource(R.string.tv_settings_reorder),
                        stringResource(R.string.tv_settings_reorder_detail),
                        MqTvIcons.DragHandle,
                    )
                    rows.forEach { row ->
                        b.opens(SettingsLevel(SettingsPage.Row, row.id), rowTitle(row), rowSummary(row))
                    }
                    homeRows?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                        b.opens(
                            SettingsPage.Genres,
                            stringResource(R.string.tv_settings_genre_rows),
                            stringResource(R.string.tv_settings_genre_rows_on, genres.count { it.second }),
                        )
                    }
                    stringResource(R.string.tv_settings_home_rows)
                }

                SettingsPage.RowOrder -> {
                    // Select picks a row up, Up and Down move it, Select puts it down (#621).
                    b.summary = stringResource(R.string.tv_settings_reorder_hint)
                    rows.forEach { row ->
                        val up = lifted == row.id
                        b.row(
                            SettingEntry(
                                row.id,
                                rowTitle(row),
                                when {
                                    up -> stringResource(R.string.tv_settings_row_moving)
                                    !row.shown -> stringResource(R.string.tv_settings_row_hidden)
                                    else -> null
                                },
                                lifted = up,
                                onMove =
                                    { toUp: Boolean -> onHomeRowsAction(TvHomeRowsAction.Move(row.id, toUp)) }
                                        .takeIf { up },
                            ) { lift(if (up) null else row.id) },
                        )
                    }
                    stringResource(R.string.tv_settings_reorder)
                }

                SettingsPage.Row -> {
                    val row = rows.firstOrNull { it.id == arg }
                    if (row != null) {
                        b.row(
                            SettingEntry(
                                "show",
                                stringResource(R.string.tv_settings_row_show),
                                control = MqTvControl.Switch,
                                checked = row.shown,
                            ) {
                                onHomeRowsAction(TvHomeRowsAction.Toggle(row.id))
                                // A genre turned off leaves the rows: back to them.
                                if (row.genre != null) back()
                            },
                        )
                        // Immersive (#521) for rows with pictures; the mixes have none.
                        row.section?.takeIf { it.kind != HomeKind.Shortcuts }?.let { section ->
                            b.row(
                                SettingEntry(
                                    "immersive",
                                    stringResource(R.string.tv_settings_immersive_row),
                                    control = MqTvControl.Switch,
                                    checked = row.immersive,
                                ) { onHomeRowsAction(TvHomeRowsAction.ToggleImmersive(section)) },
                            )
                        }
                        b.opens(
                            SettingsLevel(SettingsPage.RowDesign, row.id),
                            stringResource(R.string.tv_settings_carousel_design),
                            stringResource(row.cardStyle.label),
                        )
                        b.opens(
                            SettingsLevel(SettingsPage.RowSize, row.id),
                            stringResource(R.string.tv_settings_row_size),
                            perScreenLabel(row.perScreen),
                        )
                    }
                    row?.let { rowTitle(it) }.orEmpty()
                }

                SettingsPage.RowDesign -> {
                    val row = rows.firstOrNull { it.id == arg }
                    (listOf(null) + TvCardStyle.entries).forEach { style ->
                        b.pick(
                            style?.name ?: "default",
                            stringResource(style.label),
                            row != null && row.cardStyle == style,
                            stringResource(style.detail),
                        ) { arg?.let { onHomeRowsAction(TvHomeRowsAction.SetCardStyle(it, style)) } }
                    }
                    stringResource(R.string.tv_settings_carousel_design)
                }

                SettingsPage.RowSize -> {
                    val row = rows.firstOrNull { it.id == arg }
                    (listOf(null) + CARDS_PER_SCREEN.reversed()).forEach { count ->
                        b.pick(
                            count?.toString() ?: "default",
                            perScreenLabel(count),
                            row != null && row.perScreen == count,
                            stringResource(R.string.tv_settings_size_default_detail).takeIf { count == null },
                        ) { arg?.let { onHomeRowsAction(TvHomeRowsAction.SetPerScreen(it, count)) } }
                    }
                    stringResource(R.string.tv_settings_row_size)
                }

                SettingsPage.Genres -> {
                    // A genre turned on joins Home rows, last (#621).
                    b.summary = stringResource(R.string.tv_settings_genre_rows_detail)
                    homeRows?.genres.orEmpty().forEach { (genre, on) ->
                        b.row(
                            SettingEntry(
                                "genre:$genre",
                                genre,
                                control = MqTvControl.Switch,
                                checked = on,
                            ) { onHomeRowsAction(TvHomeRowsAction.ToggleGenre(genre)) },
                        )
                    }
                    stringResource(R.string.tv_settings_genre_rows)
                }

                SettingsPage.Carousel -> {
                    b.opens(
                        SettingsPage.CarouselDesign,
                        stringResource(R.string.tv_settings_carousel_design),
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
                    stringResource(R.string.tv_settings_carousel)
                }

                SettingsPage.CarouselDesign -> {
                    TvCarousel.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.carousel, stringResource(it.detail)) {
                            onAction(TvSettingsAction.SetCarousel(it))
                        }
                    }
                    stringResource(R.string.tv_settings_carousel_design)
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

                SettingsPage.ScreenMovies, SettingsPage.ScreenSeries, SettingsPage.ScreenMusic -> {
                    // The detail page's own level (#621), where its later settings go too.
                    val target = page.titlePage
                    b.opens(
                        SettingsLevel(SettingsPage.TitlePages, target.name),
                        stringResource(target.pagesTitle),
                        stringResource((state.titleStyles[target] ?: TvTitleStyle.Logo).label),
                    )
                    stringResource(page.screenTitle)
                }

                SettingsPage.TitlePages -> {
                    val target = TvTitlePage.valueOf(arg ?: TvTitlePage.Movie.name)
                    val style = state.titleStyles[target] ?: TvTitleStyle.Logo
                    b.opens(
                        SettingsLevel(SettingsPage.TitleStyle, target.name),
                        stringResource(R.string.tv_settings_title_style),
                        stringResource(style.label),
                    )
                    // Only while the page shows a logo (#578).
                    if (style != TvTitleStyle.Text) {
                        b.opens(
                            SettingsLevel(SettingsPage.LogoSize, target.name),
                            stringResource(R.string.tv_settings_logo_size),
                            stringResource((state.logoSizes[target] ?: TvLogoSize.Medium).label),
                        )
                    }
                    stringResource(target.pagesTitle)
                }

                SettingsPage.TitleStyle -> {
                    val target = TvTitlePage.valueOf(arg ?: TvTitlePage.Movie.name)
                    TvTitleStyle.entries.forEach {
                        val checked = it == (state.titleStyles[target] ?: TvTitleStyle.Logo)
                        b.pick(it.name, stringResource(it.label), checked, stringResource(it.detail)) {
                            onAction(TvSettingsAction.SetTitleStyle(target, it))
                        }
                    }
                    stringResource(R.string.tv_settings_title_style)
                }

                SettingsPage.LogoSize -> {
                    val target = TvTitlePage.valueOf(arg ?: TvTitlePage.Movie.name)
                    TvLogoSize.entries.forEach {
                        b.pick(
                            it.name,
                            stringResource(it.label),
                            it == (state.logoSizes[target] ?: TvLogoSize.Medium),
                        ) {
                            onAction(TvSettingsAction.SetLogoSize(target, it))
                        }
                    }
                    stringResource(R.string.tv_settings_logo_size)
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
                    if (video) {
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
                    captions(b, state.subtitleStyle.size == SubtitleStyle.Size.System)
                    stringResource(R.string.tv_settings_subtitle_size)
                }

                SettingsPage.SubtitleBackground -> {
                    SubtitleStyle.Background.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.subtitleStyle.background) {
                            onAction(TvSettingsAction.SetSubtitleStyle(state.subtitleStyle.copy(background = it)))
                        }
                    }
                    captions(b, state.subtitleStyle.background == SubtitleStyle.Background.System)
                    stringResource(R.string.tv_settings_subtitle_background)
                }

                SettingsPage.SubtitleColor -> {
                    SubtitleStyle.Color.entries.forEach {
                        b.pick(it.name, stringResource(it.label), it == state.subtitleStyle.color) {
                            onAction(TvSettingsAction.SetSubtitleStyle(state.subtitleStyle.copy(color = it)))
                        }
                    }
                    captions(b, state.subtitleStyle.color == SubtitleStyle.Color.System)
                    stringResource(R.string.tv_settings_subtitle_color)
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
        return Level(title, b.rows.toList(), b.children.toMap(), b.summary)
    }

    /** A value of a level that picks one: a radio, and choosing it comes back to the level before. */
    private fun LevelBuilder.pick(
        key: String,
        title: String,
        checked: Boolean,
        summary: String? = null,
        set: () -> Unit,
    ) = row(
        SettingEntry(key, title, summary, control = MqTvControl.Radio, checked = checked) {
            set()
            back()
        },
    )

    /** While a subtitle part follows the TV (#619): the way to the system caption settings it follows. */
    @Composable
    private fun captions(
        b: LevelBuilder,
        system: Boolean,
    ) {
        if (!system) return
        b.row(
            SettingEntry(
                "captions",
                stringResource(R.string.tv_settings_caption_settings),
                stringResource(R.string.tv_settings_caption_settings_detail),
                onClick = onOpenCaptions,
            ),
        )
    }
}

// Between the rows and the preview of the next level.
private val PANE_GAP = 124.dp

// The last level's panel starts this far right of its rows.
private val LEAF_PANEL_GAP = 56.dp
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

private val TvCarousel.detail: Int
    get() =
        when (this) {
            TvCarousel.Off -> R.string.tv_settings_carousel_off_detail
            TvCarousel.Card -> R.string.tv_settings_carousel_card_detail
            TvCarousel.Immersive -> R.string.tv_settings_carousel_immersive_detail
        }

private val io.github.kentprodan.mediatheq.core.data.TvMenuStyle.detail: Int
    get() =
        when (this) {
            io.github.kentprodan.mediatheq.core.data.TvMenuStyle.Modal -> {
                R.string.tv_settings_menu_style_modal_detail
            }

            io.github.kentprodan.mediatheq.core.data.TvMenuStyle.Standard -> {
                R.string.tv_settings_menu_style_standard_detail
            }
        }

private val TvTitleStyle.detail: Int
    get() =
        when (this) {
            TvTitleStyle.Logo -> R.string.tv_settings_title_logo_detail
            TvTitleStyle.LogoAndText -> R.string.tv_settings_title_logo_text_detail
            TvTitleStyle.Text -> R.string.tv_settings_title_text_detail
        }

/** The detail page a screen's level sets (#619). */
private val SettingsPage.titlePage: TvTitlePage
    get() =
        when (this) {
            SettingsPage.ScreenSeries -> TvTitlePage.Series
            SettingsPage.ScreenMusic -> TvTitlePage.Artist
            else -> TvTitlePage.Movie
        }

/** The level of a detail page's settings (#621). */
private val TvTitlePage.pagesTitle: Int
    get() =
        when (this) {
            TvTitlePage.Movie -> R.string.tv_settings_movie_pages
            TvTitlePage.Series -> R.string.tv_settings_series_pages
            TvTitlePage.Artist -> R.string.tv_settings_artist_pages
        }

/** A Home row's name (#621): its section's, or its genre. */
@Composable
private fun rowTitle(row: TvHomeRow): String = row.section?.let { stringResource(it.title) } ?: row.genre.orEmpty()

/** What a Home row's level holds (#621): shown or hidden, then what differs from its defaults. */
@Composable
private fun rowSummary(row: TvHomeRow): String =
    listOfNotNull(
        stringResource(if (row.shown) R.string.tv_settings_row_shown else R.string.tv_settings_row_hidden),
        row.cardStyle?.let { stringResource(it.label) },
        row.perScreen?.let { stringResource(R.string.tv_settings_per_screen, it) },
        stringResource(R.string.tv_settings_immersive_row).takeIf { row.immersive },
    ).joinToString(" · ")

@Composable
private fun perScreenLabel(count: Int?): String =
    count?.let { stringResource(R.string.tv_settings_per_screen, it) }
        ?: stringResource(R.string.tv_settings_size_default)

private val TvCardStyle?.label: Int
    get() =
        when (this) {
            null -> R.string.tv_settings_design_default
            TvCardStyle.Standard -> R.string.tv_settings_design_standard
            TvCardStyle.Classic -> R.string.tv_settings_design_classic
            TvCardStyle.Compact -> R.string.tv_settings_design_compact
            TvCardStyle.WideStandard -> R.string.tv_settings_design_wide_standard
            TvCardStyle.WideClassic -> R.string.tv_settings_design_wide_classic
        }

private val TvCardStyle?.detail: Int
    get() =
        when (this) {
            null -> R.string.tv_settings_design_default_detail
            TvCardStyle.Standard -> R.string.tv_settings_design_standard_detail
            TvCardStyle.Classic -> R.string.tv_settings_design_classic_detail
            TvCardStyle.Compact -> R.string.tv_settings_design_compact_detail
            TvCardStyle.WideStandard -> R.string.tv_settings_design_wide_standard_detail
            TvCardStyle.WideClassic -> R.string.tv_settings_design_wide_classic_detail
        }

private val SettingsPage.screenTitle: Int
    get() =
        when (this) {
            SettingsPage.ScreenSeries -> R.string.tv_settings_series
            SettingsPage.ScreenMusic -> R.string.tv_settings_music
            else -> R.string.tv_settings_movies
        }

/** The system caption settings (#619), or the system Settings where a TV has no such page. */
private fun openCaptionSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS)) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
}
