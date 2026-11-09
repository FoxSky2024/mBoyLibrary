package app.gbalibrary.data

import android.util.Log

/**
 * Detecta el tipo de guardado y el RTC leyendo la ROM (cadenas de las librerias de guardado de
 * Nintendo incrustadas en el codigo), NO por el codigo de juego de la cabecera: los hacks cambian
 * ese codigo y mGBA deja de reconocerlos (sin FLASH 128K ni RTC no guardan o se quedan en negro).
 */
object SaveType {
    const val AUTO = 0
    const val SRAM = 1
    const val FLASH512 = 2
    const val FLASH1M = 3
    const val EEPROM = 4 // 8 KB
    const val EEPROM512 = 5 // 512 bytes

    fun label(t: Int) = when (t) {
        SRAM -> "SRAM"
        FLASH512 -> "FLASH 64K"
        FLASH1M -> "FLASH 128K"
        EEPROM -> "EEPROM 8K"
        EEPROM512 -> "EEPROM 512B"
        else -> "Auto"
    }

    class Result(val type: Int, val rtc: Boolean, val reason: String)

    /**
     * [savFileLength]: tamano del .sav que ya existe (0 si no hay). Sirve de desempate cuando la ROM
     * trae mas de una libreria de guardado.
     */
    fun analyze(rom: ByteArray, savFileLength: Long): Result {
        var sram = false
        var flash512 = false
        var flash1m = false
        var eeprom = false
        var rtc = false

        // Una sola pasada por la ROM (hasta 32 MB) filtrando por la primera letra.
        val n = rom.size - 12
        var i = 0
        while (i < n) {
            when (rom[i].toInt()) {
                'F'.code -> {
                    if (match(rom, i, "FLASH1M_V")) flash1m = true
                    else if (match(rom, i, "FLASH512_V") || match(rom, i, "FLASH_V")) flash512 = true
                }
                'E'.code -> if (match(rom, i, "EEPROM_V")) eeprom = true
                'S'.code -> {
                    if (match(rom, i, "SRAM_V") || match(rom, i, "SRAM_F_V")) sram = true
                    else if (match(rom, i, "SIIRTC_V")) rtc = true
                }
            }
            i++
        }

        val flash = flash1m || flash512
        val families = (if (sram) 1 else 0) + (if (flash) 1 else 0) + (if (eeprom) 1 else 0)

        val type: Int
        val reason: String
        when {
            families == 1 && flash -> {
                type = if (flash1m) FLASH1M else FLASH512
                reason = "libreria FLASH en la ROM"
            }
            families == 1 && sram -> {
                type = SRAM
                reason = "libreria SRAM en la ROM"
            }
            families == 1 && eeprom -> {
                type = eepromBySize(savFileLength)
                reason = if (type == AUTO) "EEPROM (tamano lo decide mGBA)" else "EEPROM por tamano del .sav"
            }
            families > 1 -> {
                type = bySize(savFileLength)
                reason = if (type == AUTO) "varias librerias, lo decide mGBA" else "varias librerias, desempate por tamano del .sav"
            }
            else -> {
                type = AUTO
                reason = "sin libreria reconocida, lo decide mGBA"
            }
        }
        Log.i("SaveType", "guardado=${label(type)} ($reason) rtc=$rtc")
        return Result(type, rtc, reason)
    }

    private fun eepromBySize(len: Long) = when (len) {
        512L -> EEPROM512
        8192L -> EEPROM
        else -> AUTO
    }

    private fun bySize(len: Long) = when (len) {
        32768L -> SRAM
        65536L -> FLASH512
        131072L -> FLASH1M
        512L -> EEPROM512
        8192L -> EEPROM
        else -> AUTO
    }

    private fun match(data: ByteArray, at: Int, pat: String): Boolean {
        if (at + pat.length > data.size) return false
        for (k in pat.indices) if (data[at + k].toInt() != pat[k].code) return false
        return true
    }
}
