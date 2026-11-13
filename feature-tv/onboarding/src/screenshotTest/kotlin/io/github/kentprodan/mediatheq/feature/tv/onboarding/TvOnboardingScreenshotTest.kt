package io.github.kentprodan.mediatheq.feature.tv.onboarding

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.LogoutReason
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.feature.tv.onboarding.login.TvLoginError
import io.github.kentprodan.mediatheq.feature.tv.onboarding.login.TvLoginScreen
import io.github.kentprodan.mediatheq.feature.tv.onboarding.login.TvLoginUiState
import io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect.TvQuickConnectScreen
import io.github.kentprodan.mediatheq.feature.tv.onboarding.quickconnect.TvQuickConnectUiState
import io.github.kentprodan.mediatheq.feature.tv.onboarding.server.TvServerError
import io.github.kentprodan.mediatheq.feature.tv.onboarding.server.TvServerScreen
import io.github.kentprodan.mediatheq.feature.tv.onboarding.server.TvServerUiState

@PreviewTest
@MqTvPreviews
@Composable
fun TvServerScreenshot() = MediatheqTvTheme { TvServerScreen(TvServerUiState(address = "192.168.1.10:8096"), {}) }

/** An unreachable server, after a 401 sign-out. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvServerErrorScreenshot() =
    MediatheqTvTheme {
        TvServerScreen(
            TvServerUiState("nas", error = TvServerError.Unreachable, notice = LogoutReason.SessionExpired),
            {},
        )
    }

@PreviewTest
@MqTvPreviews
@Composable
fun TvQuickConnectWaitingScreenshot() =
    MediatheqTvTheme { TvQuickConnectScreen(TvQuickConnectUiState.Waiting("Home", "482913"), {}, {}) }

@PreviewTest
@MqTvPreviews
@Composable
fun TvQuickConnectExpiredScreenshot() =
    MediatheqTvTheme { TvQuickConnectScreen(TvQuickConnectUiState.Expired("Home"), {}, {}) }

@PreviewTest
@MqTvPreviews
@Composable
fun TvQuickConnectDisabledScreenshot() =
    MediatheqTvTheme { TvQuickConnectScreen(TvQuickConnectUiState.Disabled("Home"), {}, {}) }

@PreviewTest
@MqTvPreviews
@Composable
fun TvLoginScreenshot() =
    MediatheqTvTheme {
        TvLoginScreen(TvLoginUiState("Home", userName = "ana", password = "x"), {})
    }

@PreviewTest
@MqTvPreviews
@Composable
fun TvLoginErrorScreenshot() =
    MediatheqTvTheme {
        TvLoginScreen(
            TvLoginUiState("Home", userName = "ana", password = "wrong", error = TvLoginError.WrongCredentials),
            {},
        )
    }

@PreviewTest
@MqTvPreviews
@Composable
fun TvQuickConnectStartingScreenshot() =
    MediatheqTvTheme { TvQuickConnectScreen(TvQuickConnectUiState.Starting("Home"), {}, {}) }

@PreviewTest
@MqTvPreviews
@Composable
fun TvQuickConnectErrorScreenshot() =
    MediatheqTvTheme { TvQuickConnectScreen(TvQuickConnectUiState.Error("Home", unreachable = true), {}, {}) }

@PreviewTest
@MqTvPreviews
@Composable
fun TvLoginSubmittingScreenshot() =
    MediatheqTvTheme { TvLoginScreen(TvLoginUiState("Home", userName = "ana", password = "x", submitting = true), {}) }
