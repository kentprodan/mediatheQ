package io.github.kentprodan.mediatheq.feature.onboarding

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.LogoutReason
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.feature.onboarding.login.LoginError
import io.github.kentprodan.mediatheq.feature.onboarding.login.LoginScreen
import io.github.kentprodan.mediatheq.feature.onboarding.login.LoginUiState
import io.github.kentprodan.mediatheq.feature.onboarding.login.QuickConnectAvailability
import io.github.kentprodan.mediatheq.feature.onboarding.quickconnect.QuickConnectScreen
import io.github.kentprodan.mediatheq.feature.onboarding.quickconnect.QuickConnectUiState
import io.github.kentprodan.mediatheq.feature.onboarding.server.ServerError
import io.github.kentprodan.mediatheq.feature.onboarding.server.ServerScreen
import io.github.kentprodan.mediatheq.feature.onboarding.server.ServerUiState

// One image per distinct layout; the error texts themselves are covered by the ViewModel tests.

@PreviewTest
@MqPreviews
@Composable
fun ServerEmptyScreenshot() = MediatheqTheme { ServerScreen(ServerUiState(), onAction = {}) }

@PreviewTest
@MqPreviews
@Composable
fun ServerErrorScreenshot() =
    MediatheqTheme {
        ServerScreen(ServerUiState("192.168.1.10:8096", error = ServerError.Unsupported("10.11.6")), onAction = {})
    }

@PreviewTest
@MqPreviews
@Composable
fun ServerSessionExpiredScreenshot() =
    MediatheqTheme { ServerScreen(ServerUiState(notice = LogoutReason.SessionExpired), onAction = {}) }

@PreviewTest
@MqPreviews
@Composable
fun LoginEmptyScreenshot() =
    MediatheqTheme {
        LoginScreen(LoginUiState("Home", quickConnect = QuickConnectAvailability.Available), onAction = {})
    }

@PreviewTest
@MqPreviews
@Composable
fun LoginWrongPasswordScreenshot() =
    MediatheqTheme {
        LoginScreen(
            LoginUiState(
                "Home",
                userName = "ana",
                password = "secret",
                error = LoginError.WrongCredentials,
                quickConnect = QuickConnectAvailability.Available,
            ),
            onAction = {},
        )
    }

@PreviewTest
@MqPreviews
@Composable
fun LoginQuickConnectDisabledScreenshot() =
    MediatheqTheme {
        LoginScreen(LoginUiState("Home", quickConnect = QuickConnectAvailability.Disabled), onAction = {})
    }

@PreviewTest
@MqPreviews
@Composable
fun QuickConnectWaitingScreenshot() =
    MediatheqTheme { QuickConnectScreen(QuickConnectUiState.Waiting("Home", "123456"), onAction = {}, onCancel = {}) }

@PreviewTest
@MqPreviews
@Composable
fun QuickConnectExpiredScreenshot() =
    MediatheqTheme { QuickConnectScreen(QuickConnectUiState.Expired("Home"), onAction = {}, onCancel = {}) }

@PreviewTest
@MqPreviews
@Composable
fun QuickConnectErrorScreenshot() =
    MediatheqTheme {
        QuickConnectScreen(QuickConnectUiState.Error("Home", unreachable = true), onAction = {}, onCancel = {})
    }
