package io.github.kentprodan.mediatheq.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.data.Mix
import io.github.kentprodan.mediatheq.core.designsystem.component.bottomBarsPadding
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.ui.icon
import io.github.kentprodan.mediatheq.core.ui.label

/**
 * Home's search app bar (#533): a search-bar pill that opens Search, then the mode menu (when [mode] is set) and the
 * profile menu (the profile's initial: Switch profile, Settings). Not an input field: typing happens on Search.
 */
@Composable
internal fun HomeSearchBar(
    navigation: HomeNavigation,
    mode: AppMode?,
    onSetMode: (AppMode) -> Unit,
    profile: String?,
    onOpenProfiles: () -> Unit,
) {
    val label = stringResource(R.string.home_search)
    Surface(
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
        modifier =
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = SearchBarDefaults.InputFieldHeight)
                    .clickable(onClickLabel = label, role = Role.Button, onClick = navigation.onOpenSearch)
                    .padding(start = MediatheqSpacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.l),
            ) {
                Icon(MqIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            mode?.let { ModeMenu(it, onSetMode) }
            ProfileMenu(profile, onOpenProfiles, navigation.onOpenServers, navigation.onOpenSettings)
        }
    }
}

/** The app mode (ADR 0051) as an icon; its menu picks Music, Video or Both. */
@Composable
private fun ModeMenu(
    mode: AppMode,
    onSetMode: (AppMode) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = stringResource(mode.label)
    Box {
        IconButton(
            onClick = { open = true },
            Modifier.semantics { stateDescription = current },
        ) { Icon(mode.icon, contentDescription = stringResource(R.string.home_mode)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AppMode.entries.forEach { option ->
                val selected = option == mode
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    leadingIcon = { Icon(option.icon, contentDescription = null) },
                    trailingIcon = if (selected) ({ Icon(MqIcons.Check, contentDescription = null) }) else null,
                    onClick = {
                        open = false
                        if (!selected) onSetMode(option)
                    },
                    modifier =
                        Modifier.semantics {
                            role = Role.RadioButton
                            this.selected = selected
                        },
                )
            }
        }
    }
}

/**
 * The active profile's initial (a person without one); its menu: the name, Switch profile, Servers (accounts and
 * sync, #536), Settings.
 */
@Composable
private fun ProfileMenu(
    profile: String?,
    onOpenProfiles: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Box(
                Modifier
                    .size(MediatheqSizes.profileAvatar)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val description = stringResource(R.string.home_profile_menu)
                if (profile != null) {
                    Text(
                        profile.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { contentDescription = description },
                    )
                } else {
                    Icon(MqIcons.Person, description, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (profile != null) {
                Text(
                    profile,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s),
                )
                MenuEntry(MqIcons.Person, R.string.home_switch_profile) {
                    open = false
                    onOpenProfiles()
                }
            }
            MenuEntry(MqIcons.Lan, R.string.home_servers) {
                open = false
                onOpenServers()
            }
            HorizontalDivider()
            MenuEntry(MqIcons.Settings, R.string.home_settings) {
                open = false
                onOpenSettings()
            }
        }
    }
}

@Composable
private fun MenuEntry(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/**
 * Shuffle all and the mixes (#533): the M3 FAB menu above the bottom bars. The toggle turns into a close button and
 * the items unfold above it; a choice or Back folds it again.
 */
@Composable
internal fun ShuffleFabMenu(onAction: (HomeAction) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(open) { open = false }
    val choose = { action: HomeAction ->
        open = false
        onAction(action)
    }
    val name = stringResource(R.string.home_shuffle_menu)
    FloatingActionButtonMenu(
        expanded = open,
        button = {
            ToggleFloatingActionButton(
                checked = open,
                onCheckedChange = { open = it },
                modifier = Modifier.semantics { contentDescription = name },
            ) {
                Icon(if (open) MqIcons.Close else MqIcons.Shuffle, contentDescription = null)
            }
        },
        modifier = Modifier.bottomBarsPadding(),
    ) {
        FloatingActionButtonMenuItem(
            onClick = { choose(HomeAction.ShuffleAll) },
            icon = { Icon(MqIcons.Shuffle, contentDescription = null) },
            text = { Text(stringResource(R.string.home_shuffle_all)) },
        )
        FloatingActionButtonMenuItem(
            onClick = { choose(HomeAction.PlayMix(Mix.Tracks)) },
            icon = { Icon(MqIcons.QueueMusic, contentDescription = null) },
            text = { Text(stringResource(R.string.home_track_mix)) },
        )
        FloatingActionButtonMenuItem(
            onClick = { choose(HomeAction.PlayMix(Mix.Albums)) },
            icon = { Icon(MqIcons.Library, contentDescription = null) },
            text = { Text(stringResource(R.string.home_album_mix)) },
        )
    }
}
