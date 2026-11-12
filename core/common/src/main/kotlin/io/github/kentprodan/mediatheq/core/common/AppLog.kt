package io.github.kentprodan.mediatheq.core.common

/**
 * The app's own log (#169): one line per event, kept on the phone in a rotating file the user can export. Never
 * blocks and never throws; every message and error is [redact]ed before it is written. Not for secrets anyway: no
 * token, password or URL with a query string goes into a message.
 */
interface AppLog {
    fun info(
        tag: String,
        message: String,
    )

    fun warn(
        tag: String,
        message: String,
        error: Throwable? = null,
    )

    companion object {
        /** Logs nothing: for helpers built without the app's log (tests). */
        val None: AppLog =
            object : AppLog {
                override fun info(
                    tag: String,
                    message: String,
                ) = Unit

                override fun warn(
                    tag: String,
                    message: String,
                    error: Throwable?,
                ) = Unit
            }
    }
}
