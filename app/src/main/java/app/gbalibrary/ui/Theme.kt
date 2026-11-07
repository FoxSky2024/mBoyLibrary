package app.gbalibrary.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SteamBg = Color(0xFF1B2838)
val SteamPanel = Color(0xFF171A21)
val SteamCard = Color(0xFF2A475E)
val SteamAccent = Color(0xFF66C0F4)
val SteamGreen = Color(0xFF5C7E10)
val SteamGreenLight = Color(0xFF799905)
val SteamText = Color(0xFFC7D5E0)

@Composable
fun GbaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SteamAccent,
            onPrimary = Color.Black,
            secondary = SteamGreenLight,
            background = SteamBg,
            onBackground = SteamText,
            surface = SteamPanel,
            onSurface = SteamText,
            surfaceVariant = SteamCard,
            onSurfaceVariant = SteamText,
        ),
        content = content,
    )
}
