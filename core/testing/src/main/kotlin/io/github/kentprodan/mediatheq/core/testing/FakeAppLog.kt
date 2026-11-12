package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.AppLog

/** [AppLog] in memory: each call as "I Tag: message" or "W Tag: message | cause", redacted like the file. */
class FakeAppLog : AppLog {
    val lines = mutableListOf<String>()

    override fun info(
        tag: String,
        message: String,
    ) {
        lines +=
            io.github.kentprodan.mediatheq.core.common
                .redact("I $tag: $message")
    }

    override fun warn(
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        val cause = error?.let { " | ${io.github.kentprodan.mediatheq.core.common.causeChain(it)}" }.orEmpty()
        lines +=
            io.github.kentprodan.mediatheq.core.common
                .redact("W $tag: $message$cause")
    }
}
