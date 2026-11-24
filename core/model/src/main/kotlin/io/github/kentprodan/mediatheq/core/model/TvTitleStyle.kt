package io.github.kentprodan.mediatheq.core.model

/**
 * How the TV detail pages (movie, series, artist) show their title (#523, Settings -> Screens -> each page): the item's
 * Jellyfin logo (the name as text when it has none), the logo with the name under it, or the name as text only.
 */
enum class TvTitleStyle { Logo, LogoAndText, Text }

/** How big the logo title is (#578); Medium is the size of #523. */
enum class TvLogoSize { Small, Medium, Large }

/** The TV detail pages whose title and logo size are set each on its own (#619). */
enum class TvTitlePage { Movie, Series, Artist }
