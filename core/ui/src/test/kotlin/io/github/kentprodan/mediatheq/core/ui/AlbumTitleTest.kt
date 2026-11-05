package io.github.kentprodan.mediatheq.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlbumTitleTest {
    @Test
    fun `a trailing edition becomes the version, without its parentheses`() {
        assertThat(albumTitle("Abbey Road (Deluxe Edition)")).isEqualTo(AlbumTitle("Abbey Road", "Deluxe Edition"))
        assertThat(albumTitle("Bad (25th Anniversary)")).isEqualTo(AlbumTitle("Bad", "25th Anniversary"))
        assertThat(albumTitle("1989 [Taylor's Version]")).isEqualTo(AlbumTitle("1989", "Taylor's Version"))
        assertThat(albumTitle("Nevermind (Remastered)")).isEqualTo(AlbumTitle("Nevermind", "Remastered"))
        assertThat(albumTitle("Rumours (Super Deluxe)")).isEqualTo(AlbumTitle("Rumours", "Super Deluxe"))
        assertThat(albumTitle("Pet Sounds (Mono & Stereo)")).isEqualTo(AlbumTitle("Pet Sounds", "Mono & Stereo"))
        assertThat(albumTitle("MTV Unplugged (Live)")).isEqualTo(AlbumTitle("MTV Unplugged", "Live"))
        assertThat(albumTitle("Satelit (Ediție de colecție) (Collector's Edition)").version)
            .isEqualTo("Collector's Edition")
    }

    @Test
    fun `parentheses that are no edition stay in the name`() {
        listOf(
            "Lose Yourself (feat. Eminem)",
            "Crazy (with Cee-Lo)",
            "Symphony No. 5 (Part 1)",
            "(What's the Story) Morning Glory?",
            "Interlude (Intro)",
            "(Deluxe)",
            "Satelit",
            "Live at Wembley",
            "Untitled ()",
        ).forEach { title -> assertThat(albumTitle(title)).isEqualTo(AlbumTitle(title, null)) }
    }

    @Test
    fun `spaces after the parentheses do not matter`() {
        assertThat(albumTitle("Thriller (Special Edition)  ")).isEqualTo(AlbumTitle("Thriller", "Special Edition"))
    }
}
