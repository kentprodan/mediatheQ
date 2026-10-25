package io.github.kentprodan.mediatheq.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.component.MqMiniPlayer
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.preview.rememberPreviewCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

@Composable
private fun Shell(
    tab: TopLevelTab,
    offline: Boolean = false,
    miniPlayer: (@Composable () -> Unit)? = null,
) {
    // Layoutlib has no Activity, so provide the back-event dispatcher NavDisplay expects.
    val dispatcherOwner =
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
        MediatheqTheme {
            val navigator =
                TabNavigator(TopLevelTab.entries.associateWith { mutableListOf(it.rootKey) }, mutableStateOf(tab))
            AppShell(navigator, placeholderEntries(navigator), offline = offline, miniPlayer = miniPlayer)
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

/** Offline, a tab other than Downloads (all of them look the same). */
@PreviewTest
@MqPreviews
@Composable
fun ShellOfflineScreenshot() = Shell(TopLevelTab.Library, offline = true)

/** The mini player slot in use, with the real pill as the player host places it. */
@PreviewTest
@MqPreviews
@Composable
fun ShellWithMiniPlayerScreenshot() =
    Shell(TopLevelTab.Library) {
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
    }
