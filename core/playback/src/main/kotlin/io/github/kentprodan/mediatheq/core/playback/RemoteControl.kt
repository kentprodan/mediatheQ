package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LiveSync
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RemoteCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays what other clients ask for (#389): Jellyfin web or the phone pause, seek or skip this session, or send it a
 * movie to play from a position. A video started this way is announced in [videoStarted], so the TV opens its player.
 */
@Singleton
class RemoteControl
    @Inject
    constructor(
        private val liveSync: LiveSync?,
        private val playback: PlaybackConnection,
        private val videos: VideoRepository,
        private val library: LibraryRepository,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val started = MutableSharedFlow<MediaId>(extraBufferCapacity = 1)

        /** A video another client started here: the screen showing it opens. */
        val videoStarted: SharedFlow<MediaId> = started.asSharedFlow()

        /** Follows the commands for the app's lifetime; call once from the Application. */
        fun start() {
            val commands = liveSync?.remoteCommands ?: return
            scope.launch(Dispatchers.Main) { commands.collect { apply(it) } }
        }

        internal suspend fun apply(command: RemoteCommand) {
            val playing = playback.state.value.isPlaying
            when (command) {
                RemoteCommand.Pause, RemoteCommand.Stop -> if (playing) playback.pause()
                RemoteCommand.Unpause -> if (!playing) playback.togglePlayPause()
                RemoteCommand.PlayPause -> playback.togglePlayPause()
                RemoteCommand.Next -> playback.next()
                RemoteCommand.Previous -> playback.previous()
                is RemoteCommand.Seek -> playback.seekTo(command.position)
                is RemoteCommand.Play -> play(command)
            }
        }

        /** Plays [id] from [position] as a command would (#386: a Continue watching card of the Google TV home). */
        suspend fun play(
            id: MediaId,
            position: kotlin.time.Duration,
        ) = play(RemoteCommand.Play(listOf(id), startPosition = position))

        /** A movie or an episode plays as a video from the position; anything else as music from the library. */
        private suspend fun play(command: RemoteCommand.Play) {
            val first = command.items.getOrNull(command.startIndex) ?: return
            val video = videos.video(first).first()
            if (video != null) {
                playback.playVideo(video, command.startPosition)
                started.tryEmit(video.id)
                return
            }
            val tracks = library.tracksById(command.items)
            if (tracks.isEmpty()) return
            val start = tracks.indexOfFirst { it.id == first }.coerceAtLeast(0)
            playback.play(tracks, start, startPosition = command.startPosition)
        }
    }
