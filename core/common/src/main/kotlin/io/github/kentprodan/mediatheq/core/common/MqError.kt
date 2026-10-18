package io.github.kentprodan.mediatheq.core.common

/** The closed set of errors the app shows or reacts to (architecture skill, section 4). */
sealed interface MqError {
    data object Network : MqError

    data object Unauthorized : MqError

    data object Forbidden : MqError

    data object NotFound : MqError

    data class Server(
        val code: Int,
    ) : MqError

    data class Client(
        val code: Int,
        val detail: String? = null,
    ) : MqError

    data object Storage : MqError

    data class Unknown(
        val cause: Throwable,
    ) : MqError
}
