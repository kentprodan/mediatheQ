package io.github.kentprodan.mediatheq.feature.tv.onboarding.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCard
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

/** "Who's watching?" (#433): one card per profile (its initial), the active one focused first, then Add profile. */
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
                style = MaterialTheme.typography.displaySmall,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                items(profiles, key = { it.id }) { account ->
                    val index = profiles.indexOf(account)
                    MqTvCard(
                        account.userName,
                        { onPick(account) },
                        if (index == focused) Modifier.focusRequester(first) else Modifier,
                        subtitle = account.serverName,
                    ) { Avatar(account.userName.take(1).uppercase()) }
                }
                item(key = "add") {
                    MqTvCard(
                        stringResource(R.string.tv_profiles_add),
                        onAdd,
                        if (profiles.isEmpty()) Modifier.focusRequester(first) else Modifier,
                    ) { Avatar(null) }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        runCatching { first.requestFocus() }
    }
}

@Composable
private fun Avatar(initial: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (initial != null) {
                Text(
                    initial,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Icon(MqTvIcons.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}
