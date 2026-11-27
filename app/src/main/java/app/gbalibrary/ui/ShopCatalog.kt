package app.gbalibrary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
class ShopBanner(val id: String, val name: String, val price: Int, val rarity: Rarity, val title: String, val subtitle: String, val colors: List<Color>, val style: Int, val img: String = "")
/** shape: 0 original, 1 redondeado, 2 cuadrado, 3 diamante, 4 hexagono. pattern: 0 liso, 1 rayas, 2 puntos, 3 brillo. */
class ShopSkin(
    val id: String, val name: String, val price: Int, val rarity: Rarity, val idle: Color, val border: Color, val glyph: Color, val active: Color?,
    val shape: Int = 0, val pattern: Int = 0, val idle2: Color? = null, val theme: String = "",
)
class ShopKeychain(val id: String, val name: String, val price: Int, val rarity: Rarity, val charm: Int, val c1: Color, val c2: Color, val img: String = "", val fx: Int = 0)
class ShopFrame(val id: String, val name: String, val price: Int, val rarity: Rarity, val kind: Int, val c1: Color, val c2: Color, val img: String = "")
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
        ShopBanner("bn_mint", "Menta fresca", 30, Rarity.COMMON, "MENTA", "Aire fresco", listOf(c(0xFF2FBF9B), c(0xFF0B3D33)), 1),
        ShopBanner("bn_peach", "Durazno", 30, Rarity.COMMON, "DURAZNO", "Dulce y suave", listOf(c(0xFFFFB38A), c(0xFFC2566B)), 2),
        ShopBanner("bn_slate", "Pizarra", 30, Rarity.COMMON, "PIZARRA", "Sobrio y limpio", listOf(c(0xFF4A5568), c(0xFF1A202C)), 0),
        ShopBanner("bn_lemon", "Limón", 35, Rarity.COMMON, "LIMÓN", "Ácido y brillante", listOf(c(0xFFFFF176), c(0xFFF9A825)), 3),
        ShopBanner("bn_cherry", "Cereza", 40, Rarity.COMMON, "CEREZA", "Rojo intenso", listOf(c(0xFFD81B60), c(0xFF4A0A22)), 1),
        ShopBanner("bn_sky", "Cielo", 45, Rarity.COMMON, "CIELO", "Nubes y calma", listOf(c(0xFF81D4FA), c(0xFF1976D2)), 2),
        ShopBanner("bn_neon", "Neón city", 80, Rarity.RARE, "NEÓN CITY", "Luces de la noche", listOf(c(0xFF00E5FF), c(0xFFFF2BD6)), 1),
        ShopBanner("bn_forest", "Bosque", 70, Rarity.RARE, "BOSQUE", "Entre los árboles", listOf(c(0xFF2E7D32), c(0xFF0B2A12)), 2),
        ShopBanner("bn_deep", "Profundo", 75, Rarity.RARE, "ABISMO", "Más abajo aún", listOf(c(0xFF0D47A1), c(0xFF020B1F)), 2),
        ShopBanner("bn_candy", "Algodón", 75, Rarity.RARE, "ALGODÓN", "Dulce de feria", listOf(c(0xFFFF9ACB), c(0xFF8EC5FF)), 3),
        ShopBanner("bn_lava", "Lava", 85, Rarity.RARE, "LAVA", "Calor puro", listOf(c(0xFFFF6F00), c(0xFF3E0F05)), 1),
        ShopBanner("bn_blizz", "Ventisca", 80, Rarity.RARE, "VENTISCA", "Frío extremo", listOf(c(0xFFE1F5FE), c(0xFF4FC3F7)), 3),
        ShopBanner("bn_arcade", "Arcade", 90, Rarity.RARE, "ARCADE", "Insert coin", listOf(c(0xFFFFD600), c(0xFF6A1B9A)), 3),
        ShopBanner("bn_galaxy", "Galaxia", 130, Rarity.EPIC, "GALAXIA", "Entre estrellas", listOf(c(0xFF7C4DFF), c(0xFF1A0B4A), c(0xFF05060F)), 3),
        ShopBanner("bn_cyber", "Cyber", 140, Rarity.EPIC, "CYBER", "Sistema en línea", listOf(c(0xFF00E676), c(0xFF004D40), c(0xFF03130E)), 1),
        ShopBanner("bn_royal", "Real", 150, Rarity.EPIC, "REAL", "Para la realeza", listOf(c(0xFF6A1B9A), c(0xFFFFD54F)), 1),
        ShopBanner("bn_toxic", "Tóxico", 130, Rarity.EPIC, "TÓXICO", "Peligro radiactivo", listOf(c(0xFFAEEA00), c(0xFF1B5E20)), 2),
        ShopBanner("bn_vapor", "Vaporwave", 140, Rarity.EPIC, "VAPORWAVE", "Estética 80s", listOf(c(0xFFFF71CE), c(0xFF01CDFE), c(0xFF05FFA1)), 2),
        ShopBanner("bn_rainbow", "Arcoíris", 300, Rarity.LEGENDARY, "ARCOÍRIS", "Todos los colores", listOf(c(0xFFFF5252), c(0xFFFFEB3B), c(0xFF69F0AE), c(0xFF448AFF), c(0xFFE040FB)), 1),
        ShopBanner("bn_diamond", "Diamante", 320, Rarity.LEGENDARY, "DIAMANTE", "Brillo eterno", listOf(c(0xFFE0F7FA), c(0xFF80DEEA), c(0xFF7E57C2)), 3),
        ShopBanner("bn_void", "Vacío", 300, Rarity.LEGENDARY, "VACÍO", "Nada te detiene", listOf(c(0xFF000000), c(0xFF4A148C), c(0xFF000000)), 2),
        ShopBanner("bn_sun", "Sol naciente", 280, Rarity.LEGENDARY, "SOL NACIENTE", "Nuevo día", listOf(c(0xFFFFCA28), c(0xFFFF7043), c(0xFFC2185B)), 2),
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
        ShopSkin("sk_lav", "Lavanda", 60, Rarity.COMMON, c(0xAA6A4C9C), c(0xFFD1C4E9), Color.White, c(0xCCEDE7F6), shape = 1, pattern = 3, theme = "Suave y morado"),
        ShopSkin("sk_cherry", "Cereza", 60, Rarity.COMMON, c(0xAAB71C4A), c(0xFFFF8A80), Color.White, c(0xCCFFCDD2), shape = 1, pattern = 2, idle2 = c(0xAA6A0F2B), theme = "Frutal"),
        ShopSkin("sk_chrome", "Cromo", 80, Rarity.RARE, c(0xCC9EA7AD), c(0xFFFFFFFF), c(0xFF111111), c(0xCCCFD8DC), shape = 0, pattern = 3, theme = "Metal pulido"),
        ShopSkin("sk_matrix", "Matrix", 100, Rarity.RARE, c(0xCC001A0A), c(0xFF00E676), c(0xFF00E676), c(0xCC69F0AE), shape = 2, pattern = 1, theme = "Código verde"),
        ShopSkin("sk_sunset", "Atardecer", 100, Rarity.RARE, c(0xAAC2410C), c(0xFFFFAB40), Color.White, c(0xCCFFE082), shape = 3, pattern = 2, idle2 = c(0xAA6A1B6E), theme = "Cielo naranja"),
        ShopSkin("sk_bubble", "Burbuja", 90, Rarity.RARE, c(0x884FC3F7), c(0xFFFFFFFF), Color.White, c(0xCCB3E5FC), shape = 1, pattern = 2, idle2 = c(0x88AB47BC), theme = "Pompas"),
        ShopSkin("sk_midnight", "Medianoche", 150, Rarity.EPIC, c(0xCC0A0F2A), c(0xFF7C4DFF), c(0xFFB388FF), c(0xCC651FFF), shape = 4, pattern = 2, theme = "Cielo estrellado"),
        ShopSkin("sk_candy", "Caramelo", 140, Rarity.EPIC, c(0xAAFF6FA8), c(0xFFFFFFFF), Color.White, c(0xCCFFF59D), shape = 3, pattern = 1, idle2 = c(0xAA7C4DFF), theme = "Rayas dulces"),
        ShopSkin("sk_toxic", "Tóxico", 150, Rarity.EPIC, c(0xCC1B3A0A), c(0xFFAEEA00), c(0xFFAEEA00), c(0xCCCCFF90), shape = 4, pattern = 1, theme = "Radiactivo"),
        ShopSkin("sk_obsidian", "Obsidiana", 320, Rarity.LEGENDARY, c(0xE6000000), c(0xFFB388FF), c(0xFFE1BEE7), c(0xCCEA80FC), shape = 4, pattern = 3, idle2 = c(0xE61A0B2E), theme = "Cristal oscuro"),
        ShopSkin("sk_rainbow", "Arcoíris", 300, Rarity.LEGENDARY, c(0xAA3B2F7A), c(0xFFFFFFFF), Color.White, c(0xCCFFEB3B), shape = 3, pattern = 3, idle2 = c(0xAAC2185B), theme = "Prisma"),
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
        ShopFrame("fr_silver", "Plata", 70, Rarity.COMMON, 0, c(0xFFF5F5F5), c(0xFF8E9AA6)),
        ShopFrame("fr_bronze", "Bronce", 60, Rarity.COMMON, 0, c(0xFFE0A96D), c(0xFF7A4A1E)),
        ShopFrame("fr_rose", "Oro rosa", 110, Rarity.RARE, 0, c(0xFFFFD1DC), c(0xFFB76E79)),
        ShopFrame("fr_birch", "Abedul", 50, Rarity.COMMON, 3, c(0xFFE8D8B8), c(0xFF9C8660)),
        ShopFrame("fr_dark", "Roble oscuro", 60, Rarity.COMMON, 3, c(0xFF5D4037), c(0xFF2B1B15)),
        ShopFrame("fr_pixmint", "Píxel menta", 50, Rarity.COMMON, 2, c(0xFF2FBF9B), c(0xFF06231D)),
        ShopFrame("fr_pixred", "Píxel rojo", 50, Rarity.COMMON, 2, c(0xFFE53935), c(0xFF2A0A0A)),
        ShopFrame("fr_neong", "Neón verde", 120, Rarity.RARE, 1, c(0xFF69F0AE), c(0xFFFFFF00)),
        ShopFrame("fr_neono", "Neón naranja", 120, Rarity.RARE, 1, c(0xFFFF9100), c(0xFFFF1744)),
        ShopFrame("fr_neonp", "Neón rosa", 125, Rarity.RARE, 1, c(0xFFFF80AB), c(0xFF7C4DFF)),
        ShopFrame("fr_ticketb", "Entrada azul", 65, Rarity.COMMON, 7, c(0xFFB3E5FC), c(0xFF4F8FC0)),
        ShopFrame("fr_pawsb", "Huellas azules", 130, Rarity.RARE, 6, c(0xFF81D4FA), c(0xFF0D2A4A)),
        ShopFrame("fr_pawsr", "Huellas rosa", 130, Rarity.RARE, 6, c(0xFFFF8AB8), c(0xFF4A1A33)),
        ShopFrame("fr_silverorn", "Ornamentado plata", 200, Rarity.EPIC, 5, c(0xFFECEFF1), c(0xFF37474F)),
        ShopFrame("fr_emerald", "Ornamentado esmeralda", 210, Rarity.EPIC, 5, c(0xFF69F0AE), c(0xFF0B3D2A)),
        ShopFrame("fr_aurora2", "Aurora boreal", 160, Rarity.RARE, 8, c(0xFF80D8FF), c(0xFFB9F6CA)),
        ShopFrame("fr_aurora3", "Aurora rosa", 160, Rarity.RARE, 8, c(0xFFFF80AB), c(0xFFEA80FC)),
        ShopFrame("fr_blue", "Llama azul", 180, Rarity.EPIC, 9, c(0xFF80D8FF), c(0xFF0D47A1)),
        ShopFrame("fr_toxicf", "Llama tóxica", 180, Rarity.EPIC, 9, c(0xFFCCFF90), c(0xFF1B5E20)),
        ShopFrame("fr_ice2", "Hielo profundo", 150, Rarity.RARE, 10, c(0xFFB3E5FC), c(0xFF01579B)),
        ShopFrame("fr_crystalp", "Cristal rosa", 220, Rarity.EPIC, 11, c(0xFFF8BBD0), c(0xFFAD1457)),
        ShopFrame("fr_crystalg", "Cristal esmeralda", 220, Rarity.EPIC, 11, c(0xFFB9F6CA), c(0xFF00695C)),
        ShopFrame("fr_galaxy2", "Nebulosa", 250, Rarity.LEGENDARY, 13, c(0xFF3A1A6A), c(0xFF0A0A2A)),
        ShopFrame("fr_sakura2", "Sakura azul", 125, Rarity.RARE, 14, c(0xFFB3E5FC), c(0xFF1565C0)),
        ShopFrame("fr_circuit2", "Circuito rojo", 185, Rarity.EPIC, 15, c(0xFFFF5252), c(0xFF2A0606)),
        ShopFrame("fr_circuit3", "Circuito cian", 185, Rarity.EPIC, 15, c(0xFF18FFFF), c(0xFF06202A)),
        ShopFrame("fr_holo2", "Holográfico oscuro", 280, Rarity.LEGENDARY, 4, c(0xFFB388FF), c(0xFF000000)),
        ShopFrame("fr_rainbow2", "Píxel arcoíris oscuro", 170, Rarity.RARE, 12, c(0xFFFFFFFF), c(0xFF000000)),
        ShopFrame("fr_flame", "Llamas", 220, Rarity.EPIC, 16, c(0xFFFFD54F), c(0xFFD84315)),
        ShopFrame("fr_flameb", "Llamas azules", 230, Rarity.EPIC, 16, c(0xFF80D8FF), c(0xFF0D47A1)),
        ShopFrame("fr_inferno", "Infierno", 330, Rarity.LEGENDARY, 16, c(0xFFFF5252), c(0xFF1A0000)),
        ShopFrame("fr_snow", "Nevada", 150, Rarity.RARE, 17, c(0xFFFFFFFF), c(0xFF4FC3F7)),
        ShopFrame("fr_blizz", "Ventisca", 300, Rarity.LEGENDARY, 17, c(0xFFE1F5FE), c(0xFF0D47A1)),
        ShopFrame("fr_stars", "Lluvia de estrellas", 190, Rarity.EPIC, 18, c(0xFFFFF59D), c(0xFF7C4DFF)),
        ShopFrame("fr_stars2", "Estrellas rosa", 190, Rarity.EPIC, 18, c(0xFFFF80AB), c(0xFF4A148C)),
        ShopFrame("fr_cosmic", "Cósmico", 320, Rarity.LEGENDARY, 18, c(0xFF18FFFF), c(0xFF1A0B4A)),
        ShopFrame("fr_hearts", "Corazones", 150, Rarity.RARE, 19, c(0xFFFF80AB), c(0xFF880E4F)),
        ShopFrame("fr_hearts2", "Corazones morados", 160, Rarity.RARE, 19, c(0xFFEA80FC), c(0xFF311B92)),
        ShopFrame("fr_bubbles", "Burbujas", 120, Rarity.RARE, 20, c(0xFFB3E5FC), c(0xFF0277BD)),
        ShopFrame("fr_bubbles2", "Burbujas menta", 120, Rarity.RARE, 20, c(0xFFB9F6CA), c(0xFF00695C)),
        ShopFrame("fr_elec", "Voltaje", 230, Rarity.EPIC, 21, c(0xFFFFEA00), c(0xFF0D0D2B)),
        ShopFrame("fr_elec2", "Rayo violeta", 310, Rarity.LEGENDARY, 21, c(0xFFE040FB), c(0xFF1A0033)),
        ShopFrame("fr_ember", "Cenizas", 160, Rarity.RARE, 22, c(0xFFFF7043), c(0xFF212121)),
        ShopFrame("fr_smoke", "Humo", 140, Rarity.RARE, 22, c(0xFFCFD8DC), c(0xFF37474F)),
        ShopFrame("fr_petal", "Pétalos", 140, Rarity.RARE, 23, c(0xFFFFC1D9), c(0xFFD81B60)),
        ShopFrame("fr_petal2", "Pétalos dorados", 190, Rarity.EPIC, 23, c(0xFFFFE082), c(0xFF6D4C41)),
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
        ShopPack("pk_bn3", "Pack de banners x3", "3 banners aleatorios", 105, setOf("banner"), 3, c(0xFF6A5ACD)),
        ShopPack("pk_frm3", "Pack de marcos x3", "3 marcos aleatorios", 210, setOf("frame"), 3, c(0xFFE0A030)),
        ShopPack("pk_big", "Mega sobre", "6 objetos: banners y marcos", 220, setOf("banner", "frame"), 6, c(0xFFEF5350)),
    )

    fun skin(id: String) = skins.firstOrNull { it.id == id }
    /** Objetos creados desde el modo desarrollador (se guardan en el dispositivo). */
    val devBanners = androidx.compose.runtime.mutableStateListOf<ShopBanner>()
    val devFrames = androidx.compose.runtime.mutableStateListOf<ShopFrame>()
    /** Llaveros: cuelgan en el perfil y se balancean. charm: 0 huella, 1 estrella, 2 corazon, 3 gema, 4 cartucho. */
    val keychains = listOf(
        ShopKeychain("kc_paw", "Huella", 40, Rarity.COMMON, 0, c(0xFFE8892B), c(0xFF7A3D0A)),
        ShopKeychain("kc_star", "Estrella", 60, Rarity.COMMON, 1, c(0xFFFFD54F), c(0xFFC77800)),
        ShopKeychain("kc_heart", "Corazón", 80, Rarity.RARE, 2, c(0xFFFF6F9C), c(0xFF9B1B4A)),
        ShopKeychain("kc_gem", "Gema", 130, Rarity.RARE, 3, c(0xFF7FE9FF), c(0xFF1565C0)),
        ShopKeychain("kc_cart", "Cartucho", 170, Rarity.EPIC, 4, c(0xFFB0BEC5), c(0xFF37474F)),
        ShopKeychain("kc_gpaw", "Huella dorada", 280, Rarity.LEGENDARY, 0, c(0xFFFFE082), c(0xFFB8860B), fx = 1),
        ShopKeychain("kc_spark", "Estrella brillante", 150, Rarity.RARE, 1, c(0xFFFFF176), c(0xFFF9A825), fx = 1),
        ShopKeychain("kc_flame", "Corazón de fuego", 220, Rarity.EPIC, 2, c(0xFFFF7043), c(0xFFB71C1C), fx = 2),
        ShopKeychain("kc_snow", "Gema de hielo", 220, Rarity.EPIC, 3, c(0xFFB3E5FC), c(0xFF0277BD), fx = 3),
        ShopKeychain("kc_love", "Amor flotante", 200, Rarity.EPIC, 2, c(0xFFF48FB1), c(0xFFAD1457), fx = 4),
        ShopKeychain("kc_bubble", "Cartucho burbuja", 260, Rarity.LEGENDARY, 4, c(0xFF80DEEA), c(0xFF006064), fx = 5),
    )
    val assetKeychains = androidx.compose.runtime.mutableStateListOf<ShopKeychain>()
    fun keychainsAll(): List<ShopKeychain> = keychains + assetKeychains
    fun keychain(id: String): ShopKeychain? = keychainsAll().firstOrNull { it.id == id }

    /** Objetos hechos con imagenes: assets/shop/banners y assets/shop/frames, con nombre `rareza_precio_nombre.png`. */
    val assetBanners = androidx.compose.runtime.mutableStateListOf<ShopBanner>()
    val assetFrames = androidx.compose.runtime.mutableStateListOf<ShopFrame>()
    fun allBanners(): List<ShopBanner> = banners + assetBanners + devBanners
    fun allFrames(): List<ShopFrame> = frames + assetFrames + devFrames

    private fun parseAsset(dir: String, file: String): Triple<Rarity, Int, String>? {
        val base = file.substringBeforeLast('.')
        val parts = base.split("_", limit = 3)
        if (parts.size < 3) return null
        val r = when (parts[0].lowercase()) {
            "comun", "común", "common" -> Rarity.COMMON
            "raro", "rare" -> Rarity.RARE
            "epico", "épico", "epic" -> Rarity.EPIC
            "legendario", "legendary" -> Rarity.LEGENDARY
            else -> return null
        }
        val price = parts[1].toIntOrNull() ?: return null
        return Triple(r, price, parts[2].replace('-', ' ').trim())
    }

    /** Lee las imagenes de la tienda desde assets (se llama al iniciar la app). */
    fun loadAssets(ctx: android.content.Context) {
        val am = ctx.assets
        assetBanners.clear(); assetFrames.clear(); assetKeychains.clear()
        runCatching { am.list("shop/keychains") }.getOrNull().orEmpty().sorted().forEach { f ->
            val (r, pr, nm) = parseAsset("keychains", f) ?: return@forEach
            // efecto de particulas segun palabras del nombre: brillo, fuego, nieve, amor, burbuja
            val ln = nm.lowercase()
            val fx = when { "brillo" in ln -> 1; "fuego" in ln -> 2; "nieve" in ln -> 3; "amor" in ln -> 4; "burbuja" in ln -> 5; else -> 0 }
            assetKeychains.add(ShopKeychain("as_k_" + f.substringBeforeLast('.'), nm, pr, r, 0, Color.White, Color.Gray, "shop/keychains/$f", fx))
        }
        runCatching { am.list("shop/banners") }.getOrNull().orEmpty().sorted().forEach { f ->
            val (r, pr, nm) = parseAsset("banners", f) ?: return@forEach
            assetBanners.add(ShopBanner("as_b_" + f.substringBeforeLast('.'), nm, pr, r, nm, "", listOf(Color(0xFF1B2430), Color(0xFF0B0F14)), 0, "shop/banners/$f"))
        }
        runCatching { am.list("shop/frames") }.getOrNull().orEmpty().sorted().forEach { f ->
            val (r, pr, nm) = parseAsset("frames", f) ?: return@forEach
            assetFrames.add(ShopFrame("as_f_" + f.substringBeforeLast('.'), nm, pr, r, 0, Color.White, Color.Black, "shop/frames/$f"))
        }
    }

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
fun BannerArt(title: String, subtitle: String, colors: List<Color>, style: Int, modifier: Modifier = Modifier, img: String = "", asset: String = "") {
    if (asset.isNotEmpty()) {
        val ab = rememberAssetImage(asset)
        Box(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(Color(0xFF14181F))) {
            if (ab != null) androidx.compose.foundation.Image(ab, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
        }
        return
    }
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
    ShopCatalog.banner(id)?.let { BannerArt(it.title, it.subtitle, it.colors, it.style, modifier, asset = it.img); return true }
    ShopCatalog.custom(id)?.let { BannerArt(it.title, it.subtitle, it.colors.map { c -> Color(c) }, it.style, modifier, it.img); return true }
    return false
}

/** Fondo de pantalla equipado (si hay), sobre el color base. */
@Composable
fun Modifier.equippedBackground(): Modifier = this // los fondos de la tienda se retiraron


/** Imagen de assets (cacheada) para objetos de la tienda hechos con imagenes. */
internal object AssetImages { val map = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>() }

@Composable
fun rememberAssetImage(path: String): androidx.compose.ui.graphics.ImageBitmap? {
    val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(AssetImages.map[path], path) {
        if (AssetImages.map.containsKey(path)) { value = AssetImages.map[path]; return@produceState }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }.also { AssetImages.map[path] = it }
    }
    return bmp
}


/** Precarga (durante la pantalla de carga) las imagenes de la tienda hechas con PNG. */
fun ShopCatalog.preloadImages(ctx: android.content.Context) {
    val paths = assetBanners.map { it.img } + assetFrames.map { it.img } + keychainsAll().map { it.img }
    paths.filter { it.isNotEmpty() && !AssetImages.map.containsKey(it) }.forEach { path ->
        AssetImages.map[path] = runCatching { ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
    }
}
