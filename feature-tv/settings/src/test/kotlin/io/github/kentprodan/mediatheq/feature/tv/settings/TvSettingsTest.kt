package io.github.kentprodan.mediatheq.feature.tv.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
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
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakeAccountRepository
import io.github.kentprodan.mediatheq.core.testing.FakeAudioOutputRepository
import io.github.kentprodan.mediatheq.core.testing.FakeAudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySyncRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
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

    private fun TestScope.viewModel(): TvSettingsViewModel {
        val viewModel = TvSettingsViewModel(outputs, preferences, sync, accounts, playback)
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
    fun `without an HDMI output the switch row does nothing`() =
        test {
            val viewModel = viewModel()
            assertThat(viewModel.content().hdmi).isNull()

            viewModel.onAction(TvSettingsAction.TogglePassthrough)
            runCurrent()

            assertThat(outputs.outputs.value).isEqualTo(RememberedOutputs())
        }
}
