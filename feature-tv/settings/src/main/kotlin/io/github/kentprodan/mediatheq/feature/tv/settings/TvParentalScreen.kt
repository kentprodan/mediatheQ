package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.tv.material3.Text
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvListItem
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenu
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvMenuEntry
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvPinDialog
import io.github.kentprodan.mediatheq.core.designsystem.tv.focusMemory
import io.github.kentprodan.mediatheq.core.designsystem.tv.rememberFocusMemory

@Composable
fun TvParentalRoute(viewModel: TvParentalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state?.let { TvParentalScreen(it, viewModel::onAction) }
}

/**
 * Settings -> Parental controls (#434): the PIN, then each profile's age limit. Another profile than the main one sees
 * the settings but cannot change them. Limits apply once a PIN is set.
 */
@Composable
fun TvParentalScreen(
    state: TvParentalState,
    onAction: (TvParentalAction) -> Unit,
) {
    var newPin by remember { mutableStateOf(false) }
    var limitOf by remember { mutableStateOf<Account?>(null) }
    val focus = rememberFocusMemory()
    val hint =
        if (state.isMain) {
            stringResource(R.string.tv_parental_hint)
        } else {
            stringResource(R.string.tv_parental_main_only, state.mainName)
        }
    SettingsPage(stringResource(R.string.tv_settings_parental), hint) {
        item {
            SettingRow(
                SettingEntry(
                    "pin",
                    stringResource(if (state.hasPin) R.string.tv_parental_change_pin else R.string.tv_parental_set_pin),
                    enabled = state.isMain,
                ) { newPin = true },
                Modifier.focusMemory(focus, "pin"),
            )
        }
        if (state.hasPin) {
            item {
                SettingRow(
                    SettingEntry("removePin", stringResource(R.string.tv_parental_remove_pin), enabled = state.isMain) {
                        onAction(TvParentalAction.SetPin(null))
                    },
                    Modifier.focusMemory(focus, "removePin"),
                )
            }
        }
        items(state.profiles, key = { it.first.id }) { (account, limit) ->
            SettingRow(
                SettingEntry(account.id, account.userName, limitLabel(limit), enabled = state.isMain) {
                    limitOf = account
                },
                Modifier.focusMemory(focus, account.id),
            )
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        focus.restore("pin")
    }
    if (newPin) {
        MqTvPinDialog(
            stringResource(R.string.tv_parental_new_pin),
            stringResource(R.string.tv_parental_pin_label),
            onSubmit = {
                onAction(TvParentalAction.SetPin(it))
                newPin = false
            },
            onDismiss = { newPin = false },
        )
    }
    limitOf?.let { account ->
        MqTvMenu(
            account.userName,
            (listOf(null) + AGE_LIMITS).map { age ->
                MqTvMenuEntry(limitLabel(age), null) { onAction(TvParentalAction.SetLimit(account.id, age)) }
            },
            onDismiss = { limitOf = null },
        )
    }
}

@Composable
private fun limitLabel(limit: Int?): String =
    if (limit ==
        null
    ) {
        stringResource(R.string.tv_parental_no_limit)
    } else {
        stringResource(R.string.tv_parental_limit, limit)
    }
