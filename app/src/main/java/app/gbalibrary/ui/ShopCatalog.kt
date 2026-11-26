package app.gbalibrary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.CustomBanner
import app.gbalibrary.data.Pawb
import kotlin.math.PI
import kotlin.math.sin

enum class Rarity(val label: String, val color: Color, val weight: Int) {
    COMMON("Común", Color(0xFF9AA5B1), 60), RARE("Raro", Color(0xFF3B8ED0), 28), EPIC("Épico", Color(0xFFA855F7), 10), LEGENDARY("Legendario", Color(0xFFF5B642), 2),
}

/** Estilos de dibujo (0 liso, 1 diagonal, 2 ondas, 3 puntos) compartidos por fondos y banners. */
val StyleNames = listOf("Liso", "Diagonal", "Ondas", "Puntos")

class ShopBg(val id: String, val name: String, val price: Int, val rarity: Rarity, val colors: List<Color>, val style: Int)
class ShopBanner(val id: String, val name: String, val price: Int, val rarity: Rarity, val title: String, val subtitle: String, val colors: List<Color>, val style: Int)
/** shape: 0 original, 1 redondeado, 2 cuadrado, 3 diamante, 4 hexagono. pattern: 0 liso, 1 rayas, 2 puntos, 3 brillo. */
class ShopSkin(
    val id: String, val name: String, val price: Int, val rarity: Rarity, val idle: Color, val border: Color, val glyph: Color, val active: Color?,
    val shape: Int = 0, val pattern: Int = 0, val idle2: Color? = null, val theme: String = "",
)
class ShopFrame(val id: String, val name: String, val price: Int, val rarity: Rarity, val kind: Int, val c1: Color, val c2: Color)
class ShopSticker(val id: String, val name: String, val price: Int, val rarity: Rarity)
class ShopPack(val id: String, val name: String, val desc: String, val price: Int, val kinds: Set<String>, val count: Int, val color: Color)

/**
 * CATALOGO DE LA TIENDA (editar aqui para agregar/quitar objetos). Ids unicos, precios en Pawb.
 */
object ShopCatalog {
    private fun c(v: Long) = Color(v)

    val backgrounds = listOf(
        ShopBg("bg_night", "Noche azul", 0, Rarity.COMMON, listOf(c(0xFF0E2236), c(0xFF05050A)), 0),
        ShopBg("bg_plum", "Ciruela", 30, Rarity.COMMON, listOf(c(0xFF3A1B4F), c(0xFF0B0612)), 0),
        ShopBg("bg_forest", "Bosque", 30, Rarity.COMMON, listOf(c(0xFF14432B), c(0xFF04110A)), 1),
        ShopBg("bg_ember", "Brasas", 45, Rarity.COMMON, listOf(c(0xFF7A2E0E), c(0xFF140805)), 2),
        ShopBg("bg_ocean", "Océano", 60, Rarity.RARE, listOf(c(0xFF0B5E8C), c(0xFF031A2B)), 2),
        ShopBg("bg_sakura", "Sakura", 70, Rarity.RARE, listOf(c(0xFFD86B9A), c(0xFF2A0F1E)), 3),
        ShopBg("bg_mint", "Menta", 70, Rarity.RARE, listOf(c(0xFF2FBF9B), c(0xFF06231D)), 1),
        ShopBg("bg_dusk", "Atardecer", 110, Rarity.EPIC, listOf(c(0xFFFF8A4C), c(0xFF6A1B6E), c(0xFF0B0620)), 2),
        ShopBg("bg_aurora", "Aurora", 120, Rarity.EPIC, listOf(c(0xFF37E8A0), c(0xFF2B4BD6), c(0xFF0A0620)), 2),
        ShopBg("bg_gold", "Oro viejo", 250, Rarity.LEGENDARY, listOf(c(0xFFF5B642), c(0xFF7A4A08), c(0xFF140C02)), 3),
    )

