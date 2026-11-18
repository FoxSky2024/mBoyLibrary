package app.gbalibrary.emu

import app.gbalibrary.data.Settings
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pantallas de la Nintendo DS dibujadas por la app (no por el nucleo): el nucleo siempre entrega
 * "arriba sobre abajo" (256x384) y [GbaGlView] recorta cada mitad y la dibuja donde dice [DsConfig].
 * Asi se pueden mover, escalar y rotar libremente, y el toque se calcula SIEMPRE desde el
 * rectangulo real de la pantalla inferior, en cualquier distribucion.
 */
class DsXf(val cx: Float, val cy: Float, val wf: Float, val rot: Float = 0f, val visible: Boolean = true) {
    /** cx, cy: centro (fraccion del ancho / alto de la vista). wf: ancho como fraccion del ancho de la vista. rot: grados (horario). */
    fun copy(cx: Float = this.cx, cy: Float = this.cy, wf: Float = this.wf, rot: Float = this.rot, visible: Boolean = this.visible) =
        DsXf(cx, cy, wf, rot, visible)
}

class DsConfig(val top: DsXf, val bottom: DsXf, val custom: Boolean, val layout: Int, val swap: Boolean, val portrait: Boolean) {
    fun toJson(): String = JSONObject()
        .put("layout", layout).put("swap", swap).put("portrait", portrait).put("custom", custom)
        .put("top", xfJson(top)).put("bottom", xfJson(bottom)).toString()

    private fun xfJson(x: DsXf) = JSONObject().put("cx", x.cx.toDouble()).put("cy", x.cy.toDouble())
        .put("w", x.wf.toDouble()).put("r", x.rot.toDouble()).put("v", x.visible)
}

object DsScreens {
    /** Proporcion de cada pantalla de la DS (256x192). */
    const val ASPECT = 0.75f
    const val SCREEN_W = 256
    const val SCREEN_H = 192

    /** Configuracion en uso (la lee el hilo de GL; se reemplaza entera al cambiar). */
    @Volatile var cfg: DsConfig? = null

    /** Margenes seguros en px (izq, arriba, der, abajo): notch / esquinas. */
    @Volatile var safe = floatArrayOf(0f, 0f, 0f, 0f)

    @Volatile var editing = false

    /** 0 = pantalla superior, 1 = inferior (la que se esta editando). */
    @Volatile var selected = 1

    // ---- Persistencia por juego y por orientacion ----

    private fun key(gameId: String, portrait: Boolean) = "ds:$gameId:" + if (portrait) "v" else "h"

    fun save(gameId: String) {
        val c = cfg ?: return
        if (c.custom) Settings.saveLayout(key(gameId, c.portrait), c.toJson())
    }

    fun clearSaved(gameId: String, portrait: Boolean) {
        Settings.saveLayout(key(gameId, portrait), "")
    }

    /** Carga la configuracion guardada del juego (si coincide con la distribucion elegida) o null. */
    fun loadSaved(gameId: String, portrait: Boolean): DsConfig? {
        val raw = Settings.loadLayout(key(gameId, portrait)) ?: return null
        if (raw.isBlank()) return null
        return try {
            val o = JSONObject(raw)
            if (o.optInt("layout", -1) != Settings.dsLayout.intValue || o.optBoolean("swap") != Settings.dsSwap.value) return null
            fun xf(j: JSONObject) = DsXf(j.getDouble("cx").toFloat(), j.getDouble("cy").toFloat(), j.getDouble("w").toFloat(), j.optDouble("r", 0.0).toFloat(), j.optBoolean("v", true))
            DsConfig(xf(o.getJSONObject("top")), xf(o.getJSONObject("bottom")), true, o.getInt("layout"), o.getBoolean("swap"), portrait)
        } catch (e: Exception) {
            null
        }
    }

    // ---- Distribuciones predefinidas ----

    /**
     * 0 arriba/abajo, 1 lado a lado, 2 solo arriba, 3 solo abajo, 4 grande + pequena. En vertical las
     * pantallas ocupan la parte alta (~60%) y deja el resto para los botones.
     */
    fun preset(layout: Int, swap: Boolean, sw: Float, sh: Float, safe: FloatArray, portrait: Boolean): DsConfig {
        val l = safe[0]
        val t = safe[1]
        val r = sw - safe[2]
        var b = sh - safe[3]
        if (portrait) b = t + (b - t) * 0.60f
        val aw = (r - l).coerceAtLeast(1f)
        val ah = (b - t).coerceAtLeast(1f)
        val cx0 = (l + r) / 2f
        val cy0 = (t + b) / 2f
        fun xf(cx: Float, cy: Float, w: Float, vis: Boolean = true) = DsXf(cx / sw, cy / sh, w / sw, 0f, vis)

        var top: DsXf
        var bot: DsXf
        when {
            layout == 1 && !portrait -> {
                val w = min(aw / 2f, ah / ASPECT)
                top = xf(cx0 - w / 2f, cy0, w)
                bot = xf(cx0 + w / 2f, cy0, w)
            }
            layout == 2 -> {
                val w = min(aw, ah / ASPECT)
                top = xf(cx0, cy0, w)
                bot = xf(cx0, cy0, w, false)
            }
            layout == 3 -> {
                val w = min(aw, ah / ASPECT)
                top = xf(cx0, cy0, w, false)
                bot = xf(cx0, cy0, w)
            }
            layout == 4 -> {
                if (portrait) {
                    val big = min(aw, ah * 0.66f / ASPECT)
                    val small = min(big * 0.55f, aw)
                    top = xf(cx0, t + big * ASPECT / 2f, big)
                    bot = xf(cx0, t + big * ASPECT + small * ASPECT / 2f, small)
                } else {
                    val big = min(aw * 0.68f, ah / ASPECT)
                    val small = min(aw - big - 8f, big * 0.55f).coerceAtLeast(60f)
                    top = xf(l + big / 2f, cy0, big)
                    bot = xf(r - small / 2f, b - small * ASPECT / 2f - 4f, small)
                }
            }
            else -> { // arriba / abajo (tambien lado a lado en vertical)
                val w = min(aw, (ah / 2f) / ASPECT)
                top = xf(cx0, cy0 - ASPECT * w / 2f, w)
                bot = xf(cx0, cy0 + ASPECT * w / 2f, w)
            }
        }
        if (swap) { val tmp = top; top = bot; bot = tmp }
        return DsConfig(top, bot, false, layout, swap, portrait)
    }

