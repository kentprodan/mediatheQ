package io.github.kentprodan.mediatheq.feature.library

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.LibraryHubSpacing
import io.github.kentprodan.mediatheq.core.data.LibraryHubStyle
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut

/** As the hub shows [page] with no videos synced: the video tiles hidden (#270). */
@Composable
private fun Hub(page: LibraryPagePreferences) =
    MediatheqTheme {
        LibraryHubScreen(LibraryHubUiState.Content(page.copy(shortcuts = page.shortcuts - VIDEO)), {})
    }

private val VIDEO = setOf(LibraryShortcut.Movies, LibraryShortcut.Series)
private val VIDEO_COUNTS = mapOf(LibraryShortcut.Movies to 189, LibraryShortcut.Series to 23)

/** With movies and series synced (#270): their tiles at the end, with their counts. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryHubVideoScreenshot() =
    MediatheqTheme { LibraryHubScreen(LibraryHubUiState.Content(LibraryPagePreferences(), VIDEO_COUNTS), {}) }

@PreviewTest
@MqPreviews
@Composable
fun LibraryHubVideoListScreenshot() =
    MediatheqTheme {
        LibraryHubScreen(
            LibraryHubUiState.Content(LibraryPagePreferences(style = LibraryHubStyle.List), VIDEO_COUNTS),
            {},
        )
    }

/** Video mode (ADR 0051): only the Movies and Series tiles. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryHubVideoModeScreenshot() =
    MediatheqTheme {
        LibraryHubScreen(
            LibraryHubUiState.Content(
                LibraryPagePreferences(listOf(LibraryShortcut.Movies, LibraryShortcut.Series)),
                VIDEO_COUNTS,
            ),
            {},
        )
    }

/** The Library hub (#129) by default: every tile, a grid of 3 columns. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryHubThreeColumnsScreenshot() = Hub(LibraryPagePreferences())

@PreviewTest
@MqPreviews
@Composable
fun LibraryHubTwoColumnsScreenshot() = Hub(LibraryPagePreferences(columns = 2, spacing = LibraryHubSpacing.Large))

@PreviewTest
@MqPreviews
@Composable
fun LibraryHubFourColumnsScreenshot() = Hub(LibraryPagePreferences(columns = 4, spacing = LibraryHubSpacing.Compact))

/** List style: one row per tile. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryHubListScreenshot() = Hub(LibraryPagePreferences(style = LibraryHubStyle.List))

/** Landscape uses its own column count (default 5). */
@PreviewTest
@Preview(name = "Light", device = "spec:width=891dp,height=411dp,dpi=420")
@Preview(name = "Dark", device = "spec:width=891dp,height=411dp,dpi=420", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun LibraryHubLandscapeScreenshot() = Hub(LibraryPagePreferences())
