package io.github.kentprodan.mediatheq.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import io.github.kentprodan.mediatheq.core.data.AdvancedPreferences
import io.github.kentprodan.mediatheq.core.data.AlbumButton
import io.github.kentprodan.mediatheq.core.data.AlbumPagePreferences
import io.github.kentprodan.mediatheq.core.data.AppFont
import io.github.kentprodan.mediatheq.core.data.ArtistPagePreferences
import io.github.kentprodan.mediatheq.core.data.ArtistPlayAction
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.BehaviorSwitch
import io.github.kentprodan.mediatheq.core.data.ColorSource
import io.github.kentprodan.mediatheq.core.data.GenrePagePreferences
import io.github.kentprodan.mediatheq.core.data.HomePreferences
import io.github.kentprodan.mediatheq.core.data.KeepScreenOn
import io.github.kentprodan.mediatheq.core.data.LibraryHubStyle
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.NowPlayingColors
import io.github.kentprodan.mediatheq.core.data.NowPlayingPreferences
import io.github.kentprodan.mediatheq.core.data.PlayerBackground
import io.github.kentprodan.mediatheq.core.data.PlayerProgress
import io.github.kentprodan.mediatheq.core.data.PlaylistButton
import io.github.kentprodan.mediatheq.core.data.PlaylistHeaderBackground
import io.github.kentprodan.mediatheq.core.data.PlaylistPagePreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.SearchPagePreferences
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SwipeAction
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.TrackAction
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqSeedPresets
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
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
                    onOpenOutputs = { events += "outputs" },
                    onBack = { events += "back" },
                    onOpenTheme = { events += "theme" },
                    onOpenAdvanced = { events += "advanced" },
                    onOpenBehavior = { events += "behavior" },
                    onOpenDecoding = { events += "decoding" },
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
            SettingsPage.Decoding,
            SettingsUiState.Content("Home", "Ana", AudioPreferences(meteredQuality = StreamingQuality.Kbps128)),
        )

        // Mobile data first, then Wi-Fi.
        composeRule.onAllNodesWithText("128 kbps")[0].assertIsSelected()
        composeRule.onAllNodesWithText("Original")[1].assertIsSelected()
        composeRule.onAllNodesWithText("256 kbps")[1].performClick()

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
    fun `playback opens the output settings, and back works`() {
        show(SettingsPage.Playback)

        composeRule.onNode(hasText("Output settings") and hasClickAction()).performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(events).containsExactly("outputs", "back").inOrder()
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
    fun `the playback hub holds its pages, normalization lives per output`() {
        show(SettingsPage.Playback)
        assertRows("Decoding and transcoding", "Output settings")
        composeRule.onNodeWithText("Volume normalization").assertDoesNotExist()
    }

    @Test
    fun `the decoding page holds both bitrates and instant transcoding`() {
        show(SettingsPage.Decoding)
        assertRows("Mobile maximum bitrate", "Wi-Fi maximum bitrate", "Force instant transcoding")
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
        // The page settings rows push Ratings below the fold.
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(isHeading() and hasText("Ratings"))
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
                    navEntriesDraft(NavigationPreferences.DEFAULT_ENTRIES),
                    { actions += it },
                )
            }
        }

        composeRule.onNode(hasText("Home") and isToggleable()).assertIsNotEnabled()
        // The dialog's list scrolls: Tracks sits below the fold.
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Tracks"))
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
        // Rows come in Home's order: the second one moves up.
        val secondRow = composeRule.onAllNodes(hasCustomActions())[1].fetchSemanticsNode()
        secondRow.config[SemanticsActions.CustomActions].first { it.label == "Move up" }.action()

        val defaults = HomePreferences()
        assertThat(actions)
            .containsExactly(
                SettingsAction.SetHomeHidden(defaults.hidden + HomeSection.FavoriteAlbums),
                SettingsAction.SetHomeHidden(defaults.hidden - HomeSection.RecentlyAddedTracks),
                SettingsAction.SetHomeOrder(
                    listOf(HomeSection.entries[1], HomeSection.entries[0]) + HomeSection.entries.drop(2),
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
                    listOf(HomeSection.entries[1], HomeSection.entries[0]) + HomeSection.entries.drop(2),
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

    @Test
    fun `the library page opens the organize dialog and applies a column choice at once`() {
        show(SettingsPage.Library)

        composeRule.onNode(hasText("Library shortcuts") and hasClickAction()).performClick()
        composeRule.onNode(hasText("2") and isSelectable()).performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.OpenLibraryShortcuts,
                SettingsAction.SetLibraryPage(LibraryPagePreferences(columns = 2)),
            ).inOrder()
    }

    @Test
    fun `the library organize dialog checks, moves, and answers OK and Cancel`() {
        var draft by mutableStateOf(OrganizeDraft.of(LibraryShortcut.entries, LibraryShortcut.entries))
        composeRule.setContent {
            MediatheqTheme {
                LibraryShortcutsDialog(draft) { action ->
                    actions += action
                    if (action is SettingsAction.ToggleLibraryShortcut) draft = draft.toggle(action.shortcut)
                }
            }
        }

        composeRule.onNode(hasText("Tracks") and isToggleable()).assertIsOn().performClick()
        composeRule.onNode(hasText("Tracks") and isToggleable()).assertIsOff()
        val secondRow = composeRule.onAllNodes(hasCustomActions())[1].fetchSemanticsNode()
        secondRow.config[SemanticsActions.CustomActions].first { it.label == "Move up" }.action()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.ToggleLibraryShortcut(LibraryShortcut.Tracks),
                SettingsAction.MoveLibraryShortcut(1, 0),
                SettingsAction.ConfirmLibraryShortcuts,
                SettingsAction.DismissLibraryShortcuts,
            ).inOrder()
    }

    @Test
    fun `columns hide in the List style`() {
        show(
            SettingsPage.Library,
            SettingsUiState.Content(
                "Home",
                "Ana",
                library = LibraryPreferences(libraryPage = LibraryPagePreferences(style = LibraryHubStyle.List)),
            ),
        )

        composeRule.onNodeWithText("Columns").assertDoesNotExist()
        composeRule.onNodeWithText("List").assertExists()
    }

    @Test
    fun `the album page switches apply at once and its order row opens the organize dialog`() {
        show(SettingsPage.Album)

        composeRule.onNode(hasText("Show rating bar") and isToggleable()).performClick()
        composeRule.onNode(hasText("Page content order") and hasClickAction()).performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetAlbumPage(AlbumPagePreferences(showRatingBar = true)),
                SettingsAction.OpenAlbumSections,
            ).inOrder()
    }

    @Test
    fun `hidden album buttons are checked in the dialog and stored only with OK`() {
        show(SettingsPage.Album)

        composeRule.onNode(hasText("Hidden buttons") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Resume") and isToggleable()).performClick()
        composeRule.onNode(hasText("Favorite") and isToggleable()).performClick()
        composeRule.onNodeWithText("OK").performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetAlbumPage(
                    AlbumPagePreferences(hiddenButtons = setOf(AlbumButton.Resume, AlbumButton.Favorite)),
                ),
            )
    }

    @Test
    fun `the artist page switches and columns apply at once and its order row opens the organize dialog`() {
        show(SettingsPage.Artist)

        composeRule.onNode(hasText("Show \"Appears on\"") and isToggleable()).performClick()
        composeRule.onNode(hasText("Page content order") and hasClickAction()).performClick()
        composeRule.onNode(hasText("2") and hasClickAction()).performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetArtistPage(ArtistPagePreferences(showAppearsOn = false)),
                SettingsAction.OpenArtistSections,
                SettingsAction.SetArtistPage(ArtistPagePreferences(albumColumns = 2)),
            ).inOrder()
    }

    @Test
    fun `the artist play action is chosen in its dialog`() {
        show(SettingsPage.Artist)

        composeRule.onNode(hasText("Play button action") and hasClickAction()).performClick()
        composeRule.onNodeWithText("All tracks shuffled").performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetArtistPage(ArtistPagePreferences(playAction = ArtistPlayAction.AllShuffled)),
            )
    }

    @Test
    fun `the genre page switches apply at once and its dynamic colors are chosen in a dialog`() {
        show(SettingsPage.Genre)

        composeRule.onNode(hasText("Show \"Album artists\"") and isToggleable()).performClick()
        composeRule.onNode(hasText("Dynamic colors") and hasClickAction()).performClick()
        composeRule.onNodeWithText("From header image").performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetGenrePage(GenrePagePreferences(showAlbumArtists = true)),
                SettingsAction.SetGenrePage(GenrePagePreferences(dynamicColors = true)),
            ).inOrder()
    }

    @Test
    fun `the playlist page background and hidden buttons are chosen in dialogs`() {
        show(SettingsPage.Playlist)

        composeRule.onNode(hasText("Header background") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Image color (gradient)").performClick()
        composeRule.onNode(hasText("Hidden buttons") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Favorite").performClick()
        composeRule.onNodeWithText("OK").performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetPlaylistPage(
                    PlaylistPagePreferences(headerBackground = PlaylistHeaderBackground.ImageColor),
                ),
                SettingsAction.SetPlaylistPage(PlaylistPagePreferences(hiddenButtons = setOf(PlaylistButton.Favorite))),
            ).inOrder()
    }

    @Test
    fun `the search page switches apply at once and the track action is chosen in a dialog`() {
        show(SettingsPage.Search)

        composeRule.onNode(hasText("Show favorite button") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Default track action") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Play next").performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetSearchPage(SearchPagePreferences(showFavorite = false)),
                SettingsAction.SetSearchPage(SearchPagePreferences(trackAction = TrackAction.PlayNext)),
            ).inOrder()
    }

    @Test
    fun `the advanced settings apply at once, a dialog choice and a switch`() {
        show(SettingsPage.Advanced)

        composeRule.onNode(hasText("Keep screen on") and hasClickAction()).performClick()
        composeRule.onNodeWithText("While Now Playing is open").performClick()
        composeRule.onNode(hasText("Hide status bar") and hasClickAction()).performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetAdvanced(AdvancedPreferences(keepScreenOn = KeepScreenOn.NowPlaying)),
                SettingsAction.SetAdvanced(AdvancedPreferences(hideStatusBar = true)),
            ).inOrder()
    }

    @Test
    fun `Interface opens Advanced settings`() {
        show(SettingsPage.Interface)

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Advanced settings"))
        composeRule.onNode(hasText("Advanced settings") and hasClickAction()).performClick()

        assertThat(events).containsExactly("advanced")
    }

    @Test
    fun `the Playback hub opens its pages and the output settings`() {
        show(SettingsPage.Playback)

        composeRule.onNode(hasText("Decoding and transcoding") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Playback") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Output settings") and hasClickAction()).performClick()

        assertThat(events).containsExactly("decoding", "behavior", "outputs").inOrder()
    }

    @Test
    fun `decoding and behavior switches apply at once`() {
        show(SettingsPage.Decoding)
        composeRule.onNode(hasText("Force instant transcoding") and hasClickAction()).performClick()
        composeRule.onAllNodesWithText("192 kbps")[0].performClick()

        assertThat(actions)
            .containsExactly(
                SettingsAction.SetForceInstantTranscoding(true),
                SettingsAction.SetStreamingQuality(metered = true, StreamingQuality.Kbps192),
            ).inOrder()
    }

    @Test
    fun `a playback behavior switch applies at once`() {
        show(SettingsPage.PlaybackBehavior)

        composeRule.onNode(hasText("Weighted shuffle") and hasClickAction()).performClick()

        assertThat(actions).containsExactly(SettingsAction.SetBehavior(BehaviorSwitch.WeightedShuffle, false))
    }
}

/** Where the drag handle is across a Home page row. */
private const val HANDLE_FRACTION = 0.06f
