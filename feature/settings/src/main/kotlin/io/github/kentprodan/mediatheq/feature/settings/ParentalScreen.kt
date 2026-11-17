package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import io.github.kentprodan.mediatheq.core.data.ParentalControls
import io.github.kentprodan.mediatheq.core.designsystem.component.MqPinDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings -> Video -> Parental controls on the phone (#443), as on the TV (#434). */
data class ParentalState(
    val hasPin: Boolean,
    /** Only the main profile (the first account) changes anything; [mainName] says who that is. */
    val isMain: Boolean,
    val mainName: String,
    val profiles: List<Pair<Account, Int?>>,
    /** With a PIN set the page opens only once it is entered. */
    val unlocked: Boolean,
)

/** The ages a limit can be (owner decision #434). */
val PARENTAL_AGES = listOf(7, 10, 12, 14, 16, 18)

@HiltViewModel
class ParentalViewModel
    @Inject
    constructor(
        accounts: AccountRepository,
        private val parental: ParentalControls,
    ) : ViewModel() {
        private val entered = MutableStateFlow(false)
        private val wrong = MutableStateFlow(false)
        val wrongPin: StateFlow<Boolean> = wrong

        /** `null` until read. */
        val state: StateFlow<ParentalState?> =
            combine(accounts.accounts, parental.hasPin, parental.limits, entered) { all, hasPin, limits, ok ->
                val profiles = all.filter { it.signedIn }
                val main = profiles.firstOrNull()
                ParentalState(
                    hasPin = hasPin,
                    isMain = main?.active == true,
                    mainName = main?.userName.orEmpty(),
                    profiles = profiles.map { it to limits[it.id] },
                    unlocked = !hasPin || ok,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun enter(pin: String) {
            viewModelScope.launch {
                if (parental.checkPin(pin)) entered.value = true else wrong.value = true
            }
        }

        fun edited() {
            wrong.value = false
        }

        fun setPin(pin: String?) {
            if (state.value?.isMain != true) return
            viewModelScope.launch {
                parental.setPin(pin)
                entered.value = true
            }
        }

        fun setLimit(
            accountId: String,
            limit: Int?,
        ) {
            if (state.value?.isMain != true) return
            viewModelScope.launch { parental.setLimit(accountId, limit) }
        }
    }

@Composable
internal fun ParentalRoute(
    onBack: () -> Unit,
    viewModel: ParentalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wrong by viewModel.wrongPin.collectAsStateWithLifecycle()
    val current = state ?: return
    if (!current.unlocked) {
        MqPinDialog(
            title = stringResource(R.string.settings_parental_enter_pin),
            label = stringResource(R.string.settings_parental_pin),
            confirmLabel = stringResource(R.string.settings_ok),
            dismissLabel = stringResource(R.string.settings_cancel),
            onSubmit = viewModel::enter,
            onDismiss = onBack,
            error = if (wrong) stringResource(R.string.settings_parental_wrong_pin) else null,
            onEdit = viewModel::edited,
        )
        return
    }
    ParentalScreen(current, viewModel::setPin, viewModel::setLimit, onBack)
}

/**
 * The PIN and each profile's age limit (#443): content rated above a profile's limit asks for the PIN on that profile.
 * Another profile than the main one sees the page but cannot change it.
 */
@Composable
fun ParentalScreen(
    state: ParentalState,
    onSetPin: (String?) -> Unit,
    onSetLimit: (String, Int?) -> Unit,
    onBack: () -> Unit,
) {
    var newPin by rememberSaveable { mutableStateOf(false) }
    var limitOf by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsScaffold(stringResource(R.string.settings_parental), onBack) {
        item(key = "intro") {
            Text(
                if (state.isMain) {
                    stringResource(R.string.settings_parental_intro)
                } else {
                    stringResource(R.string.settings_parental_main_only, state.mainName)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "pin") {
            Group(R.string.settings_parental_pin) {
                item { shapes ->
                    SegmentedListItem(
                        onClick = { newPin = true },
                        shapes = shapes,
                        enabled = state.isMain,
                        colors = settingsColors(),
                    ) {
                        val change = R.string.settings_parental_change_pin
                        Text(stringResource(if (state.hasPin) change else R.string.settings_parental_set_pin))
                    }
                }
                if (state.hasPin) {
                    item { shapes ->
                        SegmentedListItem(
                            onClick = { onSetPin(null) },
                            shapes = shapes,
                            enabled = state.isMain,
                            colors = settingsColors(),
                        ) { Text(stringResource(R.string.settings_parental_remove_pin)) }
                    }
                }
            }
        }
        item(key = "limits") {
            Group(R.string.settings_parental_limits) {
                state.profiles.forEach { (account, limit) ->
                    item { shapes ->
                        SegmentedListItem(
                            onClick = { limitOf = account.id },
                            shapes = shapes,
                            enabled = state.isMain,
                            colors = settingsColors(),
                            supportingContent = { Text(limitLabel(limit)) },
                        ) { Text(account.userName) }
                    }
                }
            }
        }
    }
    if (newPin) {
        MqPinDialog(
            title = stringResource(R.string.settings_parental_new_pin),
            label = stringResource(R.string.settings_parental_pin),
            confirmLabel = stringResource(R.string.settings_ok),
            dismissLabel = stringResource(R.string.settings_cancel),
            onSubmit = {
                onSetPin(it)
                newPin = false
            },
            onDismiss = { newPin = false },
        )
    }
    limitOf?.let { id ->
        val name =
            state.profiles
                .firstOrNull { it.first.id == id }
                ?.first
                ?.userName
                .orEmpty()
        ChoiceDialog(
            title = name,
            options = listOf<Int?>(null) + PARENTAL_AGES,
            selected = state.profiles.firstOrNull { it.first.id == id }?.second,
            label = { limitLabel(it) },
            onSelect = {
                onSetLimit(id, it)
                limitOf = null
            },
            onDismiss = { limitOf = null },
        )
    }
}

@Composable
private fun limitLabel(limit: Int?): String =
    if (limit == null) {
        stringResource(R.string.settings_parental_no_limit)
    } else {
        stringResource(R.string.settings_parental_up_to, limit)
    }
