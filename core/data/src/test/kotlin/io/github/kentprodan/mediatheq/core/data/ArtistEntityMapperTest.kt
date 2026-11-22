package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.ImageType
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Test

class ArtistEntityMapperTest {
    @Test
    fun `an artist keeps its picture, backdrop, biography and logo through its row (#523)`() {
        val artist =
            Artist(
                MediaId(ServerId("s"), "a1"),
                "Ada",
                "ada",
                Artwork("a1", "p1"),
                backdrop = Artwork("a1", "b1", type = ImageType.Backdrop),
                logo = Artwork("a1", "l1", type = ImageType.Logo),
                overview = "Pianist.",
            )

        assertThat(artist.toEntity(isAlbumArtist = true).toDomain()).isEqualTo(artist)
    }
}
