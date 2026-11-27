package app.gbalibrary.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.gbalibrary.data.ConsoleDef
import app.gbalibrary.data.Game
import app.gbalibrary.data.Systems
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Consola de un juego (integrada o externa) a partir de la etiqueta que da [systemOf]. */
fun consoleOf(g: Game): ConsoleDef = Systems.byShort(systemOf(g)) ?: Systems.builtIn[0]

private const val SIDE_TURN = 58f // giro de las cajas laterales (hacia el centro)

/**
 * Modo Estanteria: pantalla completa propia (sin paneles, chips ni fondo de Steam).
 *  - Arriba solo iconos: buscar, filtro de consola, agregar, vista Steam y engranaje.
 *  - Un toque en una caja la selecciona: aparecen nombre + JUGAR + Detalles. Un toque fuera u otra caja los cambia.
 *  - La caja seleccionada gira 360 con arrastre horizontal (frente > lomo > atras > lomo > frente);
 *    el arrastre sobre el resto del carrusel cambia de juego.
 * El indice central lo lleva quien la llama ([selected]) para compartir el foco del mando.
 */
@Composable
fun ShelfView(
    games: List<Game>,
    all: List<Game>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onOpen: (Game) -> Unit,
    onPlay: (Game) -> Unit,
    onLong: (Game) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    systemFilter: String,
    onSystemFilter: (String) -> Unit,
    onSettings: () -> Unit,
    onToggleView: () -> Unit,
    onAddFolder: () -> Unit,
    onAddFiles: () -> Unit,
    onRescan: () -> Unit,
) {
    val appCtx = LocalContext.current.applicationContext
    val idx = selected.coerceIn(0, (games.size - 1).coerceAtLeast(0))
    val cur = games.getOrNull(idx)
    val console = if (cur != null) consoleOf(cur) else Systems.builtIn[0]
    val posState = animateFloatAsState(idx.toFloat(), spring(stiffness = Spring.StiffnessMediumLow), label = "shelfPos")
    val top by animateColorAsState(console.bgTop, tween(450), label = "shelfTop")

    val idxNow by rememberUpdatedState(idx)
    val gamesNow by rememberUpdatedState(games)
    val onSelectNow by rememberUpdatedState(onSelect)
    val onLongNow by rememberUpdatedState(onLong)

    // ---- seleccion y giro de la caja ----
    var activeId by remember { mutableStateOf<String?>(null) }
    var spin by remember { mutableFloatStateOf(0f) }
    var spinOwner by remember { mutableStateOf<String?>(null) }
    var spinJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val active = cur?.takeIf { it.id == activeId }

    fun deselect() {
        if (activeId == null) return
        activeId = null
        spinJob?.cancel()
        val s = spin
        val target = (s / 360f).roundToInt() * 360f // vuelve al frente por el camino mas corto
        spinJob = scope.launch {
            animate(s, target, animationSpec = tween(380)) { v, _ -> spin = v }
            spin = 0f
            spinOwner = null
        }
    }

    fun select(i: Int) {
        val g = gamesNow.getOrNull(i) ?: return
        spinJob?.cancel()
        if (spinOwner != g.id) spin = 0f
        spinOwner = g.id
        activeId = g.id
        onSelectNow(i)
    }

    fun settleSpin() {
        spinJob?.cancel()
        val s = spin
        val target = (s / 90f).roundToInt() * 90f // reposa de cara en frente, lomo, atras o canto
        spinJob = scope.launch { animate(s, target, animationSpec = spring(stiffness = Spring.StiffnessLow)) { v, _ -> spin = v } }
    }

    // si el mando / otro gesto cambia la caja central, la seleccion anterior se cierra
    LaunchedEffect(idx, games) {
        if (activeId != null && games.getOrNull(idx)?.id != activeId) deselect()
    }
    // precarga: 5 cajas a cada lado en memoria (mas de las 3 pedidas, que es lo que se ve a la vez)
    LaunchedEffect(idx, games) { CoverBitmaps.preload(appCtx, games, idx, 5) }

    var lastActive by remember { mutableStateOf<Game?>(null) }
    LaunchedEffect(active) { if (active != null) lastActive = active }

    var inspect by remember { mutableStateOf<Game?>(null) }
    Box(Modifier.fillMaxSize()) {
        ShelfBackdrop(cur, top)
        if (cur == null) {
            Text(
                "No hay juegos con este filtro", color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val floorPx = hPx * 0.70f // la "repisa"
            val cardHpx = floorPx * 0.60f * 0.6f * 1.6f // zoom out 40% y luego zoom in 60%
            val cardH = with(density) { cardHpx.toDp() }
            val refW = cardHpx * 0.80f // separacion entre cajas

            fun scaleOf(d: Float): Float = if (abs(d) < 1f) 1.18f - 0.28f * abs(d) else 0.90f
            fun cardWpx(g: Game): Float = cardHpx * consoleOf(g).boxRatio

            // Caja bajo el dedo (con su ancho visible real al estar girada); la mas cercana al centro gana.
            fun hitIndex(x: Float, y: Float): Int? {
                val pos = posState.value
                var best: Int? = null
                var bestD = Float.MAX_VALUE
                for (i in (idxNow - 6)..(idxNow + 6)) {
                    val g = gamesNow.getOrNull(i) ?: continue
                    val d = i - pos
                    val near = d.coerceIn(-1f, 1f)
                    val far = d - near
                    val scale = scaleOf(d)
                    val cw = cardWpx(g)
                    val th = Math.toRadians((-near * SIDE_TURN).toDouble())
                    val depth = cardHpx * caseStyleFor(consoleOf(g)).depthRatio
                    val visible = (cw * abs(cos(th)).toFloat() + depth * abs(sin(th)).toFloat()) * scale
                    val center = wPx / 2f + near * refW * 1.05f + far * refW * 0.30f
                    val topY = floorPx - cardHpx * scale
                    if (abs(x - center) <= visible / 2f && y >= topY && y <= floorPx && abs(d) < bestD) {
                        best = i; bestD = abs(d)
                    }
                }
                return best
            }
            val hit = rememberUpdatedState<(Float, Float) -> Int?>({ x, y -> hitIndex(x, y) })

            // suelo: linea de repisa y reflejo de luz
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent, 1f to Color.White.copy(alpha = 0.05f),
                            startY = floorPx - 40f, endY = floorPx,
                        ),
                        topLeft = Offset(0f, floorPx - 40f), size = Size(size.width, 40f),
                    )
                    drawRect(Color.White.copy(alpha = 0.10f), topLeft = Offset(0f, floorPx), size = Size(size.width, 1.5f))
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color(0xFF14141C), 1f to Color(0xFF020204), startY = floorPx, endY = size.height,
                        ),
                        topLeft = Offset(0f, floorPx + 1.5f), size = Size(size.width, size.height - floorPx),
                    )
                }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { off ->
                                val i = hit.value(off.x, off.y)
                                if (i == null) deselect() else select(i)
                            },
                            onLongPress = { off ->
                                hit.value(off.x, off.y)?.let { gamesNow.getOrNull(it) }?.let { onLongNow(it) }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        var rotating = false
                        var acc = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { off ->
                                acc = 0f
                                val c = gamesNow.getOrNull(idxNow)
                                rotating = c != null && c.id == activeId && hit.value(off.x, off.y) == idxNow
                                if (rotating) spinJob?.cancel()
                            },
                            onDragEnd = { if (rotating) settleSpin(); rotating = false },
                            onDragCancel = { if (rotating) settleSpin(); rotating = false },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                if (rotating) {
                                    spin += dx * 0.55f // arrastrar a la derecha: el frente gira a la derecha y asoma el lomo
                                } else {
                                    acc += dx
                                    if (acc > 100f) { deselect(); onSelectNow((idxNow - 1).coerceAtLeast(0)); acc = 0f }
                                    else if (acc < -100f) { deselect(); onSelectNow((idxNow + 1).coerceAtMost(gamesNow.size - 1)); acc = 0f }
                                }
                            },
                        )
                    },
            )

            for (i in (idx - 5)..(idx + 5)) {
                val g = games.getOrNull(i) ?: continue
                key(g.id) {
                    val c = consoleOf(g)
                    val cw = cardH * c.boxRatio
                    val isActive = active?.id == g.id
                    val back = rememberBackData(g, enabled = isActive)
                    val angle: () -> Float = {
                        val d = i - posState.value
                        -d.coerceIn(-1f, 1f) * SIDE_TURN + (if (spinOwner == g.id) spin else 0f)
                    }
                    val phase: () -> Float = { (i - posState.value) * 0.6f + angle() / 110f }
                    val strength: () -> Float = { 1f - 0.65f * min(1f, abs(i - posState.value)) }

                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, (floorPx - cardHpx).roundToInt()) }
                            .size(cw, cardH)
                            .zIndex(-abs(i - idx).toFloat())
                            .graphicsLayer {
                                val d = i - posState.value
                                val near = d.coerceIn(-1f, 1f)
                                val far = d - near
                                val s = scaleOf(d)
                                transformOrigin = TransformOrigin(0.5f, 1f) // crece hacia arriba: siempre apoyada en la repisa
                                translationX = near * refW * 1.05f + far * refW * 0.30f
                                scaleX = s
                                scaleY = s
                                alpha = (1f - (abs(d) - 4f).coerceAtLeast(0f) * 0.35f).coerceIn(0f, 1f)
                            }
                            .drawBehind {
                                // sombra de contacto sobre la repisa
                                drawOval(
                                    Brush.radialGradient(
                                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                                        center = Offset(size.width / 2f, size.height),
                                        radius = size.width * 0.62f,
                                    ),
                                    topLeft = Offset(-size.width * 0.15f, size.height - 14f),
                                    size = Size(size.width * 1.3f, 28f),
                                )
                            },
                    ) {
                        Case3D(
                            game = g, width = cw, height = cardH,
                            angle = angle, glarePhase = phase, glareStrength = strength,
                            showBack = isActive || spinOwner == g.id, back = back,
                        )
                        if (abs(i - idx) <= 3) {
                            // reflejo en la repisa (solo las cajas cercanas)
                            Box(
                                Modifier
                                    .offset(y = cardH)
                                    .size(cw, cardH)
                                    .graphicsLayer {
                                        scaleY = -1f
                                        alpha = 0.40f
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    },
                            ) {
                                Case3D(
                                    game = g, width = cw, height = cardH,
                                    angle = angle, glarePhase = phase, glareStrength = strength,
                                    showBack = false, back = null, cheap = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- panel inferior de cristal (siempre visible, como el video) ----
        if (cur != null) {
            ShelfBottomPanel(
                cur, idx, games.size,
                onPlay = { onPlay(cur) }, onInspect = { inspect = cur }, onDetails = { onOpen(cur) },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
            )
        }
        ShelfStatusPill(Modifier.align(Alignment.TopCenter).safeNoCutout().padding(top = 8.dp))

        inspect?.let { g -> InspectOverlay(g, onClose = { inspect = null }, onPlay = { inspect = null; onPlay(g) }) }

        // ---- arriba: solo iconos ----
        ShelfTopIcons(
            all = all, query = query, onQuery = onQuery,
            systemFilter = systemFilter, onSystemFilter = onSystemFilter,
            onSettings = onSettings, onToggleView = onToggleView,
            onAddFolder = onAddFolder, onAddFiles = onAddFiles, onRescan = onRescan,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** Fila de iconos translucidos: buscar, filtro por consola, agregar, vista Steam y engranaje. */
@Composable
private fun ShelfTopIcons(
    all: List<Game>,
    query: String,
    onQuery: (String) -> Unit,
    systemFilter: String,
    onSystemFilter: (String) -> Unit,
    onSettings: () -> Unit,
    onToggleView: () -> Unit,
    onAddFolder: () -> Unit,
    onAddFiles: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searching by remember { mutableStateOf(query.isNotEmpty()) }
    var filterMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }

    @Composable
    fun Btn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = Color.White, onClick: () -> Unit) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable { Sfx.click(); onClick() },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp)) }
    }

    Row(
        modifier.safeNoCutout().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (searching) {
            Box(
                Modifier.width(190.dp).height(38.dp).clip(RoundedCornerShape(19.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { MenuNav.textFocus = it.isFocused },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text("Buscar juegos...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                            inner()
                        }
                    },
                )
            }
            Btn(Icons.Filled.Close, "Cerrar busqueda") { onQuery(""); searching = false }
        } else {
            Btn(Icons.Filled.Search, "Buscar") { searching = true }
        }

        Box {
            Btn(Icons.Filled.FilterList, "Filtrar por consola", tint = if (systemFilter.isEmpty()) Color.White else Color(0xFFFFD54F)) { filterMenu = true }
            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                // Todos / GBA / GBC / GB / DS siempre; el resto de consolas solo si hay juegos. Los contadores se recalculan solos.
                val keys = listOf("" to "Todos", "GBA" to "GBA", "GBC" to "GBC", "GB" to "GB", "DS" to "DS") +
                    Systems.external.map { it.short to it.short }
                keys.forEach { (key, name) ->
                    val n = if (key.isEmpty()) all.size else all.count { systemOf(it) == key }
                    if (key.isEmpty() || n > 0 || systemFilter == key || key in setOf("GBA", "GBC", "GB", "DS")) {
                        DropdownMenuItem(
                            text = { Text(if (systemFilter == key) "● $name  $n" else "$name  $n") },
                            onClick = { onSystemFilter(key); filterMenu = false },
                        )
                    }
                }
            }
        }

        Box {
            Btn(Icons.Filled.Add, "Agregar juegos") { addMenu = true }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                DropdownMenuItem(text = { Text("Agregar carpeta de ROMs") }, onClick = { addMenu = false; onAddFolder() })
                DropdownMenuItem(text = { Text("Agregar archivos (.gba .gbc .gb .nds .zip .7z)") }, onClick = { addMenu = false; onAddFiles() })
                DropdownMenuItem(text = { Text("Volver a escanear carpetas") }, onClick = { addMenu = false; onRescan() })
            }
        }

        Btn(Icons.Filled.GridView, "Vista Steam") { onToggleView() }
        Btn(Icons.Filled.Settings, "Ajustes") { onSettings() }
    }
}

