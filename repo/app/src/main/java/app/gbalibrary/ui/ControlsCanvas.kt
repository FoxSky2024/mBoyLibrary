@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import app.gbalibrary.emu.Engine
import kotlin.math.roundToInt

/**
 * Dibuja todos los controles tactiles del [layout]. Con [editing] se pueden arrastrar y
 * seleccionar (sin enviar teclas); [engine] puede ser null (vista previa en Ajustes).
 */
@Composable
fun ControlsCanvas(
    layout: ControlLayout,
    engine: Engine?,
    editing: Boolean,
    showPad: Boolean,
    onMenu: () -> Unit,
    onQuick: () -> Unit,
) {
    val live = !editing && engine != null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        val ds = app.gbalibrary.emu.Native.isDs
        if (ds) layout.ensureDsButtons()
        for (item in layout.items.toList()) {
            if ((item.kind == Kind.X || item.kind == Kind.Y) && !ds && !editing) continue
            val isSystem = item.kind == Kind.MENU
            if (!isSystem && !showPad && !editing) continue
            if (item.hidden && !editing) continue
            PlacedItem(item, layout, engine, sw, sh, editing, live, onMenu, onQuick)
        }
    }
}

@Composable
private fun PlacedItem(
    item: PadItem,
    layout: ControlLayout,
    engine: Engine?,
    sw: Float,
    sh: Float,
    editing: Boolean,
    live: Boolean,
    onMenu: () -> Unit,
    onQuick: () -> Unit,
) {
    val d = LocalDensity.current
    val k = Settings.padSize.floatValue * item.size
    val wDp = Kind.baseW(item.kind) * k
    val hDp = Kind.baseH(item.kind) * k
    val wPx = with(d) { wDp.dp.toPx() }
    val hPx = with(d) { hDp.dp.toPx() }
    var a = (item.alpha * Settings.padOpacity.floatValue / 0.55f).coerceIn(0.1f, 1f)
    if (item.kind == Kind.MENU) a = maxOf(a, 0.45f)
    if (item.hidden) a = 0.2f // solo se ve asi dentro del editor, para poder volver a mostrarlo
    val selected = editing && layout.selected == item.id
    val source = Engine.SOURCE_WIDGET_BASE + layout.slotOf(item.id) % (Engine.SOURCE_COUNT - Engine.SOURCE_WIDGET_BASE)

    Box(
        Modifier
            .offset { IntOffset((item.x * sw - wPx / 2f).roundToInt(), (item.y * sh - hPx / 2f).roundToInt()) }
            .size(wDp.dp, hDp.dp)
            .alpha(a),
    ) {
        when (item.kind) {
            Kind.MOVE ->
                if (layout.moveType == MoveType.STICK) Stick(engine, live, layout.deadzone, source = source)
                else DPad(engine, live, source = source)
            Kind.MENU -> RoundTap(Icons.Filled.Menu, live, onMenu)
            Kind.QUICK -> RoundTap(Icons.Filled.FlashOn, live, onQuick)
            Kind.L, Kind.R -> PadButton(Kind.glyph(item.kind), Kind.mask(item.kind), engine, live, shape = RoundedCornerShape(14.dp), source = source)
            Kind.START, Kind.SELECT ->
                PadButton(Kind.glyph(item.kind), Kind.mask(item.kind), engine, live, shape = RoundedCornerShape(17.dp), fontSize = 11, source = source)
            else ->
                PadButton(Kind.glyph(item.kind), Kind.mask(item.kind), engine, live, fontSize = if (item.kind.length > 1 && item.kind.length < 3) 18 else 17, source = source)
        }
        if (editing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(if (selected) 3.dp else 2.dp, if (selected) Color(0xFF00E676) else Color(0xFFFFEB3B), RoundedCornerShape(8.dp))
                    .pointerInput(item.id) { detectTapGestures { layout.selected = item.id } }
                    .pointerInput(item.id) {
                        detectDragGestures(onDragStart = { layout.selected = item.id }) { change, drag ->
                            change.consume()
                            layout.move(item.id, drag.x / sw, drag.y / sh)
                        }
                    },
            )
        }
    }
}

@Composable
private fun RoundTap(glyph: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxSize().clip(CircleShape).background(Color(0x66000000)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ic(glyph, size = 22.dp, tint = Color.White) }
}

