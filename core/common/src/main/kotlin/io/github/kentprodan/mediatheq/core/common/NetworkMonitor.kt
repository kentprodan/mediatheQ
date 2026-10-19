package io.github.kentprodan.mediatheq.core.common

/** The device's current network, as far as data use goes. Implemented with the platform in `:core:network`. */
interface NetworkMonitor {
    /**
     * True on networks that may cost money: cellular, and Wi-Fi marked as metered (e.g. a phone hotspot). Streams then
     * use reduced quality (media3 skill, section 3).
     */
    fun isMetered(): Boolean
}
