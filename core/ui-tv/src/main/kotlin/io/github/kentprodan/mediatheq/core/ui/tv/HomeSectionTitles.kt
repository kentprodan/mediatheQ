package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.annotation.StringRes
import io.github.kentprodan.mediatheq.core.model.HomeSection

/** A Home section's title on TV (#236); the same words as the phone's (`:core:ui` cannot be used on TV, ADR 0037). */
@get:StringRes
val HomeSection.title: Int
    get() =
        when (this) {
            HomeSection.Shortcuts -> R.string.core_ui_tv_home_shortcuts
            HomeSection.FavoritePlaylists -> R.string.core_ui_tv_home_favorite_playlists
            HomeSection.FavoriteAlbums -> R.string.core_ui_tv_home_favorite_albums
            HomeSection.FavoriteTracks -> R.string.core_ui_tv_home_favorite_tracks
            HomeSection.FavoriteArtists -> R.string.core_ui_tv_home_favorite_artists
            HomeSection.RecentlyAddedAlbums -> R.string.core_ui_tv_home_recently_added_albums
            HomeSection.RediscoverAlbums -> R.string.core_ui_tv_home_rediscover_albums
            HomeSection.LastPlayedAlbums -> R.string.core_ui_tv_home_last_played_albums
            HomeSection.RandomArtists -> R.string.core_ui_tv_home_random_artists
            HomeSection.RandomAlbums -> R.string.core_ui_tv_home_random_albums
            HomeSection.ContinueAlbums -> R.string.core_ui_tv_home_continue_albums
            HomeSection.MostPlayedTracks -> R.string.core_ui_tv_home_most_played_tracks
            HomeSection.RecentlyAddedTracks -> R.string.core_ui_tv_home_recently_added_tracks
            HomeSection.RecentlyAddedArtists -> R.string.core_ui_tv_home_recently_added_artists
            HomeSection.LastPlayedTracks -> R.string.core_ui_tv_home_last_played_tracks
            HomeSection.LastPlayedArtists -> R.string.core_ui_tv_home_last_played_artists
            HomeSection.MostPlayedAlbums -> R.string.core_ui_tv_home_most_played_albums
            HomeSection.MostPlayedArtists -> R.string.core_ui_tv_home_most_played_artists
            HomeSection.RandomTracks -> R.string.core_ui_tv_home_random_tracks
            HomeSection.RediscoverTracks -> R.string.core_ui_tv_home_rediscover_tracks
            HomeSection.FiveStarTracks -> R.string.core_ui_tv_home_five_star_tracks
        }
