package io.github.kentprodan.mediatheq.feature.downloads

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.DownloadsKey

/** Registers the Downloads tab root. */
fun EntryProviderScope<NavKey>.downloadsEntry() {
    entry<DownloadsKey> { DownloadsRoute() }
}
