package io.github.kentprodan.mediatheq.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * The seed of Home's random sections (#127): one per app start, so the same set shows until pull to refresh asks for a
 * new one (not on every library change or every time Home is opened again).
 */
@Singleton
class HomeShuffle(
    initial: Long,
) {
    @Inject
    constructor() : this(Random.nextLong())

    private val current = MutableStateFlow(initial)

    val seed: StateFlow<Long> = current

    fun reshuffle() {
        current.value = Random.nextLong()
    }

    // Mixes (#128) draw from here: a new order at each tap, the same ones for the same initial seed (tests).
    private val mixes = Random(initial)

    /** A fresh random source for one mix. */
    fun mixRandom(): Random = Random(mixes.nextLong())
}
