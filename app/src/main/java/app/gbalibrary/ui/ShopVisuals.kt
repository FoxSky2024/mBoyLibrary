package app.gbalibrary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Pawb
import kotlinx.coroutines.launch

// ------------------------------------------------------------------------------------------ marcos

private fun DrawScope.frameBack(f: ShopFrame, ph: Float, th: Float) {
    val r = CornerRadius(10f, 10f)
    val full = Size(size.width, size.height)
    when (f.kind) {
        0, 5 -> drawRoundRect(Brush.linearGradient(listOf(f.c1, f.c2, f.c1), Offset.Zero, Offset(size.width, size.height)), size = full, cornerRadius = r)
        1 -> {
            drawRoundRect(Color(0xFF05060C), size = full, cornerRadius = r)
            val c = if (ph < 0.5f) f.c1 else f.c2
            for (i in 3 downTo 1) drawRoundRect(c.copy(alpha = 0.10f * (4 - i)), Offset(th / 2 - i * 2f, th / 2 - i * 2f), Size(size.width - th + i * 4f, size.height - th + i * 4f), r, style = Stroke(th * 0.5f + i * 2f))
            drawRoundRect(Color.White, Offset(th * 0.4f, th * 0.4f), Size(size.width - th * 0.8f, size.height - th * 0.8f), r, style = Stroke(2f))
        }
        2 -> {
            drawRect(f.c2)
            val b = th
            var x = 0f; var i = 0
            while (x < size.width) { drawRect(if (i % 2 == 0) f.c1 else Color.White.copy(alpha = 0.85f), Offset(x, 0f), Size(b, b)); drawRect(if (i % 2 == 0) Color.White.copy(alpha = 0.85f) else f.c1, Offset(x, size.height - b), Size(b, b)); x += b; i++ }
            var y = 0f; i = 0
            while (y < size.height) { drawRect(if (i % 2 == 0) f.c1 else Color.White.copy(alpha = 0.85f), Offset(0f, y), Size(b, b)); drawRect(if (i % 2 == 0) Color.White.copy(alpha = 0.85f) else f.c1, Offset(size.width - b, y), Size(b, b)); y += b; i++ }
        }
        3 -> {
            drawRoundRect(Brush.verticalGradient(listOf(f.c1, f.c2)), size = full, cornerRadius = r)
            var y = 2f; while (y < size.height) { drawLine(Color.Black.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y + 3f), 1.5f); y += 6f }
        }
        4 -> drawRoundRect(Brush.sweepGradient(listOf(Color(0xFFFF5E8A), Color(0xFFFFD54F), Color(0xFF69F0AE), Color(0xFF4FC3F7), Color(0xFFB388FF), Color(0xFFFF5E8A)), center = Offset(size.width / 2, size.height / 2)).let { it }, size = full, cornerRadius = r, alpha = 0.55f + 0.4f * ph)
        6 -> {
            drawRoundRect(f.c2, size = full, cornerRadius = r)
            var x = th; while (x < size.width - th) { drawCircle(f.c1, th * 0.28f, Offset(x, th / 2)); drawCircle(f.c1, th * 0.28f, Offset(x, size.height - th / 2)); x += th * 1.6f }
            var y = th; while (y < size.height - th) { drawCircle(f.c1, th * 0.28f, Offset(th / 2, y)); drawCircle(f.c1, th * 0.28f, Offset(size.width - th / 2, y)); y += th * 1.6f }
        }
        8 -> {
            val cols = (0..4).map { Color.hsv(((ph * 360f) + it * 55f) % 360f, 0.55f, 1f) }
            drawRoundRect(Brush.linearGradient(cols, Offset.Zero, Offset(size.width, size.height)), size = full, cornerRadius = r)
        }
        9 -> drawRoundRect(Brush.verticalGradient(listOf(f.c1, f.c2, Color(0xFF3E0F05))), size = full, cornerRadius = r)
        10 -> drawRoundRect(Brush.linearGradient(listOf(f.c1, f.c2, f.c1), Offset.Zero, Offset(size.width, size.height)), size = full, cornerRadius = r)
        11 -> {
            drawRoundRect(Brush.linearGradient(listOf(f.c1, f.c2), Offset.Zero, Offset(size.width, size.height)), size = full, cornerRadius = r)
            var x = -size.height; while (x < size.width) { drawLine(Color.White.copy(alpha = 0.22f), Offset(x, size.height), Offset(x + size.height, 0f), th * 0.5f); x += th * 2.2f }
        }
        12 -> {
            drawRoundRect(f.c2, size = full, cornerRadius = r)
            val b = th; var i = 0; var x = 0f
            while (x < size.width) { val c = Color.hsv(((ph * 360f) + i * 40f) % 360f, 0.8f, 1f); drawRect(c, Offset(x, 0f), Size(b, b)); drawRect(c, Offset(x, size.height - b), Size(b, b)); x += b; i++ }
            var y = 0f; while (y < size.height) { val c = Color.hsv(((ph * 360f) + i * 40f) % 360f, 0.8f, 1f); drawRect(c, Offset(0f, y), Size(b, b)); drawRect(c, Offset(size.width - b, y), Size(b, b)); y += b; i++ }
        }
        13 -> {
            drawRoundRect(Brush.linearGradient(listOf(f.c1, f.c2, f.c1), Offset.Zero, Offset(size.width, size.height)), size = full, cornerRadius = r)
            for (i in 0 until 40) {
                val tw = 0.5f + 0.5f * kotlin.math.sin((ph * 2f + (i * 0.37f) % 1f) * 6.2832f)
                drawCircle(Color.White.copy(alpha = tw.coerceIn(0.1f, 1f)), 1.2f + (i % 3), Offset(((i * 97) % 100) / 100f * size.width, ((i * 53) % 100) / 100f * size.height))
            }
        }
        14 -> {
            drawRoundRect(Brush.verticalGradient(listOf(f.c1, f.c2)), size = full, cornerRadius = r)
            for (i in 0 until 16) {
                val p = (ph + i * 0.0625f) % 1f
                drawCircle(Color.White.copy(alpha = 0.85f * (1f - p)), th * 0.22f, Offset(((i * 61) % 100) / 100f * size.width, p * size.height))
            }
        }
        15 -> {
            drawRoundRect(f.c2, size = full, cornerRadius = r)
            val per = 2f * (size.width + size.height)
            drawRoundRect(f.c1.copy(alpha = 0.35f), Offset(th * 0.4f, th * 0.4f), Size(size.width - th * 0.8f, size.height - th * 0.8f), r, style = Stroke(3f))
            for (k in 0 until 3) {
                val d = ((ph + k / 3f) % 1f) * per
                val pt = when {
                    d < size.width -> Offset(d, th * 0.4f)
                    d < size.width + size.height -> Offset(size.width - th * 0.4f, d - size.width)
                    d < 2 * size.width + size.height -> Offset(size.width - (d - size.width - size.height), size.height - th * 0.4f)
                    else -> Offset(th * 0.4f, size.height - (d - 2 * size.width - size.height))
                }
                drawCircle(f.c1, th * 0.3f, pt); drawCircle(f.c1.copy(alpha = 0.3f), th * 0.7f, pt)
            }
        }
        7 -> {
            drawRoundRect(f.c1, size = full, cornerRadius = r)
            drawRoundRect(f.c2, Offset(th * 0.45f, th * 0.45f), Size(size.width - th * 0.9f, size.height - th * 0.9f), r, style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f))))
        }
    }
}

