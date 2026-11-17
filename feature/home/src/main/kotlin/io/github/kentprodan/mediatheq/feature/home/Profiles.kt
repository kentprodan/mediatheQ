package io.github.kentprodan.mediatheq.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The phone's profiles (#443): the signed-in accounts (#147); another one makes the app restart on it. */
@HiltViewModel
class ProfilesViewModel
    @Inject
    constructor(
        private val accounts: AccountRepository,
    ) : ViewModel() {
        val profiles: StateFlow<List<Account>> =
            accounts.accounts
                .map { all -> all.filter { it.signedIn } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun switchTo(id: String) {
            viewModelScope.launch { accounts.makeActive(id) }
        }
    }

/** The active profile's initial in a circle (#443); a tap or a long press opens the profiles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProfileAvatar(
    name: String,
    onOpen: () -> Unit,
) {
    Box(
        Modifier
            .padding(horizontal = MediatheqSpacing.s)
            .size(MediatheqSizes.profileAvatar)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .combinedClickable(
                onClickLabel = stringResource(R.string.home_profiles_open, name),
                role = Role.Button,
                onLongClick = onOpen,
                onClick = onOpen,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** The profiles sheet (#443). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilesSheet(
    profiles: List<Account>,
    onSwitch: (Account) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) { ProfilesContent(profiles, onSwitch, onAdd) }
}

/** The sheet's content (screenshots render it in place): a row per profile, the active one checked, then Add profile. */
@Composable
fun ProfilesContent(
    profiles: List<Account>,
    onSwitch: (Account) -> Unit,
    onAdd: () -> Unit,
) {
    Column(Modifier.padding(bottom = MediatheqSpacing.l)) {
        Text(
            stringResource(R.string.home_profiles),
            Modifier.padding(horizontal = MediatheqSpacing.l, vertical = MediatheqSpacing.s).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        profiles.forEach { account ->
            ListItem(
                headlineContent = { Text(account.userName) },
                supportingContent = { Text(account.serverName) },
                leadingContent = { Initial(account.userName) },
                trailingContent = {
                    if (account.active) Icon(MqIcons.Check, stringResource(R.string.home_profiles_active))
                },
                modifier = Modifier.clickable(enabled = !account.active, role = Role.Button) { onSwitch(account) },
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.home_profiles_add)) },
            leadingContent = { Icon(MqIcons.Add, contentDescription = null) },
            modifier = Modifier.clickable(role = Role.Button, onClick = onAdd),
        )
    }
}

@Composable
private fun Initial(name: String) {
    Box(
        Modifier
            .size(
                MediatheqSizes.profileAvatar,
            ).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
