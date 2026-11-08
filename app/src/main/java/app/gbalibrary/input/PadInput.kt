package app.gbalibrary.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import app.gbalibrary.data.Settings
import app.gbalibrary.emu.GbaKey

/** Un boton fisico del diagrama estilo Xbox. (x, y) son fracciones del dibujo. */
class PadSlot(val key: Int, val name: String, val x: Float, val y: Float, val round: Boolean = true)

/**
 * Mandos fisicos Android: deteccion, perfiles Mando 1 / Mando 2 (accion de cada boton),
 * captura para remapear, modo "Probar mando" y vibracion.
 */
object PadInput {
    // Posiciones sobre la silueta de un mando de Xbox (fracciones del dibujo, alto = 0.62 del ancho).
    val SLOTS = listOf(
        PadSlot(KeyEvent.KEYCODE_BUTTON_L2, "LT", 0.19f, 0.045f, false),
        PadSlot(KeyEvent.KEYCODE_BUTTON_R2, "RT", 0.81f, 0.045f, false),
        PadSlot(KeyEvent.KEYCODE_BUTTON_L1, "LB", 0.20f, 0.14f, false),
        PadSlot(KeyEvent.KEYCODE_BUTTON_R1, "RB", 0.80f, 0.14f, false),
        PadSlot(KeyEvent.KEYCODE_BUTTON_SELECT, "Back", 0.41f, 0.35f),
        PadSlot(KeyEvent.KEYCODE_BUTTON_MODE, "Guía", 0.50f, 0.25f),
        PadSlot(KeyEvent.KEYCODE_BUTTON_START, "Start", 0.59f, 0.35f),
        PadSlot(KeyEvent.KEYCODE_BUTTON_THUMBL, "L3", 0.24f, 0.37f),
        PadSlot(KeyEvent.KEYCODE_BUTTON_THUMBR, "R3", 0.62f, 0.64f),
        PadSlot(KeyEvent.KEYCODE_DPAD_UP, "↑", 0.40f, 0.53f),
        PadSlot(KeyEvent.KEYCODE_DPAD_DOWN, "↓", 0.40f, 0.77f),
        PadSlot(KeyEvent.KEYCODE_DPAD_LEFT, "←", 0.32f, 0.65f),
        PadSlot(KeyEvent.KEYCODE_DPAD_RIGHT, "→", 0.48f, 0.65f),
        PadSlot(KeyEvent.KEYCODE_BUTTON_Y, "Y", 0.77f, 0.22f),
        PadSlot(KeyEvent.KEYCODE_BUTTON_X, "X", 0.69f, 0.35f),
        PadSlot(KeyEvent.KEYCODE_BUTTON_B, "B", 0.85f, 0.35f),
        PadSlot(KeyEvent.KEYCODE_BUTTON_A, "A", 0.77f, 0.48f),
    )

    fun slotName(key: Int) = SLOTS.firstOrNull { it.key == key }?.name ?: KeyEvent.keyCodeToString(key)

    // ---- Acciones ----

    fun actionName(a: Int): String {
        if (a == 0) return "Sin acción"
        val parts = ArrayList<String>()
        if (a and GbaKey.A != 0) parts.add("A")
        if (a and GbaKey.B != 0) parts.add("B")
        if (a and GbaKey.L != 0) parts.add("L")
        if (a and GbaKey.R != 0) parts.add("R")
        if (a and GbaKey.START != 0) parts.add("Start")
        if (a and GbaKey.SELECT != 0) parts.add("Select")
        if (a and GbaKey.UP != 0) parts.add("D-Pad ↑")
        if (a and GbaKey.DOWN != 0) parts.add("D-Pad ↓")
        if (a and GbaKey.LEFT != 0) parts.add("D-Pad ←")
        if (a and GbaKey.RIGHT != 0) parts.add("D-Pad →")
        if (a and GbaKey.TURBO_A != 0) parts.add("Turbo A")
        if (a and GbaKey.TURBO_B != 0) parts.add("Turbo B")
        if (a and GbaKey.MENU != 0) parts.add("Menú")
        if (a and GbaKey.QUICK != 0) parts.add("Guardado rápido")
        return parts.joinToString(" + ")
    }

