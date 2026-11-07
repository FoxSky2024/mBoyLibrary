package app.gbalibrary.data

/**
 * Detecta el tipo de guardado leyendo la ROM (cadenas de la libreria de guardado de Nintendo
 * incrustadas en el codigo), NO por el codigo de juego de la cabecera.
 */
object SaveType {
    const val AUTO = 0
    const val SRAM = 1
    const val FLASH512 = 2
    const val FLASH1M = 3
    const val EEPROM = 4

    fun label(t: Int) = when (t) {
        SRAM -> "SRAM"
        FLASH512 -> "FLASH 64K"
        FLASH1M -> "FLASH 128K"
        EEPROM -> "EEPROM"
        else -> "Auto"
    }

    private val TAGS: List<Pair<ByteArray, Int>> = listOf(
        "FLASH1M_V" to FLASH1M,
        "FLASH512_V" to FLASH512,
        "FLASH_V" to FLASH512,
        "EEPROM_V" to EEPROM,
        "SRAM_F_V" to SRAM,
        "SRAM_V" to SRAM,
    ).map { it.first.toByteArray(Charsets.US_ASCII) to it.second }

    fun detect(rom: ByteArray): Int {
        var best = AUTO
        var bestRank = Int.MAX_VALUE
        TAGS.forEachIndexed { rank, (tag, type) ->
            if (rank < bestRank && indexOf(rom, tag) >= 0) {
                best = type
                bestRank = rank
            }
        }
        return best
    }

    private fun indexOf(data: ByteArray, pat: ByteArray): Int {
        val first = pat[0]
        val last = data.size - pat.size
        var i = 0
        while (i <= last) {
            if (data[i] == first) {
                var j = 1
                while (j < pat.size && data[i + j] == pat[j]) j++
                if (j == pat.size) return i
            }
            i++
        }
        return -1
    }
}
