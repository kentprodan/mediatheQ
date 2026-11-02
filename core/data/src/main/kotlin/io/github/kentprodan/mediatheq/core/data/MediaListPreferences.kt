package io.github.kentprodan.mediatheq.core.data

/** How the top button row of a list header looks (#123). */
enum class TopButtonStyle { Hidden, Normal, SmallIcons, LargeIcons }

/** A button of the list header's top row; each acts on the whole list as filtered and sorted. */
enum class TopButton { Play, Shuffle, PlayNext, AddToQueue }

/** Extra information on the trailing side of a track row. */
enum class TrackInfo { None, Duration, Favorite, Rating, PlayCount, Offline, Codec, Bitrate, CodecAndBitrate }

/** What swiping a track row one way does; [Disabled] = no swipe that way. */
enum class SwipeAction { Disabled, AddToQueue, PlayNext, AddToPlaylist, Download, ToggleFavorite, Rate }

/** Settings -> Interface -> Media lists (#123). */
data class MediaListPreferences(
    val showItemCounts: Boolean = true,
    val topButtonStyle: TopButtonStyle = TopButtonStyle.SmallIcons,
    val hiddenTopButtons: Set<TopButton> = emptySet(),
    val invertedSortArrows: Boolean = false,
    val hideBackButton: Boolean = false,
    val trackInfo: TrackInfo = TrackInfo.Duration,
    val secondTrackInfo: TrackInfo = TrackInfo.None,
    val centerGridText: Boolean = true,
    val favoriteOverlay: Boolean = true,
    val offlineOverlay: Boolean = false,
    val swipeRight: SwipeAction = SwipeAction.AddToQueue,
    val swipeLeft: SwipeAction = SwipeAction.PlayNext,
)