private fun DrawScope.frameFront(f: ShopFrame, ph: Float, th: Float) {
    // linea interior fina que separa el marco del contenido + adornos
    drawRect(Color.Black.copy(alpha = 0.35f), Offset(th - 1.5f, th - 1.5f), Size(size.width - 2 * th + 3f, size.height - 2 * th + 3f), style = Stroke(2f))
    if (f.kind == 5) {
        val s = th * 0.9f
        listOf(Offset(0f, 0f), Offset(size.width - s * 1.6f, 0f), Offset(0f, size.height - s * 1.6f), Offset(size.width - s * 1.6f, size.height - s * 1.6f)).forEach {
            drawCircle(Color.White.copy(alpha = 0.9f), s * 0.45f, it + Offset(s * 0.8f, s * 0.8f)); drawCircle(f.c2, s * 0.25f, it + Offset(s * 0.8f, s * 0.8f))
        }
    }
    // efecto holografico + destellos en todos los marcos (solo sobre el borde)
    val ring = androidx.compose.ui.graphics.Path().apply {
        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        addRect(androidx.compose.ui.geometry.Rect(th, th, size.width - th, size.height - th))
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
    }
    clipPath(ring) {
        val x0 = size.width * (ph * 2.4f - 0.7f)
        drawRect(
            Brush.linearGradient(listOf(Color.Transparent, Color(0x55FF5EC8), Color(0x5500E5FF), Color(0x55FFEB3B), Color.Transparent), Offset(x0 - size.width * 0.3f, 0f), Offset(x0 + size.width * 0.3f, size.height)),
            blendMode = androidx.compose.ui.graphics.BlendMode.Screen,
        )
        for (i in 0 until 6) {
            val a = (0.5f + 0.5f * kotlin.math.sin((ph * 2f + i * 0.31f) * 6.2832f)).coerceIn(0f, 1f)
            val cx = ((i * 37 + 11) % 100) / 100f * size.width
            val cy = if (i % 2 == 0) th * 0.5f else size.height - th * 0.5f
            val rr = th * 0.55f * a
            drawLine(Color.White.copy(alpha = a), Offset(cx - rr, cy), Offset(cx + rr, cy), 2f)
            drawLine(Color.White.copy(alpha = a), Offset(cx, cy - rr), Offset(cx, cy + rr), 2f)
        }
    }
    if (f.kind == 0 || f.kind == 4) {
        // destello que recorre el marco
        val x = size.width * ph
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), x - 60f, x + 60f), Offset(0f, 0f), Size(size.width, th))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), x - 60f, x + 60f), Offset(0f, size.height - th), Size(size.width, th))
    }
}