    /** Configuracion actual para una vista de [sw]x[sh]: la guardada si coincide, si no la predefinida. */
    fun ensure(gameId: String, sw: Float, sh: Float): DsConfig {
        val portrait = sh > sw
        val c = cfg
        if (c != null && c.portrait == portrait && c.layout == Settings.dsLayout.intValue && c.swap == Settings.dsSwap.value) return c
        val n = loadSaved(gameId, portrait) ?: preset(Settings.dsLayout.intValue, Settings.dsSwap.value, sw, sh, safe, portrait)
        cfg = n
        return n
    }

    fun reset(gameId: String, sw: Float, sh: Float) {
        val portrait = sh > sw
        clearSaved(gameId, portrait)
        cfg = preset(Settings.dsLayout.intValue, Settings.dsSwap.value, sw, sh, safe, portrait)
    }

    fun swapScreens() {
        val c = cfg ?: return
        cfg = DsConfig(c.bottom, c.top, true, c.layout, c.swap, c.portrait)
        selected = 1 - selected
    }

    /** Cambia una pantalla (0 arriba, 1 abajo) y marca la configuracion como personalizada. */
    fun update(which: Int, change: (DsXf) -> DsXf) {
        val c = cfg ?: return
        cfg = if (which == 0) DsConfig(change(c.top), c.bottom, true, c.layout, c.swap, c.portrait)
        else DsConfig(c.top, change(c.bottom), true, c.layout, c.swap, c.portrait)
    }

    // ---- Geometria ----

    /** Centro y medio tamano de una pantalla en px de la vista. */
    class Box(val cx: Float, val cy: Float, val hw: Float, val hh: Float, val rot: Float)

    fun box(x: DsXf, sw: Float, sh: Float): Box {
        val w = x.wf * sw
        return Box(x.cx * sw, x.cy * sh, w / 2f, w * ASPECT / 2f, x.rot)
    }

    /** Esquinas (BL, BR, TL, TR) en px, con la rotacion aplicada alrededor del centro. */
    fun corners(b: Box): FloatArray {
        val rad = Math.toRadians(b.rot.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        fun p(lx: Float, ly: Float, out: FloatArray, i: Int) {
            out[i] = b.cx + lx * c - ly * s
            out[i + 1] = b.cy + lx * s + ly * c
        }
        val o = FloatArray(8)
        p(-b.hw, b.hh, o, 0); p(b.hw, b.hh, o, 2); p(-b.hw, -b.hh, o, 4); p(b.hw, -b.hh, o, 6)
        return o
    }

    /** Punto de la vista -> coordenadas 0..1 dentro de la pantalla (null si cae fuera y [clamp] es false). */
    fun toLocal(b: Box, px: Float, py: Float, clamp: Boolean): FloatArray? {
        val rad = Math.toRadians(-b.rot.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val dx = px - b.cx
        val dy = py - b.cy
        val lx = dx * c - dy * s
        val ly = dx * s + dy * c
        var u = lx / (b.hw * 2f) + 0.5f
        var v = ly / (b.hh * 2f) + 0.5f
        if (!clamp && (u < 0f || u > 1f || v < 0f || v > 1f)) return null
        u = u.coerceIn(0f, 1f)
        v = v.coerceIn(0f, 1f)
        return floatArrayOf(u, v)
    }

    /**
     * Toque -> puntero para Retro.setTouch, normalizado sobre la imagen COMPLETA del nucleo (256x384
     * en "arriba / abajo"). La pantalla inferior es la mitad de abajo del fotograma.
     */
    fun frameTouch(u: Float, v: Float, frameH: Int): FloatArray {
        val fh = if (frameH > 0) frameH.toFloat() else (SCREEN_H * 2).toFloat()
        val ny = ((fh - SCREEN_H) + v * SCREEN_H) / fh
        return floatArrayOf(u, ny.coerceIn(0f, 1f))
    }
}
