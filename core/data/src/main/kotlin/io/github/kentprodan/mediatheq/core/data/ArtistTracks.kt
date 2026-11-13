package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Track

// What an artist page plays, for both apps (moved from :feature:artist for the TV, #239).

/** Every played track, most played first; ties keep the discography order (the sort is stable). */
fun playedTracks(tracks: List<Track>): List<Track> =
    tracks.filter { it.userData.playCount > 0 }.sortedByDescending { it.userData.playCount }

/** The own albums' tracks in [albums]' order (the page's sort), then the tracks the artist is credited on. */
fun albumOrder(
    tracks: List<Track>,
    albums: List<Album>,
): List<Track> {
    val rank = albums.withIndex().associate { (index, album) -> album.id to index }
    // Stable: tracks keep their order within an album, credits keep theirs at the end.
    return tracks.sortedBy { track -> track.albumId?.let(rank::get) ?: Int.MAX_VALUE }
}

/** What Play starts (#133): the queue and whether it is shuffled. Top tracks fall back to the albums when none. */
fun playQueue(
    action: ArtistPlayAction,
    tracks: List<Track>,
    topTracks: List<Track>,
): Pair<List<Track>, Boolean> =
    when (action) {
        ArtistPlayAction.Albums -> tracks to false
        ArtistPlayAction.TopTracks -> topTracks.ifEmpty { tracks } to false
        ArtistPlayAction.AllShuffled -> tracks to true
    }