/** Ambiente de galeria: foco de luz sobre la caja central, bokeh flotante y aro de luz en el suelo (estilo "vitrina"). */
@Composable
private fun ShelfWaves(accent: Color) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "amb")
    val ph by t.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        androidx.compose.animation.core.infiniteRepeatable(tween(30000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "ambPh",
    )
    val col by animateColorAsState(accent, tween(450), label = "ambCol")
    Box(
        Modifier.fillMaxSize().drawBehind {
            val w = size.width
            val h = size.height
            val floor = h * 0.70f
            // foco cenital
            val cone = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.44f, 0f); lineTo(w * 0.56f, 0f); lineTo(w * 0.80f, floor); lineTo(w * 0.20f, floor); close()
            }
            drawPath(cone, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), col.copy(alpha = 0.05f), Color.Transparent), startY = 0f, endY = floor))
            // bokeh
            for (k in 0 until 14) {
                val f = k / 14f
                val x = w * ((f * 7.3f + 0.1f * sin(ph + k)) % 1f)
                val y = h * (0.08f + 0.55f * ((f * 3.1f + 0.05f * kotlin.math.cos(ph * 1.3f + k * 2f)) % 1f))
                val r = w * (0.012f + 0.02f * ((k * 37 % 10) / 10f))
                drawCircle(col.copy(alpha = 0.07f + 0.05f * ((k % 3) / 2f)), r, Offset(x, y))
            }
            // aro de luz en el suelo
            drawOval(
                Brush.radialGradient(listOf(col.copy(alpha = 0.35f), Color.Transparent), center = Offset(w / 2f, floor), radius = w * 0.40f),
                topLeft = Offset(w * 0.10f, floor - h * 0.03f), size = androidx.compose.ui.geometry.Size(w * 0.80f, h * 0.10f),
            )
            // viñeta
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.60f)), center = Offset(w / 2f, h / 2f), radius = w * 0.75f))
        },
    )
}
