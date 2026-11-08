package app.gbalibrary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import app.gbalibrary.input.PadInput

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
)


// ---------------------------------------------------------------------------------------------
// Menu de pausa y ajustes dentro del juego, estilo Hollow Knight: fondo oscuro, lista centrada de
// texto serif sin botones ni tarjetas, adornos de rombo y punteros a los lados de la opcion
// seleccionada, valores con flechas (◂ ▸) y barras de muescas para los deslizadores.
// Se maneja con toque, mando (D-Pad / stick, A, B, Start) o teclado.
// ---------------------------------------------------------------------------------------------

private val HkText = Color(0xFFEFE9DC)
private val HkDim = Color(0xFF7D786B)
private val HkLine = Color(0xFFB9B2A0)
private val HkDanger = Color(0xFFE57373)
private val HkSerif = FontFamily.Serif

private sealed class HkRow {
    class Action(val label: String, val enabled: Boolean = true, val danger: Boolean = false, val onClick: () -> Unit) : HkRow()
    class Toggle(val label: String, val value: Boolean, val onChange: (Boolean) -> Unit) : HkRow()
    class Choice(val label: String, val options: List<String>, val index: Int, val onChange: (Int) -> Unit) : HkRow()
    class Slide(
        val label: String,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val shown: String,
        val onChange: (Float) -> Unit,
        val onFinished: (() -> Unit)? = null,
    ) : HkRow()
    class Note(val text: String) : HkRow()
    class Head(val text: String) : HkRow()

    val selectable: Boolean get() = this !is Note && this !is Head
}

private enum class HkScreen(val title: String) {
    PAUSE("Pausa"), SAVE("Guardar partida"), LOAD("Cargar partida"), OPTIONS("Ajustes"),
    VIDEO("Video"), AUDIO("Audio"), CONTROLS("Controles"), GAME("Juego"), WINDOW("Ventana de video"),
}

private val HK_SLOTS = listOf("quick" to "Guardado rápido", "slot1" to "Ranura 1", "slot2" to "Ranura 2", "slot3" to "Ranura 3")

