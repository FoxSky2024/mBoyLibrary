@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Pawb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private object StickerBitmaps { val map = HashMap<String, ImageBitmap?>() }

/** Dibujo de un sticker (cuadrado de [size]). Los "cs:<id>" son imagenes propias del usuario. */
@Composable
fun StickerArt(id: String, size: Dp = 64.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (id.startsWith("cs:")) {
            val ctx = LocalContext.current.applicationContext
            val bmp by produceState<ImageBitmap?>(StickerBitmaps.map[id], id) {
                if (StickerBitmaps.map.containsKey(id)) { value = StickerBitmaps.map[id]; return@produceState }
                value = withContext(Dispatchers.IO) {
                    runCatching { android.graphics.BitmapFactory.decodeFile(java.io.File(Pawb.stickerDir(ctx), id.removePrefix("cs:") + ".png").path)?.asImageBitmap() }.getOrNull()
                }.also { StickerBitmaps.map[id] = it }
            }
            bmp?.let { Image(it, null, Modifier.size(size), contentScale = ContentScale.Fit) }
            return@Box
        }
        val fs = (size.value * 0.30f).sp
        when (id) {
            "st_star" -> Ic(Icons.Filled.Star, size = size, tint = Color(0xFFFFC83D))
            "st_heart" -> Ic(Icons.Filled.Favorite, size = size, tint = Color(0xFFFF4D6D))
            "st_thumb" -> Ic(Icons.Filled.ThumbUp, size = size * 0.9f, tint = Color(0xFF4FC3F7))
            "st_paw" -> PawIcon(size)
            "st_gg" -> Badge("GG", Color(0xFF2E9A4E), size, fs * 1.2f)
            "st_new" -> Badge("NEW!", Color(0xFFE53935), size, fs)
            "st_100" -> Badge("100%", Color(0xFF8A4CD6), size, fs)
            "st_pow" -> Badge("¡POW!", Color(0xFFFF8F00), size, fs)
            "st_ok" -> Badge("OK", Color(0xFF43A047), size, fs * 1.2f)
            "st_wow" -> Badge("WOW", Color(0xFFFFB300), size, fs * 1.1f)
            "st_lol" -> Badge("LOL", Color(0xFF00ACC1), size, fs * 1.1f)
            "st_fire" -> Badge("FUEGO", Color(0xFFE64A19), size, fs * 0.85f)
            "st_gm" -> Badge("GAME OVER", Color(0xFF37474F), size, fs * 0.55f)
            "st_1up" -> Badge("1UP", Color(0xFF2E7D32), size, fs * 1.2f)
            "st_pro" -> Badge("PRO", Color(0xFF1565C0), size, fs * 1.2f)
            "st_mvp" -> Badge("MVP", Color(0xFFAD1457), size, fs * 1.2f)
            "st_king" -> Badge("REY", Color(0xFFB8860B), size, fs * 1.2f)
            else -> PawIcon(size)
        }
    }
}

@Composable
private fun Badge(text: String, color: Color, size: Dp, fs: androidx.compose.ui.unit.TextUnit) {
    Box(
        Modifier.size(size, size * 0.62f).clip(RoundedCornerShape(size * 0.16f)).background(color)
            .border(2.dp, Color.White, RoundedCornerShape(size * 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = fs, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false) }
}

/** Stickers que el usuario puede pegar: los comprados + los propios. */
fun ownedStickerIds(): List<String> =
    ShopCatalog.stickers.filter { Pawb.has(it.id) }.map { it.id } + Pawb.customStickers.value

/**
 * Capa de stickers del PERFIL: se dibujan sobre la pantalla del perfil (como pegatinas en una consola real).
 * Fuera del modo edicion no capturan toques. En edicion: arrastrar, pellizcar (tamano y giro), "+ Sticker", "Quitar" y "Listo".
 */
@Composable
fun StickerLayer(modifier: Modifier = Modifier) {
    val edit = Pawb.stickerEdit.value
    val list = Pawb.placed.value
    val d = LocalDensity.current
    var picker by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val base = with(d) { 64.dp.toPx() }
        for (st in list) {
            val sel = edit && Pawb.stickerSel.value == st.uid
            Box(
                Modifier
                    .offset { IntOffset((st.x * w - base / 2f).roundToInt(), (st.y * h - base / 2f).roundToInt()) }
                    .size(64.dp)
                    .graphicsLayer { scaleX = st.scale; scaleY = st.scale; rotationZ = st.rot }
                    .then(if (sel) Modifier.border(1.5.dp, Color(0xFF00E676), RoundedCornerShape(6.dp)) else Modifier)
                    .then(
                        if (!edit) Modifier else Modifier.pointerInput(st.uid) {
                            detectTransformGestures { _, pan, zoom, rot ->
                                Pawb.stickerSel.value = st.uid
                                val cur = Pawb.placed.value.firstOrNull { it.uid == st.uid } ?: return@detectTransformGestures
                                Pawb.updateSticker(cur.uid, cur.x + pan.x * cur.scale / w, cur.y + pan.y * cur.scale / h, cur.scale * zoom, cur.rot + rot)
                            }
                        }.pointerInput(st.uid + "t") {
                            detectTapGestures(onPress = { Pawb.stickerSel.value = st.uid; tryAwaitRelease(); Pawb.commitStickers() })
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) { StickerArt(st.sid, 64.dp) }
        }
        if (edit) {
            val p = LocalPalette.current
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0xE6101418)).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+ Sticker", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable { picker = true }.padding(horizontal = 14.dp, vertical = 7.dp))
                if (Pawb.stickerSel.value.isNotEmpty()) Text("Quitar", color = Color(0xFFFF8A80), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FF5252)).clickable { Pawb.removeSticker(Pawb.stickerSel.value) }.padding(horizontal = 14.dp, vertical = 7.dp))
                Text("Listo", color = p.onPlay, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(p.play).clickable { Pawb.commitStickers(); Pawb.stickerEdit.value = false; Pawb.stickerSel.value = "" }.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
    }
    if (picker) {
        val mine = ownedStickerIds()
        AlertDialog(
            onDismissRequest = { picker = false },
            confirmButton = { TextButton({ picker = false }) { Text("Cerrar") } },
            title = { Text("Pegar sticker") },
            text = {
                if (mine.isEmpty()) Text("Aún no tienes stickers. Consíguelos en la tienda (pestaña Stickers) o crea uno con tu imagen.")
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    mine.forEach { id -> Box(Modifier.clickable { Pawb.placeSticker(id); picker = false }.padding(4.dp)) { StickerArt(id, 52.dp) } }
                }
            },
        )
    }
}
