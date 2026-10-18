package io.github.kentprodan.mediatheq.core.testing

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A [Clock] that only moves when the test says so. */
class TestClock(
    var now: Instant = Instant.fromEpochSeconds(1_760_000_000),
) : Clock {
    override fun now(): Instant = now

    fun advanceBy(duration: Duration) {
        now += duration
    }
}