/**
 * Panel del modo "Editar controles": agregar botones, tipo de movimiento, tamano / opacidad /
 * mapeo / borrar del seleccionado, restablecer y guardar (global o por juego).
 * [gameName] es null en Ajustes (solo hay layout global).
 */
@Composable
fun ControlsEditorPanel(
    layout: ControlLayout,
    gameName: String?,
    onSaveGlobal: () -> Unit,
    onSaveGame: (() -> Unit)?,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    var addOpen by remember { mutableStateOf(false) }
    val sel = layout.find(layout.selected)
    var collapsed by remember { mutableStateOf(false) }
    Column(
        modifier
            .widthIn(max = 560.dp)
            .background(Color(0xE6101418), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Editar controles — arrastra para mover, toca para seleccionar", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { collapsed = !collapsed }) { Ic(if (collapsed) Icons.Filled.ArrowDropDown else Icons.Filled.ArrowDropUp, size = 22.dp, tint = p.accent) }
            if (collapsed) Button(onClick = onDone) { Text("Listo") }
        }
        if (!collapsed) EditorBody(layout, gameName, sel, addOpen, { addOpen = it }, onSaveGlobal, onSaveGame, onReset, onDone)
    }
}

@Composable
private fun EditorBody(
    layout: ControlLayout,
    gameName: String?,
    sel: PadItem?,
    addOpen: Boolean,
    setAddOpen: (Boolean) -> Unit,
    onSaveGlobal: () -> Unit,
    onSaveGame: (() -> Unit)?,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = layout.moveType == MoveType.CROSS, onClick = { layout.moveType = MoveType.CROSS }, label = { Text("Cruceta") })
            FilterChip(selected = layout.moveType == MoveType.STICK, onClick = { layout.moveType = MoveType.STICK }, label = { Text("Joystick") })
            OutlinedButton(onClick = { setAddOpen(!addOpen) }) { Text(if (addOpen) "Cerrar" else "+ Botón") }
            OutlinedButton(onClick = onReset) { Text("Restablecer") }
        }
        if (layout.moveType == MoveType.STICK) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zona muerta ${(layout.deadzone * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.widthIn(min = 110.dp))
                Slider(value = layout.deadzone, onValueChange = { layout.deadzone = it }, valueRange = 0.05f..0.7f, modifier = Modifier.weight(1f))
            }
        }
        if (addOpen) {
            Text("Mapear el nuevo botón a:", color = Color.White, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Kind.ASSIGNABLE.forEach { k ->
                    FilterChip(selected = false, onClick = { layout.add(k); setAddOpen(false) }, label = { Text(Kind.label(k)) })
                }
            }
        }
        if (sel != null) {
            Text("Seleccionado: ${Kind.label(sel.kind)}${if (sel.extra) " (extra)" else ""}", color = p.accent, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tamaño", color = Color.White, fontSize = 12.sp, modifier = Modifier.widthIn(min = 70.dp))
                Slider(value = sel.size, onValueChange = { layout.setSize(sel.id, it) }, valueRange = 0.5f..2.5f, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opacidad", color = Color.White, fontSize = 12.sp, modifier = Modifier.widthIn(min = 70.dp))
                Slider(value = sel.alpha, onValueChange = { layout.setAlpha(sel.id, it) }, valueRange = 0.1f..1f, modifier = Modifier.weight(1f))
            }
            if (sel.extra) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Kind.ASSIGNABLE.forEach { k ->
                        FilterChip(selected = sel.kind == k, onClick = { layout.setKind(sel.id, k) }, label = { Text(Kind.label(k)) })
                    }
                }
            }
            if (sel.kind != Kind.MENU) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { layout.setHidden(sel.id, !sel.hidden) }) { Text(if (sel.hidden) "Mostrar este botón" else "Ocultar este botón") }
                    OutlinedButton(onClick = { layout.remove(sel.id) }) { Text("Borrar este botón", color = Color(0xFFFF8A80)) }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onSaveGlobal) { Text("Guardar global") }
            if (onSaveGame != null) OutlinedButton(onClick = onSaveGame) { Text("Guardar para ${gameName ?: "este juego"}".take(34)) }
            Button(onClick = onDone) { Text("Listo") }
        }
    }
}
