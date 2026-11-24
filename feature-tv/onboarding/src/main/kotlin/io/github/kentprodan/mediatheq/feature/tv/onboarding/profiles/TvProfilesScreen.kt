package io.github.kentprodan.mediatheq.feature.tv.onboarding.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.ui.tv.MqTvAvatar
import io.github.kentprodan.mediatheq.feature.tv.onboarding.R

@Composable
internal fun TvProfilesRoute(
    onUse: () -> Unit,
    onClose: () -> Unit,
    onAdd: (baseUrl: String, serverName: String) -> Unit,
    viewModel: TvProfilesViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    profiles?.let { list ->
        TvProfilesScreen(
            list,
            onPick = { account ->
                onUse()
                viewModel.pick(account.id, then = onClose)
            },
            onAdd = { list.firstOrNull { it.active }?.let { onAdd(it.baseUrl, it.serverName) } },
        )
    }
}

/**
 * "Who's watching?" (#433): a round picture per profile (#621, the guideline's single-pane profile picker), the active
 * one focused first and growing with a ring, its name under it; Add profile as a button below.
 */
@Composable
fun TvProfilesScreen(
    profiles: List<Account>,
    onPick: (Account) -> Unit,
    onAdd: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val focused = profiles.indexOfFirst { it.active }.coerceAtLeast(0)
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.tv_profiles_title),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.Top,
            ) {
                itemsIndexed(profiles, key = { _, it -> it.id }) { index, account ->
                    val start = if (index == focused) Modifier.focusRequester(first) else Modifier
                    Profile(account, { onPick(account) }, start)
                }
            }
            OutlinedButton(
                onAdd,
                if (profiles.isEmpty()) Modifier.focusRequester(first) else Modifier,
            ) {
                Icon(MqTvIcons.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tv_profiles_add))
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        runCatching { first.requestFocus() }
    }
}

/** One profile: its round Jellyfin picture (#556, the initial without one), its name and server under it. */
@Composable
private fun Profile(
    account: Account,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = FOCUSED_SCALE),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(3.dp, scheme.onSurface), shape = CircleShape),
                ),
        ) {
            MqTvAvatar(account.userName, account.imageUrl, AVATAR, MaterialTheme.typography.displaySmall)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                account.userName,
                style = if (focused) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (focused) scheme.onSurface else scheme.onSurfaceVariant,
            )
            Text(
                account.serverName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val AVATAR = 120.dp
private const val FOCUSED_SCALE = 1.15f
