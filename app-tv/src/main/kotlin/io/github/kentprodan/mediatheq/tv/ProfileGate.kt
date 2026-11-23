package io.github.kentprodan.mediatheq.tv

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** When "Who's watching?" shows (#433). */
enum class ProfilePicker {
    Hidden,

    /** The TV was not used for a while: shown when there is more than one profile. */
    Due,

    /** Asked for from Settings: shown even with one profile (to add one). */
    Opened,
}

/**
 * Owner decisions: the picker comes back after the app was not used for 4 hours (#433), and always after the app was
 * closed with Back (#572); a short trip out with the remote's Home button does not ask.
 */
fun profilePickerDue(
    lastUsed: Instant?,
    now: Instant,
    closedWithBack: Boolean = false,
): Boolean = closedWithBack || lastUsed == null || now - lastUsed >= PICKER_IDLE

private val PICKER_IDLE = 4.hours

/**
 * Remembers when the TV app was last in use (in a small preferences file, so it survives the process) and decides on
 * each return to the foreground whether "Who's watching?" is due (#433).
 */
@Singleton
class ProfileGate
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val clock: Clock,
    ) {
        private val prefs by lazy { context.getSharedPreferences(PROFILES_FILE, Context.MODE_PRIVATE) }
        private val mutableState = MutableStateFlow(ProfilePicker.Hidden)
        val state: StateFlow<ProfilePicker> = mutableState.asStateFlow()

        fun onForeground() {
            val last = prefs.getLong(LAST_USED, 0L).takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds)
            val closed = prefs.getBoolean(CLOSED_WITH_BACK, false)
            if (mutableState.value == ProfilePicker.Hidden && profilePickerDue(last, clock.now(), closed)) {
                mutableState.value = ProfilePicker.Due
            }
            if (closed) prefs.edit { putBoolean(CLOSED_WITH_BACK, false) }
        }

        /** The app was closed with Back (#572): the next start asks again, however soon. */
        fun closedWithBack() = prefs.edit(commit = true) { putBoolean(CLOSED_WITH_BACK, true) }

        fun onBackground() = used()

        /** Written at once: picking another profile restarts the app right after. */
        fun used() = prefs.edit(commit = true) { putLong(LAST_USED, clock.now().toEpochMilliseconds()) }

        fun open() {
            mutableState.value = ProfilePicker.Opened
        }

        fun close() {
            used()
            mutableState.value = ProfilePicker.Hidden
        }

        private companion object {
            const val PROFILES_FILE = "profiles"
            const val LAST_USED = "last_used"
            const val CLOSED_WITH_BACK = "closed_with_back"
        }
    }
