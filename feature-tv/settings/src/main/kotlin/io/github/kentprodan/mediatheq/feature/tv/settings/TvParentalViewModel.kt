package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import io.github.kentprodan.mediatheq.core.data.ParentalControls
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings -> Parental controls on TV (#434). */
data class TvParentalState(
    val hasPin: Boolean,
    /** Only the main profile (the first account) changes anything; [mainName] says who that is. */
    val isMain: Boolean,
    val mainName: String,
    val profiles: List<Pair<Account, Int?>>,
)

sealed interface TvParentalAction {
    data class SetPin(
        val pin: String?,
    ) : TvParentalAction

    data class SetLimit(
        val accountId: String,
        val limit: Int?,
    ) : TvParentalAction
}

/** The ages a limit can be (owner decision #434: a profile's limit is an age, content rated above it asks the PIN). */
val AGE_LIMITS = listOf(7, 10, 12, 14, 16, 18)

@HiltViewModel
class TvParentalViewModel
    @Inject
    constructor(
        accounts: AccountRepository,
        private val parental: ParentalControls,
    ) : ViewModel() {
        /** `null` until read. */
        val state: StateFlow<TvParentalState?> =
            combine(accounts.accounts, parental.hasPin, parental.limits) { all, hasPin, limits ->
                val profiles = all.filter { it.signedIn }
                val main = profiles.firstOrNull()
                TvParentalState(
                    hasPin = hasPin,
                    isMain = main?.active == true,
                    mainName = main?.userName.orEmpty(),
                    profiles = profiles.map { it to limits[it.id] },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun onAction(action: TvParentalAction) {
            if (state.value?.isMain != true) return
            viewModelScope.launch {
                when (action) {
                    is TvParentalAction.SetPin -> parental.setPin(action.pin)
                    is TvParentalAction.SetLimit -> parental.setLimit(action.accountId, action.limit)
                }
            }
        }
    }
