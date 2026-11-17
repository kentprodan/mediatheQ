package io.github.kentprodan.mediatheq.feature.tv.onboarding.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.Account
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Who's watching?" (#433): the signed-in accounts are the profiles; picking another one restarts the app on it. */
@HiltViewModel
class TvProfilesViewModel
    @Inject
    constructor(
        private val accounts: AccountRepository,
    ) : ViewModel() {
        /** `null` until read. */
        val profiles: StateFlow<List<Account>?> =
            accounts.accounts
                .map { all -> all.filter { it.signedIn } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Makes [id] the active profile (another one restarts the app), then [then]. */
        fun pick(
            id: String,
            then: () -> Unit,
        ) {
            viewModelScope.launch {
                accounts.makeActive(id)
                then()
            }
        }
    }