@Composable
fun GameMenu(title: String, speed: Int, layout: ControlLayout, padConnected: Boolean, forceTouch: Boolean, actions: MenuActions) {
    var stack by remember { mutableStateOf(listOf(HkScreen.PAUSE)) }
    val screen = stack.last()
    val sels = remember { mutableStateMapOf<HkScreen, Int>() }
    var confirmReset by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) } // refresca la hora de las ranuras tras guardar

    fun push(s: HkScreen) { stack = stack + s }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) else actions.close() }

    // ---- Filas de cada pantalla (se vuelven a armar al cambiar cualquier ajuste) ----
    val rows: List<HkRow> = buildList {
        val back = HkRow.Action("Atrás") { pop() }
        when (screen) {
            HkScreen.PAUSE -> {
                add(HkRow.Action("Continuar") { actions.close() })
                add(HkRow.Action("Guardar") { push(HkScreen.SAVE) })
                add(HkRow.Action("Cargar") { push(HkScreen.LOAD) })
                add(HkRow.Action(if (confirmReset) "¿Seguro? Reiniciar" else "Reiniciar", danger = confirmReset) {
                    if (confirmReset) { confirmReset = false; actions.reset() } else confirmReset = true
                })
                add(HkRow.Action("Ajustes") { push(HkScreen.OPTIONS) })
                add(HkRow.Action("Salir al menú principal", danger = true) { actions.exit() })
                add(HkRow.Note(
                    if (confirmReset) "Reiniciar borra la partida automática de este juego (tus guardados con ranura se conservan)."
                    else "Al salir, tu progreso se guarda solo y se retoma al volver a entrar.",
                ))
            }
            HkScreen.SAVE, HkScreen.LOAD -> {
                val saving = screen == HkScreen.SAVE
                HK_SLOTS.forEach { (slot, name) ->
                    val time = remember(slot, tick) { actions.slotTime(slot) }
                    val empty = time <= 0L
                    add(HkRow.Action("$name  ·  ${if (empty) "vacía" else formatAgo(time)}", enabled = saving || !empty) {
                        if (saving) { actions.save(slot); tick++ } else actions.load(slot)
                    })
                }
                add(HkRow.Note(if (saving) "Guarda el instante exacto del juego." else "Carga el instante guardado y continúa desde ahí."))
                add(back)
            }
            HkScreen.OPTIONS -> {
                add(HkRow.Action("Video") { push(HkScreen.VIDEO) })
                add(HkRow.Action("Audio") { push(HkScreen.AUDIO) })
                add(HkRow.Action("Controles") { push(HkScreen.CONTROLS) })
                add(HkRow.Action("Juego") { push(HkScreen.GAME) })
                add(HkRow.Action("Ventana de video") { push(HkScreen.WINDOW) })
                add(back)
            }
            HkScreen.VIDEO -> {
                add(HkRow.Choice("Calidad de imagen", listOf("Pixel Perfect", "Suave", "CRT"), Settings.filter.intValue) { Settings.setFilter(it) })
                add(HkRow.Slide("Brillo", Settings.brightness.floatValue, 0.5f..1.5f, "${(Settings.brightness.floatValue * 100).toInt()}%", { Settings.setBrightness(it) }))
                add(HkRow.Choice("Escala", listOf("Ajustar", "Original", "Estirar", "Llenar"), Settings.scale.intValue) { Settings.setScale(it) })
                add(HkRow.Toggle("Mostrar FPS", Settings.showFps.value) { Settings.setShowFps(it) })
                add(back)
            }
            HkScreen.AUDIO -> {
                add(HkRow.Toggle("Sonido del juego", !Settings.muted.value) { Settings.setMuted(!it) })
                add(HkRow.Slide("Volumen del juego", Settings.volume.intValue.toFloat(), 0f..100f, "${Settings.volume.intValue}%", { Settings.setVolume(it.toInt()) }))
                add(HkRow.Note("Si el sonido se corta, cambia de salida de audio o vuelve a abrir el juego: la app recrea el audio sola."))
                add(back)
            }
            HkScreen.CONTROLS -> {
                add(HkRow.Toggle("Botones táctiles", Settings.padVisible.value) { Settings.setPadVisible(it) })
                if (padConnected) add(HkRow.Toggle("Táctiles aunque haya mando", forceTouch) { actions.setForceTouch(it) })
                add(HkRow.Slide("Tamaño general", Settings.padSize.floatValue, 0.6f..1.6f, "${(Settings.padSize.floatValue * 100).toInt()}%", { Settings.setPadSize(it) }))
                add(HkRow.Slide("Opacidad general", Settings.padOpacity.floatValue, 0.15f..1f, "${(Settings.padOpacity.floatValue * 100).toInt()}%", { Settings.setPadOpacity(it) }))
                add(HkRow.Choice("Tipo de movimiento", listOf("Cruceta", "Joystick"), layout.moveType) {
                    layout.moveType = it
                    actions.persistLayout()
                })
                if (layout.moveType == MoveType.STICK) {
                    add(HkRow.Slide("Zona muerta", layout.deadzone, 0.05f..0.7f, "${(layout.deadzone * 100).toInt()}%", { layout.deadzone = it }, actions.persistLayout))
                }
                add(HkRow.Action("Editar controles") { actions.editControls() })
                add(HkRow.Action("Guardar para este juego") { actions.saveGameLayout() })
                add(HkRow.Action("Guardar global") { actions.saveGlobalLayout() })
                add(HkRow.Action("Restablecer") { actions.resetLayout() })
                add(HkRow.Note("En el editor puedes mover, escalar y ocultar cada botón. Se guarda un layout distinto para cada orientación."))
                add(back)
            }
            HkScreen.GAME -> {
                add(HkRow.Choice("Velocidad", listOf("x1", "Rápido x${Settings.ffSpeed.intValue}"), if (speed > 1) 1 else 0) {
                    actions.setSpeed(if (it == 0) 1 else Settings.ffSpeed.intValue)
                })
                if (Settings.musicInGame.value && Music.hasSource()) {
                    add(HkRow.Head("Música"))
                    add(HkRow.Action(if (Music.playing.value) "Pausar música" else "Reanudar música") { actions.toggleMusic() })
                    add(HkRow.Action("Siguiente canción") { Music.next() })
                    add(HkRow.Slide("Volumen música", Settings.musicVolume.floatValue, 0f..1f, "${(Settings.musicVolume.floatValue * 100).toInt()}%", {
                        Settings.setMusicVolume(it)
                        Music.applyVolume()
                    }))
                }
                add(HkRow.Head("Rendimiento"))
                add(HkRow.Choice("Calidad de renderizado", listOf("Bajo", "Medio", "Alto"), Settings.renderQuality.intValue) { Settings.setRenderQuality(it) })
                add(HkRow.Choice("Límite de FPS", listOf("30", "60", "Sin límite"), when (Settings.fpsLimit.intValue) { 30 -> 0; 0 -> 2; else -> 1 }) {
                    Settings.setFpsLimit(when (it) { 0 -> 30; 2 -> 0; else -> 60 })
                })
                add(HkRow.Choice("Fast-Forward", listOf("x2", "x3", "x4"), Settings.ffSpeed.intValue - 2) { Settings.setFfSpeed(it + 2) })
                add(HkRow.Toggle("Saltar frames", Settings.frameSkip.value) { Settings.setFrameSkip(it) })
                add(HkRow.Toggle("Sincronización de audio", Settings.audioSync.value) { Settings.setAudioSync(it) })
                add(HkRow.Action("Captura de pantalla") { actions.screenshot() })
                add(back)
            }
            HkScreen.WINDOW -> {
                add(HkRow.Action("Video de la galería") { actions.pickPipVideo() })
                if (Pip.kind.intValue != Pip.NONE) add(HkRow.Action("Cerrar ventana") { Pip.close() })
                add(HkRow.Choice("Posición", listOf("Sup. izquierda", "Sup. derecha", "Inf. izquierda", "Inf. derecha"), Settings.pipCorner.intValue) {
                    Settings.setPipCorner(it)
                    Pip.custom.value = null
                })
                add(HkRow.Choice("Tamaño", listOf("Pequeño", "Mediano", "Grande"), Settings.pipSize.intValue) { Settings.setPipSize(it) })
                add(HkRow.Slide("Opacidad", Settings.pipOpacity.floatValue, 0.2f..1f, "${(Settings.pipOpacity.floatValue * 100).toInt()}%", { Settings.setPipOpacity(it) }))
                add(HkRow.Toggle("Siempre encima", Settings.pipOnTop.value) { Settings.setPipOnTop(it) })
                add(HkRow.Note("Arrastra la ventana desde su barra superior; ✕ la cierra."))
                add(back)
            }
        }
    }

    // ---- Seleccion y acciones ----
    val selectable = rows.indices.filter { rows[it].selectable }
    var sel = sels[screen] ?: selectable.firstOrNull() ?: 0
    if (sel !in selectable) sel = selectable.firstOrNull() ?: 0

    fun adjust(r: HkRow, dir: Int) {
        when (r) {
            is HkRow.Toggle -> r.onChange(!r.value)
            is HkRow.Choice -> r.onChange((r.index + dir + r.options.size) % r.options.size)
            is HkRow.Slide -> {
                val step = (r.range.endInclusive - r.range.start) / 20f
                r.onChange((r.value + dir * step).coerceIn(r.range.start, r.range.endInclusive))
                r.onFinished?.invoke()
            }
            else -> {}
        }
    }

    fun activate(r: HkRow) {
        when (r) {
            is HkRow.Action -> if (r.enabled) { Sfx.click(); r.onClick() }
            is HkRow.Toggle, is HkRow.Choice -> { Sfx.click(); adjust(r, 1) }
            else -> {}
        }
    }

    val onNav: (NavKey) -> Unit = onNav@{ k ->
        if (selectable.isEmpty()) return@onNav
        val pos = selectable.indexOf(sel).coerceAtLeast(0)
        when (k) {
            NavKey.UP -> sels[screen] = selectable[(pos - 1 + selectable.size) % selectable.size]
            NavKey.DOWN -> sels[screen] = selectable[(pos + 1) % selectable.size]
            NavKey.LEFT -> adjust(rows[sel], -1)
            NavKey.RIGHT -> adjust(rows[sel], 1)
            NavKey.OK -> activate(rows[sel])
            NavKey.BACK -> pop()
            NavKey.START -> actions.close()
            NavKey.NONE -> {}
        }
        Unit
    }
    val navNow by rememberUpdatedState(onNav)
    LaunchedEffect(Unit) { MenuNav.events.collect { navNow(it) } }

    val listState = remember(screen) { LazyListState() }
    LaunchedEffect(screen, sel) {
        val info = listState.layoutInfo
        val vis = info.visibleItemsInfo.firstOrNull { it.index == sel }
        if (vis == null || vis.offset < 0 || vis.offset + vis.size > info.viewportEndOffset) {
            listState.animateScrollToItem((sel - 1).coerceAtLeast(0))
        }
    }

    // ---- Dibujo ----
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(220)) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Brush.radialGradient(listOf(Color(0xF2121212), Color(0xFA000000))))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.TopCenter,
    ) {
        StatusIndicators(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp), tint = HkText)
        Column(
            Modifier.fillMaxHeight().widthIn(max = 560.dp).fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                screen.title.uppercase(),
                style = TextStyle(fontFamily = HkSerif, fontSize = 26.sp, color = HkText, letterSpacing = 6.sp, textAlign = TextAlign.Center),
                maxLines = 1,
            )
            if (screen == HkScreen.PAUSE) {
                Text(
                    title, style = TextStyle(fontFamily = HkSerif, fontSize = 13.sp, color = HkDim, textAlign = TextAlign.Center),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, start = 24.dp, end = 24.dp),
                )
            }
            HkDivider(Modifier.padding(top = 6.dp, bottom = 2.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
            ) {
                itemsIndexed(rows) { i, r ->
                    HkRowView(
                        row = r,
                        selected = r.selectable && i == sel,
                        onSelect = { if (r.selectable) sels[screen] = i },
                        onActivate = { activate(r) },
                        onAdjust = { d -> adjust(r, d) },
                    )
                }
            }
            if (PadInput.connected.value) {
                Text(
                    "▲▼ mover    ◂▸ cambiar    A aceptar    B atrás    Start continuar",
                    style = TextStyle(fontFamily = HkSerif, fontSize = 11.sp, color = HkDim, textAlign = TextAlign.Center),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HkRowView(row: HkRow, selected: Boolean, onSelect: () -> Unit, onActivate: () -> Unit, onAdjust: (Int) -> Unit) {
    if (row is HkRow.Note) {
        Text(
            row.text, style = TextStyle(fontFamily = HkSerif, fontSize = 12.sp, color = HkDim, textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        )
        return
    }
    if (row is HkRow.Head) {
        Text(
            row.text.uppercase(),
            style = TextStyle(fontFamily = HkSerif, fontSize = 12.sp, color = HkLine, letterSpacing = 4.sp, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
        )
        return
    }
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(140), label = "hkSel")
    val noRipple = remember { MutableInteractionSource() }
    val labelColor = when {
        row is HkRow.Action && !row.enabled -> HkDim.copy(alpha = 0.5f)
        row is HkRow.Action && row.danger -> if (selected) HkDanger else HkDanger.copy(alpha = 0.7f)
        selected -> Color.White
        else -> HkText.copy(alpha = 0.78f)
    }
    val glow = if (selected) Shadow(Color(0x88FFFFFF), Offset.Zero, 16f) else null
    fun label(t: String, size: Int = 21) = TextStyle(
        fontFamily = HkSerif, fontSize = size.sp, color = labelColor, letterSpacing = 1.sp, textAlign = TextAlign.Center, shadow = glow,
    )
    val valueColor = if (selected) HkText else HkDim

    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp)
            .clickable(interactionSource = noRipple, indication = null) { onSelect(); onActivate() }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HkPointer(left = true, alpha = a)
        Column(Modifier.widthIn(min = 230.dp, max = 380.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (row) {
                is HkRow.Action -> Text(row.label, style = label(row.label), maxLines = 2, overflow = TextOverflow.Ellipsis)
                is HkRow.Toggle -> {
                    Text(row.label, style = label(row.label), maxLines = 2)
                    HkValue(if (row.value) "Activado" else "Desactivado", valueColor, onPrev = { onSelect(); onAdjust(-1) }, onNext = { onSelect(); onAdjust(1) })
                }
                is HkRow.Choice -> {
                    Text(row.label, style = label(row.label), maxLines = 2)
                    HkValue(row.options.getOrElse(row.index) { "" }, valueColor, onPrev = { onSelect(); onAdjust(-1) }, onNext = { onSelect(); onAdjust(1) })
                }
                is HkRow.Slide -> {
                    Text("${row.label}   ${row.shown}", style = label(row.label), maxLines = 2)
                    HkBar(row.value, row.range, selected, row.onChange, row.onFinished, onSelect)
                }
                else -> {}
            }
        }
        HkPointer(left = false, alpha = a)
    }
}

@Composable
private fun HkValue(text: String, color: Color, onPrev: () -> Unit, onNext: () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("◂", style = TextStyle(fontFamily = HkSerif, fontSize = 18.sp, color = color),
            modifier = Modifier.clickable(interactionSource = noRipple, indication = null, onClick = onPrev).padding(horizontal = 10.dp, vertical = 2.dp))
        Text(text, style = TextStyle(fontFamily = HkSerif, fontSize = 15.sp, color = color, textAlign = TextAlign.Center), maxLines = 1)
        Text("▸", style = TextStyle(fontFamily = HkSerif, fontSize = 18.sp, color = color),
            modifier = Modifier.clickable(interactionSource = noRipple, indication = null, onClick = onNext).padding(horizontal = 10.dp, vertical = 2.dp))
    }
}

/** Deslizador de muescas (20 marcas), se arrastra o se toca. */
@Composable
private fun HkBar(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    active: Boolean,
    onChange: (Float) -> Unit,
    onFinished: (() -> Unit)?,
    onTouch: () -> Unit,
) {
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    val touch by rememberUpdatedState(onTouch)
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    fun at(x: Float, w: Float) = range.start + (x / w).coerceIn(0f, 1f) * (range.endInclusive - range.start)
    Canvas(
        Modifier.width(260.dp).height(28.dp)
            .pointerInput(range) { detectTapGestures { off -> touch(); change(at(off.x, size.width.toFloat())); finished?.invoke() } }
            .pointerInput(range) {
                detectHorizontalDragGestures(onDragStart = { touch() }, onDragEnd = { finished?.invoke() }) { ch, _ ->
                    ch.consume()
                    change(at(ch.position.x, size.width.toFloat()))
                }
            },
    ) {
        val n = 20
        val gap = size.width / n
        for (i in 0 until n) {
            val on = (i + 0.5f) / n <= frac + 0.001f
            val c = when {
                on && active -> HkText
                on -> HkLine.copy(alpha = 0.8f)
                else -> Color(0x33FFFFFF)
            }
            drawRect(c, Offset(i * gap + gap * 0.2f, size.height * 0.18f), Size(gap * 0.6f, size.height * 0.64f))
        }
    }
}

/** Linea horizontal que se desvanece en los extremos con un rombo en el centro. */
@Composable
private fun HkDivider(modifier: Modifier = Modifier) {
    Canvas(modifier.width(300.dp).height(16.dp)) {
        val cy = size.height / 2
        val cx = size.width / 2
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, HkLine, HkLine, Color.Transparent)),
            Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1.5f,
        )
        fun diamond(x: Float, r: Float, filled: Boolean) {
            val path = Path().apply { moveTo(x, cy - r); lineTo(x + r, cy); lineTo(x, cy + r); lineTo(x - r, cy); close() }
            drawPath(path, Color(0xFF050505))
            drawPath(path, HkText, style = Stroke(1.5f))
            if (filled) drawPath(path, HkText)
        }
        diamond(cx - 34.dp.toPx(), 2.5.dp.toPx(), true)
        diamond(cx + 34.dp.toPx(), 2.5.dp.toPx(), true)
        diamond(cx, 6.dp.toPx(), false)
    }
}

/** Puntero ornamental a cada lado de la opcion seleccionada (siempre reserva su espacio). */
@Composable
private fun HkPointer(left: Boolean, alpha: Float) {
    Canvas(Modifier.width(40.dp).height(16.dp).graphicsLayer { this.alpha = alpha }) {
        val cy = size.height / 2
        val r = 5.dp.toPx()
        val tipX = if (left) size.width - 4.dp.toPx() - r else 4.dp.toPx() + r
        val farX = if (left) 2.dp.toPx() else size.width - 2.dp.toPx()
        drawLine(
            Brush.horizontalGradient(if (left) listOf(Color.Transparent, HkText) else listOf(HkText, Color.Transparent)),
            Offset(farX, cy), Offset(tipX, cy), strokeWidth = 1.5f,
        )
        val path = Path().apply { moveTo(tipX, cy - r); lineTo(tipX + r, cy); lineTo(tipX, cy + r); lineTo(tipX - r, cy); close() }
        drawPath(path, HkText)
    }
}
