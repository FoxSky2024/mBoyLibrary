package app.gbalibrary.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import app.gbalibrary.data.Settings
import app.gbalibrary.emu.GbaKey
import org.json.JSONArray
import org.json.JSONObject

/** Tipos de control tactil. */
object Kind {
    const val MOVE = "move" // cruceta o joystick (segun el tipo de movimiento elegido)
    const val A = "a"
    const val B = "b"
    const val L = "l"
    const val R = "r"
    const val START = "start"
    const val SELECT = "select"
    const val TURBO_A = "turbo_a"
    const val TURBO_B = "turbo_b"
    const val UP = "up"
    const val DOWN = "down"
    const val LEFT = "left"
    const val RIGHT = "right"
    const val MENU = "menu"
    const val QUICK = "quick"

    /** A que se puede mapear un boton extra. */
    val ASSIGNABLE = listOf(A, B, L, R, START, SELECT, TURBO_A, TURBO_B, UP, DOWN, LEFT, RIGHT)

    fun label(k: String) = when (k) {
        MOVE -> "Movimiento"; A -> "A"; B -> "B"; L -> "L"; R -> "R"
        START -> "Start"; SELECT -> "Select"; TURBO_A -> "Turbo A"; TURBO_B -> "Turbo B"
        UP -> "D-Pad ↑"; DOWN -> "D-Pad ↓"; LEFT -> "D-Pad ←"; RIGHT -> "D-Pad →"
        MENU -> "Menú"; QUICK -> "Guardar"; else -> k
    }

    /** Texto corto dentro del boton. */
    fun glyph(k: String) = when (k) {
        START -> "START"; SELECT -> "SELECT"; TURBO_A -> "A»"; TURBO_B -> "B»"
        UP -> "↑"; DOWN -> "↓"; LEFT -> "←"; RIGHT -> "→"
        else -> k.uppercase()
    }

    fun mask(k: String): Int = when (k) {
        A -> GbaKey.A; B -> GbaKey.B; L -> GbaKey.L; R -> GbaKey.R
        START -> GbaKey.START; SELECT -> GbaKey.SELECT
        TURBO_A -> GbaKey.TURBO_A; TURBO_B -> GbaKey.TURBO_B
        UP -> GbaKey.UP; DOWN -> GbaKey.DOWN; LEFT -> GbaKey.LEFT; RIGHT -> GbaKey.RIGHT
        else -> 0
    }

    fun baseW(k: String) = when (k) {
        MOVE -> 150; A, B, TURBO_A, TURBO_B -> 72; L, R -> 96; START, SELECT -> 84
        MENU, QUICK -> 44; else -> 56
    }

    fun baseH(k: String) = when (k) {
        MOVE -> 150; A, B, TURBO_A, TURBO_B -> 72; L, R -> 46; START, SELECT -> 34
        MENU, QUICK -> 44; else -> 56
    }
}

/** Un control en pantalla: posicion (centro, fraccion de la pantalla), tamano y opacidad propios. */
data class PadItem(
    val id: String,
    val kind: String,
    val x: Float,
    val y: Float,
    val size: Float = 1f,
    val alpha: Float = 0.55f,
    val extra: Boolean = false,
    /** Oculto: no se dibuja ni responde al tacto (en el editor se ve atenuado para poder volver a mostrarlo). */
    val hidden: Boolean = false,
)

object MoveType {
    const val CROSS = 0
    const val STICK = 1
}

/** Layout de controles: lista de botones + tipo de movimiento. Se guarda como JSON por juego o global. */
class ControlLayout {
    val items = mutableStateListOf<PadItem>()
    var moveType by mutableIntStateOf(MoveType.CROSS)
    var deadzone by mutableFloatStateOf(0.25f)
    var selected by mutableStateOf<String?>(null)

    /**
     * Orientacion del layout. Cada orientacion guarda el suyo (vertical con sufijo "@v"; el horizontal
     * conserva la clave de siempre, asi no se pierde lo ya guardado).
     */
    var portrait by mutableStateOf(false)

    init { reset() }

    private fun key(scope: String) = if (portrait) "$scope@v" else scope

