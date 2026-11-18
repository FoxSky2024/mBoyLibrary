package app.gbalibrary.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTransformGestures
import app.gbalibrary.data.DataDir
import app.gbalibrary.data.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manuales de instrucciones. Cada juego puede tener GBALibrary/Manuals/<id>.pdf (se importa desde el visor)
 * o una carpeta GBALibrary/Manuals/<id>/ con paginas .png/.jpg/.webp ordenadas por nombre.
 */
object Manuals {
    private fun dir(ctx: Context) = File(DataDir.root(ctx), "Manuals").apply { mkdirs() }
    private fun pdf(ctx: Context, g: Game) = File(dir(ctx), "${g.id}.pdf")
    private fun imgDir(ctx: Context, g: Game) = File(dir(ctx), g.id)

    private fun images(ctx: Context, g: Game): List<File> =
        imgDir(ctx, g).listFiles { f -> f.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
            ?.sortedBy { it.name } ?: emptyList()

    fun has(ctx: Context, g: Game): Boolean = pdf(ctx, g).exists() || images(ctx, g).isNotEmpty()

    /** Copia el PDF elegido por el usuario. */
    suspend fun import(ctx: Context, g: Game, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> pdf(ctx, g).outputStream().use { o -> i.copyTo(o) } }
            pdf(ctx, g).length() > 0
        } catch (e: Exception) {
            false
        }
    }

    fun remove(ctx: Context, g: Game) { pdf(ctx, g).delete() }

    /** Abre el manual: devuelve el numero de paginas y una funcion que renderiza la pagina i (llamar en IO). */
    class Doc(val count: Int, val render: (Int) -> Bitmap?, val close: () -> Unit)

    fun open(ctx: Context, g: Game): Doc? {
        val p = pdf(ctx, g)
        if (p.exists()) {
            return try {
                val fd = ParcelFileDescriptor.open(p, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(fd)
                Doc(r.pageCount, { i ->
                    synchronized(r) {
                        try {
                            r.openPage(i).use { pg ->
                                val w = 1100
                                val h = (w * pg.height.toFloat() / pg.width).toInt().coerceAtLeast(1)
                                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                b.eraseColor(AColor.WHITE)
                                pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                b
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }, { runCatching { r.close(); fd.close() } })
            } catch (e: Exception) {
                null
            }
        }
        val imgs = images(ctx, g)
        if (imgs.isEmpty()) return null
        return Doc(imgs.size, { i -> runCatching { android.graphics.BitmapFactory.decodeFile(imgs[i].path) }.getOrNull() }, {})
    }
}

private enum class Phase { CLOSED, OPEN, MANUAL }

/**
 * Inspeccion de la caja (como el video): caja de frente que se puede dar vuelta, abrir (portada + disco),
 * sacar el disco y leer el manual. Fondo oscuro desenfocado, titulo debajo y barra de acciones.
 */
@Composable
fun InspectOverlay(game: Game, onClose: () -> Unit, onPlay: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val console = remember(game.id) { consoleOf(game) }
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.CLOSED) }
    var discOut by remember { mutableStateOf(false) }
    var manualVer by remember { mutableIntStateOf(0) }
    val hasManual = remember(game.id, manualVer) { Manuals.has(ctx, game) }
    val flip = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    val front = rememberBitmap(game, "front")
    val back = rememberBackData(game, enabled = true)
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            if (Manuals.import(ctx, game, uri)) { manualVer++; phase = Phase.MANUAL }
        }
    }

    BackHandler {
        when {
            phase == Phase.MANUAL -> phase = Phase.OPEN
            phase == Phase.OPEN -> scope.launch { open.animateTo(0f, tween(350)); phase = Phase.CLOSED; discOut = false }
            else -> onClose()
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(console.bgTop.copy(alpha = 0.97f), Color(0xFF020204))))
            .pointerInput(Unit) { detectTransformGestures { _, p, z, _ -> if (phase == Phase.CLOSED) { zoom = (zoom * z).coerceIn(1f, 3f); pan = if (zoom > 1f) pan + p else Offset.Zero } } },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(bottom = 110.dp, top = 24.dp)) {
            val boxH = maxHeight * 0.92f
            val boxW = (boxH * console.boxRatio).coerceAtMost(maxWidth * 0.8f)
            if (phase != Phase.MANUAL) {
                val openF = open.value
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    // cubierta / portada (se abre hacia la izquierda)
                    Box(
                        Modifier.size(boxW, boxH)
                            .graphicsLayer {
                                translationX = -openF * boxW * 0.55f + pan.x
                                translationY = pan.y
                                scaleX = zoom; scaleY = zoom
                                cameraDistance = 14f * density
                                rotationY = flip.value
                            }
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0E0E14)),
                    ) {
                        val showFront = flip.value < 90f || flip.value > 270f
                        if (showFront) {
                            if (front != null) Image(front, game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else FrontArt(game, Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { BackFace(game, back) }
                        }
                        // brillo
                        Box(Modifier.fillMaxSize().drawBehind {
                            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.18f))))
                        })
                    }
                    if (openF > 0.01f) {
                        // interior: bandeja con el disco
                        Box(
                            Modifier.padding(start = 10.dp).size(boxW, boxH)
                                .graphicsLayer { alpha = openF; translationX = -openF * boxW * 0.30f }
                                .clip(RoundedCornerShape(4.dp))
                                .background(Brush.verticalGradient(listOf(console.accent.copy(alpha = 0.45f), Color(0xFF0A0F0C))))
                                .border(2.dp, console.accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .clickable { discOut = !discOut },
                            contentAlignment = Alignment.Center,
                        ) {
                            Disc(game, front, Modifier.size(boxW * 0.82f).graphicsLayer {
                                val s = if (discOut) 1.18f else 1f
                                scaleX = s; scaleY = s
                                translationY = if (discOut) -boxH * 0.04f else 0f
                                rotationZ = if (discOut) 14f else 0f
                            })
                        }
                    }
                }
            } else {
                ManualViewer(game, Modifier.fillMaxSize())
            }
        }

        // titulo + acciones
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when (phase) { Phase.CLOSED -> if (flip.value in 90f..270f) "CONTRAPORTADA" else "CAJA"; Phase.OPEN -> "ABIERTA"; Phase.MANUAL -> "MANUAL" },
                color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, letterSpacing = 3.sp,
            )
            Text(game.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (phase == Phase.CLOSED) {
                    Act("Dar vuelta") { scope.launch { flip.animateTo(if (flip.value < 90f) 180f else 0f, tween(600, easing = FastOutSlowInEasing)) } }
                    Act("Abrir caja") { scope.launch { flip.snapTo(0f); phase = Phase.OPEN; open.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) } }
                }
                if (phase == Phase.OPEN) {
                    Act(if (discOut) "Guardar disco" else "Sacar disco") { discOut = !discOut }
                    Act(if (hasManual) "Leer manual" else "Agregar manual") { if (hasManual) phase = Phase.MANUAL else pick.launch(arrayOf("application/pdf")) }
                    Act("Cerrar caja") { scope.launch { open.animateTo(0f, tween(350)); phase = Phase.CLOSED; discOut = false } }
                }
                if (phase == Phase.MANUAL) {
                    Act("Cambiar manual") { pick.launch(arrayOf("application/pdf")) }
                    Act("Volver") { phase = Phase.OPEN }
                }
                if (phase != Phase.MANUAL) Act("Jugar", true) { onPlay() }
                Act("Cerrar") { onClose() }
            }
        }
    }
    LaunchedEffect(phase) { zoom = 1f; pan = Offset.Zero }
}