    val banners = listOf(
        ShopBanner("bn_retro", "Retro", 0, Rarity.COMMON, "RETRO FAN", "Jugando clásicos", listOf(c(0xFFE8892B), c(0xFF8A3B12)), 1),
        ShopBanner("bn_night", "Modo noche", 25, Rarity.COMMON, "MODO NOCHE", "Partidas hasta tarde", listOf(c(0xFF1A1033), c(0xFF3B1F6B)), 3),
        ShopBanner("bn_green", "Verde GB", 40, Rarity.COMMON, "GAME BOY", "Cuatro tonos de verde", listOf(c(0xFF9BBC0F), c(0xFF306230)), 3),
        ShopBanner("bn_wave", "Ola", 60, Rarity.RARE, "SURF", "Sigue la corriente", listOf(c(0xFF0B5E8C), c(0xFF2FBF9B)), 2),
        ShopBanner("bn_dual", "Doble pantalla", 70, Rarity.RARE, "DUAL SCREEN", "Toca y juega", listOf(c(0xFF3B7DD8), c(0xFF123566)), 1),
        ShopBanner("bn_paws", "Huellitas", 90, Rarity.EPIC, "PAWB CLUB", "· · ·", listOf(c(0xFF8A4CD6), c(0xFF2A1A6B)), 3),
        ShopBanner("bn_sunset", "Atardecer", 110, Rarity.EPIC, "ÚLTIMA PARTIDA", "El sol se va", listOf(c(0xFFFF8A4C), c(0xFF6A1B6E)), 2),
        ShopBanner("bn_champ", "Campeón", 260, Rarity.LEGENDARY, "CAMPEÓN", "Todos los hitos", listOf(c(0xFFF5B642), c(0xFF7A4A08)), 1),
    )

    /** Skins del emulador: colores de los botones tactiles. */
    val skins = listOf(
        ShopSkin("sk_gba", "Índigo GBA", 40, Rarity.COMMON, c(0xAA3B2F7A), c(0xFF8E7CFF), Color.White, c(0xCCB8A9FF), shape = 1, pattern = 3, theme = "Portátil clásica"),
        ShopSkin("sk_dmg", "Gris DMG", 50, Rarity.COMMON, c(0xCCB4B8BD), c(0xFF6E7176), c(0xFF1B1D20), c(0xCC8D9196), shape = 0, pattern = 0, theme = "Game Boy original"),
        ShopSkin("sk_mint", "Menta", 50, Rarity.COMMON, c(0xAA17706B), c(0xFF4FD1C5), Color.White, c(0xCC9AF0E8), shape = 1, pattern = 2, idle2 = c(0xAA0E4A4A), theme = "Burbujas"),
        ShopSkin("sk_pixel", "Píxel", 70, Rarity.RARE, c(0xCC1B3A8C), c(0xFFFFFFFF), Color.White, c(0xCCFFD54F), shape = 2, pattern = 2, theme = "8 bits"),
        ShopSkin("sk_fire", "Fuego", 90, Rarity.RARE, c(0xAA8A2A0E), c(0xFFFF8A4C), Color.White, c(0xCCFFC107), shape = 3, pattern = 1, idle2 = c(0xAA3B0F05), theme = "Diamantes ardientes"),
        ShopSkin("sk_sakura", "Sakura", 90, Rarity.RARE, c(0xAAB84A7B), c(0xFFFFB3D1), Color.White, c(0xCCFFE0EC), shape = 0, pattern = 2, idle2 = c(0xAA7A2B50), theme = "Pétalos"),
        ShopSkin("sk_neon", "Neón tech", 140, Rarity.EPIC, c(0x99000000), c(0xFF00E5FF), c(0xFF00E5FF), c(0xCCFF2BD6), shape = 4, pattern = 1, theme = "Hexágonos cyber"),
        ShopSkin("sk_ice", "Cristal de hielo", 150, Rarity.EPIC, c(0x884FC3F7), c(0xFFE1F5FE), Color.White, c(0xCCB3E5FC), shape = 3, pattern = 3, idle2 = c(0x881565C0), theme = "Diamantes de hielo"),
        ShopSkin("sk_gold", "Dorado real", 300, Rarity.LEGENDARY, c(0xAA7A4A08), c(0xFFFFD54F), c(0xFFFFE9A8), c(0xCCFFD54F), shape = 4, pattern = 3, idle2 = c(0xAA3E2504), theme = "Hexágonos de oro"),
    )

