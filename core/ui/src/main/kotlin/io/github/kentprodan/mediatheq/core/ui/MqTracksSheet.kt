package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

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
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        MqTracksContent(state, onAudio, onSubtitle, onWholeSeason)
    }
}

/** The sheet's content, for previews and screenshots (popups render blank there). */
@Composable
fun MqTracksContent(
    state: TracksSheetState,
    onAudio: (Int) -> Unit,
    onSubtitle: (Int?) -> Unit,
    onWholeSeason: (Boolean) -> Unit,
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
    }
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
