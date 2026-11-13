package io.github.kentprodan.mediatheq.feature.tv.nowplaying

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.NowPlayingKey

/** Registers the full-screen player on TV (#240), a drawer destination. */
fun EntryProviderScope<NavKey>.tvNowPlayingEntry() {
    entry<NowPlayingKey> { TvNowPlayingRoute() }
}
