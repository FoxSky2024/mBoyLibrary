package app.gbalibrary.ui

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.abs

/** Acciones de navegacion de menu (mando, teclado). */
enum class NavKey { NONE, UP, DOWN, LEFT, RIGHT, OK, BACK, START }

/**
 * Navegacion de menus estilo Steam Big Picture. MainActivity traduce mando / teclado / stick
 * a [NavKey]; la pantalla activa decide que hacer segun [mode]:
 *  - HOME: la biblioteca maneja el foco por indice (barra lateral, destacados, cuadricula).
 *  - DETAIL: A = jugar, B = volver; las flechas siguen el foco normal de Compose.
 *  - FOCUS: Ajustes y demas: flechas = foco de Compose, A = pulsar, B = volver.
 */
object MenuNav {
    enum class Mode { HOME, DETAIL, FOCUS }

    @Volatile var mode: Mode = Mode.HOME

    /** Foco visible: true tras usar mando / teclado; se apaga al tocar la pantalla (si no hay mando). */
    val active = mutableStateOf(false)

    /** True mientras un campo de texto (busqueda) tiene el foco: el teclado fisico escribe en vez de navegar. */
    @Volatile var textFocus: Boolean = false

    val events = MutableSharedFlow<NavKey>(extraBufferCapacity = 32)
    val flow: SharedFlow<NavKey> get() = events

    fun send(k: NavKey) {
        active.value = true
        events.tryEmit(k)
    }

    fun fromKey(code: Int): NavKey = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> NavKey.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> NavKey.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> NavKey.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> NavKey.RIGHT
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> NavKey.OK
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> NavKey.BACK
        KeyEvent.KEYCODE_BUTTON_START -> NavKey.START
        else -> NavKey.NONE
    }

    fun isDirection(k: NavKey) = k == NavKey.UP || k == NavKey.DOWN || k == NavKey.LEFT || k == NavKey.RIGHT

    // ---- Repeticion de teclas mantenidas (limita la velocidad al mover el foco) ----
    private var lastKeyAt = 0L
    fun repeatAllowed(now: Long): Boolean {
        if (now - lastKeyAt < 110) return false
        lastKeyAt = now
        return true
    }

    // ---- Stick analogico / hat: direccion con repeticion (350 ms la primera vez, luego 130 ms) ----
    private var stickDir = NavKey.NONE
    private var stickNext = 0L

    /** Devuelve la direccion que hay que emitir ahora (o NONE). */
    fun stick(x: Float, y: Float, now: Long): NavKey {
        val dir = when {
            abs(x) < 0.55f && abs(y) < 0.55f -> NavKey.NONE
            abs(x) >= abs(y) -> if (x > 0) NavKey.RIGHT else NavKey.LEFT
            else -> if (y > 0) NavKey.DOWN else NavKey.UP
        }
        if (dir == NavKey.NONE) {
            stickDir = NavKey.NONE
            return NavKey.NONE
        }
        if (dir != stickDir) {
            stickDir = dir
            stickNext = now + 350
            return dir
        }
        if (now >= stickNext) {
            stickNext = now + 130
            return dir
        }
        return NavKey.NONE
    }
}
