package io.github.kentprodan.mediatheq.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import io.github.kentprodan.mediatheq.core.designsystem.R

/** Default M3 type scale with the system font, including the emphasized styles of material3 1.5. */
val MediatheqTypography = Typography()

/**
 * The type scale for [font] (ADR 0026): downloadable Google Fonts, the system font until one is fetched (or offline,
 * or without Play services). Display fonts change headings only (display, headline, title); body and labels stay.
 */
fun mediatheqTypography(font: MqFont): Typography {
    val (heading, body) =
        when (font) {
            MqFont.System -> return MediatheqTypography
            MqFont.Inter -> family("Inter").let { it to it }
            MqFont.Lato -> family("Lato").let { it to it }
            MqFont.Nunito -> family("Nunito").let { it to it }
            MqFont.Roboto -> family("Roboto").let { it to it }
            MqFont.RobotoSlab -> family("Roboto Slab").let { it to it }
            MqFont.LexendDeca -> family("Lexend Deca").let { it to it }
            MqFont.BitterRubik -> family("Bitter") to family("Rubik")
            MqFont.BebasNeue -> family("Bebas Neue") to null
            MqFont.CastoroTitling -> family("Castoro Titling") to null
        }
    val base = body?.let { Typography(fontFamily = it) } ?: MediatheqTypography
    if (heading == body) return base

    fun TextStyle.heading() = copy(fontFamily = heading)
    return with(base) {
        Typography(
            displayLarge = displayLarge.heading(),
            displayMedium = displayMedium.heading(),
            displaySmall = displaySmall.heading(),
            headlineLarge = headlineLarge.heading(),
            headlineMedium = headlineMedium.heading(),
            headlineSmall = headlineSmall.heading(),
            titleLarge = titleLarge.heading(),
            titleMedium = titleMedium.heading(),
            titleSmall = titleSmall.heading(),
            bodyLarge = bodyLarge,
            bodyMedium = bodyMedium,
            bodySmall = bodySmall,
            labelLarge = labelLarge,
            labelMedium = labelMedium,
            labelSmall = labelSmall,
            displayLargeEmphasized = displayLargeEmphasized.heading(),
            displayMediumEmphasized = displayMediumEmphasized.heading(),
            displaySmallEmphasized = displaySmallEmphasized.heading(),
            headlineLargeEmphasized = headlineLargeEmphasized.heading(),
            headlineMediumEmphasized = headlineMediumEmphasized.heading(),
            headlineSmallEmphasized = headlineSmallEmphasized.heading(),
            titleLargeEmphasized = titleLargeEmphasized.heading(),
            titleMediumEmphasized = titleMediumEmphasized.heading(),
            titleSmallEmphasized = titleSmallEmphasized.heading(),
            bodyLargeEmphasized = bodyLargeEmphasized,
            bodyMediumEmphasized = bodyMediumEmphasized,
            bodySmallEmphasized = bodySmallEmphasized,
            labelLargeEmphasized = labelLargeEmphasized,
            labelMediumEmphasized = labelMediumEmphasized,
            labelSmallEmphasized = labelSmallEmphasized,
        )
    }
}

private val provider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

// The weights the type scale uses; a font without one gets the nearest.
private fun family(name: String): FontFamily =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold).map { Font(GoogleFont(name), provider, it) },
    )
