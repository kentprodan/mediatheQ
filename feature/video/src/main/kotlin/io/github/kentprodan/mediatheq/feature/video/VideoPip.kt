package io.github.kentprodan.mediatheq.feature.video

import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import io.github.kentprodan.mediatheq.core.playback.mediaButtonIntent

/** What the PiP window is set up with (#278, ADR 0049). Pure, so it can be tested without a window. */
data class PipSpec(
    /** The home gesture enters PiP by itself: the player shows, a video plays and the setting is on. */
    val autoEnter: Boolean,
    /** Width : height, kept inside what the platform accepts (2.39 : 1 to 1 : 2.39). */
    val aspectRatio: Pair<Int, Int>,
    /** The window's buttons: play or pause (whichever applies), then next when an episode follows. */
    val actions: List<PipAction>,
)

enum class PipAction { Play, Pause, Next }

/** The PiP set-up for the player's state (#278). */
fun pipSpec(
    pipSetting: Boolean,
    isPlaying: Boolean,
    hasNext: Boolean,
    aspectRatio: Float?,
): PipSpec {
    val ratio = (aspectRatio ?: (16 / 9f)).coerceIn(1 / MAX_RATIO, MAX_RATIO)
    return PipSpec(
        autoEnter = pipSetting && isPlaying,
        aspectRatio = (ratio * RATIO_SCALE).toInt() to RATIO_SCALE,
        actions = listOfNotNull(if (isPlaying) PipAction.Pause else PipAction.Play, PipAction.Next.takeIf { hasNext }),
    )
}

/** Whether Back stops the sound (#278): it does unless "Keep audio when leaving" is on. */
fun leavePauses(keepAudio: Boolean): Boolean = !keepAudio

private const val MAX_RATIO = 2.39f
private const val RATIO_SCALE = 1000

/** Applies [spec] to the activity while the player shows; auto-enter goes off when it leaves. */
@Composable
internal fun PipParams(
    spec: PipSpec,
    sourceRect: Rect?,
) {
    val activity = LocalActivity.current ?: return
    val context = LocalContext.current
    val labels =
        mapOf(
            PipAction.Play to stringResource(R.string.video_pip_play),
            PipAction.Pause to stringResource(R.string.video_pip_pause),
            PipAction.Next to stringResource(R.string.video_pip_next),
        )
    DisposableEffect(spec, sourceRect) {
        val actions =
            spec.actions.map { action ->
                val (icon, key) =
                    when (action) {
                        PipAction.Play -> {
                            Triple(
                                android.R.drawable.ic_media_play,
                                R.string.video_pip_play,
                                KeyEvent.KEYCODE_MEDIA_PLAY,
                            )
                        }

                        PipAction.Pause -> {
                            Triple(
                                android.R.drawable.ic_media_pause,
                                R.string.video_pip_pause,
                                KeyEvent.KEYCODE_MEDIA_PAUSE,
                            )
                        }

                        PipAction.Next -> {
                            Triple(
                                android.R.drawable.ic_media_next,
                                R.string.video_pip_next,
                                KeyEvent.KEYCODE_MEDIA_NEXT,
                            )
                        }
                    }
                val text = labels.getValue(action)
                RemoteAction(Icon.createWithResource(context, icon), text, text, mediaButtonIntent(context, key))
            }
        val params =
            PictureInPictureParams
                .Builder()
                .setAspectRatio(Rational(spec.aspectRatio.first, spec.aspectRatio.second))
                .setAutoEnterEnabled(spec.autoEnter)
                .setSeamlessResizeEnabled(true)
                .setActions(actions)
                .apply { sourceRect?.let(::setSourceRectHint) }
                .build()
        runCatching { activity.setPictureInPictureParams(params) }
        onDispose {
            runCatching {
                activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
            }
        }
    }
}

/** Whether the activity is in PiP now (#278), from its mode listener. */
@Composable
internal fun rememberIsInPipMode(): Boolean {
    val activity = LocalActivity.current as? ComponentActivity ?: return false
    var inPip by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}
