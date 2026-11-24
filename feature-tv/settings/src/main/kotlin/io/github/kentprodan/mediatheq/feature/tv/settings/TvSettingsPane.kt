package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Checkbox
import androidx.tv.material3.CheckboxDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.RadioButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvControl

/**
 * One row of a Settings page (#580), drawn like the Google TV system Settings: an optional round icon, the title and a
 * summary, and a [control] showing [checked]. [section] is the overline above it, starting a group.
 */
internal data class SettingEntry(
    val key: String,
    val title: String,
    val summary: String? = null,
    val icon: ImageVector? = null,
    val control: MqTvControl? = null,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val section: String? = null,
    /** Long-press of select: the row's menu (Home rows). */
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit = {},
)

/**
 * The left column of a Settings page (#580): its [title], an optional [summary] and the rows under it, which scroll
 * when they don't fit. Also the dimmed preview of the next level on the right of the main Settings.
 */
@Composable
internal fun SettingsColumn(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.width(PANE_WIDTH)) {
        Header(title, summary)
        Column(Modifier.padding(top = 24.dp).verticalScroll(rememberScrollState()), content = rows)
    }
}

/**
 * A Settings page opened from a row (Home rows, Parental controls, the licenses; #580): the same title and rows as the
 * main Settings, in one wider column of lazy [rows] for the long lists.
 */
@Composable
internal fun SettingsPage(
    title: String,
    summary: String? = null,
    rows: LazyListScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        // The surface passes its full size down as the minimum: let the column take its own width.
        Column(
            Modifier
                .padding(start = START, top = TOP)
                .wrapContentSize(Alignment.TopStart)
                .width(SINGLE_PANE_WIDTH),
        ) {
            Header(title, summary)
            LazyColumn(contentPadding = PaddingValues(top = 24.dp, bottom = TOP), content = rows)
        }
    }
}

@Composable
private fun Header(
    title: String,
    summary: String?,
) {
    Text(
        title,
        Modifier.padding(start = ROW_INSET).semantics { heading() },
        style = MaterialTheme.typography.displaySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    summary?.let {
        Text(
            it,
            Modifier.padding(start = ROW_INSET, top = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Where Settings start on screen: the system Settings' left edge and top.
internal val START = 106.dp
internal val TOP = 56.dp
private val SINGLE_PANE_WIDTH = 560.dp

/** The pane's width, from the guideline's two-pane settings: about half the screen with the preview beside it. */
internal val PANE_WIDTH = 380.dp

/** A row's content starts this far in, so titles and overlines line up with the icons. */
internal val ROW_INSET = 24.dp

@Composable
internal fun SettingSection(title: String) {
    Text(
        title.uppercase(),
        Modifier.padding(start = ROW_INSET, top = 16.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A focusable Settings row: transparent at rest, a light filled card when focused (the Google TV system look); select
 * runs [SettingEntry.onClick]. [onFocus] tells the page which row has the focus, for the preview.
 */
@Composable
internal fun SettingRow(
    entry: SettingEntry,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
) {
    entry.section?.let { SettingSection(it) }
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = entry.onClick,
        onLongClick = entry.onLongClick,
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }.then(controlSemantics(entry)),
        enabled = entry.enabled,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = scheme.onSurface,
                focusedContainerColor = scheme.inverseSurface,
                // Near black on the light card, as the system Settings.
                focusedContentColor = scheme.background,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = scheme.onSurface.copy(alpha = DISABLED),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = FOCUSED_SCALE),
    ) {
        RowContent(entry, focused)
    }
}

/** A row of the preview: the same content, never focused. */
@Composable
internal fun PreviewRow(entry: SettingEntry) {
    entry.section?.let { SettingSection(it) }
    Box { RowContent(entry, focused = false) }
}

@Composable
private fun RowContent(
    entry: SettingEntry,
    focused: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.heightIn(min = ROW_HEIGHT).padding(horizontal = ROW_INSET, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entry.icon?.let {
            Box(
                Modifier
                    .size(ICON_CIRCLE)
                    .background(if (focused) scheme.primaryContainer else scheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    it,
                    contentDescription = null,
                    Modifier.size(20.dp),
                    tint = if (focused) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Smaller and greyer than the title, a little apart from it (#619).
            entry.summary?.let {
                Text(
                    it,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (focused) LocalContentColor.current.copy(alpha = SUMMARY) else scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // The row handles select: the control only shows the state, dark on the light focused card.
        val ink = LocalContentColor.current
        when (entry.control) {
            MqTvControl.Radio -> {
                RadioButton(
                    selected = entry.checked,
                    onClick = null,
                    enabled = entry.enabled,
                    colors = if (focused) RadioButtonDefaults.colors(ink, ink) else RadioButtonDefaults.colors(),
                )
            }

            MqTvControl.Checkbox -> {
                Checkbox(
                    checked = entry.checked,
                    onCheckedChange = null,
                    enabled = entry.enabled,
                    colors =
                        if (focused) {
                            CheckboxDefaults.colors(ink, ink, scheme.inverseSurface)
                        } else {
                            CheckboxDefaults.colors()
                        },
                )
            }

            MqTvControl.Switch -> {
                Switch(
                    checked = entry.checked,
                    onCheckedChange = null,
                    enabled = entry.enabled,
                    colors =
                        if (focused) {
                            SwitchDefaults.colors(
                                checkedThumbColor = scheme.inverseSurface,
                                checkedTrackColor = ink,
                                uncheckedThumbColor = ink,
                                uncheckedBorderColor = ink,
                            )
                        } else {
                            SwitchDefaults.colors()
                        },
                )
            }

            null -> {
                Unit
            }
        }
    }
}

private fun controlSemantics(entry: SettingEntry): Modifier =
    entry.control?.let { control ->
        Modifier.semantics {
            role =
                when (control) {
                    MqTvControl.Radio -> Role.RadioButton
                    MqTvControl.Checkbox -> Role.Checkbox
                    MqTvControl.Switch -> Role.Switch
                }
            toggleableState = ToggleableState(entry.checked)
        }
    } ?: Modifier

private val ROW_HEIGHT = 64.dp
private val ICON_CIRCLE = 40.dp
private const val FOCUSED_SCALE = 1.03f
private const val SUMMARY = 0.7f
private const val DISABLED = 0.38f
