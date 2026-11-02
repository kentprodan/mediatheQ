package io.github.kentprodan.mediatheq

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.TrackInfo
import io.github.kentprodan.mediatheq.core.ui.MediaListStyle
import io.github.kentprodan.mediatheq.core.ui.TrackDetail
import org.junit.Test

class MediaListStylesTest {
    private fun style(
        lists: MediaListPreferences = MediaListPreferences(),
        rating: RatingMode = RatingMode.WholeStars,
    ) = LibraryPreferences(ratingMode = rating, mediaLists = lists).toMediaListStyle(emptySet())

    @Test
    fun `the defaults map to the list style defaults`() {
        assertThat(style()).isEqualTo(MediaListStyle())
    }

    @Test
    fun `every track info maps to the detail of the same name, None to nothing`() {
        TrackInfo.entries.forEach { info ->
            val details = style(MediaListPreferences(trackInfo = info)).trackDetails
            if (info == TrackInfo.None) {
                assertThat(details).isEmpty()
            } else {
                assertThat(details.single().name).isEqualTo(info.name)
            }
        }
    }

    @Test
    fun `the first info shows above the second`() {
        val details =
            style(MediaListPreferences(trackInfo = TrackInfo.Codec, secondTrackInfo = TrackInfo.PlayCount)).trackDetails

        assertThat(details).containsExactly(TrackDetail.Codec, TrackDetail.PlayCount).inOrder()
    }

    @Test
    fun `the rating mode decides the stars, Off hides them`() {
        assertThat(style(rating = RatingMode.WholeStars).halfStars).isFalse()
        assertThat(style(rating = RatingMode.HalfStars).halfStars).isTrue()
        assertThat(style(rating = RatingMode.Off).halfStars).isNull()
    }
}