    val frames = listOf(
        ShopFrame("fr_wood", "Madera", 40, Rarity.COMMON, 3, c(0xFF8B5A2B), c(0xFF5A3A1A)),
        ShopFrame("fr_pixel", "Píxeles", 40, Rarity.COMMON, 2, c(0xFF3B7DD8), c(0xFF0E2236)),
        ShopFrame("fr_ticket", "Entrada", 55, Rarity.COMMON, 7, c(0xFFF5E6C8), c(0xFFC9A66B)),
        ShopFrame("fr_gold", "Oro", 100, Rarity.RARE, 0, c(0xFFFFE08A), c(0xFFB8860B)),
        ShopFrame("fr_neon", "Neón", 120, Rarity.RARE, 1, c(0xFF00E5FF), c(0xFFFF2BD6)),
        ShopFrame("fr_paws", "Huellas", 130, Rarity.RARE, 6, c(0xFFF5B642), c(0xFF2A1A4A)),
        ShopFrame("fr_ornate", "Ornamentado", 190, Rarity.EPIC, 5, c(0xFFE8C766), c(0xFF6B1E2E)),
        ShopFrame("fr_holo", "Holográfico", 260, Rarity.LEGENDARY, 4, c(0xFFFFFFFF), c(0xFF000000)),
        ShopFrame("fr_aurora", "Aurora", 150, Rarity.RARE, 8, c(0xFF69F0AE), c(0xFFB388FF)),
        ShopFrame("fr_fire", "Fuego", 170, Rarity.EPIC, 9, c(0xFFFFD54F), c(0xFFD84315)),
        ShopFrame("fr_ice", "Hielo", 140, Rarity.RARE, 10, c(0xFFE1F5FE), c(0xFF4FC3F7)),
        ShopFrame("fr_crystal", "Cristal", 210, Rarity.EPIC, 11, c(0xFFB3E5FC), c(0xFF7E57C2)),
        ShopFrame("fr_rainbow", "Píxel arcoíris", 160, Rarity.RARE, 12, c(0xFFFFFFFF), c(0xFF101018)),
        ShopFrame("fr_galaxy", "Galaxia", 240, Rarity.LEGENDARY, 13, c(0xFF1A1040), c(0xFF05060F)),
        ShopFrame("fr_sakura", "Sakura", 120, Rarity.RARE, 14, c(0xFFFFC1D9), c(0xFFD81B60)),
        ShopFrame("fr_circuit", "Circuito", 180, Rarity.EPIC, 15, c(0xFF00E676), c(0xFF06210F)),
    )

    val stickers = listOf(
        ShopSticker("st_star", "Estrella", 20, Rarity.COMMON),
        ShopSticker("st_heart", "Corazón", 20, Rarity.COMMON),
        ShopSticker("st_thumb", "Me gusta", 25, Rarity.COMMON),
        ShopSticker("st_paw", "Huella", 30, Rarity.COMMON),
        ShopSticker("st_gg", "GG", 40, Rarity.RARE),
        ShopSticker("st_new", "NEW!", 40, Rarity.RARE),
        ShopSticker("st_100", "100%", 70, Rarity.EPIC),
        ShopSticker("st_pow", "¡POW!", 70, Rarity.EPIC),
    )

    val packs = listOf(
        ShopPack("pk_bn", "Sobre de banners", "1 banner aleatorio", 40, setOf("banner"), 1, c(0xFF8A4CD6)),
        ShopPack("pk_frm", "Sobre de marcos", "1 marco aleatorio con efectos", 80, setOf("frame"), 1, c(0xFFD4A017)),
        ShopPack("pk_mix", "Sobre sorpresa", "3 objetos: banners y marcos", 120, setOf("banner", "frame"), 3, c(0xFFD64B4B)),
    )

    fun skin(id: String) = skins.firstOrNull { it.id == id }
    /** Objetos creados desde el modo desarrollador (se guardan en el dispositivo). */
    val devBanners = androidx.compose.runtime.mutableStateListOf<ShopBanner>()
    val devFrames = androidx.compose.runtime.mutableStateListOf<ShopFrame>()
    fun allBanners(): List<ShopBanner> = banners + devBanners
    fun allFrames(): List<ShopFrame> = frames + devFrames

    fun frame(id: String) = allFrames().firstOrNull { it.id == id }
    fun sticker(id: String) = stickers.firstOrNull { it.id == id }
    fun bg(id: String) = backgrounds.firstOrNull { it.id == id }
    fun banner(id: String) = allBanners().firstOrNull { it.id == id }
    fun custom(id: String) = Pawb.custom.value.firstOrNull { it.id == id }

