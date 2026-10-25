package io.github.kentprodan.mediatheq.core.data

import androidx.sqlite.SQLiteException
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult

/** A database failure during an edit becomes [MqError.Storage]. */
internal inline fun <T> storageSafe(block: () -> MqResult<T>): MqResult<T> =
    try {
        block()
    } catch (_: SQLiteException) {
        MqResult.Failure(MqError.Storage)
    }
