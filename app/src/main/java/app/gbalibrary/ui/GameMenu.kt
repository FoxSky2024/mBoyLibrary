@file:OptIn(ExperimentalMaterial3Api::class)

package app.gbalibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import app.gbalibrary.data.Settings

/** Acciones que el menu delega en la Activity. */
class MenuActions(
    val close: () -> Unit,
    val save: (String) -> Unit,
    val load: (String) -> Unit,
    val reset: () -> Unit,
    val screenshot: () -> Unit,
    val exit: () -> Unit,
    val editControls: () -> Unit,
    val saveGameLayout: () -> Unit,
    val saveGlobalLayout: () -> Unit,
    val resetLayout: () -> Unit,
    val persistLayout: () -> Unit,
    val setForceTouch: (Boolean) -> Unit,
    val setSpeed: (Int) -> Unit,
    val toggleMusic: () -> Unit = {},
    val pickPipVideo: () -> Unit = {},
    /** Cuando se guardo la ranura (ms) o 0 si esta vacia. */
    val slotTime: (String) -> Long = { 0L },
    /** Ruta de la miniatura de la ranura (o null si no hay). */
    val slotThumb: (String) -> String? = { null },
)

private enum class PauseView { MAIN, SAVE, LOAD, SETTINGS }

private val SETTING_TABS = listOf("Video", "Audio", "Controles", "Juego", "Ventana")

private val SLOTS = listOf("quick" to "Guardado rápido", "slot1" to "Ranura 1", "slot2" to "Ranura 2", "slot3" to "Ranura 3")

/**
 * Menu de pausa. Misma estructura en vertical y en horizontal: un panel centrado con botones grandes
 * (1 columna si es estrecho, 2 si cabe). Todo se arma con los componentes de Design.kt.
 */
@Composable
fun GameMenu(title: String, speed: Int, layout: ControlLayout, padConnected: Boolean, forceTouch: Boolean, actions: MenuActions) {
    val p = LocalPalette.current
    var view by remember { mutableStateOf(PauseView.MAIN) }
    var confirmReset by remember { mutableStateOf(false) }
    val noRipple = remember { MutableInteractionSource() }

    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000))
            .clickable(interactionSource = noRipple, indication = null) {
                if (view == PauseView.MAIN) actions.close() else view = PauseView.MAIN
            },
        contentAlignment = Alignment.Center,
    ) {
        StatusIndicators(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp), tint = Color.White)
        BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            val wide = maxWidth >= 560.dp
            Column(
                Modifier
                    .widthIn(max = if (view == PauseView.SETTINGS) 760.dp else 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .background(p.panel, RoundedCornerShape(16.dp))
                    .clickable(interactionSource = noRipple, indication = null) {}
                    .padding(Ds.Pad),
                verticalArrangement = Arrangement.spacedBy(Ds.GapL),
            ) {
                // Cabecera: titulo del juego + (si no es el menu principal) volver.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ds.GapL)) {
                    if (view != PauseView.MAIN) {
                        Text(
                            "←", color = p.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { Sfx.click(); view = PauseView.MAIN }.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (view) {
                                PauseView.MAIN -> "Pausa"
                                PauseView.SAVE -> "Guardar partida"
                                PauseView.LOAD -> "Cargar partida"
                                PauseView.SETTINGS -> "Ajustes"
                            },
                            color = p.text, fontSize = Ds.TitleSp.sp, fontWeight = FontWeight.Black,
                        )
                        Text(title, color = p.dim, fontSize = Ds.LabelSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                when (view) {
                    PauseView.MAIN -> Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Ds.GapL),
                    ) {
                        val items: List<Triple<String, BtnKind, () -> Unit>> = listOf(
                            Triple("Continuar", BtnKind.PRIMARY, { actions.close() }),
                            Triple("Guardar", BtnKind.SECONDARY, { view = PauseView.SAVE }),
                            Triple("Cargar", BtnKind.SECONDARY, { view = PauseView.LOAD }),
                            Triple(if (confirmReset) "¿Seguro? Toca otra vez" else "Reiniciar", BtnKind.SECONDARY, {
                                if (confirmReset) { confirmReset = false; actions.reset() } else confirmReset = true
                            }),
                            Triple("Ajustes", BtnKind.SECONDARY, { view = PauseView.SETTINGS }),
                            Triple("Salir", BtnKind.DANGER, { actions.exit() }),
                        )
                        val icons = listOf("▶", "💾", "📂", "↺", "⚙", "⏏")
                        val cols = if (wide) 2 else 1
                        items.chunked(cols).forEachIndexed { rowIdx, rowItems ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ds.GapL)) {
                                rowItems.forEachIndexed { i, (label, kind, act) ->
                                    AppButton(label, act, Modifier.weight(1f), kind = kind, big = true, icon = icons[rowIdx * cols + i])
                                }
                            }
                        }
                        Hint(
                            if (confirmReset) "Reiniciar borra la partida automática de este juego (tus guardados con ranura se conservan)."
                            else "Al salir, tu progreso se guarda solo y se retoma al volver a entrar.",
                        )
                    }
                    PauseView.SAVE -> SlotList(true, actions)
                    PauseView.LOAD -> SlotList(false, actions)
                    PauseView.SETTINGS -> PauseSettings(speed, layout, padConnected, forceTouch, actions, wide)
                }
            }
        }
    }
}

