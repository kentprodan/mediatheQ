package io.github.kentprodan.mediatheq.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

@Composable
private fun Shell(
    tab: TopLevelTab,
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
            AppShell(navigator, placeholderEntries(navigator), miniPlayer = miniPlayer)
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

/** The mini player slot in use: a stand-in pill until #13 builds the real one. */
@PreviewTest
@MqPreviews
@Composable
fun ShellWithMiniPlayerScreenshot() =
    Shell(TopLevelTab.Library) {
        Surface(
            Modifier.fillMaxWidth().height(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) { Text("Mini player") }
        }
    }
