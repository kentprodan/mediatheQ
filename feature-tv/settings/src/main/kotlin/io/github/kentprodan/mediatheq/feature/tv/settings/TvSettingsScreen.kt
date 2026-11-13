package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.SyncStep
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvListItem
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory

@Composable
fun TvSettingsRoute(
    versionName: String,
    onOpenLicenses: () -> Unit,
    viewModel: TvSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvSettingsScreen(state, versionName, viewModel::onAction, onOpenLicenses)
}

/**
 * Settings on TV (#243): one list in sections (audio output, streaming, library, account, about); focus starts on its
 * first row, or on the row it had when coming back from the licenses. The quality choice and the sign-out confirmation are dialogs.
 */
@Composable
fun TvSettingsScreen(
    state: TvSettingsUiState,
    versionName: String,
    onAction: (TvSettingsAction) -> Unit,
    onOpenLicenses: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        if (state !is TvSettingsUiState.Content) return@Surface
        // Coming back from the licenses focuses the row that opened them (ADR 0041).
        val focus = rememberFocusMemory()
        LazyColumn(
            Modifier.fillMaxHeight().wrapContentWidth(Alignment.Start).width(720.dp),
            contentPadding = PaddingValues(48.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.tv_settings_title),
                    Modifier.padding(bottom = 16.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
            section(R.string.tv_settings_output)
            item {
                val hdmi = state.hdmi
                if (hdmi != null) {
                    MqTvListItem(
                        stringResource(R.string.tv_settings_passthrough),
                        { onAction(TvSettingsAction.TogglePassthrough) },
                        Modifier.focusMemory(focus, PASSTHROUGH),
                        supporting = stringResource(R.string.tv_settings_passthrough_detail, hdmi.name),
                        trailing = { Switch(checked = hdmi.atmosPassthrough, onCheckedChange = null) },
                    )
                } else {
                    // Nothing to switch yet: the player remembers the HDMI output on its first play there.
                    MqTvListItem(
                        stringResource(R.string.tv_settings_passthrough),
                        {},
                        supporting = stringResource(R.string.tv_settings_passthrough_unknown),
                        enabled = false,
                    )
                }
            }
            section(R.string.tv_settings_streaming)
            item {
                MqTvListItem(
                    stringResource(R.string.tv_settings_quality),
                    { onAction(TvSettingsAction.ChooseQuality) },
                    Modifier.focusMemory(focus, QUALITY),
                    supporting = qualityLabel(state.quality),
                )
            }
            section(R.string.tv_settings_library)
            item {
                MqTvListItem(
                    stringResource(R.string.tv_settings_sync),
                    { onAction(TvSettingsAction.SyncNow) },
                    Modifier.focusMemory(focus, "sync"),
                    supporting = syncLabel(state.sync),
                )
            }
            section(R.string.tv_settings_account)
            item {
                MqTvListItem(
                    state.serverName.orEmpty(),
                    {},
                    Modifier.focusMemory(focus, "account"),
                    supporting = state.userName,
                )
            }
            item {
                MqTvListItem(
                    stringResource(R.string.tv_settings_sign_out),
                    { onAction(TvSettingsAction.AskSignOut) },
                    Modifier.focusMemory(focus, "signOut"),
                )
            }
            section(R.string.tv_settings_about)
            item {
                MqTvListItem(
                    stringResource(R.string.tv_settings_version),
                    {},
                    Modifier.focusMemory(focus, "version"),
                    supporting = versionName,
                )
            }
            item {
                MqTvListItem(
                    stringResource(R.string.tv_settings_licenses),
                    onOpenLicenses,
                    Modifier.focusMemory(focus, "licenses"),
                )
            }
        }
        LaunchedEffect(Unit) {
            // The rows are lazy items, laid out after this effect starts.
            withFrameNanos {}
            focus.restore(if (state.hdmi != null) PASSTHROUGH else QUALITY)
        }
        when (state.dialog) {
            TvSettingsDialog.Quality -> QualityDialog(state.quality, onAction)
            TvSettingsDialog.SignOut -> SignOutDialog(state, onAction)
            null -> Unit
        }
    }
}

private fun LazyListScope.section(title: Int) =
    item {
        Text(
            stringResource(title),
            Modifier.padding(top = 24.dp, bottom = 4.dp, start = 12.dp).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

@Composable
private fun QualityDialog(
    current: StreamingQuality,
    onAction: (TvSettingsAction) -> Unit,
) {
    val currentMark = stringResource(R.string.tv_settings_quality_current)
    MqTvMenu(
        title = stringResource(R.string.tv_settings_quality),
        entries =
            StreamingQuality.entries.map { quality ->
                val label = qualityLabel(quality)
                MqTvMenuEntry(if (quality == current) "$label · $currentMark" else label, null) {
                    onAction(TvSettingsAction.SetQuality(quality))
                }
            },
        onDismiss = { onAction(TvSettingsAction.Dismiss) },
    )
}

@Composable
private fun SignOutDialog(
    state: TvSettingsUiState.Content,
    onAction: (TvSettingsAction) -> Unit,
) = MqTvMenu(
    title = stringResource(R.string.tv_settings_sign_out_question, state.serverName.orEmpty()),
    entries =
        listOf(
            MqTvMenuEntry(stringResource(R.string.tv_settings_sign_out), null) { onAction(TvSettingsAction.SignOut) },
            MqTvMenuEntry(stringResource(R.string.tv_settings_cancel), null) { onAction(TvSettingsAction.Dismiss) },
        ),
    onDismiss = { onAction(TvSettingsAction.Dismiss) },
)

@Composable
private fun qualityLabel(quality: StreamingQuality): String =
    quality.bitrate?.let { stringResource(R.string.tv_settings_kbps, it / BITS_PER_KBIT) }
        ?: stringResource(R.string.tv_settings_original)

@Composable
private fun syncLabel(status: SyncStatus): String =
    when (status) {
        is SyncStatus.Idle -> {
            stringResource(R.string.tv_settings_sync_idle)
        }

        is SyncStatus.Running -> {
            stringResource(R.string.tv_settings_sync_running, stepLabel(status.step), status.fetched, status.total)
        }

        is SyncStatus.Failed -> {
            stringResource(
                if (status.error ==
                    MqError.Network
                ) {
                    R.string.tv_settings_sync_unreachable
                } else {
                    R.string.tv_settings_sync_failed
                },
            )
        }
    }

@Composable
private fun stepLabel(step: SyncStep): String =
    stringResource(
        when (step) {
            SyncStep.AlbumArtists -> R.string.tv_settings_step_artists
            SyncStep.Albums -> R.string.tv_settings_step_albums
            SyncStep.Tracks -> R.string.tv_settings_step_tracks
            SyncStep.Playlists, SyncStep.PlaylistEntries -> R.string.tv_settings_step_playlists
            SyncStep.Movies -> R.string.tv_settings_step_movies
            SyncStep.Series, SyncStep.Seasons -> R.string.tv_settings_step_series
            SyncStep.Episodes -> R.string.tv_settings_step_episodes
        },
    )

private const val BITS_PER_KBIT = 1000
private const val PASSTHROUGH = "passthrough"
private const val QUALITY = "quality"
