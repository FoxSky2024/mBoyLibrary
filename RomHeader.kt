package app.gbalibrary.data

/** Lectura de la cabecera estandar de una ROM de GBA (primeros 0xC0 bytes). */
object RomHeader {
    class Info(val title: String, val code: String, val maker: String, val valid: Boolean)

    fun parse(head: ByteArray, length: Int): Info? {
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

    /** Nombre legible a partir del archivo: quita extension, (USA), [!], guiones bajos. */
    fun cleanTitle(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        val noTags = base
            .replace(Regex("""\s*[\(\[][^)\]]*[)\]]"""), "")
            .replace('_', ' ')
            .trim()
        return noTags.ifBlank { base }
    }
}