/** Contenido dentro de un marco (null = sin marco). */
@Composable
fun FramedBox(frame: ShopFrame?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (frame == null) { Box(modifier) { content() }; return }
    val th = 4.dp
    val thPx = with(LocalDensity.current) { th.toPx() }
    val ph by rememberInfiniteTransition(label = "frame").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "ph")
    Box(modifier.clip(RoundedCornerShape(6.dp)).drawBehind { frameBack(frame, ph, thPx) }.padding(th)) {
        Box(Modifier.matchParentSize()) { content() }
        Canvas(Modifier.matchParentSize()) { frameFront(frame, ph, thPx) }
    }
}

// ------------------------------------------------------------------------------------------ vitrina del perfil

/** Contenido del banner equipado, con los textos/imagen que el usuario edito dentro del marco. */
@Composable
fun VitrinaContent(modifier: Modifier = Modifier) {
    val id = Pawb.banner.value
    val cat = ShopCatalog.banner(id)
    val cus = ShopCatalog.custom(id)
    val p = LocalPalette.current
    val baseTitle = cat?.title ?: cus?.title ?: app.gbalibrary.data.Settings.profileName.value.ifBlank { "Mi vitrina" }
    val baseSub = cat?.subtitle ?: cus?.subtitle ?: ""
    val colors = cat?.colors ?: cus?.colors?.map { Color(it) } ?: listOf(p.accent, p.bg)
    val style = cat?.style ?: cus?.style ?: 1
    val img = Pawb.vImg.value.ifEmpty { cus?.img ?: "" }
    val vid = Pawb.vVideo.value
    Box(modifier) {
        BannerArt(Pawb.vTitle.value.ifEmpty { baseTitle }, Pawb.vSub.value.ifEmpty { baseSub }, colors, style, Modifier.fillMaxSize(), img)
        if (vid.isNotEmpty() && java.io.File(vid).exists()) {
            VideoBanner(vid, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
            Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp)) {
                val tt = Pawb.vTitle.value.ifEmpty { baseTitle }
                Text(tt, color = Color.White, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1)
                val ss = Pawb.vSub.value.ifEmpty { baseSub }
                if (ss.isNotBlank()) Text(ss, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
            }
        }
        BannerFx(Pawb.vFx.value, Modifier.fillMaxSize())
    }
}

