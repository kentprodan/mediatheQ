package io.github.kentprodan.mediatheq.core.server

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.UserData

/** One message of [LiveEvents]. */
sealed interface LiveEvent {
    /** The connection is open. */
    data object Connected : LiveEvent

    /** The signed-in user's data changed on these items (in this app or another client). */
    data class UserDataChanged(
        val changes: Map<MediaId, UserData>,
    ) : LiveEvent

    /** Items were added, changed or removed in the library. */
    data object LibraryChanged : LiveEvent

    /** The connection ended with [error] (`Unauthorized`: the token is no longer valid). Always the last event. */
    data class Closed(
        val error: MqError,
    ) : LiveEvent
}
