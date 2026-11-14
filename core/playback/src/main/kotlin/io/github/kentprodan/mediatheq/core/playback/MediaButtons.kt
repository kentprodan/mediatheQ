package io.github.kentprodan.mediatheq.core.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

/**
 * A media button sent to the playback service (#278: the PiP window's play / pause and next): Media3's session service
 * handles `ACTION_MEDIA_BUTTON` start intents like a headset button, so the notification stays in step.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun mediaButtonIntent(
    context: Context,
    keyCode: Int,
): PendingIntent =
    PendingIntent.getService(
        context,
        keyCode,
        Intent(Intent.ACTION_MEDIA_BUTTON)
            .setClass(context, MediatheqPlaybackService::class.java)
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
