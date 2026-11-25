package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import kotlin.time.Duration

/** One row of the tracks sheet: a stream by its server index (`null` = Off for subtitles). */
data class TrackOption(
    val index: Int?,
    val label: String,
    val selected: Boolean,
)

/** What the tracks sheet lists (#274). */
data class TracksSheetState(
    val audio: List<TrackOption>,
    /** Off first, then each subtitle. */
    val subtitles: List<TrackOption>,
    /** For an episode: whether the choice is saved for its whole season; `null` for a movie. */
    val wholeSeason: Boolean? = null,
    /** In the player with subtitles on (#456): how much later (negative: earlier) they show; `null` hides the row. */
    val subtitleDelay: Duration? = null,
    /** In the player (#635): Off, then a plain text subtitle per language for a second track at the top; `null` hides. */
    val secondSubtitles: List<TrackOption>? = null,
)

/** The audio and subtitle choice as a sheet (#274); a pick applies at once, the sheet stays for the other list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MqTracksSheet(
    state: TracksSheetState,
    onAudio: (Int) -> Unit,
    onSubtitle: (Int?) -> Unit,
    onWholeSeason: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** Subtitles 0.1 s later (`true`), earlier, or back in step (`null`), #456. */
    onShiftSubtitles: (Boolean?) -> Unit = {},
    /** A row of the second subtitles (#635), by stream index; `null` = Off. */
    onSecondSubtitle: (Int?) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        MqTracksContent(state, onAudio, onSubtitle, onWholeSeason, onShiftSubtitles, onSecondSubtitle)
    }
}

/** The sheet's content, for previews and screenshots (popups render blank there). */
@Composable
fun MqTracksContent(
    state: TracksSheetState,
    onAudio: (Int) -> Unit,
    onSubtitle: (Int?) -> Unit,
    onWholeSeason: (Boolean) -> Unit,
    onShiftSubtitles: (Boolean?) -> Unit = {},
    onSecondSubtitle: (Int?) -> Unit = {},
) {
    LazyColumn(contentPadding = PaddingValues(bottom = MediatheqSpacing.l)) {
        state.wholeSeason?.let { whole ->
            item(key = "season") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.core_ui_tracks_whole_season)) },
                    trailingContent = { Switch(checked = whole, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.toggleable(whole, role = Role.Switch, onValueChange = onWholeSeason),
                )
            }
        }
        item(key = "audio") { Title(R.string.core_ui_tracks_audio) }
        state.audio.forEach { option ->
            item(key = "a${option.index}") { OptionRow(option) { option.index?.let(onAudio) } }
        }
        item(key = "subtitles") { Title(R.string.core_ui_tracks_subtitles) }
        state.subtitles.forEach { option ->
            item(key = "s${option.index}") { OptionRow(option) { onSubtitle(option.index) } }
        }
        state.subtitleDelay?.let { delay -> item(key = "timing") { TimingRow(delay, onShiftSubtitles) } }
        state.secondSubtitles?.let { options ->
            item(key = "second") { Title(R.string.core_ui_tracks_second_subtitles) }
            options.forEach { option ->
                item(key = "t${option.index}") { OptionRow(option) { onSecondSubtitle(option.index) } }
            }
        }
    }
}

@Composable
private fun TimingRow(
    delay: Duration,
    onShift: (Boolean?) -> Unit,
) {
    val seconds = "%.1f".format(delay.absoluteValue.inWholeMilliseconds / 1000.0)
    ListItem(
        headlineContent = { Text(stringResource(R.string.core_ui_subtitle_timing)) },
        supportingContent = {
            Text(
                when {
                    delay.isPositive() -> stringResource(R.string.core_ui_subtitles_later_by, seconds)
                    delay.isNegative() -> stringResource(R.string.core_ui_subtitles_earlier_by, seconds)
                    else -> stringResource(R.string.core_ui_subtitles_in_step)
                },
            )
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
                if (delay != Duration.ZERO) {
                    TextButton({ onShift(null) }) { Text(stringResource(R.string.core_ui_subtitles_reset)) }
                }
                OutlinedButton({ onShift(false) }) { Text(stringResource(R.string.core_ui_subtitles_earlier)) }
                OutlinedButton({ onShift(true) }) { Text(stringResource(R.string.core_ui_subtitles_later)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Title(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s)
                .semantics { heading() },
    )
}

@Composable
private fun OptionRow(
    option: TrackOption,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(option.label) },
        trailingContent = if (option.selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(option.selected, role = Role.RadioButton, onClick = onClick),
    )
}
