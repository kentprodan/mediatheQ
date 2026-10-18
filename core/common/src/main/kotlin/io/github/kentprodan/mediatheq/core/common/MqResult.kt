package io.github.kentprodan.mediatheq.core.common

/** Result of a one-shot operation: a value or an [MqError]. */
sealed interface MqResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : MqResult<T>

    data class Failure(
        val error: MqError,
    ) : MqResult<Nothing>
}

/** Transforms the value of a [MqResult.Success]; failures pass through. */
inline fun <T, R> MqResult<T>.map(transform: (T) -> R): MqResult<R> =
    when (this) {
        is MqResult.Success -> MqResult.Success(transform(value))
        is MqResult.Failure -> this
    }

/** The value, or `null` on failure. */
fun <T> MqResult<T>.getOrNull(): T? = (this as? MqResult.Success)?.value

/** The error, or `null` on success. */
fun MqResult<*>.errorOrNull(): MqError? = (this as? MqResult.Failure)?.error
