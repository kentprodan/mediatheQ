package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialog
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderHandle
import io.github.kentprodan.mediatheq.core.designsystem.component.mqReorderItem
import io.github.kentprodan.mediatheq.core.designsystem.component.rememberMqReorderState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.ui.OverflowMenu

@Composable
internal fun ProvidersRoute(
    onAdd: () -> Unit,
    onSignInAgain: (Account) -> Unit,
    onBack: () -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProvidersScreen(state, viewModel::onAction, onAdd, onSignInAgain, onBack)
}

/**
 * Settings -> Manage media providers (#147): the accounts in order (drag handle, server icon, name, user and address,
 * the active one marked, a menu), then "Add media provider". Switching and removing ask first.
 */
@Composable
fun ProvidersScreen(
    state: ProvidersUiState,
    onAction: (ProvidersAction) -> Unit,
    onAdd: () -> Unit,
    onSignInAgain: (Account) -> Unit,
    onBack: () -> Unit,
) {
    val content = state as? ProvidersUiState.Content
    val accounts = content?.accounts.orEmpty()
    // The order shown while dragging; saved on drop.
    var rows by remember(accounts) { mutableStateOf(accounts) }
    val listState = rememberLazyListState()
    val reorder =
        rememberMqReorderState(
            listState,
            onMove = { from, to -> rows = rows.toMutableList().apply { add(to, removeAt(from)) } },
            onDrop = { _, _ -> if (rows != accounts) onAction(ProvidersAction.Reorder(rows.map { it.id })) },
            // The accounts come first; the button after them is no drop target.
            canDropOn = { key -> rows.any { it.id == key } },
        )
    // Dialogs by account id (saveable).
    var switching by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    SettingsScaffold(stringResource(R.string.settings_providers), onBack, listState) {
        content ?: return@SettingsScaffold
        itemsIndexed(rows, key = { _, account -> account.id }) { index, account ->
            ProviderRow(
                account = account,
                index = index,
                count = rows.size,
                onMove = { to ->
                    onAction(
                        ProvidersAction.Reorder(rows.toMutableList().apply { add(to, removeAt(index)) }.map { it.id }),
                    )
                },
                menu = { close ->
                    ProviderMenuItems(
                        account,
                        onMakeActive = { switching = account.id },
                        onEdit = { onAction(ProvidersAction.StartEdit(account.id)) },
                        onSignInAgain = { onSignInAgain(account) },
                        onSync = { onAction(ProvidersAction.SyncNow) },
                        onRemove = { removing = account.id },
                        close = close,
                    )
                },
                modifier = Modifier.mqReorderItem(reorder, account.id),
                handle = Modifier.mqReorderHandle(reorder, account.id),
            )
        }
        item(key = "add") {
            FilledTonalButton(onClick = { picking = true }, Modifier.fillMaxWidth()) {
                Icon(MqIcons.Add, contentDescription = null)
                Text(stringResource(R.string.settings_providers_add), Modifier.padding(start = MediatheqSpacing.s))
            }
        }
    }
    accounts.firstOrNull { it.id == switching }?.let { account ->
        SwitchAccountDialog(
            account.serverName,
            { onAction(ProvidersAction.MakeActive(account.id)) },
        ) { switching = null }
    }
    accounts.firstOrNull { it.id == removing }?.let { account ->
        RemoveAccountDialog(
            account.serverName,
            { onAction(ProvidersAction.Remove(account.id, it)) },
        ) { removing = null }
    }
    if (picking) {
        ProviderPickerDialog(
            onPick = {
                picking = false
                onAdd()
            },
        ) { picking = false }
    }
    content?.edit?.let { edit ->
        accounts.firstOrNull { it.id == edit.id }?.let { account ->
            EditAddressDialog(account.baseUrl, edit, { onAction(ProvidersAction.EditAddress(account.id, it)) }) {
                onAction(ProvidersAction.DismissEdit)
            }
        }
    }
}

