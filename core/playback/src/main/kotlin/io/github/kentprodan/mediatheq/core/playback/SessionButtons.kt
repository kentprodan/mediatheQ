package io.github.kentprodan.mediatheq.core.playback

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import io.github.kentprodan.mediatheq.core.data.NotificationButtonsPreferences
import io.github.kentprodan.mediatheq.core.data.SessionButtonAction

/** The session's own commands behind the notification's action buttons (#139). */
internal val FAVORITE_COMMAND = SessionCommand("io.github.kentprodan.mediatheq.TOGGLE_FAVORITE", Bundle.EMPTY)
internal val REPEAT_COMMAND = SessionCommand("io.github.kentprodan.mediatheq.CYCLE_REPEAT", Bundle.EMPTY)
internal val SHUFFLE_COMMAND = SessionCommand("io.github.kentprodan.mediatheq.TOGGLE_SHUFFLE", Bundle.EMPTY)

/** What the action buttons show. */
internal data class ButtonState(
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val isFavorite: Boolean = false,
)

/** The buttons' names (TalkBack, the car): resources of the service. */
internal data class ButtonNames(
    val favorite: String,
    val unfavorite: String,
    val repeat: String,
    val shuffle: String,
)

/**
 * Settings -> Playback -> Notification buttons (#139) as the session's media button preferences, for the notification,
 * the lock screen and the car: the chosen actions in order, icons following [state]; an action takes the place of a
 * hidden previous or next button, the others go to the overflow.
 */
@UnstableApi
internal fun sessionButtons(
    preferences: NotificationButtonsPreferences,
    state: ButtonState,
    names: ButtonNames,
): List<CommandButton> {
    val freeSlots =
        ArrayDeque(
            listOfNotNull(
                CommandButton.SLOT_BACK.takeUnless { preferences.showPrevious },
                CommandButton.SLOT_FORWARD.takeUnless { preferences.showNext },
            ),
        )
    return preferences.actions.map { action ->
        val builder =
            when (action) {
                SessionButtonAction.Favorite -> {
                    val heart = state.isFavorite
                    val icon = if (heart) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
                    CommandButton
                        .Builder(icon)
                        .setDisplayName(if (heart) names.unfavorite else names.favorite)
                        .setSessionCommand(FAVORITE_COMMAND)
                }

                SessionButtonAction.Repeat -> {
                    CommandButton
                        .Builder(
                            when (state.repeatMode) {
                                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                                else -> CommandButton.ICON_REPEAT_OFF
                            },
                        ).setDisplayName(names.repeat)
                        .setSessionCommand(REPEAT_COMMAND)
                }

                SessionButtonAction.Shuffle -> {
                    CommandButton
                        .Builder(if (state.shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                        .setDisplayName(names.shuffle)
                        .setSessionCommand(SHUFFLE_COMMAND)
                }

                SessionButtonAction.Nothing -> {
                    error("Nothing is never among the actions")
                }
            }
        val slot = freeSlots.removeFirstOrNull()
        (if (slot != null) builder.setSlots(slot, CommandButton.SLOT_OVERFLOW) else builder).build()
    }
}

/** Repeat cycles off -> all -> one -> off, as the player screen does. */
internal fun nextRepeatMode(mode: Int): Int =
    when (mode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }

/**
 * The media notification's player commands: previous and next as chosen (System UI, the lock screen, the car).
 * Media3 applies them to the platform session too, so [hideQueue] (Bluetooth lyrics on, #143) leaves it without a
 * queue: Bluetooth holds back a title that differs from the queue's active item (ADR 0034).
 */
@UnstableApi
internal fun notificationPlayerCommands(
    commands: Player.Commands,
    preferences: NotificationButtonsPreferences,
    hideQueue: Boolean = false,
): Player.Commands =
    commands
        .buildUpon()
        .apply {
            if (hideQueue) remove(Player.COMMAND_GET_TIMELINE)
            if (!preferences.showPrevious) {
                removeAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }
            if (!preferences.showNext) removeAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }.build()

/** The session commands with the action buttons' own. */
@UnstableApi
internal fun withButtonCommands(commands: SessionCommands): SessionCommands =
    commands
        .buildUpon()
        .add(FAVORITE_COMMAND)
        .add(REPEAT_COMMAND)
        .add(SHUFFLE_COMMAND)
        .build()
