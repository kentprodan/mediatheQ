package io.github.kentprodan.mediatheq.feature.tv.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
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
    fun `the app mode is shown and changed from its dialog, hidden without video on the server`() =
        test {
            val viewModel = viewModel()
            assertThat(viewModel.content().appMode).isEqualTo(AppMode.Both)

            viewModel.onAction(TvSettingsAction.ChooseAppMode)
            assertThat(viewModel.content().dialog).isEqualTo(TvSettingsDialog.AppMode)
            viewModel.onAction(TvSettingsAction.SetAppMode(AppMode.Music))
            runCurrent()

            assertThat(appMode.stored.value).isEqualTo(AppMode.Music)
            assertThat(viewModel.content().appMode).isEqualTo(AppMode.Music)
            assertThat(viewModel.content().dialog).isNull()

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

            viewModel.onAction(TvSettingsAction.NextCarousel)
            runCurrent()
            assertThat(viewModel.content().carousel).isEqualTo(TvCarousel.Immersive)
            viewModel.onAction(TvSettingsAction.NextCarousel)
            runCurrent()
            assertThat(viewModel.content().carousel).isEqualTo(TvCarousel.Off)

            viewModel.onAction(TvSettingsAction.ChooseCarouselSource)
            assertThat(viewModel.content().dialog).isEqualTo(TvSettingsDialog.CarouselSource)
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
            assertThat(viewModel.content().dialog).isEqualTo(TvSettingsDialog.CarouselSource)
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
    fun `logo size starts at Medium, goes Large then Small, and is stored (#578)`() =
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
            assertThat(viewModel.content().logoSize).isEqualTo(TvLogoSize.Medium)

            viewModel.onAction(TvSettingsAction.NextLogoSize)
            runCurrent()
            assertThat(viewModel.content().logoSize).isEqualTo(TvLogoSize.Large)
            viewModel.onAction(TvSettingsAction.NextLogoSize)
            runCurrent()

            assertThat(
                library.preferences
                    .first()
                    .navigation.tvLogoSize,
            ).isEqualTo(TvLogoSize.Small)
        }

    @Test
    fun `menu style switches between Modal and Standard and is stored (#520)`() =
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

            viewModel.onAction(TvSettingsAction.NextMenuStyle)
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
    fun `Title on pages goes Logo, Logo and name, Name only, then Logo again and is stored (#523)`() =
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
                (1..3).map {
                    viewModel.onAction(TvSettingsAction.NextTitleStyle)
                    runCurrent()
                    viewModel.content().titleStyle
                }

            assertThat(seen).containsExactly(TvTitleStyle.LogoAndText, TvTitleStyle.Text, TvTitleStyle.Logo).inOrder()
            assertThat(
                library.preferences
                    .first()
                    .navigation.tvTitle,
            ).isEqualTo(TvTitleStyle.Logo)
        }

    @Test
    fun `a subtitle style part is chosen from its dialog and stored`() =
        test {
            val video = FakeVideoPreferencesRepository()
            val viewModel = TvSettingsViewModel(outputs, preferences, sync, accounts, playback, appMode, video)
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            viewModel.onAction(TvSettingsAction.ChooseSubtitle(TvSettingsDialog.SubtitleSize))
            assertThat(viewModel.content().dialog).isEqualTo(TvSettingsDialog.SubtitleSize)
            viewModel.onAction(TvSettingsAction.SetSubtitleStyle(SubtitleStyle(size = SubtitleStyle.Size.Large)))
            runCurrent()

            assertThat(video.preferences.value.subtitleStyle.size).isEqualTo(SubtitleStyle.Size.Large)
            assertThat(viewModel.content().subtitleStyle.size).isEqualTo(SubtitleStyle.Size.Large)
            assertThat(viewModel.content().dialog).isNull()
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
    fun `the quality is chosen in a dialog and written for unmetered networks`() =
        test {
            val viewModel = viewModel()

            viewModel.onAction(TvSettingsAction.ChooseQuality)
            runCurrent()
            assertThat(viewModel.content().dialog).isEqualTo(TvSettingsDialog.Quality)

            viewModel.onAction(TvSettingsAction.SetQuality(StreamingQuality.Kbps256))
            runCurrent()
            assertThat(viewModel.content().dialog).isNull()
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
    fun `sign out asks first, then pauses the music and removes the active account`() =
        test {
            playback.state.value = PlayerUiModel(isPlaying = true)
            val viewModel = viewModel()

            viewModel.onAction(TvSettingsAction.AskSignOut)
            runCurrent()
            assertThat(viewModel.content().dialog).isEqualTo(TvSettingsDialog.SignOut)
            assertThat(accounts.removed).isEmpty()

            viewModel.onAction(TvSettingsAction.SignOut)
            advanceUntilIdle()
            assertThat(playback.commands).containsExactly("togglePlayPause")
            assertThat(accounts.removed).containsExactly("a1" to true)
            assertThat(viewModel.content().dialog).isNull()
        }

    @Test
    fun `cancelling sign out keeps the account`() =
        test {
            val viewModel = viewModel()

            viewModel.onAction(TvSettingsAction.AskSignOut)
            viewModel.onAction(TvSettingsAction.Dismiss)
            advanceUntilIdle()

            assertThat(viewModel.content().dialog).isNull()
            assertThat(accounts.removed).isEmpty()
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

            composeRule.onNodeWithText("Dolby Atmos passthrough").performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
            runCurrent()

            assertThat(
                outputs.outputs.value
                    .profileOf(tv)
                    .atmosPassthrough,
            ).isFalse()
            assertThat(viewModel.content().hdmi?.atmosPassthrough).isFalse()
        }

    @Test
    fun `focus starts on the passthrough row`() {
        val content =
            TvSettingsUiState.Content(
                hdmi = TvHdmiOutput(tv, "LG TV", atmosPassthrough = true),
                quality = StreamingQuality.Original,
                sync = SyncStatus.Idle(null),
                serverName = "Home server",
                userName = "kent",
            )
        composeRule.setContent { MediatheqTvTheme { TvSettingsScreen(content, "1.0", {}, onOpenLicenses = {}) } }
        composeRule.onNodeWithText("Dolby Atmos passthrough").assertIsFocused()
    }

    @Test
    fun `before any HDMI output focus starts on the quality row`() {
        val content =
            TvSettingsUiState.Content(
                hdmi = null,
                quality = StreamingQuality.Original,
                sync = SyncStatus.Idle(null),
                serverName = "Home server",
                userName = "kent",
            )
        composeRule.setContent { MediatheqTvTheme { TvSettingsScreen(content, "1.0", {}, onOpenLicenses = {}) } }
        composeRule.onNodeWithText("Streaming quality").assertIsFocused()
    }

    @Test
    fun `the quality dialog opens on the current choice, whose radio is on (#523)`() {
        val content =
            TvSettingsUiState.Content(
                hdmi = null,
                quality = StreamingQuality.Kbps320,
                sync = SyncStatus.Idle(null),
                serverName = "Home server",
                userName = "kent",
            )
        var dialog by mutableStateOf<TvSettingsDialog?>(null)
        composeRule.setContent {
            MediatheqTvTheme { TvSettingsScreen(content.copy(dialog = dialog), "1.0", {}, onOpenLicenses = {}) }
        }
        composeRule.waitForIdle()
        dialog = TvSettingsDialog.Quality

        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        composeRule
            .onNode(hasText("320", substring = true) and radio)
            .assertIsFocused()
            .assertIsSelected()
        composeRule.onNode(hasText("Original") and radio).assertIsNotSelected()
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