/** One account: drag handle, server icon, name, "user · address", Active; a menu; Move up / Move down for TalkBack. */
@Composable
internal fun ProviderRow(
    account: Account,
    index: Int,
    count: Int,
    onMove: (to: Int) -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    handle: Modifier = Modifier,
) {
    val moveUp = stringResource(R.string.settings_move_up)
    val moveDown = stringResource(R.string.settings_move_down)
    Surface(
        modifier.fillMaxWidth().semantics {
            customActions =
                listOfNotNull(
                    CustomAccessibilityAction(moveUp) {
                        onMove(index - 1)
                        true
                    }.takeIf { index > 0 },
                    CustomAccessibilityAction(moveDown) {
                        onMove(index + 1)
                        true
                    }.takeIf { index < count - 1 },
                )
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.heightIn(min = MediatheqSpacing.xxl).padding(start = MediatheqSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
        ) {
            // A drag handle, not a button: the custom actions move the row for TalkBack.
            Icon(MqIcons.DragHandle, contentDescription = null, handle)
            Icon(MqIcons.Lan, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(vertical = MediatheqSpacing.s)) {
                Text(account.serverName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_providers_account_line, account.userName, account.baseUrl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (account.active) {
                    Text(
                        stringResource(R.string.settings_providers_active),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            OverflowMenu(stringResource(R.string.settings_providers_menu, account.serverName)) { close -> menu(close) }
        }
    }
}

/** The account menu: Make active (not on the active one), Edit address, Sign in again, Sync now (active), remove. */
@Composable
internal fun ProviderMenuItems(
    account: Account,
    onMakeActive: () -> Unit,
    onEdit: () -> Unit,
    onSignInAgain: () -> Unit,
    onSync: () -> Unit,
    onRemove: () -> Unit,
    close: () -> Unit,
) {
    @Composable
    fun item(
        label: Int,
        onClick: () -> Unit,
        color: Color = Color.Unspecified,
    ) = DropdownMenuItem(
        text = { Text(stringResource(label), color = color) },
        onClick = {
            onClick()
            close()
        },
    )
    if (!account.active) item(R.string.settings_providers_make_active, onMakeActive)
    item(R.string.settings_providers_edit_address, onEdit)
    item(R.string.settings_providers_sign_in_again, onSignInAgain)
    if (account.active) item(R.string.settings_providers_sync_now, onSync)
    item(R.string.settings_providers_remove, onRemove, MaterialTheme.colorScheme.error)
}

@Composable
private fun SwitchAccountDialog(
    name: String,
    onSwitch: () -> Unit,
    onDismiss: () -> Unit,
) {
    MqDialog(
        title = stringResource(R.string.settings_providers_switch_title, name),
        confirmLabel = stringResource(R.string.settings_providers_switch_confirm),
        onConfirm = {
            onSwitch()
            onDismiss()
        },
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) { SwitchAccountBody() }
}

@Composable
internal fun SwitchAccountBody() {
    Text(stringResource(R.string.settings_providers_switch_message))
}

@Composable
private fun RemoveAccountDialog(
    name: String,
    onRemove: (deleteDownloads: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteDownloads by rememberSaveable { mutableStateOf(true) }
    MqDialog(
        title = stringResource(R.string.settings_providers_remove_title, name),
        confirmLabel = stringResource(R.string.settings_providers_remove_confirm),
        onConfirm = {
            onRemove(deleteDownloads)
            onDismiss()
        },
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) { RemoveAccountBody(deleteDownloads) { deleteDownloads = it } }
}

/** What removing deletes, and the choice to delete the downloads too (on by default: kept ones show only again when the account is added back). */
@Composable
internal fun RemoveAccountBody(
    deleteDownloads: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column {
        Text(stringResource(R.string.settings_providers_remove_message))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_providers_remove_downloads)) },
            leadingContent = { Checkbox(checked = deleteDownloads, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.toggleable(deleteDownloads, role = Role.Checkbox) { onChange(it) },
        )
    }
}

@Composable
private fun ProviderPickerDialog(
    onPick: () -> Unit,
    onDismiss: () -> Unit,
) {
    MqDialog(
        title = stringResource(R.string.settings_providers_add),
        confirmLabel = null,
        onConfirm = {},
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
    ) { ProviderPickerBody(onPick) }
}

/** The providers the app supports: Jellyfin for now (others appear when their providers exist). */
@Composable
internal fun ProviderPickerBody(onPick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_providers_jellyfin)) },
        leadingContent = { Icon(MqIcons.Lan, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onPick),
    )
}

@Composable
private fun EditAddressDialog(
    current: String,
    edit: AddressEdit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(current) }
    MqDialog(
        title = stringResource(R.string.settings_providers_edit_address),
        confirmLabel = stringResource(R.string.settings_providers_save),
        onConfirm = { onSave(address) },
        dismissLabel = stringResource(R.string.settings_cancel),
        onDismiss = onDismiss,
        confirmEnabled = !edit.checking && address.isNotBlank(),
    ) { EditAddressBody(address, edit) { address = it } }
}

/** The address field; the error under it says why the address was not saved. */
@Composable
internal fun EditAddressBody(
    address: String,
    edit: AddressEdit,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = address,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.settings_providers_address)) },
        singleLine = true,
        isError = edit.error != null,
        supportingText = edit.error?.let { error -> { Text(stringResource(error.message)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

private val AddressError.message: Int
    get() =
        when (this) {
            AddressError.InvalidAddress -> R.string.settings_providers_error_invalid
            AddressError.CleartextNotAllowed -> R.string.settings_providers_error_cleartext
            AddressError.Unreachable -> R.string.settings_providers_error_unreachable
            AddressError.OtherServer -> R.string.settings_providers_error_other_server
            AddressError.Unsupported -> R.string.settings_providers_error_unsupported
            AddressError.Other -> R.string.settings_providers_error_other
        }
