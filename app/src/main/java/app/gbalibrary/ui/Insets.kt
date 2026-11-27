package app.gbalibrary.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier

/** Como safeDrawingPadding pero SIN reservar el recorte de la camara: la interfaz ocupa toda la pantalla. */
@androidx.compose.runtime.Composable
fun Modifier.safeNoCutout(): Modifier = this.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
