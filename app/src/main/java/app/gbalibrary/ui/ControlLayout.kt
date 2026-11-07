package app.gbalibrary.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import app.gbalibrary.data.Settings

/** Identificadores de los controles tactiles. */
object Ctl {
    const val DPAD = "dpad"
    const val A = "a"
    const val B = "b"
    const val L = "l"
    const val R = "r"
    const val START = "start"
    const val SELECT = "select"
    const val MENU = "menu"
    const val QUICK = "quick"
    val ALL = listOf(DPAD, A, B, L, R, START, SELECT, MENU, QUICK)
}

/** Posicion (centro) de cada control como fraccion del ancho/alto de la pantalla. */
class ControlLayout {
    val pos = mutableStateMapOf<String, Offset>()

    init { reset() }

    fun reset() {
        pos[Ctl.DPAD] = Offset(0.11f, 0.64f)
        pos[Ctl.L] = Offset(0.09f, 0.20f)
        pos[Ctl.R] = Offset(0.91f, 0.20f)
        pos[Ctl.A] = Offset(0.93f, 0.52f)
        pos[Ctl.B] = Offset(0.84f, 0.70f)
        pos[Ctl.SELECT] = Offset(0.39f, 0.93f)
        pos[Ctl.START] = Offset(0.61f, 0.93f)
        pos[Ctl.MENU] = Offset(0.04f, 0.07f)
        pos[Ctl.QUICK] = Offset(0.96f, 0.07f)
    }

    fun move(id: String, dx: Float, dy: Float) {
        val o = pos[id] ?: return
        pos[id] = Offset((o.x + dx).coerceIn(0.02f, 0.98f), (o.y + dy).coerceIn(0.04f, 0.96f))
    }

    fun load(scope: String) {
        reset()
        val raw = Settings.loadLayout(scope) ?: return
        for (part in raw.split(";")) {
            val f = part.split(":")
            if (f.size == 3) {
                val x = f[1].toFloatOrNull() ?: continue
                val y = f[2].toFloatOrNull() ?: continue
                if (f[0] in Ctl.ALL) pos[f[0]] = Offset(x, y)
            }
        }
    }

    fun save(scope: String) {
        Settings.saveLayout(scope, pos.entries.joinToString(";") { "${it.key}:${it.value.x}:${it.value.y}" })
    }
}
