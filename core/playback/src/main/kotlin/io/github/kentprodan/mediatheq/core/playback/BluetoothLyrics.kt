package io.github.kentprodan.mediatheq.core.playback

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The lyrics line to show as the title at [position]; `null` before the first line, on an empty line, unsynced. */
internal fun lyricsTitle(
    lyrics: Lyrics,
    position: Duration,
): String? =
    lyrics.lines
        .getOrNull(lyrics.currentLineIndex(position))
        ?.text
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * How long until the title is looked at again: at the next line's start, but never sooner than [MIN_LYRICS_GAP]
 * after the last change ([sinceLastUpdate]); `null` after the last line and for unsynced lyrics.
 */
internal fun nextLyricsUpdateDelay(
    lyrics: Lyrics,
    position: Duration,
    sinceLastUpdate: Duration,
): Duration? {
    if (!lyrics.isSynced) return null
    val next = lyrics.lines.firstOrNull { it.start!! > position } ?: return null
    return maxOf(next.start!! - position, MIN_LYRICS_GAP - sinceLastUpdate)
}

/** Bluetooth displays and the session get at most one title change in this time. */
internal val MIN_LYRICS_GAP = 500.milliseconds

/**
 * Bluetooth lyrics (#143, ADR 0034): while [enabled] and the playing track has synced lyrics, [onTitle] gets the line
 * sung now (the session's player shows it as the title), and `null` for the real title: before the first line, on an
 * empty line, on a track change, without synced lyrics, when loading fails or the setting goes off. The line always
 * comes from the player's position when a timer fires, never from the timer itself.
 */
@UnstableApi
internal class BluetoothLyrics(
    private val player: Player,
    private val enabled: () -> Boolean,
    /** The track's lyrics, `null` when it has none or they cannot be loaded. */
    private val load: suspend (MediaId) -> Lyrics?,
    /** On the player's thread. */
    private val scope: CoroutineScope,
    /** Runs a block after a delay; the result cancels it. */
    private val schedule: (Long, () -> Unit) -> () -> Unit,
    private val now: () -> Long = SystemClock::uptimeMillis,
    private val onTitle: (String?) -> Unit,
) : Player.Listener {
    private var trackId: MediaId? = null
    private var lyrics: Lyrics? = null
    private var loading: Job? = null
    private var cancelTimer: (() -> Unit)? = null
    private var shown: String? = null
    private var lastChange: Long? = null

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) follow()
        if (events.containsAny(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
            )
        ) {
            update()
        }
    }

    /** The setting changed. */
    fun onSettingChanged() = follow()

    fun release() {
        loading?.cancel()
        stopTimer()
    }

    /** Loads the lyrics of the track that plays now, once per track; the real title until they are there. */
    private fun follow() {
        val id = if (enabled()) player.currentMediaItem?.queuedTrackId() else null
        if (id == trackId) return
        trackId = id
        lyrics = null
        loading?.cancel()
        stopTimer()
        show(null)
        if (id == null) return
        loading =
            scope.launch {
                val loaded = load(id)?.takeIf { it.isSynced }
                // A fast skip: these are another track's.
                if (trackId != id) return@launch
                lyrics = loaded
                update()
            }
    }

    private fun update() {
        stopTimer()
        val lyrics = lyrics ?: return
        val position = player.currentPosition.milliseconds
        val title = lyricsTitle(lyrics, position)
        val sinceChange = lastChange?.let { (now() - it).milliseconds } ?: MIN_LYRICS_GAP
        if (title != shown && sinceChange < MIN_LYRICS_GAP) {
            // Too soon after the last change: look again when the gap has passed.
            cancelTimer = schedule((MIN_LYRICS_GAP - sinceChange).inWholeMilliseconds, ::update)
            return
        }
        show(title)
        if (!player.isPlaying) return
        val sinceLast = lastChange?.let { (now() - it).milliseconds } ?: MIN_LYRICS_GAP
        nextLyricsUpdateDelay(lyrics, position, sinceLast)?.let {
            cancelTimer = schedule(it.inWholeMilliseconds, ::update)
        }
    }

    private fun show(title: String?) {
        if (title == shown) return
        shown = title
        lastChange = now()
        onTitle(title)
    }

    private fun stopTimer() {
        cancelTimer?.invoke()
        cancelTimer = null
    }
}
