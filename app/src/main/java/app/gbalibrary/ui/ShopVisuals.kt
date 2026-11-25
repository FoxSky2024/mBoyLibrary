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
    val th = 9.dp
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
    BannerArt(Pawb.vTitle.value.ifEmpty { baseTitle }, Pawb.vSub.value.ifEmpty { baseSub }, colors, style, modifier, img)
}

/** Vitrina: banner dentro del marco obtenido en los sobres. Se muestra en el perfil. */
@Composable
fun Vitrina(modifier: Modifier = Modifier) {
    FramedBox(ShopCatalog.frame(Pawb.vFrame.value), modifier) { VitrinaContent(Modifier.fillMaxSize()) }
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
private fun RevealCard(id: String, onFlip: () -> Unit, flipped: Boolean) {
    val r = ShopCatalog.rarityOf(id)
    val rot = remember { Animatable(if (flipped) 180f else 0f) }
    val dens = LocalDensity.current.density
    androidx.compose.runtime.LaunchedEffect(flipped) { if (flipped) rot.animateTo(180f, tween(520)) }
    val tr = rememberInfiniteTransition(label = "card")
    val glow by tr.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")
    val sweep by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    Box(
        Modifier.size(124.dp, 176.dp)
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
        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if (flipped.all { it }) "¡Todo revelado!" else "Toca cada carta para revelarla", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ids.forEachIndexed { i, id -> RevealCard(id, flipped = flipped[i], onFlip = { flipped[i] = true }) }
            }
            if (flipped.all { it }) PriceButtonPublic("Continuar") { onClose() }
            else Text("Revelar todo", color = Color(0xFF8E7CFF), fontSize = 13.sp, modifier = Modifier.clickable { for (i in flipped.indices) flipped[i] = true }.padding(8.dp))
        }
    }
}

@Composable
fun PriceButtonPublic(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(text, color = p.onPlay, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(p.play).clickable { Sfx.click(); onClick() }.padding(horizontal = 26.dp, vertical = 11.dp))
}