    fun actionShort(a: Int): String = when (a) {
        0 -> "—"
        GbaKey.A -> "A"; GbaKey.B -> "B"; GbaKey.L -> "L"; GbaKey.R -> "R"
        GbaKey.START -> "ST"; GbaKey.SELECT -> "SE"
        GbaKey.UP -> "↑"; GbaKey.DOWN -> "↓"; GbaKey.LEFT -> "←"; GbaKey.RIGHT -> "→"
        GbaKey.TURBO_A -> "A»"; GbaKey.TURBO_B -> "B»"
        GbaKey.MENU -> "☰"; GbaKey.QUICK -> "⚡"
        else -> "?"
    }

    private fun defaultProfile(): Map<Int, Int> = mapOf(
        KeyEvent.KEYCODE_BUTTON_B to GbaKey.A,
        KeyEvent.KEYCODE_BUTTON_Y to GbaKey.A,
        KeyEvent.KEYCODE_BUTTON_A to GbaKey.B,
        KeyEvent.KEYCODE_BUTTON_X to GbaKey.B,
        KeyEvent.KEYCODE_BUTTON_L1 to GbaKey.L,
        KeyEvent.KEYCODE_BUTTON_R1 to GbaKey.R,
        KeyEvent.KEYCODE_BUTTON_L2 to GbaKey.TURBO_B,
        KeyEvent.KEYCODE_BUTTON_R2 to GbaKey.TURBO_A,
        KeyEvent.KEYCODE_BUTTON_START to GbaKey.START,
        KeyEvent.KEYCODE_BUTTON_SELECT to GbaKey.SELECT,
        KeyEvent.KEYCODE_BUTTON_MODE to GbaKey.MENU,
        KeyEvent.KEYCODE_DPAD_UP to GbaKey.UP,
        KeyEvent.KEYCODE_DPAD_DOWN to GbaKey.DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to GbaKey.LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to GbaKey.RIGHT,
    )

    /** Perfiles (slot 1 y 2): tecla fisica -> accion. Observables para la UI. */
    val profile1 = mutableStateMapOf<Int, Int>()
    val profile2 = mutableStateMapOf<Int, Int>()
    fun profile(n: Int) = if (n == 2) profile2 else profile1

    private fun fill(n: Int, data: Map<Int, Int>) {
        val p = profile(n)
        p.clear()
        SLOTS.forEach { p[it.key] = data[it.key] ?: 0 }
    }

    private fun load(n: Int) {
        val raw = Settings.loadRaw("pad_profile_$n")
        val map = HashMap<Int, Int>()
        if (raw == null) {
            map.putAll(defaultProfile())
        } else {
            for (part in raw.split(";")) {
                val f = part.split(":")
                if (f.size == 2) {
                    val k = f[0].toIntOrNull() ?: continue
                    val a = f[1].toIntOrNull() ?: continue
                    map[k] = a
                }
            }
        }
        fill(n, map)
    }

    fun saveProfile(n: Int) {
        Settings.saveRaw("pad_profile_$n", profile(n).entries.joinToString(";") { "${it.key}:${it.value}" })
    }

    fun resetProfile(n: Int) {
        fill(n, defaultProfile())
        saveProfile(n)
    }

    /** Intercambia la accion de dos botones (el que se toco en el diagrama y el que se presiono). */
    fun swap(n: Int, a: Int, b: Int) {
        if (a == b) return
        val p = profile(n)
        val x = p[a] ?: 0
        val y = p[b] ?: 0
        p[a] = y
        p[b] = x
        saveProfile(n)
    }

    // ---- Deteccion de mandos ----

    val connected = mutableStateOf(false)
    val deviceNames = mutableStateOf<List<String>>(emptyList())
    private var gamepadIds = listOf<Int>()
    private var manager: InputManager? = null
    private var inited = false

