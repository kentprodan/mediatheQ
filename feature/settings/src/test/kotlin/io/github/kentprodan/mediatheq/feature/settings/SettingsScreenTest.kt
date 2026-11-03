package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AppFont
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.ColorSource
import io.github.kentprodan.mediatheq.core.data.HomePreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.NowPlayingColors
import io.github.kentprodan.mediatheq.core.data.NowPlayingPreferences
import io.github.kentprodan.mediatheq.core.data.PlayerBackground
import io.github.kentprodan.mediatheq.core.data.PlayerProgress
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SwipeAction
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqSeedPresets
import io.github.kentprodan.mediatheq.core.model.HomeSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<SettingsAction>()
    private val events = mutableListOf<String>()

    private fun show(
        page: SettingsPage,
        content: SettingsUiState = SettingsUiState.Content("Home", "Ana"),
    ) {
        composeRule.setContent {
            MediatheqTheme {
                SettingsPageScreen(
                    page,
                    content,
                    { actions += it },
                    onOpenAudio = { events += "audio" },
                    onBack = { events += "back" },
                    onOpenTheme = { events += "theme" },
                )
            }
        }
    }

    private fun showAbout() {
        composeRule.setContent {
            MediatheqTheme {
                AboutSettingsScreen(
                    "1.2.3",
                    onOpenShowcase = { events += "showcase" },
                    onOpenLicenses = { events += "licenses" },
                    onBack = { events += "back" },
                )
            }
        }
    }

    /** The titles of a page's rows, so every setting is on exactly one page. */
    private fun assertRows(vararg titles: String) {
        titles.forEach { title ->
            scrollTo(title)
            composeRule.onAllNodesWithText(title).assertCountEquals(1)
        }
    }

    private fun scrollTo(text: String) =
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))

    @Test
    fun `manage media providers shows the account, and log out sends its action`() {
        show(SettingsPage.Providers)

        composeRule.onNodeWithText("Home").assertExists()
        composeRule.onNodeWithText("Ana").assertExists()
        composeRule.onNodeWithText("Log out").performClick()

        assertThat(actions).containsExactly(SettingsAction.LogOut)
    }

    @Test
    fun `each network has its own quality chips, the current one selected`() {
        show(
            SettingsPage.Playback,
            SettingsUiState.Content("Home", "Ana", AudioPreferences(meteredQuality = StreamingQuality.Kbps128)),
        )

        composeRule.onAllNodesWithText("Original")[0].assertIsSelected()
        composeRule.onAllNodesWithText("128 kbps")[1].assertIsSelected()
        composeRule.onAllNodesWithText("256 kbps")[0].performClick()

        assertThat(
            actions,
        ).containsExactly(SettingsAction.SetStreamingQuality(metered = false, StreamingQuality.Kbps256))
    }

    @Test
    fun `switches are whole rows with their state`() {
        show(SettingsPage.Storage)
        scrollTo("Download on Wi-Fi only")

        composeRule.onNode(isToggleable() and hasText("Download on Wi-Fi only")).assertIsOn().performClick()

        assertThat(actions).containsExactly(SettingsAction.SetDownloadsUnmeteredOnly(false))
    }

    @Test
    fun `the offline mode switch sends its new state`() {
        show(SettingsPage.Storage)
        scrollTo("Offline mode")

        composeRule.onNode(isToggleable() and hasText("Offline mode")).assertIsOff().performClick()

        assertThat(actions).containsExactly(SettingsAction.SetOfflineMode(true))
    }

    @Test
    fun `the rating choice sends the mode`() {
        show(SettingsPage.Interface)
        scrollTo("Half stars")

        composeRule.onNodeWithText("Half stars").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetRatingMode(RatingMode.HalfStars))
    }

    @Test
    fun `playback opens the audio page, and back works`() {
        show(SettingsPage.Playback)

        composeRule.onNodeWithText("Audio").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(events).containsExactly("audio", "back").inOrder()
    }

    @Test
    fun `about shows the version and opens the licenses and the showcase`() {
        showAbout()

        composeRule.onNodeWithText("1.2.3").assertExists()
        composeRule.onNodeWithText("Open-source licenses").performClick()
        composeRule.onNodeWithText("Theme showcase").performClick()

        assertThat(events).containsExactly("licenses", "showcase").inOrder()
    }

    @Test
    fun `each page holds its own settings`() {
        show(SettingsPage.Interface)
        assertRows("Theme, font and colors", "Album grid columns", "Star ratings")
    }

    @Test
    fun `the playback page holds the qualities, normalization and audio`() {
        show(SettingsPage.Playback)
        assertRows("Quality on Wi-Fi", "Quality on mobile data", "Volume normalization", "Audio")
    }

    @Test
    fun `the storage page holds offline mode, downloads and the cache`() {
        show(SettingsPage.Storage)
        assertRows("Offline mode", "Download on Wi-Fi only", "Streaming cache")
    }

    @Test
    fun `the providers page holds the account and log out`() {
        show(SettingsPage.Providers)
        assertRows("Server", "Signed in as", "Log out")
    }

    @Test
    fun `a page shows nothing until its settings are read`() {
        show(SettingsPage.Interface, SettingsUiState.Loading)

        composeRule.onNodeWithText("Theme, font and colors").assertDoesNotExist()
    }

    @Test
    fun `group titles are headings`() {
        show(SettingsPage.Interface)

        composeRule.onNode(isHeading() and hasText("Appearance")).assertExists()
        composeRule.onNode(isHeading() and hasText("Ratings")).assertExists()
    }

    @Test
    fun `the interface page opens Theme, font and colors`() {
        show(SettingsPage.Interface)

        composeRule.onNodeWithText("Theme, font and colors").performClick()

        assertThat(events).containsExactly("theme")
    }

    @Test
    fun `the theme page holds the theme, Now playing, typography and restore settings`() {
        show(SettingsPage.Theme)
        assertRows(
            "Theme mode",
            "Default color source",
            "Selected theme colors",
            "Now playing color source",
            "Ignore background and surfaces",
            "Instant color updates",
            "Restore defaults",
        )
    }

    @Test
    fun `a theme choice sends every theme setting with the one changed`() {
        show(SettingsPage.Theme)

        composeRule.onNodeWithText("Black").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetTheme(ThemePreferences(mode = ThemeMode.Black)))
    }

    @Test
    fun `the custom color shows only with the custom color source`() {
        show(SettingsPage.Theme)
        composeRule.onNodeWithContentDescription("Hue").assertDoesNotExist()

        composeRule.onNodeWithText("Custom color").performClick()
        assertThat(actions).containsExactly(SettingsAction.SetTheme(ThemePreferences(colorSource = ColorSource.Custom)))
    }

    @Test
    fun `a preset seed sets the custom color`() {
        val theme = ThemePreferences(colorSource = ColorSource.Custom)
        show(SettingsPage.Theme, SettingsUiState.Content("Home", "Ana", library = LibraryPreferences(theme = theme)))

        composeRule.onNodeWithContentDescription("Color 3 of 8").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetTheme(theme.copy(customSeed = MqSeedPresets[2])))
    }

    @Test
    fun `the Now playing dialog shows the current choice, and a tap applies another and closes`() {
        show(SettingsPage.Theme)
        scrollTo("Now playing color source")

        composeRule.onNodeWithText("Now playing color source").performClick()
        composeRule.onNode(isSelectable() and hasText("Cover")).assertIsSelected()
        composeRule.onNodeWithText("Cover vibrant").performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetTheme(ThemePreferences(nowPlayingColors = NowPlayingColors.CoverVibrant)),
            )
        composeRule.onNodeWithText("Cover vibrant").assertDoesNotExist()
    }

    @Test
    fun `the typography dialog sets the font`() {
        show(SettingsPage.Theme)
        scrollTo("Typography")

        composeRule.onAllNodesWithText("Typography").filterToOne(hasClickAction()).performClick()
        composeRule.onNodeWithText("Lato").performClick()

        assertThat(actions).containsExactly(SettingsAction.SetTheme(ThemePreferences(font = AppFont.Lato)))
    }

    @Test
    fun `restore defaults asks first, and Cancel changes nothing`() {
        show(SettingsPage.Theme)
        scrollTo("Restore defaults")

        composeRule.onNodeWithText("Restore defaults").performClick()
        composeRule.onNodeWithText("Restore all options on this page to their default value?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(actions).isEmpty()
        composeRule.onNodeWithText("Restore all options on this page to their default value?").assertDoesNotExist()
    }

    @Test
    fun `restore defaults resets the page after confirming`() {
        show(SettingsPage.Theme)
        scrollTo("Restore defaults")

        composeRule.onNodeWithText("Restore defaults").performClick()
        // The group title says "Restore" too: the dialog's button is the clickable one.
        composeRule.onNode(hasText("Restore") and hasClickAction()).performClick()

        assertThat(actions).containsExactly(SettingsAction.RestoreThemeDefaults)
    }

    @Test
    fun `in the entries dialog Home cannot be unchecked and a tap toggles an entry`() {
        composeRule.setContent {
            MediatheqTheme {
                NavEntriesList(
                    NavEntriesDraft.of(NavigationPreferences.DEFAULT_ENTRIES),
                    { actions += it },
                )
            }
        }

        composeRule.onNode(hasText("Home") and isToggleable()).assertIsNotEnabled()
        composeRule.onNode(hasText("Tracks") and isToggleable()).assertIsOff().performClick()

        assertThat(actions).containsExactly(SettingsAction.ToggleNavEntry(MainNavEntry.Tracks))
    }

    @Test
    fun `the navigation page opens the entries dialog and sets a switch`() {
        show(SettingsPage.Navigation)

        composeRule.onNode(hasText("Main navigation entries") and hasClickAction()).performClick()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Expand player automatically"))
        composeRule.onNode(hasText("Expand player automatically") and isToggleable()).performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.OpenNavEntries,
                SettingsAction.SetNavigation(NavigationPreferences(expandPlayer = true)),
            ).inOrder()
    }

    @Test
    fun `media lists switches and choice dialogs apply at once`() {
        show(SettingsPage.MediaLists)

        composeRule.onNode(hasText("Inverted sort arrows") and isToggleable()).performClick()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Track swipe left action"))
        composeRule.onNode(hasText("Track swipe left action") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Disabled") and isSelectable()).performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetMediaLists(MediaListPreferences(invertedSortArrows = true)),
                SettingsAction.SetMediaLists(MediaListPreferences(swipeLeft = SwipeAction.Disabled)),
            ).inOrder()
    }

    @Test
    fun `media lists hidden top buttons apply only with OK`() {
        show(SettingsPage.MediaLists)

        composeRule.onNode(hasText("Hidden top buttons") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Shuffle") and isToggleable()).performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertThat(actions).isEmpty()

        composeRule.onNode(hasText("Hidden top buttons") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Shuffle") and isToggleable()).assertIsOff().performClick()
        composeRule.onNode(hasText("Play next") and isToggleable()).performClick()
        composeRule.onNodeWithText("OK").performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetMediaLists(
                    MediaListPreferences(hiddenTopButtons = setOf(TopButton.Shuffle, TopButton.PlayNext)),
                ),
            )
    }

    @Test
    fun `now playing switches and choice dialogs apply at once`() {
        show(SettingsPage.NowPlaying)

        composeRule.onNode(hasText("Floating player") and isToggleable()).performClick()
        composeRule.onNode(hasText("Background style") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Theme background") and isSelectable()).performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetNowPlaying(NowPlayingPreferences(floatingMiniPlayer = false)),
                SettingsAction.SetNowPlaying(NowPlayingPreferences(background = PlayerBackground.Theme)),
            ).inOrder()
    }

    @Test
    fun `the stars row is off and disabled while ratings are off, the fallback while no waveform is used`() {
        val content = SettingsUiState.Content("Home", "Ana")
        show(
            SettingsPage.NowPlaying,
            content.copy(
                library =
                    content.library.copy(
                        ratingMode = RatingMode.Off,
                        nowPlaying = NowPlayingPreferences(progress = PlayerProgress.Basic),
                    ),
            ),
        )

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Show rating stars"))
        composeRule.onNode(hasText("Show rating stars") and isToggleable()).assertIsOff().assertIsNotEnabled()
        composeRule.onNode(hasText("Ratings are off", substring = true)).assertExists()
        composeRule.onNode(hasText("Not used", substring = true)).assertExists()
    }

    @Test
    fun `the eye hides or shows a section and TalkBack can move it`() {
        show(SettingsPage.Home)

        composeRule
            .onNodeWithContentDescription(
                "Show Favorite albums",
            ).assert(hasStateDescription("Shown"))
            .performClick()
        composeRule.onNodeWithContentDescription("Show Recently added tracks").performClick()
        // Rows come in Home's order: the second one is Favorite albums.
        val secondRow = composeRule.onAllNodes(hasCustomActions())[1].fetchSemanticsNode()
        secondRow.config[SemanticsActions.CustomActions].first { it.label == "Move up" }.action()

        val defaults = HomePreferences()
        assertThat(actions)
            .containsExactly(
                SettingsAction.SetHomeHidden(defaults.hidden + HomeSection.FavoriteAlbums),
                SettingsAction.SetHomeHidden(defaults.hidden - HomeSection.RecentlyAddedTracks),
                SettingsAction.SetHomeOrder(
                    listOf(HomeSection.FavoriteAlbums, HomeSection.FavoritePlaylists) + HomeSection.entries.drop(2),
                ),
            ).inOrder()
    }

    @Test
    fun `reset active rows asks first`() {
        show(SettingsPage.Home)

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Reset active rows"))
        composeRule.onNode(hasText("Reset active rows") and hasClickAction()).performClick()
        composeRule.onAllNodes(hasText("Reset active rows") and hasClickAction()).onLast().performClick()

        assertThat(actions).containsExactly(SettingsAction.ResetHomeRows)
    }

    private fun hasCustomActions() = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)

    private fun hasStateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun `dragging a section by its handle saves the new order once, on drop`() {
        show(SettingsPage.Home)
        val first = composeRule.onAllNodes(hasCustomActions())[0]
        val rowHeight =
            first
                .fetchSemanticsNode()
                .size.height
                .toFloat()

        // The handle sits at the start of the row.
        first.performTouchInput {
            val handle = Offset(left + width * HANDLE_FRACTION, centerY)
            down(handle)
            moveBy(Offset(0f, rowHeight * 0.6f))
            moveBy(Offset(0f, rowHeight * 0.6f))
            up()
        }
        composeRule.waitForIdle()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetHomeOrder(
                    listOf(HomeSection.FavoriteAlbums, HomeSection.FavoritePlaylists) + HomeSection.entries.drop(2),
                ),
            )
    }

    @Test
    fun `a drag that ends where it started saves nothing`() {
        show(SettingsPage.Home)

        composeRule.onAllNodes(hasCustomActions())[0].performTouchInput {
            val handle = Offset(left + width * HANDLE_FRACTION, centerY)
            down(handle)
            moveBy(Offset(0f, height * 0.2f))
            moveBy(Offset(0f, -height * 0.2f))
            up()
        }
        composeRule.waitForIdle()

        assertThat(actions).isEmpty()
    }
}

/** Where the drag handle is across a Home page row. */
private const val HANDLE_FRACTION = 0.06f
