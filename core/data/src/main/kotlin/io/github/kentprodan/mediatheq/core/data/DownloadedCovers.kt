package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.DownloadCoverDir
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.server.LibrarySource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the cover of every downloaded album on the device, so it shows offline (#154): one 1024 px file per album,
 * fetched when a download completes and deleted with the album's last download. Tracks often carry their own embedded
 * image; offline they show their album's cover, which is the same picture in practice.
 */
@Singleton
class DownloadedCovers
    @Inject
    constructor(
        private val downloads: DownloadRepository,
        private val library: LibraryRepository,
        private val librarySource: LibrarySource,
        private val accountStore: AccountStore,
        @DownloadCoverDir private val dir: File,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        // Artwork item id -> cover file. In memory: covers are resolved on the main thread, so no disk access here.
        @Volatile private var files: Map<String, File> = emptyMap()

        /** The kept cover for [artwork] (a downloaded track's or album's image), or `null`. */
        fun file(artwork: Artwork): File? = files[artwork.itemId]

        // Done after the first pass: a cover asked for at start (the restored queue) waits for it instead of missing it.
        private val firstPass = CompletableDeferred<Unit>()

        /**
         * Follows the completed downloads (never a too-early empty set, so a cold start keeps the files) and the library
         * (a cover changed on the server), for the app's lifetime; call once from the Application. Not [DownloadRepository.downloads]:
         * that one ticks every second while downloads run.
         */
        fun start() {
            scope.launch(ioDispatcher) {
                combine(downloads.completedIds, library.albums()) { ids, _ -> ids }
                    // A change while fetching restarts the pass, and that pass removes what is no longer needed.
                    .collectLatest(::keep)
            }
        }

        /** Waits (briefly) for the first pass, so covers asked for right after start are found. */
        suspend fun awaitFirstPass() {
            withTimeoutOrNull(FIRST_PASS_TIMEOUT) { firstPass.await() }
        }

        /** Keeps the covers of the tracks [ids] (the completed downloads) and deletes every other file. */
        internal suspend fun keep(ids: Set<MediaId>) {
            val wanted = mutableMapOf<String, Artwork>() // file name -> cover to keep
            val names = mutableMapOf<String, String>() // artwork item id -> file name
            ids.forEach { id ->
                val track = library.track(id).first() ?: return@forEach
                val cover = track.albumId?.let { library.album(it).first()?.artwork } ?: track.artwork ?: return@forEach
                val name = fileName(id.serverId.value, cover)
                wanted[name] = cover
                names[cover.itemId] = name
                track.artwork?.let { names[it.itemId] = name }
            }
            dir.mkdirs()
            dir.listFiles()?.filter { it.name !in wanted }?.forEach { it.delete() }
            publish(names)
            firstPass.complete(Unit)
            val baseUrl = accountStore.baseUrl.first() ?: return
            wanted.filterKeys { !File(dir, it).exists() }.forEach { (name, cover) -> fetch(baseUrl, name, cover) }
            publish(names)
        }

        private fun publish(names: Map<String, String>) {
            files =
                names
                    .mapNotNull { (itemId, name) ->
                        File(dir, name).takeIf { it.exists() }?.let { itemId to it }
                    }.toMap()
        }

        /** Written to a temporary file first, so a crash never leaves half a cover. */
        private suspend fun fetch(
            baseUrl: String,
            name: String,
            cover: Artwork,
        ) {
            val bytes = (librarySource.image(baseUrl, cover, COVER_WIDTH_PX) as? MqResult.Success)?.value ?: return
            val temp = File(dir, "$name.tmp")
            try {
                temp.writeBytes(bytes)
                if (!temp.renameTo(File(dir, name))) temp.delete()
            } catch (_: IOException) {
                temp.delete()
            }
        }

        private fun fileName(
            serverId: String,
            cover: Artwork,
        ) = listOf(
            serverId,
            cover.itemId,
            cover.tag,
        ).joinToString("_") { part -> part.filter { it.isLetterOrDigit() } } +
            ".jpg"

        private companion object {
            /** Sharp in Now Playing; lists scale it down. */
            const val COVER_WIDTH_PX = 1024
            val FIRST_PASS_TIMEOUT = 2.seconds
        }
    }
