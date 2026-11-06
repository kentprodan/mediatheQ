package io.github.kentprodan.mediatheq.core.playback

import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// The queue and transport rules of Settings -> Playback -> Playback (#138), pure: QueuePlayer applies them to the
// player, so the app, the notification, headsets and Android Auto behave alike. Positions are places in play order.

/** Previous restarts the track when it played longer than this (Media3's own default). */
internal val REWIND_THRESHOLD = 3.seconds

/** What Previous does. */
internal sealed interface PreviousAction {
    /** Back to the start of the current track. */
    data object Rewind : PreviousAction

    /** To the entry at this place in play order. */
    data class To(
        val place: Int,
    ) : PreviousAction
}

/**
 * Previous at [place] of [count] after [position] of the track: a rewind past [REWIND_THRESHOLD] when [rewindFirst];
 * otherwise the entry before, the last one from the first when [wrap] (circular, or repeat all), else a rewind.
 */
internal fun previousAction(
    position: Duration,
    place: Int,
    count: Int,
    rewindFirst: Boolean,
    wrap: Boolean,
): PreviousAction =
    when {
        rewindFirst && position > REWIND_THRESHOLD -> PreviousAction.Rewind
        place > 0 -> PreviousAction.To(place - 1)
        wrap && count > 1 -> PreviousAction.To(count - 1)
        else -> PreviousAction.Rewind
    }

/** Next from [place] of [count]: the entry after, the first one from the last when [wrap]; `null` = nothing. */
internal fun nextPlace(
    place: Int,
    count: Int,
    wrap: Boolean,
): Int? =
    when {
        place < count - 1 -> place + 1
        wrap && count > 1 -> 0
        else -> null
    }

/**
 * How many entries after [current] (in [order], play order) were added with Play next and still wait: with "Preserve
 * Play next order" a new one goes after them, Spotify-style.
 */
internal fun playedNextRun(
    order: List<Int>,
    current: Int,
    isPlayedNext: (Int) -> Boolean,
): Int {
    val after = order.drop(order.indexOf(current) + 1)
    return after.takeWhile(isPlayedNext).size
}

/** What weighted shuffle knows of one queue entry (#138), from its media item extras. */
internal data class ShuffleWeight(
    val lastPlayedAt: Instant? = null,
    val isFavorite: Boolean = false,
    /** 1-10, `null` when unrated. */
    val halfStars: Int? = null,
) {
    /**
     * How likely the entry is to come early: played today 0.2, rising to 1 after [RECENT] (never played 1); a favorite
     * x1.5; a rating from x0.75 (half a star) to x1.25 (five stars), unrated x1. Always above 0.
     */
    fun weight(now: Instant): Double {
        val recency =
            lastPlayedAt?.let {
                val age = ((now - it) / RECENT).coerceIn(0.0, 1.0)
                MIN_RECENCY + (1 - MIN_RECENCY) * age
            } ?: 1.0
        val favorite = if (isFavorite) FAVORITE_BOOST else 1.0
        val rating = halfStars?.let { 0.75 + 0.5 * (it.coerceIn(1, 10) - 1) / 9 } ?: 1.0
        return recency * favorite * rating
    }

    private companion object {
        val RECENT = 30.days
        const val MIN_RECENCY = 0.2
        const val FAVORITE_BOOST = 1.5
    }
}

/**
 * A shuffle order of every index of [weights] once, [first] (the playing entry) first when given: a weighted random
 * permutation (each entry's key is u^(1/w), highest first), so heavier entries tend to come earlier.
 */
internal fun weightedShuffle(
    weights: List<Double>,
    first: Int?,
    random: Random,
): List<Int> {
    val keys = weights.map { random.nextDouble().pow(1 / it) }
    val rest = weights.indices.filter { it != first }.sortedByDescending { keys[it] }
    return listOfNotNull(first) + rest
}
