package io.github.kentprodan.mediatheq.feature.home

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
class HomeShuffle internal constructor(
    initial: Long,
) {
    @Inject
    constructor() : this(Random.nextLong())

    private val current = MutableStateFlow(initial)

    val seed: StateFlow<Long> = current

    fun reshuffle() {
        current.value = Random.nextLong()
    }
}
