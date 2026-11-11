package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.NotificationButtonsPreferences
import io.github.kentprodan.mediatheq.core.data.SessionButtonAction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class SessionButtonsTest {
    private val names = ButtonNames("Favorite", "Remove favorite", "Repeat", "Shuffle")

    private fun buttons(
        preferences: NotificationButtonsPreferences = NotificationButtonsPreferences(),
        state: ButtonState = ButtonState(),
    ) = sessionButtons(preferences, state, names)

    @Test
    fun `the defaults are favorite, repeat and shuffle, in the overflow next to previous and next`() {
        val buttons = buttons()

        assertThat(buttons.map { it.sessionCommand })
            .containsExactly(FAVORITE_COMMAND, REPEAT_COMMAND, SHUFFLE_COMMAND)
            .inOrder()
        assertThat(buttons.map { it.icon })
            .containsExactly(
                CommandButton.ICON_HEART_UNFILLED,
                CommandButton.ICON_REPEAT_OFF,
                CommandButton.ICON_SHUFFLE_OFF,
            ).inOrder()
    }

    @Test
    fun `icons follow the state`() {
        val buttons = buttons(state = ButtonState(Player.REPEAT_MODE_ONE, shuffle = true, isFavorite = true))

        assertThat(buttons.map { it.icon })
            .containsExactly(
                CommandButton.ICON_HEART_FILLED,
                CommandButton.ICON_REPEAT_ONE,
                CommandButton.ICON_SHUFFLE_ON,
            ).inOrder()
        assertThat(buttons.first().displayName.toString()).isEqualTo("Remove favorite")
        assertThat(
            buttons(state = ButtonState(Player.REPEAT_MODE_ALL))[1].icon,
        ).isEqualTo(CommandButton.ICON_REPEAT_ALL)
    }

    @Test
    fun `each action choice gives its button, nothing gives none`() {
        val buttons =
            buttons(
                NotificationButtonsPreferences(
                    action1 = SessionButtonAction.Shuffle,
                    action2 = SessionButtonAction.Nothing,
                    action3 = SessionButtonAction.Shuffle,
                ),
            )

        assertThat(buttons.map { it.sessionCommand }).containsExactly(SHUFFLE_COMMAND, SHUFFLE_COMMAND)
    }

    @Test
    fun `hidden previous and next give their places to the first actions`() {
        val buttons = buttons(NotificationButtonsPreferences(showPrevious = false, showNext = false))

        assertThat(buttons[0].slots.get(0)).isEqualTo(CommandButton.SLOT_BACK)
        assertThat(buttons[1].slots.get(0)).isEqualTo(CommandButton.SLOT_FORWARD)
        assertThat(buttons[2].slots.get(0)).isEqualTo(CommandButton.SLOT_OVERFLOW)
    }

    @Test
    fun `hidden previous and next leave the notification's commands, the others stay`() {
        val all =
            Player.Commands
                .Builder()
                .addAllCommands()
                .build()

        // Bluetooth lyrics hide the queue from the platform session, and only then.
        assertThat(
            notificationPlayerCommands(all, NotificationButtonsPreferences()).contains(Player.COMMAND_GET_TIMELINE),
        ).isTrue()
        assertThat(
            notificationPlayerCommands(all, NotificationButtonsPreferences(), hideQueue = true)
                .contains(Player.COMMAND_GET_TIMELINE),
        ).isFalse()
        val commands = notificationPlayerCommands(all, NotificationButtonsPreferences(showPrevious = false))

        assertThat(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS)).isFalse()
        assertThat(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)).isFalse()
        assertThat(commands.contains(Player.COMMAND_SEEK_TO_NEXT)).isTrue()
        assertThat(commands.contains(Player.COMMAND_PLAY_PAUSE)).isTrue()
    }

    @Test
    fun `repeat cycles off, all, one`() {
        assertThat(nextRepeatMode(Player.REPEAT_MODE_OFF)).isEqualTo(Player.REPEAT_MODE_ALL)
        assertThat(nextRepeatMode(Player.REPEAT_MODE_ALL)).isEqualTo(Player.REPEAT_MODE_ONE)
        assertThat(nextRepeatMode(Player.REPEAT_MODE_ONE)).isEqualTo(Player.REPEAT_MODE_OFF)
    }
}
