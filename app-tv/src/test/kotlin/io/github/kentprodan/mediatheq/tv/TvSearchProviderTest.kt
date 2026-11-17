package io.github.kentprodan.mediatheq.tv

import android.app.Application
import android.app.SearchManager
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.MovieKey
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TvSearchProviderTest {
    private val movie = MediaId(ServerId("s"), "m1")

    @Test
    fun `the cursor has the system search's columns, one row per suggestion with its link`() {
        val link = openUri("movie", movie)
        val cursor = suggestionCursor(listOf(SearchRow("Alien", "1979", "http://img", link, 1979, 7_000_000)))

        assertThat(cursor.count).isEqualTo(1)
        cursor.moveToFirst()
        assertThat(
            cursor.getString(cursor.getColumnIndexOrThrow(SearchManager.SUGGEST_COLUMN_TEXT_1)),
        ).isEqualTo("Alien")
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow(SearchManager.SUGGEST_COLUMN_INTENT_DATA)))
            .isEqualTo(link.toString())
        assertThat(
            cursor.getInt(cursor.getColumnIndexOrThrow(SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR)),
        ).isEqualTo(1979)
    }

    @Test
    fun `a suggestion's link opens its page, other links nothing`() {
        assertThat(parseOpenUri(openUri("movie", movie))).isEqualTo(MovieKey(movie))
        assertThat(parseOpenUri(openUri("album", movie))).isEqualTo(AlbumKey(movie))
        assertThat(parseOpenUri(openUri("track", movie))).isNull()
        assertThat(parseOpenUri(Uri.parse("mediatheq://video/s/m1"))).isNull()
    }
}
