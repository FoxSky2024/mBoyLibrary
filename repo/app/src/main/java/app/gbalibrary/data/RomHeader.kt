package app.gbalibrary.data

/** Lectura de la cabecera estandar de una ROM de GBA (primeros 0xC0 bytes). */
object RomHeader {
    class Info(val title: String, val code: String, val maker: String, val valid: Boolean)

    fun parse(head: ByteArray, length: Int): Info? {
        // Nintendo DS: CRC del logo (0xCF56) en 0x15C; titulo en 0x00, codigo en 0x0C, fabricante en 0x10.
        if (length >= 0x160 && (head[0x15C].toInt() and 0xFF) == 0x56 && (head[0x15D].toInt() and 0xFF) == 0xCF) {
            fun a(off: Int, n: Int) = String(head, off, n, Charsets.US_ASCII).filter { it.code in 32..126 }.trim()
            return Info(title = a(0, 12), code = a(0x0C, 4), maker = a(0x10, 2), valid = true)
        }
        // Game Boy / Color: logo de Nintendo en 0x104 (CE ED ...); titulo en 0x134.
        if (length >= 0x150 && (head[0x104].toInt() and 0xFF) == 0xCE && (head[0x105].toInt() and 0xFF) == 0xED) {
            val t = String(head, 0x134, 15, Charsets.US_ASCII).filter { it.code in 32..126 }.trim()
            return Info(title = t, code = "", maker = "", valid = true)
        }
        if (length < 0xC0) return null
        fun ascii(off: Int, n: Int): String =
            String(head, off, n, Charsets.US_ASCII)
                .filter { it.code in 32..126 }
                .trim()

        val valid = (head[0xB2].toInt() and 0xFF) == 0x96
        return Info(
            title = ascii(0xA0, 12),
            code = ascii(0xAC, 4),
            maker = ascii(0xB0, 2),
            valid = valid,
        )
    }

    /**
     * Nombre legible a partir del archivo: quita extension, (USA), [!], guiones bajos y caracteres
     * que rompen rutas o la interfaz ( / \\ : * ? " < > | y de control). El archivo original no se toca.
     */
    fun cleanTitle(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val noTags = base
            .replace(Regex("""\s*[\(\[][^)\]]*[)\]]"""), "")
            .replace('_', ' ')
        val safe = noTags
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return safe.ifBlank { base.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), " ").trim() }
    }

    /**
     * Nombre para buscar portadas: sin version (v0.5.9), sin textos entre parentesis, sin guiones ni
     * signos. "Pokemon Azoth v0.5.9 RIVER-EDITION" -> "Pokemon Azoth RIVER EDITION" (la busqueda
     * despues prueba tambien sin las ultimas palabras).
     */
    fun searchName(title: String): String {
        var t = title
            .replace(Regex("""[\(\[][^)\]]*[)\]]"""), " ")
            .replace(Regex("""(?i)\bv(er(sion)?)?\s*\d+([._]\d+)*[a-z]?\b"""), " ")
            .replace(Regex("""\b\d+([.]\d+){1,3}[a-z]?\b"""), " ")
        t = t.replace(Regex("""[-_.:,;!?/\\|+~]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return t.ifBlank { title.trim() }
    }
}
