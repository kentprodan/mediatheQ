package io.github.kentprodan.mediatheq.core.data

/** When offline mode turns on by itself (#144), besides the Offline mode switch. */
enum class AutoOffline {
    /** Without any network (#63). */
    NoNetwork,

    /** Whenever the network may cost money: mobile data, a metered hotspot, Tailscale over mobile data. */
    NotOnWifi,

    /** Only the switch turns it on; without a network nothing streams anyway. */
    Never,
}

/** Settings -> Offline, cache, and download -> Images (#144). */
data class ImagePreferences(
    /** Covers are kept in app storage, which the system never clears; applies at the next app start. */
    val persistentCache: Boolean = false,
    /** Covers not cached yet are not loaded on mobile data. */
    val wifiOnly: Boolean = false,
    /** Larger cover sizes from the server. */
    val highQuality: Boolean = false,
)
