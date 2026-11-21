package io.github.kentprodan.mediatheq.core.common

/**
 * A D-pad key held to seek on TV (#353), told apart by timing rather than `KeyEvent.repeatCount`: some remotes send a
 * held key as separate presses whose repeat count stays 0. Key-downs in the same direction, on the same [item], less
 * than [holdGapMs] apart are one hold. [press] gives the key-down's number in its hold (0 = a new hold), which the
 * step rules read like a repeat count, or `null` when a seek would come sooner than [seekEveryMs] after the last one.
 */
class HeldKey(
    private val holdGapMs: Long = 300,
    private val seekEveryMs: Long = 150,
) {
    private var forward: Boolean? = null
    private var item: Any? = null
    private var lastPressMs = Long.MIN_VALUE
    private var lastSeekMs = Long.MIN_VALUE
    private var count = 0

    /** A key-down at [atMs] (uptime): its number in the hold, or `null` to skip this one. */
    fun press(
        forward: Boolean,
        atMs: Long,
        item: Any? = null,
    ): Int? {
        val continues =
            forward == this.forward && item == this.item && lastPressMs != Long.MIN_VALUE &&
                atMs - lastPressMs < holdGapMs
        this.forward = forward
        this.item = item
        lastPressMs = atMs
        if (!continues) {
            count = 0
            lastSeekMs = atMs
            return 0
        }
        count++
        if (atMs - lastSeekMs < seekEveryMs) return null
        lastSeekMs = atMs
        return count
    }
}
