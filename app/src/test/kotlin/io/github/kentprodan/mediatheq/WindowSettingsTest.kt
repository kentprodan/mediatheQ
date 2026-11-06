package io.github.kentprodan.mediatheq

import android.content.pm.ActivityInfo
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.ScreenOrientation
import org.junit.Test

class WindowSettingsTest {
    @Test
    fun `orientation follows the setting, both sides of each`() {
        assertThat(ScreenOrientation.System.activityOrientation())
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        assertThat(ScreenOrientation.Portrait.activityOrientation())
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT)
        assertThat(ScreenOrientation.Landscape.activityOrientation())
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE)
    }
}