@Composable
private fun SlotList(saving: Boolean, a: MenuActions) {
    val p = LocalPalette.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) } // refresca hora y miniatura tras guardar
    var confirm by remember { mutableStateOf<String?>(null) } // ranura pendiente de confirmar sobrescritura
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Ds.GapL)) {
        SLOTS.forEach { (slot, name) ->
            val time = remember(slot, tick) { a.slotTime(slot) }
            val thumb = remember(slot, tick) { a.slotThumb(slot) }
            val empty = time <= 0L
            Row(
                Modifier.fillMaxWidth()
                    .background(p.card, RoundedCornerShape(Ds.RadiusM))
                    .padding(start = 10.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Ds.GapL),
            ) {
                SlotThumb(if (empty) null else thumb)
                Column(Modifier.weight(1f)) {
                    Text(name, color = p.text, fontSize = Ds.BodySp.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(if (empty) "Vacía" else formatAgo(time), color = p.dim, fontSize = Ds.LabelSp.sp, maxLines = 1)
                }
                if (saving) {
                    val asking = confirm == slot
                    AppButton(
                        if (empty) "Guardar" else if (asking) "¿Seguro?" else "Sobrescribir",
                        {
                            if (empty || asking) {
                                confirm = null
                                a.save(slot)
                                scope.launch { kotlinx.coroutines.delay(700); tick++ }
                            } else confirm = slot
                        },
                        Modifier.widthIn(min = 130.dp),
                        kind = if (asking) BtnKind.DANGER else BtnKind.PRIMARY,
                    )
                } else {
                    AppButton("Cargar", { a.load(slot) }, Modifier.widthIn(min = 120.dp), kind = BtnKind.PRIMARY, enabled = !empty)
                }
            }
        }
        Hint(if (saving) "Guarda el instante exacto del juego. Sobrescribir pide confirmación." else "Carga el instante guardado y continúa desde ahí.")
    }
}

@Composable
private fun PauseSettings(speed: Int, layout: ControlLayout, padConnected: Boolean, forceTouch: Boolean, a: MenuActions, wide: Boolean) {
    var tab by remember { mutableStateOf(0) }
    val body: @Composable () -> Unit = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Ds.GapL)) {
            when (tab) {
                0 -> VideoSettings()
                1 -> AudioSettings()
                2 -> PauseControls(layout, padConnected, forceTouch, a)
                3 -> PauseGame(speed, a)
                else -> {
                    PipControls(pickVideo = a.pickPipVideo)
                }
            }
        }
    }
    if (wide) {
        Row(Modifier.heightIn(max = 420.dp), horizontalArrangement = Arrangement.spacedBy(Ds.GapL)) {
            Column(Modifier.width(150.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                SETTING_TABS.forEachIndexed { i, n -> RailItem(n, "", i == tab) { tab = i } }
            }
            Column(Modifier.weight(1f)) { body() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Ds.GapL)) {
            ChipRow(SETTING_TABS, tab) { tab = it }
            Box(Modifier.heightIn(max = 360.dp)) { body() }
        }
    }
}

@Composable
private fun PauseControls(layout: ControlLayout, padConnected: Boolean, forceTouch: Boolean, a: MenuActions) {
    SwitchRow("Mostrar botones táctiles", Settings.padVisible.value) { Settings.setPadVisible(it) }
    if (padConnected) SwitchRow("Mostrar táctiles aunque haya mando", forceTouch) { a.setForceTouch(it) }
    SliderRow("Tamaño general", Settings.padSize.floatValue, 0.6f..1.6f, "${(Settings.padSize.floatValue * 100).toInt()}%") { Settings.setPadSize(it) }
    SliderRow("Opacidad general", Settings.padOpacity.floatValue, 0.15f..1f, "${(Settings.padOpacity.floatValue * 100).toInt()}%") { Settings.setPadOpacity(it) }
    Subtitle("Tipo de movimiento")
    ChipRow(listOf("Cruceta", "Joystick"), layout.moveType) {
        layout.moveType = it
        a.persistLayout()
    }
    if (layout.moveType == MoveType.STICK) {
        SliderRow("Zona muerta", layout.deadzone, 0.05f..0.7f, "${(layout.deadzone * 100).toInt()}%", { layout.deadzone = it }, onFinished = a.persistLayout)
    }
    ButtonFlow {
        AppButton("Editar controles", a.editControls, kind = BtnKind.PRIMARY)
        AppButton("Guardar para este juego", a.saveGameLayout)
        AppButton("Guardar global", a.saveGlobalLayout)
        AppButton("Restablecer", a.resetLayout)
    }
    Hint("En el editor puedes mover, escalar y ocultar cada botón. Se guarda un layout distinto para cada orientación.")
}

@Composable
private fun PauseGame(speed: Int, a: MenuActions) {
    Subtitle("Velocidad")
    ChipRow(listOf("x1", "Rápido x${Settings.ffSpeed.intValue}"), if (speed > 1) 1 else 0) {
        a.setSpeed(if (it == 0) 1 else Settings.ffSpeed.intValue)
    }
    if (Settings.musicInGame.value && Music.hasSource()) {
        Subtitle("Música")
        ButtonFlow {
            AppButton(if (Music.playing.value) "Pausar música" else "Reanudar música", a.toggleMusic)
            AppButton("Siguiente", { Music.next() })
        }
        SliderRow("Volumen música", Settings.musicVolume.floatValue, 0f..1f, "${(Settings.musicVolume.floatValue * 100).toInt()}%") {
            Settings.setMusicVolume(it)
            Music.applyVolume()
        }
    }
    Subtitle("Rendimiento")
    PerfControls()
    AppButton("Captura de pantalla", a.screenshot)
}
