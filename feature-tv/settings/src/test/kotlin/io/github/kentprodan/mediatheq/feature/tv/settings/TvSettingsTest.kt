package io.github.kentprodan.mediatheq.feature.tv.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.data.OutputProfile
import io.github.kentprodan.mediatheq.core.data.RememberedOutput
import io.github.kentprodan.mediatheq.core.data.RememberedOutputs
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.data.TvCarousel
import io.github.kentprodan.mediatheq.core.data.TvMenuStyle
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import io.github.kentprodan.mediatheq.core.model.SubtitleStyle
import io.github.kentprodan.mediatheq.core.model.TvLogoSize
import io.github.kentprodan.mediatheq.core.model.TvTitlePage
import io.github.kentprodan.mediatheq.core.model.TvTitleStyle
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakeAccountRepository
import io.github.kentprodan.mediatheq.core.testing.FakeAppModeRepository
import io.github.kentprodan.mediatheq.core.testing.FakeAudioOutputRepository
import io.github.kentprodan.mediatheq.core.testing.FakeAudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeVideoPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The ViewModel with fakes; the screen on a 1080p TV, plain Application.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvSettingsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tv = OutputId(OutputKind.Hdmi, "lgtv")
    private val outputs = FakeAudioOutputRepository()
    private val preferences = FakeAudioPreferencesRepository()
    private val sync = FakeLibrarySyncRepository()
    private val accounts =
        FakeAccountRepository(
            listOf(
                Account("a1", "Home server", "kent", "http://nas:8096", active = true),
                Account("a2", "Other", "guest", "http://other:8096", active = false),
            ),
        )
    private val playback = FakePlaybackConnection()
    private val appMode = FakeAppModeRepository(initial = AppMode.Both)

    private fun TestScope.viewModel(): TvSettingsViewModel {
        val viewModel = TvSettingsViewModel(outputs, preferences, sync, accounts, playback, appMode)
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        return viewModel
    }

    private fun TvSettingsViewModel.content() = uiState.value as TvSettingsUiState.Content

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    private fun onHdmi(profile: OutputProfile = OutputProfile()) {
        outputs.outputs.value =
            RememberedOutputs(
                listOf(RememberedOutput(OutputId.Phone, "", OutputProfile()), RememberedOutput(tv, "LG TV", profile)),
                current = tv,
            )
    }

    @Test
    fun `each setting is read, with the active account`() =
        test {
            onHdmi(OutputProfile(atmosPassthrough = false))
            preferences.setStreamingQuality(metered = false, StreamingQuality.Kbps320)
            sync.status.value = SyncStatus.Running(SyncStep.Albums, 120, 800)

            val state = viewModel().content()

            assertThat(state.hdmi).isEqualTo(TvHdmiOutput(tv, "LG TV", atmosPassthrough = false))
            assertThat(state.quality).isEqualTo(StreamingQuality.Kbps320)
            assertThat(state.sync).isEqualTo(SyncStatus.Running(SyncStep.Albums, 120, 800))
            assertThat(state.serverName).isEqualTo("Home server")
            assertThat(state.userName).isEqualTo("kent")
        }

    @Test
    fun `the app mode is shown and changed, hidden without video on the server`() =
        test {
            val viewModel = viewModel()
            assertThat(viewModel.content().appMode).isEqualTo(AppMode.Both)

            viewModel.onAction(TvSettingsAction.SetAppMode(AppMode.Music))
            runCurrent()

            assertThat(appMode.stored.value).isEqualTo(AppMode.Music)
            assertThat(viewModel.content().appMode).isEqualTo(AppMode.Music)

            val noVideo =
                TvSettingsViewModel(
                    outputs,
                    preferences,
                    sync,
                    accounts,
                    playback,
                    FakeAppModeRepository(videoAvailable = false),
                )
            backgroundScope.launch { noVideo.uiState.collect() }
            runCurrent()
            assertThat(noVideo.content().appMode).isNull()
        }

    @Test
    fun `Simple transitions switches and is stored with the navigation settings (#444)`() =
        test {
            val library = FakeLibraryPreferencesRepository()
            val viewModel =
                TvSettingsViewModel(
                    outputs,
                    preferences,
                    sync,
                    accounts,
                    playback,
                    appMode,
                    FakeVideoPreferencesRepository(),
                    library,
                )
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            assertThat(viewModel.content().simpleTransitions).isFalse()

            viewModel.onAction(TvSettingsAction.ToggleSimpleTransitions)
            runCurrent()

            assertThat(
                library.preferences
                    .first()
                    .navigation.simpleTransitions,
            ).isTrue()
            assertThat(viewModel.content().simpleTransitions).isTrue()
        }

    @Test
    fun `the Home carousel's style, source and watched filter are chosen and stored (#521)`() =
        test {
            val library = FakeLibraryPreferencesRepository()
            val viewModel =
                TvSettingsViewModel(
                    outputs,
                    preferences,
                    sync,
                    accounts,
                    playback,
                    appMode,
                    FakeVideoPreferencesRepository(),
                    library,
                )
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            assertThat(viewModel.content().carousel).isEqualTo(TvCarousel.Card)

            viewModel.onAction(TvSettingsAction.SetCarousel(TvCarousel.Immersive))
            runCurrent()
            assertThat(viewModel.content().carousel).isEqualTo(TvCarousel.Immersive)
            viewModel.onAction(TvSettingsAction.SetCarousel(TvCarousel.Off))
            runCurrent()
            assertThat(viewModel.content().carousel).isEqualTo(TvCarousel.Off)

            assertThat(viewModel.content().carouselSources).contains(HomeSection.ContinueWatching)
            // The default (#575): new movies, new episodes and Continue watching; the panel stays open on a change.
            assertThat(viewModel.content().carouselPicked)
                .containsExactly(
                    HomeSection.RecentlyAddedMovies,
                    HomeSection.RecentlyAddedEpisodes,
                    HomeSection.ContinueWatching,
                )
            viewModel.onAction(TvSettingsAction.ToggleCarouselSource(HomeSection.RecentlyAddedEpisodes))
            runCurrent()
            viewModel.onAction(TvSettingsAction.ToggleCarouselSource(HomeSection.FavoriteAlbums))
            viewModel.onAction(TvSettingsAction.ToggleCarouselSkipWatched)
            runCurrent()

            val stored = library.preferences.first().navigation
            val picked =
                listOf(HomeSection.ContinueWatching, HomeSection.RecentlyAddedMovies, HomeSection.FavoriteAlbums)
            assertThat(stored.tvCarouselSources).containsExactlyElementsIn(picked)
            assertThat(stored.tvCarouselSkipWatched).isFalse()
            assertThat(viewModel.content().carouselPicked).containsExactlyElementsIn(picked)

            // Music only: the video sections drop out, as Home leaves them out.
            appMode.setMode(AppMode.Music)
            runCurrent()
            assertThat(viewModel.content().carouselPicked).containsExactly(HomeSection.FavoriteAlbums)
            assertThat(viewModel.content().carouselSources).doesNotContain(HomeSection.ContinueWatching)
            // The last one cannot go off.
            viewModel.onAction(TvSettingsAction.ToggleCarouselSource(HomeSection.FavoriteAlbums))
            runCurrent()
            assertThat(viewModel.content().carouselPicked).containsExactly(HomeSection.FavoriteAlbums)
            // A toggle in Music mode keeps the stored video sections for when video comes back.
            viewModel.onAction(TvSettingsAction.ToggleCarouselSource(HomeSection.RecentlyAddedAlbums))
            runCurrent()
            assertThat(
                library.preferences
                    .first()
                    .navigation.tvCarouselSources,
            ).containsAtLeast(HomeSection.ContinueWatching, HomeSection.RecentlyAddedMovies)
        }

    @Test
    fun `logo size starts at Medium and the one picked is stored for that page alone (#578, #619)`() =
        test {
            val library = FakeLibraryPreferencesRepository()
            val viewModel =
                TvSettingsViewModel(
                    outputs,
                    preferences,
                    sync,
                    accounts,
                    playback,
                    appMode,
                    FakeVideoPreferencesRepository(),
                    library,
                )
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            assertThat(viewModel.content().logoSizes[TvTitlePage.Series]).isEqualTo(TvLogoSize.Medium)

            viewModel.onAction(TvSettingsAction.SetLogoSize(TvTitlePage.Series, TvLogoSize.Large))
            runCurrent()
            assertThat(viewModel.content().logoSizes[TvTitlePage.Series]).isEqualTo(TvLogoSize.Large)
            viewModel.onAction(TvSettingsAction.SetLogoSize(TvTitlePage.Series, TvLogoSize.Small))
            runCurrent()

            // Each page on its own (#619): the others keep theirs.
            assertThat(viewModel.content().logoSizes[TvTitlePage.Movie]).isEqualTo(TvLogoSize.Medium)
            assertThat(
                library.preferences
                    .first()
                    .navigation.tvLogoSizes,
            ).containsExactly(TvTitlePage.Series, TvLogoSize.Small)
        }

    @Test
    fun `menu style starts at Modal and the one picked is stored (#520)`() =
        test {
            val library = FakeLibraryPreferencesRepository()
            val viewModel =
                TvSettingsViewModel(
                    outputs,
                    preferences,
                    sync,
                    accounts,
                    playback,
                    appMode,
                    FakeVideoPreferencesRepository(),
                    library,
                )
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            assertThat(viewModel.content().menuStyle).isEqualTo(TvMenuStyle.Modal)

            viewModel.onAction(TvSettingsAction.SetMenuStyle(TvMenuStyle.Standard))
            runCurrent()

            assertThat(
                library.preferences
                    .first()
                    .navigation.tvMenuStyle,
            ).isEqualTo(TvMenuStyle.Standard)
            assertThat(viewModel.content().menuStyle).isEqualTo(TvMenuStyle.Standard)
        }

    @Test
    fun `Accent from the poster switches and is stored, off by default (#517)`() =
        test {
            val library = FakeLibraryPreferencesRepository()
            val viewModel =
                TvSettingsViewModel(
                    outputs,
                    preferences,
                    sync,
                    accounts,
                    playback,
                    appMode,
                    FakeVideoPreferencesRepository(),
                    library,
                )
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            assertThat(viewModel.content().posterAccent).isFalse()

            viewModel.onAction(TvSettingsAction.TogglePosterAccent)
            runCurrent()

            assertThat(
                library.preferences
                    .first()
                    .navigation.tvPosterAccent,
            ).isTrue()
            assertThat(viewModel.content().posterAccent).isTrue()
        }

    @Test
    fun `Title on pages takes Logo, Logo and name or Name only and is stored for that page (#523, #619)`() =
        test {
            val library = FakeLibraryPreferencesRepository()
            val viewModel =
                TvSettingsViewModel(
                    outputs,
                    preferences,
                    sync,
                    accounts,
                    playback,
                    appMode,
                    FakeVideoPreferencesRepository(),
                    library,
                )
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            val seen =
                listOf(TvTitleStyle.LogoAndText, TvTitleStyle.Text, TvTitleStyle.Logo).map {
                    viewModel.onAction(TvSettingsAction.SetTitleStyle(TvTitlePage.Artist, it))
                    runCurrent()
                    viewModel.content().titleStyles[TvTitlePage.Artist]
                }

            assertThat(seen).containsExactly(TvTitleStyle.LogoAndText, TvTitleStyle.Text, TvTitleStyle.Logo).inOrder()
            assertThat(
                library.preferences
                    .first()
                    .navigation.tvTitles,
            ).containsExactly(TvTitlePage.Artist, TvTitleStyle.Logo)
        }

    @Test
    fun `a subtitle style part is chosen and stored`() =
        test {
            val video = FakeVideoPreferencesRepository()
            val viewModel = TvSettingsViewModel(outputs, preferences, sync, accounts, playback, appMode, video)
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            viewModel.onAction(TvSettingsAction.SetSubtitleStyle(SubtitleStyle(size = SubtitleStyle.Size.Large)))
            runCurrent()

            assertThat(video.preferences.value.subtitleStyle.size).isEqualTo(SubtitleStyle.Size.Large)
            assertThat(viewModel.content().subtitleStyle.size).isEqualTo(SubtitleStyle.Size.Large)
        }

    @Test
    fun `the switch edits the current HDMI output, else the last one remembered, and none before any`() {
        assertThat(hdmiOutput(RememberedOutputs())).isNull()

        val other = OutputId(OutputKind.Hdmi, "monitor")
        val remembered =
            RememberedOutputs(
                listOf(
                    RememberedOutput(OutputId.Phone, "", OutputProfile()),
                    RememberedOutput(tv, "LG TV", OutputProfile()),
                    RememberedOutput(other, "Monitor", OutputProfile(atmosPassthrough = false)),
                ),
                current = OutputId(OutputKind.Bluetooth, "headphones"),
            )
        assertThat(hdmiOutput(remembered)?.id).isEqualTo(other)
        assertThat(hdmiOutput(remembered.copy(current = tv))?.id).isEqualTo(tv)
    }

    @Test
    fun `the quality is chosen and written for unmetered networks`() =
        test {
            val viewModel = viewModel()

            viewModel.onAction(TvSettingsAction.SetQuality(StreamingQuality.Kbps256))
            runCurrent()
            assertThat(viewModel.content().quality).isEqualTo(StreamingQuality.Kbps256)
            assertThat(preferences.preferences.value.unmeteredQuality).isEqualTo(StreamingQuality.Kbps256)
        }

    @Test
    fun `Sync now runs a sync, and a failed one shows`() =
        test {
            val viewModel = viewModel()

            viewModel.onAction(TvSettingsAction.SyncNow)
            runCurrent()
            assertThat(sync.syncCalls).isEqualTo(1)

            sync.status.value = SyncStatus.Failed(MqError.Network, lastSyncedAt = null)
            runCurrent()
            assertThat(viewModel.content().sync).isEqualTo(SyncStatus.Failed(MqError.Network, null))
        }

    @Test
    fun `sign out pauses the music and removes the active account`() =
        test {
            playback.state.value = PlayerUiModel(isPlaying = true)
            val viewModel = viewModel()

            viewModel.onAction(TvSettingsAction.SignOut)
            advanceUntilIdle()
            assertThat(playback.commands).containsExactly("togglePlayPause")
            assertThat(accounts.removed).containsExactly("a1" to true)
        }

    @Test
    fun `selecting the passthrough row on screen turns the HDMI output's switch off in the repository`() =
        test {
            onHdmi()
            val viewModel = viewModel()
            composeRule.setContent {
                val state by viewModel.uiState.collectAsState()
                MediatheqTvTheme { TvSettingsScreen(state, "1.0", viewModel::onAction, onOpenLicenses = {}) }
            }

            click("Sound and streaming")
            click("Dolby Atmos passthrough")
            runCurrent()

            assertThat(
                outputs.outputs.value
                    .profileOf(tv)
                    .atmosPassthrough,
            ).isFalse()
            assertThat(viewModel.content().hdmi?.atmosPassthrough).isFalse()
        }

    @Test
    fun `Settings opens on the categories, focus on the first, Account (#580, #619)`() {
        show(content())
        composeRule.onNodeWithText("Account").assertIsFocused()
        composeRule.onNodeWithText("Sound and streaming").assertExists()
    }

    @Test
    fun `Right opens the focused category with its rows, Left comes back to it (#580)`() {
        show(content())
        press(Key.DirectionRight)
        composeRule.onNodeWithText("Home server").assertIsFocused()

        press(Key.DirectionLeft)
        composeRule.onNodeWithText("Account").assertIsFocused()
    }

    @Test
    fun `the Home carousel design is picked under Screens, each design with its description (#619)`() {
        val actions = mutableListOf<TvSettingsAction>()
        show(content(), actions::add)
        click("Screens")
        click("Home")
        click("Home carousel")
        click("Design")

        composeRule.onNodeWithText("Card").assertIsFocused()
        composeRule.onNodeWithText("Featured titles across", substring = true).assertExists()
        click("Immersive")
        assertThat(actions).containsExactly(TvSettingsAction.SetCarousel(TvCarousel.Immersive))
    }

    @Test
    fun `reordering picks a row up with Select, and Up or Down then moves it instead of the focus (#621)`() {
        val moves = mutableListOf<TvHomeRowsAction>()
        val rows =
            TvHomeRowsState(
                listOf(TvHomeRow(HomeSection.ContinueWatching, true), TvHomeRow(null, true, genre = "Drama")),
            )
        show(content().copy(appMode = AppMode.Both), rows = rows, onRowsAction = moves::add)
        click("Screens")
        click("Home")
        click("Home rows")
        click("Reorder rows")
        composeRule.onNodeWithText("Continue watching").assertIsFocused()

        // Not picked up: Down moves the focus.
        press(Key.DirectionDown)
        composeRule.onNodeWithText("Drama").assertIsFocused()
        assertThat(moves).isEmpty()

        click("Drama")
        press(Key.DirectionUp)
        assertThat(moves).containsExactly(TvHomeRowsAction.Move("genre:Drama", up = true))
        composeRule.onNodeWithText("Drama").assertIsFocused()
    }

    @Test
    fun `a subtitle part following the TV offers the system caption settings (#619)`() {
        show(content().copy(appMode = AppMode.Both))
        click("Video and subtitles")
        click("Size")
        composeRule.onNodeWithText("Open the TV caption settings").assertExists()
    }

    @Test
    fun `the quality level opens on the current choice, whose radio is on, and picking comes back (#580)`() {
        val actions = mutableListOf<TvSettingsAction>()
        show(content().copy(quality = StreamingQuality.Kbps320), actions::add)
        click("Sound and streaming")
        click("Streaming quality")

        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        composeRule
            .onNode(hasText("320", substring = true) and radio)
            .assertIsFocused()
            .assertIsOn()
        composeRule.onNode(hasText("Original") and radio).assertIsOff()

        composeRule.onNode(hasText("Original") and radio).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertThat(actions).containsExactly(TvSettingsAction.SetQuality(StreamingQuality.Original))
        composeRule.onNodeWithText("Streaming quality").assertIsFocused()
    }

    @Test
    fun `sign out asks on its own level, and Cancel keeps the account (#580)`() {
        val actions = mutableListOf<TvSettingsAction>()
        show(content(), actions::add)
        click("Account")
        click("Sign out")
        composeRule.onNodeWithText("Sign out of Home server?").assertExists()

        click("Cancel")
        assertThat(actions).isEmpty()
        composeRule.onNodeWithText("Sign out").assertIsFocused()
    }

    private fun content() =
        TvSettingsUiState.Content(
            hdmi = TvHdmiOutput(tv, "LG TV", atmosPassthrough = true),
            quality = StreamingQuality.Original,
            sync = SyncStatus.Idle(null),
            serverName = "Home server",
            userName = "kent",
        )

    private fun show(
        content: TvSettingsUiState.Content,
        onAction: (TvSettingsAction) -> Unit = {},
        rows: TvHomeRowsState? = null,
        onRowsAction: (TvHomeRowsAction) -> Unit = {},
    ) {
        composeRule.setContent {
            MediatheqTvTheme {
                TvSettingsScreen(
                    content,
                    "1.0",
                    onAction,
                    onOpenLicenses = {},
                    homeRows = rows,
                    onHomeRowsAction = onRowsAction,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun click(text: String) {
        composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    @Test
    fun `without an HDMI output the switch row does nothing`() =
        test {
            val viewModel = viewModel()
            assertThat(viewModel.content().hdmi).isNull()

            viewModel.onAction(TvSettingsAction.TogglePassthrough)
            runCurrent()

            assertThat(outputs.outputs.value).isEqualTo(RememberedOutputs())
        }
}
