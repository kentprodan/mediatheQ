package io.github.kentprodan.mediatheq.core.model

/** How the app is used (ADR 0051): one app-wide setting; [Music] is today's app. */
enum class AppMode(
    val music: Boolean,
    val video: Boolean,
) {
    Music(music = true, video = false),
    Video(music = false, video = true),
    Both(music = true, video = true),
}
