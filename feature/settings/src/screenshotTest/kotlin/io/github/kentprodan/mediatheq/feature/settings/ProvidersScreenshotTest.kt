package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

private val home = Account("mediatheq.db", "Home", "Ana", "http://192.168.178.34:8096", active = true)
private val office = Account("mediatheq-2.db", "Office", "Bo", "https://music.example.org", active = false)

/** Manage media providers (#147): two accounts, the first active, then Add media provider. */
@PreviewTest
@MqPreviews
@Composable
fun ProvidersScreenshot() {
    MediatheqTheme { ProvidersScreen(ProvidersUiState.Content(listOf(home, office)), {}, {}, {}, {}) }
}

/** A restored account waiting for a sign-in (#150): Signed out, its menu only Sign in and remove. */
@PreviewTest
@MqPreviews
@Composable
fun ProvidersSignedOutScreenshot() {
    MediatheqTheme {
        ProvidersScreen(ProvidersUiState.Content(listOf(home, office.copy(signedIn = false))), {}, {}, {}, {})
    }
}

@PreviewTest
@MqPreviews
@Composable
fun ProviderMenuSignedOutScreenshot() {
    MediatheqTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column { ProviderMenuItems(office.copy(signedIn = false), {}, {}, {}, {}, {}, {}) }
        }
    }
}

/** The menu of an inactive account, in place (popups render blank in screenshot tests). */
@PreviewTest
@MqPreviews
@Composable
fun ProviderMenuScreenshot() {
    MediatheqTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column { ProviderMenuItems(office, {}, {}, {}, {}, {}, {}) }
        }
    }
}

/** The menu of the active account: Sync now, no Make active. */
@PreviewTest
@MqPreviews
@Composable
fun ProviderMenuActiveScreenshot() {
    MediatheqTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column { ProviderMenuItems(home, {}, {}, {}, {}, {}, {}) }
        }
    }
}

/** Add media provider: the supported providers (Jellyfin). */
@PreviewTest
@MqPreviews
@Composable
fun ProviderPickerScreenshot() {
    MediatheqTheme { MqDialogContent("Add media provider", null, {}, "Cancel", {}) { ProviderPickerBody {} } }
}

/** Switching asks first: the app restarts. */
@PreviewTest
@MqPreviews
@Composable
fun SwitchAccountScreenshot() {
    MediatheqTheme { MqDialogContent("Switch to Office?", "Switch", {}, "Cancel", {}) { SwitchAccountBody() } }
}

/** Removing asks first, the downloads only when checked. */
@PreviewTest
@MqPreviews
@Composable
fun RemoveAccountScreenshot() {
    MediatheqTheme { MqDialogContent("Remove Office?", "Remove", {}, "Cancel", {}) { RemoveAccountBody(true) {} } }
}

/** Edit address with the reason it was not saved. */
@PreviewTest
@MqPreviews
@Composable
fun EditAddressErrorScreenshot() {
    MediatheqTheme {
        MqDialogContent("Edit address", "Save", {}, "Cancel", {}) {
            EditAddressBody("http://100.100.41.27:8096", AddressEdit(home.id, error = AddressError.OtherServer)) {}
        }
    }
}
