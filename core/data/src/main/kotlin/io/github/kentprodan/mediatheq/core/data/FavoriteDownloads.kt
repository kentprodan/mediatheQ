package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** The favorites of the signed-in server, as the library holds them now. */
internal data class Favorites(
    val tracks: List<Track>,
    /** Album -> its tracks. */
    val albums: Map<MediaId, List<Track>>,
    /** Playlist -> its tracks as far as the library has them. */
    val playlists: Map<MediaId, List<Track>>,
)

/** What one pass does: download per automatic origin, and the automatic origins to drop. */
internal data class FavoriteDownloadPlan(
    val download: Map<DownloadOrigin, List<Track>> = emptyMap(),
    /** Automatic albums and playlists no longer favorites. */
    val removeCollections: List<DownloadOrigin> = emptyList(),
    /** Tracks no longer favorites, downloaded as favorite tracks. */
    val removeFavorites: List<MediaId> = emptyList(),
)

/**
 * Automatic downloads of favorites (#145), a pure decision:
 * - per [mode], every favorite track, album and playlist gets its automatic origin; a track that has it already is
 *   never asked again (so a failed automatic download waits for the user's Retry);
 * - with [removeUnfavorited] and [mode] not Disabled, an automatic origin of [server] whose item is no longer a
 *   favorite goes (its tracks are deleted unless another origin keeps them; manual origins are never touched). A
 *   lower mode keeps what is there, like Disabled. Another server's automatic downloads are left alone.
 */
internal fun favoriteDownloadPlan(
    mode: AutoDownloads,
    removeUnfavorited: Boolean,
    server: ServerId,
    favorites: Favorites,
    origins: Map<MediaId, Set<DownloadOrigin>>,
): FavoriteDownloadPlan {
    if (mode == AutoDownloads.Disabled) return FavoriteDownloadPlan()

    fun missing(
        tracks: List<Track>,
        origin: DownloadOrigin,
    ) = tracks.filter { origin !in origins[it.id].orEmpty() }

    val download =
        buildMap {
            if (mode.tracks) {
                missing(favorites.tracks, DownloadOrigin.Favorite).takeIf { it.isNotEmpty() }?.let {
                    put(DownloadOrigin.Favorite, it)
                }
            }
            if (mode.albums) {
                favorites.albums.forEach { (id, tracks) ->
                    val origin = DownloadOrigin.Album(id, auto = true)
                    missing(tracks, origin).takeIf { it.isNotEmpty() }?.let { put(origin, it) }
                }
            }
            if (mode.playlists) {
                favorites.playlists.forEach { (id, tracks) ->
                    val origin = DownloadOrigin.Playlist(id, auto = true)
                    missing(tracks, origin).takeIf { it.isNotEmpty() }?.let { put(origin, it) }
                }
            }
        }
    if (!removeUnfavorited) return FavoriteDownloadPlan(download)

    val favoriteTracks = favorites.tracks.mapTo(HashSet()) { it.id }
    val autoOrigins = origins.filterKeys { it.serverId == server }
    val removeFavorites =
        autoOrigins
            .filter { (id, set) -> DownloadOrigin.Favorite in set && id !in favoriteTracks }
            .keys
            .toList()
    val removeCollections =
        autoOrigins.values
            .flatten()
            .distinct()
            .filter { origin ->
                when (origin) {
                    // A playlist still a favorite keeps its tracks, whatever the library knows of its items now.
                    is DownloadOrigin.Album -> origin.auto && origin.id !in favorites.albums

                    is DownloadOrigin.Playlist -> origin.auto && origin.id !in favorites.playlists

                    else -> false
                }
            }
    return FavoriteDownloadPlan(download, removeCollections, removeFavorites)
}

/**
 * Keeps the favorites downloaded (#145, Settings -> Offline, cache, and download -> Media): a pass runs when the setting
 * or the favorites change (also a favorite set on another device: live sync writes it into the library), never on
 * download progress, and never when a download is removed (an automatic one has no Remove: it goes by unfavoriting).
 * Nothing happens while the library is empty (signed out, or before the first sync): downloads outlive sign-out.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Singleton
class FavoriteDownloads
    @Inject
    constructor(
        private val preferences: AudioPreferencesRepository,
        private val library: LibraryRepository,
        private val playlists: PlaylistRepository,
        private val downloads: DownloadRepository,
        @ApplicationScope private val scope: CoroutineScope,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** For the app's lifetime; call once from the Application. */
        fun start() {
            scope.launch(ioDispatcher) {
                combine(
                    preferences.preferences.map { it.autoDownloads to it.removeUnfavorited }.distinctUntilChanged(),
                    library
                        .allTracks()
                        .map { tracks -> tracks.filter { it.userData.isFavorite }.map { it.id } }
                        .distinctUntilChanged(),
                    library
                        .allAlbums()
                        .map { albums -> albums.filter { it.userData.isFavorite }.map { it.id } }
                        .distinctUntilChanged(),
                    library
                        .allPlaylists()
                        .map { lists -> lists.filter { it.userData.isFavorite }.map { it.id } }
                        .distinctUntilChanged(),
                ) { settings, _, _, _ -> settings }
                    .debounce(PASS_DELAY)
                    .collectLatest { (mode, remove) -> pass(mode, remove) }
            }
        }

        internal suspend fun pass(
            mode: AutoDownloads,
            removeUnfavorited: Boolean,
        ) {
            if (mode == AutoDownloads.Disabled) return
            val tracks = library.allTracks().first()
            val server = tracks.firstOrNull()?.id?.serverId ?: return
            val favoriteAlbums =
                library.allAlbums().first().filter { it.userData.isFavorite }.mapTo(
                    HashSet(),
                ) { it.id }
            val favoritePlaylists =
                library
                    .allPlaylists()
                    .first()
                    .filter { it.userData.isFavorite }
                    .map { it.id }
            val favorites =
                Favorites(
                    tracks = tracks.filter { it.userData.isFavorite },
                    albums =
                        tracks
                            .filter { it.albumId in favoriteAlbums }
                            .groupBy { it.albumId!! }
                            .let { byAlbum -> favoriteAlbums.associateWith { byAlbum[it].orEmpty() } },
                    playlists =
                        if (mode.playlists) {
                            favoritePlaylists.associateWith { id -> playlists.items(id).first().map { it.track } }
                        } else {
                            favoritePlaylists.associateWith { emptyList() }
                        },
                )
            val origins = downloads.downloads.first().associate { it.track.id to it.origins }
            val plan = favoriteDownloadPlan(mode, removeUnfavorited, server, favorites, origins)
            plan.download.forEach { (origin, list) -> downloads.download(list, origin) }
            plan.removeCollections.forEach(downloads::removeCollection)
            if (plan.removeFavorites.isNotEmpty()) downloads.removeOrigin(plan.removeFavorites, DownloadOrigin.Favorite)
        }

        private companion object {
            // Several favorites in a row (a sync, a quick series of taps) make one pass.
            val PASS_DELAY = 2.seconds
        }
    }
