package io.github.kentprodan.mediatheq.feature.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/**
 * Every setting by its label, per page (#536): what Settings search finds. Each page is also found by its own title.
 * SettingsIndexTest proves every label here shows on its page.
 */
internal val settingsIndex: Map<SettingsPage, List<Int>> =
    mapOf(
        SettingsPage.Interface to listOf(R.string.settings_album_columns, R.string.settings_rating_mode),
        SettingsPage.AppMode to listOf(R.string.settings_app_mode),
        SettingsPage.Theme to
            listOf(
                R.string.settings_theme_mode,
                R.string.settings_color_source,
                R.string.settings_theme_colors,
                R.string.settings_now_playing_colors,
                R.string.settings_ignore_surfaces,
                R.string.settings_instant_colors,
                R.string.settings_typography,
            ),
        SettingsPage.Navigation to
            listOf(
                R.string.settings_nav_entries,
                R.string.settings_nav_portrait,
                R.string.settings_nav_landscape,
                R.string.settings_nav_transparency,
                R.string.settings_slow_start,
                R.string.settings_track_action,
                R.string.settings_expand_player,
            ),
        SettingsPage.MediaLists to
            listOf(
                R.string.settings_ml_item_counts,
                R.string.settings_ml_top_button_style,
                R.string.settings_ml_hidden_top_buttons,
                R.string.settings_ml_inverted_sort_arrows,
                R.string.settings_ml_hide_back,
                R.string.settings_ml_track_info,
                R.string.settings_ml_second_track_info,
                R.string.settings_ml_center_grid,
                R.string.settings_ml_favorite_overlay,
                R.string.settings_ml_offline_overlay,
                R.string.settings_ml_swipe_right,
                R.string.settings_ml_swipe_left,
            ),
        SettingsPage.NowPlaying to
            listOf(
                R.string.settings_np_floating,
                R.string.settings_np_mini_swipe,
                R.string.settings_np_mini_progress,
                R.string.settings_np_background,
                R.string.settings_np_progress,
                R.string.settings_np_fallback,
                R.string.settings_np_rating,
                R.string.settings_np_cover_tap,
                R.string.settings_np_cover_swipe,
                R.string.settings_np_play_button,
            ),
        SettingsPage.Home to listOf(R.string.settings_home_genres),
        SettingsPage.Library to
            listOf(R.string.settings_lp_shortcuts, R.string.settings_lp_style, R.string.settings_lp_spacing),
        SettingsPage.Album to
            listOf(
                R.string.settings_ap_background,
                R.string.settings_ap_quality,
                R.string.settings_ap_rating,
                R.string.settings_ap_hidden_buttons,
                R.string.settings_ap_order,
                R.string.settings_ap_more,
                R.string.settings_ap_more_sort,
                R.string.settings_ap_about,
                R.string.settings_ap_track_artists,
                R.string.settings_ap_track_numbers,
                R.string.settings_ap_thumbnails,
                R.string.settings_ap_multiline,
            ),
        SettingsPage.Artist to
            listOf(
                R.string.settings_arp_background,
                R.string.settings_arp_hidden_buttons,
                R.string.settings_arp_play_action,
                R.string.settings_arp_dynamic_colors,
                R.string.settings_arp_order,
                R.string.settings_arp_albums_sort,
                R.string.settings_arp_columns,
                R.string.settings_arp_landscape_columns,
                R.string.settings_arp_appears_on,
                R.string.settings_arp_top_tracks,
                R.string.settings_arp_top_tracks_count,
                R.string.settings_arp_about,
            ),
        SettingsPage.Genre to
            listOf(
                R.string.settings_gp_background,
                R.string.settings_gp_hidden_buttons,
                R.string.settings_gp_dynamic_colors,
                R.string.settings_gp_order,
                R.string.settings_gp_albums,
                R.string.settings_gp_artists,
                R.string.settings_gp_album_artists,
                R.string.settings_gp_top_tracks,
            ),
        SettingsPage.Playlist to
            listOf(
                R.string.settings_gp_background,
                R.string.settings_gp_hidden_buttons,
                R.string.settings_gp_dynamic_colors,
            ),
        SettingsPage.Search to
            listOf(
                R.string.settings_sp_single_line_chips,
                R.string.settings_sp_show_favorite,
                R.string.settings_track_action,
                R.string.settings_sp_sort,
            ),
        SettingsPage.Advanced to
            listOf(
                R.string.settings_adv_keep_screen_on,
                R.string.settings_adv_orientation,
                R.string.settings_adv_corners,
                R.string.settings_adv_track_number,
                R.string.settings_adv_hide_status_bar,
            ),
        SettingsPage.Playback to listOf(R.string.settings_outputs),
        SettingsPage.PlaybackBehavior to
            listOf(
                R.string.settings_pb_preserve_play_next,
                R.string.settings_pb_rewind_first,
                R.string.settings_pb_circular,
                R.string.settings_pb_keep_paused,
                R.string.settings_pb_stop_repeat_one,
                R.string.settings_pb_retain_state,
                R.string.settings_pb_weighted_shuffle,
                R.string.settings_pb_bluetooth_lyrics,
                R.string.settings_pb_online_lyrics,
            ),
        SettingsPage.Decoding to
            listOf(
                R.string.settings_quality_unmetered,
                R.string.settings_quality_metered,
                R.string.settings_force_instant_transcoding,
            ),
        SettingsPage.Video to
            listOf(
                R.string.settings_video_quality,
                R.string.settings_video_audio_language,
                R.string.settings_video_subtitles,
                R.string.settings_video_subtitle_language,
                R.string.settings_video_burn_in,
                R.string.settings_video_skip_intro,
                R.string.settings_video_skip_credits,
                R.string.settings_video_skip_recap,
                R.string.settings_video_auto_play,
                R.string.settings_video_pip,
                R.string.settings_video_keep_audio,
                R.string.settings_video_new_episodes,
                R.string.settings_video_watched_removal,
                R.string.settings_video_hide_overviews,
            ),
        SettingsPage.AudioFocus to listOf(R.string.settings_af_short, R.string.settings_af_no_resume),
        SettingsPage.Headset to
            listOf(
                R.string.settings_hb_single,
                R.string.settings_hb_double,
                R.string.settings_hb_triple,
                R.string.settings_hb_next,
                R.string.settings_hb_previous,
                R.string.settings_hb_slow,
            ),
        SettingsPage.NotificationButtons to listOf(R.string.settings_nb_previous, R.string.settings_nb_next),
        SettingsPage.Transitions to
            listOf(
                R.string.settings_tr_fade_in,
                R.string.settings_tr_fade_out,
                R.string.settings_tr_crossfade,
                R.string.settings_tr_smart,
                R.string.settings_tr_out_curve,
                R.string.settings_tr_in_curve,
            ),
        SettingsPage.AutoActions to
            listOf(
                R.string.settings_aa_volume,
                R.string.settings_aa_headphones,
                R.string.settings_aa_bluetooth,
                R.string.settings_aa_task_removal,
                R.string.settings_aa_remote_stop,
            ),
        SettingsPage.Storage to emptyList(),
        SettingsPage.StorageGeneral to listOf(R.string.settings_offline_mode, R.string.settings_st_auto_offline),
        SettingsPage.StorageMedia to
            listOf(
                R.string.settings_downloads_unmetered,
                R.string.settings_st_download_quality,
                R.string.settings_st_parallel,
                R.string.settings_st_auto_downloads,
                R.string.settings_st_remove_unfavorited,
            ),
        SettingsPage.StoragePlayback to listOf(R.string.settings_cache),
        SettingsPage.StorageImages to
            listOf(R.string.settings_st_persistent, R.string.settings_st_images_wifi, R.string.settings_st_hq),
    )

