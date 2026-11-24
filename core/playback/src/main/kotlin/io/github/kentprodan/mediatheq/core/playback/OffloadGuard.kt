package io.github.kentprodan.mediatheq.core.playback

/**
 * Notices an offload stream the device's audio HAL broke (#628, engine research 3.3; the idea from PixelPlayer) and
 * turns offload off, so playback goes on as normal PCM:
 * - **Stall:** an offloaded track whose position has not moved [STALL_MS] after play started never really started (an
 *   offload HAL that fails to open). Remembered for this OS build ([onBroken] with `persist`), since it fails the same
 *   way every time; an OS update tries offload again.
 * - **Reset:** buffering within [RESET_MS] of an offloaded track starting to play, not explained by a seek or a
 *   transition just before, is the HAL resetting the offload stream. Off for this session only.
 * Pure: the clock and the scheduler are injected; the caller feeds what the player and its AudioTracks do.
 */
internal class OffloadGuard(
    private val now: () -> Long,
    private val schedule: (Long, () -> Unit) -> () -> Unit,
    private val position: () -> Long,
    private val onBroken: (persist: Boolean) -> Unit,
) {
    /** The open AudioTrack is offloaded. */
    private var offloaded = false
    private var playingSince: Long? = null
    private var explainedUntil = 0L
    private var cancelCheck: () -> Unit = {}
    private var broken = false

    fun onAudioTrackOpened(offload: Boolean) {
        offloaded = offload
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        cancelCheck()
        if (!isPlaying) {
            playingSince = null
            return
        }
        playingSince = now()
        if (!offloaded || broken) return
        val start = position()
        cancelCheck =
            schedule(STALL_MS) {
                if (offloaded && !broken && position() - start < MOVED_MS) fail(persist = true)
            }
    }

    /** A seek or a track change: the buffering right after it is expected. */
    fun onExpectedBuffering() {
        explainedUntil = now() + EXPLAINED_MS
    }

    fun onBuffering() {
        val since = playingSince ?: return
        val time = now()
        if (offloaded && !broken && time - since < RESET_MS && time >= explainedUntil) fail(persist = false)
    }

    fun release() = cancelCheck()

    private fun fail(persist: Boolean) {
        broken = true
        onBroken(persist)
    }

    private companion object {
        /** A local file plays within ~100 ms on a working output; 1.5 s still frozen means it never started. */
        const val STALL_MS = 1_500L

        /** Less than this much position in [STALL_MS] counts as not moving. */
        const val MOVED_MS = 200L
        const val RESET_MS = 500L
        const val EXPLAINED_MS = 1_000L
    }
}