/** Vitrina: banner dentro del marco obtenido en los sobres. Se muestra en el perfil. */
@Composable
fun Vitrina(modifier: Modifier = Modifier, overlayName: String? = null) {
    // ancho y alto ajustables; en automatico se adapta a la proporcion de la imagen o video que subiste
    val aspect = vitrinaAspect()
    val vh = Pawb.vH.intValue
    Box(
        modifier.fillMaxWidth().padding(top = Pawb.vDy.intValue.dp),
        contentAlignment = when (Pawb.vAlign.intValue) { 0 -> Alignment.CenterStart; 2 -> Alignment.CenterEnd; else -> Alignment.Center },
    ) {
        FramedBox(
            ShopCatalog.frame(Pawb.vFrame.value),
            Modifier.fillMaxWidth(Pawb.vW.floatValue).then(if (vh > 0) Modifier.height(vh.dp) else Modifier.aspectRatio(aspect)),
        ) {
            Box(Modifier.fillMaxSize()) {
                VitrinaContent(Modifier.fillMaxSize())
                // el nombre del jugador va dentro del banner, arriba a la izquierda
                if (overlayName != null) {
                    Text(
                        overlayName, color = Color.White, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), Offset(1f, 2f), 6f)),
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp, end = 12.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ vista previa de cualquier objeto

@Composable
fun ItemPreview(id: String, modifier: Modifier = Modifier.fillMaxSize()) {
    ShopCatalog.bg(id)?.let { b -> Canvas(modifier) { drawStyled(b.colors, b.style) }; return }
    ShopCatalog.banner(id)?.let { b -> BannerArt(b.title, b.subtitle, b.colors, b.style, modifier); return }
    ShopCatalog.frame(id)?.let { f ->
        Box(modifier.padding(6.dp), contentAlignment = Alignment.Center) {
            FramedBox(f, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF243247), Color(0xFF0E1420))))) }
        }
        return
    }
    if (id.startsWith("st_") || id.startsWith("cs:")) { Box(modifier.background(Color(0xFF1B2430)), contentAlignment = Alignment.Center) { StickerArt(id, 64.dp) }; return }
    Box(modifier.background(Color(0xFF1B2430)), contentAlignment = Alignment.Center) { PawIcon(40.dp) }
}

// ------------------------------------------------------------------------------------------ apertura de sobres

@Composable
private fun SealedPack(name: String, color: Color, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val burst = remember { Animatable(0f) }
    val tr = rememberInfiniteTransition(label = "pack")
    val wob by tr.animateFloat(-3f, 3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "wob")
    val shine by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "shine")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(
            Modifier.size(170.dp, 240.dp)
                .graphicsLayer { rotationZ = wob * (1f - burst.value); val s = 1f + 0.9f * burst.value; scaleX = s; scaleY = s; alpha = 1f - burst.value }
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f), Color(0xFF14101F))))
                .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                .clickable { scope.launch { burst.animateTo(1f, tween(420)); onOpen() } },
            contentAlignment = Alignment.Center,
        ) {
            // zigzag de apertura y destello de foil
            Canvas(Modifier.fillMaxSize()) {
                val z = 12f; var x = 0f; var up = true
                while (x < size.width) { drawLine(Color.White.copy(alpha = 0.55f), Offset(x, if (up) 0f else z), Offset(x + z, if (up) z else 0f), 3f); x += z; up = !up }
                val sx = size.width * (shine * 1.6f - 0.3f)
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent), Offset(sx - 50f, 0f), Offset(sx + 50f, size.height)))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PawIcon(64.dp, Color.White)
                Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        Text("Toca el sobre para abrirlo", color = Color(0xFFB0BAC5), fontSize = 13.sp)
    }
}

