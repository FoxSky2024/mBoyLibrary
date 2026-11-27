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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.roundToInt
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
    /** <id>.pdf o <titulo>.pdf (sin caracteres raros), para copiar manuales a la carpeta sin conocer el id. */
    private fun pdf(ctx: Context, g: Game): File {
        val byId = File(dir(ctx), "${g.id}.pdf")
        if (byId.exists()) return byId
        val want = g.title.lowercase().filter { it.isLetterOrDigit() }
        val alt = dir(ctx).listFiles { f -> f.extension.equals("pdf", true) && f.nameWithoutExtension.lowercase().filter { it.isLetterOrDigit() } == want }?.firstOrNull()
        return alt ?: byId
    }
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

    /** PDF (uno) o varias imagenes (paginas en el orden elegido). */
    suspend fun importAll(ctx: Context, g: Game, uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext false
        val pdfUri = uris.firstOrNull { ctx.contentResolver.getType(it)?.contains("pdf") == true || it.toString().lowercase().endsWith(".pdf") }
        if (pdfUri != null) return@withContext import(ctx, g, pdfUri)
        try {
            val d = imgDir(ctx, g)
            d.deleteRecursively(); d.mkdirs()
            uris.forEachIndexed { i, u ->
                ctx.contentResolver.openInputStream(u)?.use { ins -> File(d, "%04d.jpg".format(i)).outputStream().use { o -> ins.copyTo(o) } }
            }
            images(ctx, g).isNotEmpty()
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

/** Angulo de reposo de la caja: algo girada para que se vea el lomo y el grosor. */
private const val HOME_ANGLE = 22f

private enum class Phase { BOX, MANUAL }

/**
 * Inspeccion de la caja: se ve de frente y por el reverso (arrastra o usa el boton), con zoom de pellizco,
 * y se puede leer el manual pasando las hojas. Reverso sin imagen original = blanco.
 */
@Composable
fun InspectOverlay(game: Game, onClose: () -> Unit, onPlay: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val console = remember(game.id) { consoleOf(game) }
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.BOX) }
    var manualVer by remember { mutableIntStateOf(0) }
    val hasManual = remember(game.id, manualVer) { Manuals.has(ctx, game) }
    val flip = remember { Animatable(HOME_ANGLE) }
    val front = rememberBitmap(game, "front")
    val back = rememberBackData(game, enabled = true)
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            if (Manuals.importAll(ctx, game, uris)) { manualVer++; phase = Phase.MANUAL }
        }
    }

    BackHandler { if (phase == Phase.MANUAL) phase = Phase.BOX else onClose() }

    fun settle() {
        val t = ((flip.value - HOME_ANGLE) / 180f).roundToInt() * 180f + HOME_ANGLE
        scope.launch { flip.animateTo(t, tween(350, easing = FastOutSlowInEasing)) }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(console.bgTop.copy(alpha = 0.97f), Color(0xFF020204))))
            .pointerInput(phase) {
                if (phase == Phase.BOX) {
                    detectHorizontalDragGestures(onDragEnd = { settle() }, onDragCancel = { settle() }) { c, dx ->
                        c.consume(); scope.launch { flip.snapTo(flip.value + dx * 0.5f) }
                    }
                }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeNoCutout().padding(bottom = 110.dp, top = 24.dp)) {
            if (phase == Phase.BOX) {
                val boxH = maxHeight * 0.92f
                val boxW = (boxH * console.boxRatio).coerceAtMost(maxWidth * 0.85f)
                // Caja 3D de verdad (frente, lomo, reverso y canto): arrastra para girarla.
                Box(
                    Modifier.align(Alignment.Center).graphicsLayer {
                        translationX = pan.x; translationY = pan.y
                        scaleX = zoom; scaleY = zoom
                    },
                ) {
                    Case3D(
                        game = game, width = boxW, height = boxH,
                        angle = { flip.value }, glarePhase = { (flip.value / 360f) % 1f }, glareStrength = { 0.35f },
                        showBack = true, back = back,
                    )
                }
            } else {
                ManualViewer(game, Modifier.fillMaxSize())
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val a = ((flip.value % 360f) + 360f) % 360f
            Text(
                if (phase == Phase.MANUAL) "MANUAL" else if (a in 100f..280f) "REVERSO" else "FRENTE",
                color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, letterSpacing = 3.sp,
            )
            Text(game.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (phase == Phase.BOX) {
                    Act("Dar vuelta") { scope.launch { flip.animateTo(((flip.value - HOME_ANGLE) / 180f).roundToInt() * 180f + HOME_ANGLE + 180f, tween(600, easing = FastOutSlowInEasing)) } }
                    Act(if (hasManual) "Leer manual" else "Agregar manual") { if (hasManual) phase = Phase.MANUAL else pick.launch(arrayOf("application/pdf", "image/*")) }
                    Act("Jugar", true) { onPlay() }
                } else {
                    Act("Cambiar manual") { pick.launch(arrayOf("application/pdf", "image/*")) }
                    Act("Ver caja") { phase = Phase.BOX }
                }
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

/** Reverso sin imagen: texto "NO DATA". */
@Composable
fun NoData(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.background(Color(0xFF16161C)), contentAlignment = Alignment.Center) {
        Text("NO DATA", color = Color.White.copy(alpha = 0.45f), fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
    }
}

/** Reverso original; sin imagen, "NO DATA". */
@Composable
private fun BackFace(back: BackData?) {
    val real = back?.real
    if (real != null) Image(real, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else NoData()
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
                    .graphicsLayer {
                        val off = pager.currentPage + pager.currentPageOffsetFraction - page
                        cameraDistance = 16f * density
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (off > 0) 1f else 0f, 0.5f)
                        rotationY = (off * -50f).coerceIn(-50f, 50f)
                        alpha = (1f - kotlin.math.abs(off) * 0.4f).coerceIn(0f, 1f)
                    }
                    .pointerInput(page) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val ev = awaitPointerEvent()
                                // solo consume con 2 dedos o con zoom: asi el paso de hojas (deslizar) sigue funcionando
                                if (ev.changes.count { it.pressed } > 1 || z > 1f) {
                                    z = (z * ev.calculateZoom()).coerceIn(1f, 4f)
                                    o = if (z > 1f) o + ev.calculatePan() else Offset.Zero
                                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (ev.changes.any { it.pressed })
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val b = bmp
                if (b != null) Image(b, null, Modifier.fillMaxHeight().graphicsLayer { scaleX = z; scaleY = z; translationX = o.x; translationY = o.y }, contentScale = ContentScale.Fit)
                else Text("…", color = Color.White.copy(alpha = 0.6f))
            }
        }
        val sc = rememberCoroutineScope()
        if (pager.currentPage > 0) Text("‹", color = Color.White, fontSize = 34.sp, modifier = Modifier.align(Alignment.CenterStart).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable { sc.launch { pager.animateScrollToPage(pager.currentPage - 1) } }.padding(horizontal = 14.dp, vertical = 2.dp))
        if (pager.currentPage < d.count - 1) Text("›", color = Color.White, fontSize = 34.sp, modifier = Modifier.align(Alignment.CenterEnd).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable { sc.launch { pager.animateScrollToPage(pager.currentPage + 1) } }.padding(horizontal = 14.dp, vertical = 2.dp))
        Text(
            "${pager.currentPage + 1} / ${d.count}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopCenter).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