    fun reset() {
        items.clear()
        fun d(id: String, k: String, x: Float, y: Float) = items.add(PadItem(id, k, x, y))
        if (portrait) {
            // Vertical: el juego arriba y los controles abajo, sin taparlo.
            d(Kind.MOVE, Kind.MOVE, 0.24f, 0.80f)
            d(Kind.L, Kind.L, 0.14f, 0.58f)
            d(Kind.R, Kind.R, 0.86f, 0.58f)
            d(Kind.A, Kind.A, 0.88f, 0.76f)
            d(Kind.B, Kind.B, 0.68f, 0.86f)
            d(Kind.SELECT, Kind.SELECT, 0.36f, 0.96f)
            d(Kind.START, Kind.START, 0.64f, 0.96f)
            d(Kind.MENU, Kind.MENU, 0.06f, 0.04f)
            d(Kind.QUICK, Kind.QUICK, 0.94f, 0.04f)
        } else {
            d(Kind.MOVE, Kind.MOVE, 0.11f, 0.64f)
            d(Kind.L, Kind.L, 0.09f, 0.20f)
            d(Kind.R, Kind.R, 0.91f, 0.20f)
            d(Kind.A, Kind.A, 0.93f, 0.52f)
            d(Kind.B, Kind.B, 0.84f, 0.70f)
            d(Kind.SELECT, Kind.SELECT, 0.39f, 0.93f)
            d(Kind.START, Kind.START, 0.61f, 0.93f)
            d(Kind.MENU, Kind.MENU, 0.04f, 0.07f)
            d(Kind.QUICK, Kind.QUICK, 0.96f, 0.07f)
        }
        moveType = MoveType.CROSS
        deadzone = 0.25f
        selected = null
    }

    fun find(id: String?): PadItem? = items.firstOrNull { it.id == id }

    fun slotOf(id: String): Int = items.indexOfFirst { it.id == id }.coerceAtLeast(0)

    private fun replace(id: String, change: (PadItem) -> PadItem) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = change(items[i])
    }

    fun move(id: String, dx: Float, dy: Float) = replace(id) {
        it.copy(x = (it.x + dx).coerceIn(0.02f, 0.98f), y = (it.y + dy).coerceIn(0.04f, 0.96f))
    }

    fun setSize(id: String, v: Float) = replace(id) { it.copy(size = v.coerceIn(0.5f, 2.5f)) }
    fun setAlpha(id: String, v: Float) = replace(id) { it.copy(alpha = v.coerceIn(0.1f, 1f)) }
    /** Oculta / muestra un control. El boton de menu nunca se oculta (no habria como volver). */
    fun setHidden(id: String, hidden: Boolean) = replace(id) { if (it.kind == Kind.MENU) it else it.copy(hidden = hidden) }
    fun setKind(id: String, kind: String) = replace(id) { if (it.extra) it.copy(kind = kind) else it }

    /** Agrega un boton extra mapeado a [kind]. Devuelve su id. */
    fun add(kind: String): String? {
        if (items.size >= 40) return null
        var n = 1
        while (items.any { it.id == "x$n" }) n++
        val id = "x$n"
        items.add(PadItem(id, kind, 0.5f, 0.5f, 0.9f, 0.55f, extra = true))
        selected = id
        return id
    }

    fun remove(id: String) {
        if (id == Kind.MENU) return
        items.removeAll { it.id == id }
        if (selected == id) selected = null
    }

    // ---- Guardado (JSON) ----

    fun toJson(): String {
        val o = JSONObject()
        o.put("move", moveType)
        o.put("dead", deadzone.toDouble())
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("k", it.kind).put("x", it.x.toDouble()).put("y", it.y.toDouble())
                .put("s", it.size.toDouble()).put("a", it.alpha.toDouble()).put("e", it.extra).put("h", it.hidden))
        }
        o.put("items", arr)
        return o.toString()
    }

    fun hasSaved(scope: String) = Settings.loadLayout(key(scope)) != null

    fun load(scope: String) {
        reset()
        val raw = Settings.loadLayout(key(scope)) ?: return
        try {
            if (raw.trimStart().startsWith("{")) {
                val o = JSONObject(raw)
                moveType = o.optInt("move", MoveType.CROSS)
                deadzone = o.optDouble("dead", 0.25).toFloat().coerceIn(0.05f, 0.7f)
                val arr = o.optJSONArray("items") ?: return
                val list = ArrayList<PadItem>()
                for (i in 0 until arr.length()) {
                    val j = arr.getJSONObject(i)
                    list.add(
                        PadItem(
                            id = j.getString("id"), kind = j.getString("k"),
                            x = j.getDouble("x").toFloat(), y = j.getDouble("y").toFloat(),
                            size = j.optDouble("s", 1.0).toFloat(), alpha = j.optDouble("a", 0.55).toFloat(),
                            extra = j.optBoolean("e", false),
                            hidden = j.optBoolean("h", false),
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    items.clear()
                    items.addAll(list)
                }
            } else {
                // Formato antiguo (0.2.0): "id:x:y;id:x:y". Solo posiciones; "dpad" ahora es "move".
                for (part in raw.split(";")) {
                    val f = part.split(":")
                    if (f.size != 3) continue
                    val id = if (f[0] == "dpad") Kind.MOVE else f[0]
                    val x = f[1].toFloatOrNull() ?: continue
                    val y = f[2].toFloatOrNull() ?: continue
                    replace(id) { it.copy(x = x, y = y) }
                }
            }
        } catch (e: Exception) {
            reset()
        }
    }

    fun save(scope: String) = Settings.saveLayout(key(scope), toJson())
}
