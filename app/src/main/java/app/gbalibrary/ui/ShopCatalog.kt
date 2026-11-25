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
class ShopPack(val id: String, val name: String, val desc: String, val price: Int, val bgs: Boolean, val banners: Boolean, val count: Int)

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
        ShopBanner("bn_paws", "Huellitas", 90, Rarity.EPIC, "PAWB CLUB", "🐾 🐾 🐾", listOf(c(0xFF8A4CD6), c(0xFF2A1A6B)), 3),
        ShopBanner("bn_sunset", "Atardecer", 110, Rarity.EPIC, "ÚLTIMA PARTIDA", "El sol se va", listOf(c(0xFFFF8A4C), c(0xFF6A1B6E)), 2),
        ShopBanner("bn_champ", "Campeón", 260, Rarity.LEGENDARY, "CAMPEÓN", "Todos los hitos", listOf(c(0xFFF5B642), c(0xFF7A4A08)), 1),
    )

    val packs = listOf(
        ShopPack("pk_bg", "Paquete de fondos", "1 fondo aleatorio que no tengas", 40, true, false, 1),
        ShopPack("pk_bn", "Paquete de banners", "1 banner aleatorio que no tengas", 40, false, true, 1),
        ShopPack("pk_mix", "Paquete sorpresa", "3 objetos aleatorios (fondos o banners)", 100, true, true, 3),
    )

    fun bg(id: String) = backgrounds.firstOrNull { it.id == id }
    fun banner(id: String) = banners.firstOrNull { it.id == id }
    fun custom(id: String) = Pawb.custom.value.firstOrNull { it.id == id }

    /** Abre un paquete: elige objetos que no tengas, con probabilidad segun rareza. Devuelve los ids ganados. */
    fun openPack(pack: ShopPack): List<String>? {
        val pool = buildList {
            if (pack.bgs) backgrounds.filter { !Pawb.has(it.id) }.forEach { add(it.id to it.rarity) }
            if (pack.banners) banners.filter { !Pawb.has(it.id) }.forEach { add(it.id to it.rarity) }
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

    fun nameOf(id: String): String = bg(id)?.name ?: banner(id)?.name ?: custom(id)?.name ?: id
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
fun BannerArt(title: String, subtitle: String, colors: List<Color>, style: Int, modifier: Modifier = Modifier) {
    Box(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))) {
        Canvas(Modifier.fillMaxSize()) { drawStyled(colors, style) }
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
    ShopCatalog.custom(id)?.let { BannerArt(it.title, it.subtitle, it.colors.map { c -> Color(c) }, it.style, modifier); return true }
    return false
}

/** Fondo de pantalla equipado (si hay), sobre el color base. */
@Composable
fun Modifier.equippedBackground(): Modifier {
    val b = ShopCatalog.bg(Pawb.bg.value) ?: return this
    return this.then(Modifier.drawBehind { drawStyled(b.colors.map { it.copy(alpha = 0.7f) }, b.style) })
}
