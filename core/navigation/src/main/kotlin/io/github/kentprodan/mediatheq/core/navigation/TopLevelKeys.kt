package io.github.kentprodan.mediatheq.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Root of the Home tab. */
@Serializable
data object HomeKey : NavKey

/** Root of the Library tab. */
@Serializable
data object LibraryKey : NavKey

/** Root of the Search tab. */
@Serializable
data object SearchKey : NavKey

/** Root of the Downloads tab. */
@Serializable
data object DownloadsKey : NavKey
