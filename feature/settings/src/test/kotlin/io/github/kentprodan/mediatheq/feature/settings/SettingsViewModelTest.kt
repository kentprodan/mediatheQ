package io.github.kentprodan.mediatheq.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AppFont
import io.github.kentprodan.mediatheq.core.data.AudioPreferences
import io.github.kentprodan.mediatheq.core.data.ColorSource
import io.github.kentprodan.mediatheq.core.data.EqPreset
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.Normalization
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.playback.AudioOutputInfo
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakeAudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = FakeSessionRepository()
    private val audio = FakeAudioPreferencesRepository()
    private val library = FakeLibraryPreferencesRepository()
    private val playback = FakePlaybackConnection()
    private val viewModel by lazy { SettingsViewModel(session, audio, library, playback) }

    private fun signIn() {
        session.session.value = SessionState.LoggedIn("Home", "Ana")
    }

    @Test
    fun `signed out it shows nothing`() =
        runTest {
            viewModel.uiState.test { assertThat(awaitItem()).isEqualTo(SettingsUiState.Loading) }
        }

    @Test
    fun `the account, the preferences and the output show`() =
        runTest {
            signIn()
            playback.state.value = PlayerUiModel(output = AudioOutputInfo(usbDacConnected = true))

            viewModel.uiState.test {
                assertThat(expectMostRecentItem())
                    .isEqualTo(
                        SettingsUiState.Content(
                            "Home",
                            "Ana",
                            AudioPreferences(),
                            LibraryPreferences(),
                            AudioOutputInfo(usbDacConnected = true),
                        ),
                    )
            }
        }

    @Test
    fun `a changed preference shows at once`() =
        runTest {
            signIn()
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.onAction(SettingsAction.SetRatingMode(RatingMode.HalfStars))

                assertThat((awaitItem() as SettingsUiState.Content).library.ratingMode).isEqualTo(RatingMode.HalfStars)
            }
        }

    @Test
    fun `playback and storage actions reach the audio preferences`() =
        runTest {
            signIn()

            viewModel.onAction(SettingsAction.SetStreamingQuality(metered = true, StreamingQuality.Kbps128))
            viewModel.onAction(SettingsAction.SetStreamingQuality(metered = false, StreamingQuality.Kbps256))
            viewModel.onAction(SettingsAction.SetNormalization(Normalization.Off))
            viewModel.onAction(SettingsAction.SetStreamingCacheGb(8))
            viewModel.onAction(SettingsAction.SetDownloadsUnmeteredOnly(false))

            assertThat(audio.preferences.value)
                .isEqualTo(
                    AudioPreferences(
                        normalization = Normalization.Off,
                        unmeteredQuality = StreamingQuality.Kbps256,
                        meteredQuality = StreamingQuality.Kbps128,
                        streamingCacheGb = 8,
                        downloadsUnmeteredOnly = false,
                    ),
                )
        }

    @Test
    fun `audio actions reach the audio preferences`() =
        runTest {
            viewModel.onAction(SettingsAction.SetBitPerfectUsb(true))
            viewModel.onAction(SettingsAction.SetEqualizerOn(true))
            viewModel.onAction(SettingsAction.SetEqPreset(EqPreset.Vocal))
            viewModel.onAction(SettingsAction.SetEqBand(0, 5f))

            val preferences = audio.preferences.value
            assertThat(preferences.bitPerfectUsb).isTrue()
            assertThat(preferences.equalizerOn).isTrue()
            assertThat(preferences.eqPreset).isEqualTo(EqPreset.Custom)
            assertThat(preferences.eqGainsDb.first()).isEqualTo(5f)
        }

    @Test
    fun `display actions reach the library preferences`() =
        runTest {
            viewModel.onAction(SettingsAction.SetAlbumColumns(3))
            viewModel.onAction(SettingsAction.SetRatingMode(RatingMode.Off))
            viewModel.onAction(SettingsAction.SetOfflineMode(true))

            assertThat(library.preferences.value)
                .isEqualTo(
                    LibraryPreferences(
                        albumColumns = 3,
                        ratingMode = RatingMode.Off,
                        offlineMode = true,
                    ),
                )
        }

    @Test
    fun `log out signs out`() =
        runTest {
            signIn()

            viewModel.onAction(SettingsAction.LogOut)

            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut())
        }

    @Test
    fun `theme actions reach the library preferences, and restore resets only the theme`() =
        runTest {
            val theme = ThemePreferences(ThemeMode.Black, ColorSource.Custom, font = AppFont.Lato)
            viewModel.onAction(SettingsAction.SetAlbumColumns(3))

            viewModel.onAction(SettingsAction.SetTheme(theme))
            assertThat(library.preferences.value.theme).isEqualTo(theme)

            viewModel.onAction(SettingsAction.RestoreThemeDefaults)
            assertThat(library.preferences.value).isEqualTo(LibraryPreferences(albumColumns = 3))
        }
}