@Composable
private fun RevealCard(id: String, onFlip: () -> Unit, flipped: Boolean, w: androidx.compose.ui.unit.Dp = 124.dp) {
    val r = ShopCatalog.rarityOf(id)
    val rot = remember { Animatable(if (flipped) 180f else 0f) }
    val dens = LocalDensity.current.density
    androidx.compose.runtime.LaunchedEffect(flipped) { if (flipped) rot.animateTo(180f, tween(520)) }
    val tr = rememberInfiniteTransition(label = "card")
    val glow by tr.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")
    val sweep by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    Box(
        Modifier.size(w, w * 1.42f)
            .graphicsLayer { rotationY = rot.value; cameraDistance = 14f * dens }
            .clickable(enabled = !flipped) { onFlip() },
        contentAlignment = Alignment.Center,
    ) {
        if (rot.value <= 90f) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A2B6B), Color(0xFF1A1033))))
                    .border(2.dp, Color(0xFF8E7CFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { PawIcon(44.dp, Color(0xFF8E7CFF)) }
        } else {
            Column(
                Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xFF121826))
                    .border(2.5.dp, r.color.copy(alpha = if (r.weight <= 10) glow else 1f), RoundedCornerShape(12.dp))
                    .drawBehind {
                        if (r.weight <= 10) {
                            val sx = size.width * (sweep * 1.8f - 0.4f)
                            drawRect(Brush.linearGradient(listOf(Color.Transparent, r.color.copy(alpha = 0.35f), Color.Transparent), Offset(sx - 40f, 0f), Offset(sx + 40f, size.height)))
                        }
                    }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.fillMaxSize().weight(1f).clip(RoundedCornerShape(8.dp))) { ItemPreview(id) }
                Text(ShopCatalog.nameOf(id), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Text(r.label.uppercase(), color = r.color, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }
    }
}

/** Pantalla completa de apertura: el sobre se rompe y las cartas se giran una a una. */
@Composable
fun PackOpening(packName: String, packColor: Color, ids: List<String>, onClose: () -> Unit) {
    var stage by remember { mutableIntStateOf(0) }
    val flipped = remember { mutableStateListOf<Boolean>().apply { repeat(ids.size) { add(false) } } }
    Box(Modifier.fillMaxSize().background(Color(0xF2080A12)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        if (stage == 0) SealedPack(packName, packColor) { stage = 1 }
        else androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
          val cw = minOf(124.dp, (maxWidth - 12.dp * (ids.size - 1)) / ids.size, (maxHeight - 120.dp) / 1.42f).coerceAtLeast(40.dp)
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (flipped.all { it }) "¡Todo revelado!" else "Toca cada carta para revelarla", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ids.forEachIndexed { i, id -> RevealCard(id, flipped = flipped[i], onFlip = { flipped[i] = true }, w = cw) }
            }
            if (flipped.all { it }) PriceButtonPublic("Continuar") { onClose() }
            else Text("Revelar todo", color = Color(0xFF8E7CFF), fontSize = 13.sp, modifier = Modifier.clickable { for (i in flipped.indices) flipped[i] = true }.padding(8.dp))
          }
        }
    }
}

@Composable
fun PriceButtonPublic(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(text, color = p.onPlay, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(p.play).clickable { Sfx.click(); onClick() }.padding(horizontal = 26.dp, vertical = 11.dp))
}
