package io.github.kentprodan.mediatheq.core.data

/** What a short interruption (a navigation prompt, a notification sound) does to the music (#139). */
enum class ShortFocusAction { Duck, Pause, Nothing }

/** Settings -> Playback -> Audio focus (#139). */
data class AudioFocusPreferences(
    val shortAction: ShortFocusAction = ShortFocusAction.Duck,
    /** After a long interruption (a phone call) playback does not go on by itself. */
    val noResumeAfterLongLoss: Boolean = false,
)

/** What a click of a headset's play button does (#139). */
enum class HeadsetAction { PlayPause, Next, Previous, Nothing }

/** What a headset's next button does (#139). */
enum class NextButtonAction { Next, Previous, SeekForward, Nothing }

/** What a headset's previous button does (#139). */
enum class PreviousButtonAction { Previous, Next, SeekBack, Nothing }

/** Settings -> Playback -> Headset buttons (#139). */
data class HeadsetPreferences(
    val singleClick: HeadsetAction = HeadsetAction.PlayPause,
    val doubleClick: HeadsetAction = HeadsetAction.Next,
    val tripleClick: HeadsetAction = HeadsetAction.Previous,
    val nextButton: NextButtonAction = NextButtonAction.Next,
    val previousButton: PreviousButtonAction = PreviousButtonAction.Previous,
    /** A longer window between the clicks of a double or triple click. */
    val slowClicks: Boolean = false,
)

/** A button of the media notification, the lock screen and the car (#139). */
enum class SessionButtonAction { Favorite, Repeat, Shuffle, Nothing }

/** Settings -> Playback -> Notification buttons (#139). */
data class NotificationButtonsPreferences(
    val showPrevious: Boolean = true,
    val showNext: Boolean = true,
    val action1: SessionButtonAction = SessionButtonAction.Favorite,
    val action2: SessionButtonAction = SessionButtonAction.Repeat,
    val action3: SessionButtonAction = SessionButtonAction.Shuffle,
) {
    /** The three actions in order, "Nothing" left out. */
    val actions: List<SessionButtonAction>
        get() = listOf(action1, action2, action3).filter { it != SessionButtonAction.Nothing }
}
