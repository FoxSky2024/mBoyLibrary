package app.gbalibrary.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.gbalibrary.R

/**
 * TIPOGRAFIAS DE LA APP (cambiar aqui para cambiar todo el texto).
 * Fuentes en res/font: Barlow (texto) y Rajdhani (titulos). Para usar otra: copia el .ttf a res/font y cambia Font(R.font.xxx).
 */
object AppFonts {
    val Body = FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_bold, FontWeight.Bold),
    )
    val Display = FontFamily(
        Font(R.font.rajdhani_medium, FontWeight.Medium),
        Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
        Font(R.font.rajdhani_bold, FontWeight.Bold),
    )

    /** Typography de Material: titulos/etiquetas grandes en Display, el resto en Body. */
    val typography: Typography = Typography().let { t ->
        t.copy(
            displayLarge = t.displayLarge.copy(fontFamily = Display), displayMedium = t.displayMedium.copy(fontFamily = Display),
            displaySmall = t.displaySmall.copy(fontFamily = Display), headlineLarge = t.headlineLarge.copy(fontFamily = Display),
            headlineMedium = t.headlineMedium.copy(fontFamily = Display), headlineSmall = t.headlineSmall.copy(fontFamily = Display),
            titleLarge = t.titleLarge.copy(fontFamily = Display), titleMedium = t.titleMedium.copy(fontFamily = Display),
            titleSmall = t.titleSmall.copy(fontFamily = Display),
            bodyLarge = t.bodyLarge.copy(fontFamily = Body), bodyMedium = t.bodyMedium.copy(fontFamily = Body),
            bodySmall = t.bodySmall.copy(fontFamily = Body), labelLarge = t.labelLarge.copy(fontFamily = Body),
            labelMedium = t.labelMedium.copy(fontFamily = Body), labelSmall = t.labelSmall.copy(fontFamily = Body),
        )
    }
}
