package app.gbalibrary.data

import android.content.Context
import java.io.File

/**
 * Cajas COMPLETAS (frente + lomo + reverso) incluidas en la app (assets/boxes/<clave>_{front,spine,back}.jpg).
 * Si el juego coincide, se copian al cache y la caja 3D las usa tal cual, sin internet.
 * Para agregar mas: poner los 3 jpg en assets/boxes y una linea en [RULES].
 */
object BundledBoxes {
    /** consola (extension), palabras que deben aparecer (titulo+cabecera sin simbolos, minusculas), clave de la caja. */
    private class Rule(val exts: Set<String>, val any: List<String>, val key: String, val all: List<String> = emptyList())

    private val RULES = listOf(
        Rule(setOf("gba"), listOf("ruby", "rubi", "axv"), "rubi"),
        Rule(setOf("gba"), listOf("sapphire", "zafiro", "axp"), "zafiro"),
        Rule(setOf("gb", "gbc"), listOf("yellow", "pikachu"), "yellow_us", all = listOf("pokemon")),
        Rule(setOf("gb", "gbc"), listOf("amarilla"), "amarilla"),
        Rule(setOf("gb", "gbc"), listOf("red", "roja"), "roja", all = listOf("pokemon")),
        Rule(setOf("gb", "gbc"), listOf("blue", "azul"), "azul", all = listOf("pokemon")),
        Rule(setOf("gbc"), listOf("silver", "plata", "slv"), "plata", all = listOf("pokemon")),
        Rule(setOf("gbc"), listOf("crystal", "cristal"), "cristal", all = listOf("pokemon")),
        Rule(setOf("gb"), listOf("finalfantasylegend", "saga"), "ffl"),
    )

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun keyFor(game: Game): String? {
        val ext = game.fileName.substringAfterLast('.', "").lowercase()
        val text = norm(game.title + " " + game.headerTitle + " " + game.code)
        // "amarilla" gana a "yellow"/"red" cuando el nombre esta en espanol
        return RULES.firstOrNull { r ->
            ext in r.exts && r.all.all { it in text } && r.any.any { it in text }
        }?.key
    }

    /** Copia la caja incluida al cache del juego. true si el juego tiene caja incluida. [overwriteFront] = tambien el frente. */
    fun install(ctx: Context, game: Game, overwriteFront: Boolean): Boolean {
        val key = keyFor(game) ?: return false
        return try {
            fun copy(part: String, dest: File, force: Boolean) {
                if (dest.exists() && !force) return
                ctx.assets.open("boxes/${key}_$part.jpg").use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            }
            copy("spine", BoxArtFetcher.spineFile(ctx, game.id), false)
            copy("back", BoxArtFetcher.backFile(ctx, game.id), false)
            val front = Covers.coverFile(ctx, game.id)
            copy("front", front, overwriteFront || !front.exists())
            true
        } catch (e: Exception) { false }
    }
}