    fun isGamepad(d: InputDevice?): Boolean {
        if (d == null || d.isVirtual) return false
        val s = d.sources
        return (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    private fun refresh() {
        val ids = InputDevice.getDeviceIds().filter { isGamepad(InputDevice.getDevice(it)) }.sorted()
        gamepadIds = ids
        connected.value = ids.isNotEmpty()
        deviceNames.value = ids.mapNotNull { InputDevice.getDevice(it)?.name }
    }

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        load(1)
        load(2)
        val m = ctx.applicationContext.getSystemService(Context.INPUT_SERVICE) as? InputManager
        manager = m
        m?.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = refresh()
            override fun onInputDeviceRemoved(deviceId: Int) = refresh()
            override fun onInputDeviceChanged(deviceId: Int) = refresh()
        }, null)
        refresh()
    }

    /** 1 = primer mando conectado, 2 = segundo (o siguientes). */
    fun slotOf(deviceId: Int): Int = if (gamepadIds.indexOf(deviceId) >= 1) 2 else 1

    fun actionFor(deviceId: Int, keyCode: Int): Int = profile(slotOf(deviceId))[keyCode] ?: 0

    /** True si el evento viene de un mando (no del teclado). */
    fun isPadKey(e: KeyEvent): Boolean {
        val c = e.keyCode
        if (KeyEvent.isGamepadButton(c)) return true
        if (c in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT) {
            val s = e.source
            return (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }
        return false
    }

    // ---- Captura (remapear) y prueba ----

    /** Si no es null, la siguiente tecla de mando se entrega aqui y se consume. */
    var capture: ((Int) -> Unit)? = null
    var testing = false

    val pressed = mutableStateMapOf<Int, Boolean>()
    val stickX = mutableFloatStateOf(0f)
    val stickY = mutableFloatStateOf(0f)
    val rStickX = mutableFloatStateOf(0f)
    val rStickY = mutableFloatStateOf(0f)

    val busy get() = capture != null || testing

    /** Para MainActivity: devuelve true si el evento se uso (captura / prueba). */
    fun feedKey(code: Int, down: Boolean): Boolean {
        if (!busy) return false
        if (SLOTS.none { it.key == code }) return false
        if (down) capture?.let { cb -> capture = null; cb(code) }
        if (testing) pressed[code] = down
        return true
    }

    fun feedMotion(e: MotionEvent): Boolean {
        if (!busy) return false
        val lx = e.getAxisValue(MotionEvent.AXIS_X)
        val ly = e.getAxisValue(MotionEvent.AXIS_Y)
        val rx = e.getAxisValue(MotionEvent.AXIS_Z)
        val ry = e.getAxisValue(MotionEvent.AXIS_RZ)
        if (testing) {
            stickX.floatValue = lx; stickY.floatValue = ly
            rStickX.floatValue = rx; rStickY.floatValue = ry
        }
        val lt = maxOf(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = maxOf(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS))
        triggerState(KeyEvent.KEYCODE_BUTTON_L2, lt > 0.5f)
        triggerState(KeyEvent.KEYCODE_BUTTON_R2, rt > 0.5f)
        val hx = e.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
        triggerState(KeyEvent.KEYCODE_DPAD_LEFT, hx < -0.5f)
        triggerState(KeyEvent.KEYCODE_DPAD_RIGHT, hx > 0.5f)
        triggerState(KeyEvent.KEYCODE_DPAD_UP, hy < -0.5f)
        triggerState(KeyEvent.KEYCODE_DPAD_DOWN, hy > 0.5f)
        return true
    }

    private val axisHeld = HashSet<Int>()
    private fun triggerState(code: Int, on: Boolean) {
        val was = code in axisHeld
        if (on && !was) { axisHeld.add(code); feedKey(code, true) }
        if (!on && was) { axisHeld.remove(code); feedKey(code, false) }
    }

    // ---- Vibracion ----

    @Suppress("DEPRECATION")
    fun rumble(ms: Long = 60L, force: Boolean = false) {
        if (!force && !Settings.padRumble.value) return
        try {
            for (id in gamepadIds) {
                val d = InputDevice.getDevice(id) ?: continue
                val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) d.vibratorManager.defaultVibrator else d.vibrator
                if (v != null && v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                    else v.vibrate(ms)
                }
            }
        } catch (t: Throwable) {
            // sin vibracion en este mando
        }
    }

    fun canRumble(): Boolean = try {
        gamepadIds.any { id ->
            val d = InputDevice.getDevice(id)
            if (Build.VERSION.SDK_INT >= 31) d?.vibratorManager?.defaultVibrator?.hasVibrator() == true
            else @Suppress("DEPRECATION") (d?.vibrator?.hasVibrator() == true)
        }
    } catch (t: Throwable) {
        false
    }
}