/** A search hit: the setting's label and the page it is on. */
internal data class SettingsHit(
    val label: String,
    val page: SettingsPage,
)

/**
 * The settings whose label (or page title) contains [query], ignoring case; a page's own title first. [text] reads a
 * string resource.
 */
internal fun searchSettings(
    query: String,
    text: (Int) -> String,
): List<SettingsHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return SettingsPage.entries.flatMap { page ->
        val title = text(page.title)
        val labels = settingsIndex[page].orEmpty().map(text).distinct()
        buildList {
            if (title.contains(q, ignoreCase = true)) add(SettingsHit(title, page))
            labels.filter { it.contains(q, ignoreCase = true) && it != title }.forEach { add(SettingsHit(it, page)) }
        }
    }
}

/** The search field at the top of the hub. */
@Composable
internal fun SettingsSearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.settings_search)) },
        leadingIcon = { Icon(MqIcons.Search, contentDescription = null) },
        trailingIcon =
            if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(MqIcons.Close, contentDescription = stringResource(R.string.settings_search_clear))
                    }
                }
            } else {
                null
            },
        singleLine = true,
        shape = CircleShape,
    )
}

/** The hits of [query]: each row names the setting and its page; a tap opens the page. */
internal fun GroupScope.searchHits(
    hits: List<SettingsHit>,
    onOpen: (SettingsPage) -> Unit,
) {
    hits.forEach { hit ->
        item { shapes ->
            SegmentedListItem(
                onClick = { onOpen(hit.page) },
                shapes = shapes,
                colors = settingsColors(),
                supportingContent = { Text(stringResource(hit.page.title)) },
            ) { Text(hit.label) }
        }
    }
}

/** Reads a string resource in composition (for [searchSettings]). */
@Composable
internal fun rememberStringReader(): (Int) -> String {
    val resources = LocalContext.current.resources
    return { id: Int -> resources.getString(id) }
}
