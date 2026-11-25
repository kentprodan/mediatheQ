package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.os.Build
import android.os.Handler
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.NoSampleRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ASS / SSA subtitles drawn by libass as their authors styled them (#635): positions, fonts embedded in the MKV,
 * animations and karaoke. The service's player feeds [handler] (tracks, the clock, the MKV's fonts and dialogue); the
 * video screens lay [overlay] over the picture, since libass draws every frame itself and Media3's cues cannot carry
 * that. OpenGL from API 29, the canvas below. One per process: the service and the screens share it.
 */
@OptIn(UnstableApi::class)
@Singleton
class StyledSubtitles
    @Inject
    constructor() {
        internal val handler =
            AssHandler(
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {
                    AssRenderType.OVERLAY_OPEN_GL
                } else {
                    AssRenderType.OVERLAY_CANVAS
                },
            )

        /** The parsers of a video's subtitles: libass for ASS / SSA, Media3's for the rest. */
        internal val parsers = AssSubtitleParserFactory(handler)

        /** The view that draws the playing video's ASS subtitles, to lay over the picture and Media3's subtitles. */
        fun overlay(context: Context): View = AssSubtitleView(context, handler)

        /** Follows [player]'s tracks and surface; [release] stops. */
        internal fun attach(player: ExoPlayer) = handler.init(player)

        internal fun release(player: ExoPlayer) = player.removeListener(handler)
    }

/**
 * [delegate]'s renderers and one that hands libass the playing period's time (#635). ass-media's own renderer subtracts
 * a fixed offset that holds only for the first item of a playlist, so the next episode's subtitles would drift by the
 * previous one's length; this one subtracts the offset the player gives each period.
 */
@UnstableApi
internal class StyledSubtitleRenderersFactory(
    private val delegate: RenderersFactory,
    private val handler: AssHandler,
) : RenderersFactory {
    override fun createRenderers(
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput,
    ): Array<Renderer> =
        delegate.createRenderers(
            eventHandler,
            videoRendererEventListener,
            audioRendererEventListener,
            textRendererOutput,
            metadataRendererOutput,
        ) + AssClockRenderer(handler)

    override fun createSecondaryRenderer(
        renderer: Renderer,
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput,
    ): Renderer? =
        delegate.createSecondaryRenderer(
            renderer,
            eventHandler,
            videoRendererEventListener,
            audioRendererEventListener,
            textRendererOutput,
            metadataRendererOutput,
        )
}

@UnstableApi
internal class AssClockRenderer(
    private val handler: AssHandler,
) : NoSampleRenderer() {
    private var offsetUs = 0L

    override fun getName() = "AssClockRenderer"

    override fun onRendererOffsetChanged(offsetUs: Long) {
        this.offsetUs = offsetUs
    }

    override fun render(
        positionUs: Long,
        elapsedRealtimeUs: Long,
    ) {
        handler.videoTime = positionUs - offsetUs
    }
}