@Composable
private fun Act(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        text, color = if (primary) p.onPlay else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (primary) p.play else Color.Transparent)
            .clickable { Sfx.click(); onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun BackFace(game: Game, back: BackData?) {
    val real = back?.real
    if (real != null) {
        Image(real, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Column(Modifier.fillMaxSize().background(Color(0xFF15151C)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(game.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            back?.snap?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop) }
            Text(back?.description?.ifBlank { null } ?: "Sin descripción", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 12, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Disco: arte circular con aro, agujero central y brillo. */
@Composable
private fun Disc(game: Game, art: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(Color(0xFFE9E9EE)), contentAlignment = Alignment.Center) {
        if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.92f)
        Box(Modifier.fillMaxSize().drawBehind {
            drawCircle(Brush.sweepGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent, Color.White.copy(alpha = 0.25f))))
        })
        Box(Modifier.fillMaxSize(0.30f).clip(CircleShape).background(Color(0xFFCFCFD6)), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize(0.45f).clip(CircleShape).background(Color(0xFF0B0B10)))
        }
    }
}

/** Manual con paso de paginas (deslizar) y zoom con pellizco. */
@Composable
private fun ManualViewer(game: Game, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current.applicationContext
    var doc by remember { mutableStateOf<Manuals.Doc?>(null) }
    LaunchedEffect(game.id) { doc = withContext(Dispatchers.IO) { Manuals.open(ctx, game) } }
    androidx.compose.runtime.DisposableEffect(game.id) { onDispose { doc?.close?.invoke() } }
    val d = doc
    if (d == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Cargando manual…", color = Color.White.copy(alpha = 0.7f)) }
        return
    }
    val pager = rememberPagerState { d.count }
    Box(modifier) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            var bmp by remember(page) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(page) { bmp = withContext(Dispatchers.IO) { d.render(page)?.asImageBitmap() } }
            var z by remember { mutableStateOf(1f) }
            var o by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier.fillMaxSize().padding(6.dp)
                    .pointerInput(page) { detectTransformGestures { _, p, zm, _ -> z = (z * zm).coerceIn(1f, 4f); o = if (z > 1f) o + p else Offset.Zero } },
                contentAlignment = Alignment.Center,
            ) {
                val b = bmp
                if (b != null) Image(b, null, Modifier.fillMaxHeight().graphicsLayer { scaleX = z; scaleY = z; translationX = o.x; translationY = o.y }, contentScale = ContentScale.Fit)
                else Text("…", color = Color.White.copy(alpha = 0.6f))
            }
        }
        Text(
            "${pager.currentPage + 1} / ${d.count}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopCenter).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
