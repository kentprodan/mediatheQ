package io.github.kentprodan.mediatheq.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.PortraitNavStyle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqMiniPlayer
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.preview.rememberPreviewCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.navigation.HomeKey

@Composable
private fun Shell(
    tab: TopLevelTab,
    navigation: NavigationPreferences = NavigationPreferences(),
    homeStack: List<NavKey> = listOf(HomeKey),
    miniPlayer: (@Composable () -> Unit)? = null,
) {
    // Layoutlib has no Activity, so provide the back-event dispatcher NavDisplay expects.
    val dispatcherOwner =
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
        MediatheqTheme {
            val stacks =
                TopLevelTab.entries.associateWith {
                    if (it == TopLevelTab.Home) homeStack.toMutableList() else mutableListOf(it.rootKey)
                }
            val tabs = navigation.entries.map(TopLevelTab::of)
            val navigator = TabNavigator(stacks, mutableStateOf(tab)) { tabs }
            AppShell(navigator, placeholderEntries(navigator), miniPlayer = miniPlayer, navigation = navigation)
        }
    }
}

@PreviewTest
@MqPreviews
@Composable
fun ShellHomeScreenshot() = Shell(TopLevelTab.Home)

@PreviewTest
@MqPreviews
@Composable
fun ShellLibraryScreenshot() = Shell(TopLevelTab.Library)

@PreviewTest
@MqPreviews
@Composable
fun ShellSearchScreenshot() = Shell(TopLevelTab.Search)

@PreviewTest
@MqPreviews
@Composable
fun ShellDownloadsScreenshot() = Shell(TopLevelTab.Downloads)

/** The mini player slot in use, with the real pill as the player host places it. */
@PreviewTest
@MqPreviews
@Composable
fun ShellWithMiniPlayerScreenshot() = Shell(TopLevelTab.Library, miniPlayer = { MiniPlayer() })

@Composable
private fun MiniPlayer() =
    MqMiniPlayer(
        title = "Strobe",
        artist = "deadmau5",
        cover = rememberPreviewCover(),
        isPlaying = true,
        hasNext = true,
        onPlayPause = {},
        onNext = {},
        onOpen = {},
        modifier =
            Modifier.padding(
                start = MediatheqSpacing.l,
                end = MediatheqSpacing.l,
                bottom = MediatheqSpacing.s,
            ),
    )

private val fourEntries = NavigationPreferences.DEFAULT_ENTRIES
private val sixEntries =
    listOf(
        MainNavEntry.Home,
        MainNavEntry.Library,
        MainNavEntry.Albums,
        MainNavEntry.Tracks,
        MainNavEntry.Downloads,
        MainNavEntry.Search,
    )

@PreviewTest
@MqPreviews
@Composable
fun ShellBottomBarSixEntriesScreenshot() =
    Shell(TopLevelTab.Albums, NavigationPreferences(sixEntries), miniPlayer = { MiniPlayer() })

@PreviewTest
@MqPreviews
@Composable
fun ShellFloatingToolbarScreenshot() =
    Shell(
        TopLevelTab.Library,
        NavigationPreferences(fourEntries, portraitStyle = PortraitNavStyle.FloatingToolbar),
        miniPlayer = { MiniPlayer() },
    )

@PreviewTest
@MqPreviews
@Composable
fun ShellFloatingToolbarSixEntriesScreenshot() =
    Shell(TopLevelTab.Tracks, NavigationPreferences(sixEntries, portraitStyle = PortraitNavStyle.FloatingToolbar))

@PreviewTest
@MqPreviews
@Composable
fun ShellFloatingToolbarWithFabScreenshot() =
    Shell(
        TopLevelTab.Home,
        NavigationPreferences(fourEntries, portraitStyle = PortraitNavStyle.FloatingToolbarWithFab),
        miniPlayer = { MiniPlayer() },
    )

@PreviewTest
@MqPreviews
@Composable
fun ShellFloatingToolbarWithFabSixEntriesScreenshot() =
    Shell(
        TopLevelTab.Search,
        NavigationPreferences(sixEntries, portraitStyle = PortraitNavStyle.FloatingToolbarWithFab),
    )

/** Half see-through: the showcase scrolls under the bar and the mini player. */
@PreviewTest
@MqPreviews
@Composable
fun ShellTransparentBarScreenshot() =
    Shell(
        TopLevelTab.Home,
        NavigationPreferences(transparencyPercent = 50),
        homeStack = listOf(HomeKey, ThemeShowcaseKey),
        miniPlayer = { MiniPlayer() },
    )

@PreviewTest
@LandscapePreviews
@Composable
fun ShellRailScreenshot() = Shell(TopLevelTab.Library, miniPlayer = { MiniPlayer() })

@PreviewTest
@LandscapePreviews
@Composable
fun ShellRailSixEntriesScreenshot() = Shell(TopLevelTab.Tracks, NavigationPreferences(sixEntries))

/** Pixel 9 turned sideways, normal and large font. */
@Preview(
    name = "Landscape",
    device = "spec:width=924dp,height=412dp,dpi=420,orientation=landscape",
    showBackground = true,
)
@Preview(
    name = "Landscape 2x",
    device = "spec:width=924dp,height=412dp,dpi=420,orientation=landscape",
    showBackground = true,
    fontScale = 2f,
)
internal annotation class LandscapePreviews