    /** Abre un paquete: elige objetos que no tengas, con probabilidad segun rareza. Devuelve los ids ganados. */
    fun openPack(pack: ShopPack): List<String>? {
        val pool = buildList {
            if ("bg" in pack.kinds) backgrounds.filter { !Pawb.has(it.id) }.forEach { add(it.id to it.rarity) }
            if ("banner" in pack.kinds) allBanners().filter { !Pawb.has(it.id) }.forEach { add(it.id to it.rarity) }
            if ("sticker" in pack.kinds) stickers.filter { !Pawb.has(it.id) }.forEach { add(it.id to it.rarity) }
            if ("frame" in pack.kinds) allFrames().filter { !Pawb.has(it.id) }.forEach { add(it.id to it.rarity) }
        }.toMutableList()
        if (pool.isEmpty() || !Pawb.spend(pack.price)) return null
        val won = mutableListOf<String>()
        repeat(minOf(pack.count, pool.size)) {
            var r = (Math.random() * pool.sumOf { it.second.weight }).toInt()
            val pick = pool.first { r -= it.second.weight; r < 0 }
            pool.remove(pick); won += pick.first; Pawb.grant(pick.first)
        }
        return won
    }

    fun nameOf(id: String): String = bg(id)?.name ?: banner(id)?.name ?: frame(id)?.name ?: sticker(id)?.name ?: custom(id)?.name ?: id
    fun rarityOf(id: String): Rarity = bg(id)?.rarity ?: banner(id)?.rarity ?: frame(id)?.rarity ?: sticker(id)?.rarity ?: skin(id)?.rarity ?: Rarity.COMMON
}

/** Dibuja gradiente + patron segun [style]. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStyled(colors: List<Color>, style: Int) {
    val w = size.width
    val h = size.height
    drawRect(Brush.linearGradient(colors, start = Offset(0f, 0f), end = Offset(w, h)))
    when (style) {
        1 -> {
            var x = -h
            while (x < w) {
                drawPath(Path().apply { moveTo(x, h); lineTo(x + h * 0.8f, 0f); lineTo(x + h * 0.8f + 22f, 0f); lineTo(x + 22f, h); close() }, Color.White.copy(alpha = 0.08f))
                x += 70f
            }
        }
        2 -> for (k in 0 until 3) {
            val path = Path()
            val base = h * (0.35f + 0.22f * k)
            var x = 0f
            path.moveTo(0f, base)
            while (x <= w) { path.lineTo(x, base + h * 0.06f * sin(x / w * 2f * PI.toFloat() * 2f + k)); x += 10f }
            drawPath(path, Color.White.copy(alpha = 0.14f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        }
        3 -> {
            var y = 12f
            var row = 0
            while (y < h) {
                var x = if (row % 2 == 0) 12f else 30f
                while (x < w) { drawCircle(Color.White.copy(alpha = 0.10f), 4f, Offset(x, y)); x += 36f }
                y += 24f; row++
            }
        }
    }
}

/** Banner (catalogo o propio) con titulo y subtitulo. */
@Composable
fun BannerArt(title: String, subtitle: String, colors: List<Color>, style: Int, modifier: Modifier = Modifier, img: String = "") {
    val bmp = androidx.compose.runtime.remember(img) {
        if (img.isEmpty()) null else try {
            val b = android.util.Base64.decode(img, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
        } catch (e: Exception) { null }
    }
    Box(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))) {
        Canvas(Modifier.fillMaxSize()) { drawStyled(colors, style) }
        if (bmp != null) {
            androidx.compose.foundation.Image(bmp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp)) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = AppFonts.Display, maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
        }
    }
}

fun CustomBanner.art(): Pair<List<Color>, Int> = colors.map { Color(it) } to style

/** Banner equipado (de catalogo o propio) o null. */
@Composable
fun EquippedBannerArt(modifier: Modifier = Modifier): Boolean {
    val id = Pawb.banner.value
    if (id.isEmpty()) return false
    ShopCatalog.banner(id)?.let { BannerArt(it.title, it.subtitle, it.colors, it.style, modifier); return true }
    ShopCatalog.custom(id)?.let { BannerArt(it.title, it.subtitle, it.colors.map { c -> Color(c) }, it.style, modifier, it.img); return true }
    return false
}

/** Fondo de pantalla equipado (si hay), sobre el color base. */
@Composable
fun Modifier.equippedBackground(): Modifier = this // los fondos de la tienda se retiraron
