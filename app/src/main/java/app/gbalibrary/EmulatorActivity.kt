package app.gbalibrary

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.gbalibrary.data.Game
import app.gbalibrary.data.GameStore
import app.gbalibrary.data.Settings
import app.gbalibrary.emu.Engine
import app.gbalibrary.emu.GbaGlView
import app.gbalibrary.emu.GbaKey
import app.gbalibrary.ui.ControlLayout
import app.gbalibrary.ui.GameMenu
import app.gbalibrary.ui.GbaTheme
import app.gbalibrary.ui.MenuActions
import app.gbalibrary.ui.ControlsCanvas
import app.gbalibrary.ui.ControlsEditorPanel
import app.gbalibrary.data.Archive
import app.gbalibrary.input.PadInput
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

class EmulatorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_GAME_ID = "gameId"
    }

    private var engine: Engine? = null
    private var glView: GbaGlView? = null
    private lateinit var game: Game
    private lateinit var store: GameStore
    private val layout = ControlLayout()
    private var layoutScope = "global" // de donde se cargo el layout (y donde se guarda al terminar)
    private val forceTouch = mutableStateOf(false) // mostrar tactiles aunque haya mando
    private val trigHeld = HashSet<Int>()

    private var resumedAt = 0L
    private var activityResumed = false
    private var hardwareDirs = 0

    private val menuOpen = mutableStateOf(false)
    private val editing = mutableStateOf(false)
    private val message = mutableStateOf<String?>(null)
    private val speed = mutableIntStateOf(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersive()
        watchBars()
        Settings.init(this)
        store = GameStore.get(this)
        val found = intent.getStringExtra(EXTRA_GAME_ID)?.let { store.get(it) }
        if (found == null) {
            finish()
            return
        }
        game = found

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        File(filesDir, "saves").mkdirs()
        File(filesDir, "states").mkdirs()

        PadInput.init(this)
        // Layout propio del juego si existe; si no, el global.
        layoutScope = if (Settings.loadLayout(game.id) != null) game.id else "global"
        layout.load(layoutScope)

        val romUri = Uri.parse(game.uri)
        val eng = Engine(
            romProvider = {
                try {
                    if (game.entry.isNotEmpty()) {
                        // ROM dentro de un .zip/.7z: se descomprime a la cache, se lee y se borra.
                        val f = Archive.extractToCache(this, romUri, game.entry)
                        try { f?.readBytes() } finally { f?.delete() }
                    } else {
                        contentResolver.openInputStream(romUri)?.use { it.readBytes() }
                    }
                } catch (e: Exception) {
                    null
                }
            },
            savePath = File(filesDir, "saves/${game.id}.sav").path,
            autoStatePath = statePath("auto"),
            onError = { msg ->
                runOnUiThread {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    finish()
                }
            },
        )
        eng.volume = Settings.volume.intValue
        eng.muted = Settings.muted.value
        engine = eng
        eng.start()

        onBackPressedDispatcher.addCallback(this) {
            when {
                editing.value -> finishEditing()
                else -> setMenu(!menuOpen.value)
            }
        }

        setContent {
            GbaTheme { EmulatorUi(eng) }
        }
        applyImmersive()
    }

    private fun statePath(slot: String) = File(filesDir, "states/${game.id}.$slot.ss").path

    private fun setMenu(open: Boolean) {
        menuOpen.value = open
        syncPause()
    }

    private fun padShown(): Boolean {
        val hidden = Settings.padAutoHide.value && PadInput.connected.value && !forceTouch.value
        return Settings.padVisible.value && !hidden
    }

    private fun syncPause() {
        engine?.paused = menuOpen.value || editing.value || !activityResumed
    }

    private fun flash(text: String) {
        runOnUiThread { message.value = text }
    }

    private fun startEditing() {
        menuOpen.value = false
        editing.value = true
        syncPause()
    }

    private fun finishEditing() {
        layout.save(layoutScope)
        editing.value = false
        syncPause()
        flash("Layout guardado")
    }

    private fun applyImmersive() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /** Si la barra aparece (deslizando desde arriba), vuelve a esconderse sola a los pocos segundos. */
    @Suppress("DEPRECATION")
    private fun watchBars() {
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and android.view.View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                window.decorView.removeCallbacks(rehide)
                window.decorView.postDelayed(rehide, 2500)
            }
        }
    }

    private val rehide = Runnable { applyImmersive() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    // ---- Ciclo de vida: tiempo de juego, pausa y guardado automatico ----

    override fun onResume() {
        super.onResume()
        activityResumed = true
        applyImmersive()
        resumedAt = SystemClock.elapsedRealtime()
        glView?.onResume()
        syncPause()
    }

    override fun onPause() {
        super.onPause()
        activityResumed = false
        glView?.onPause()
        syncPause()
        engine?.releaseAll()
        commitPlayTime()
    }

    override fun onStop() {
        super.onStop()
        engine?.autoSave()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.shutdown()
        Archive.clearCache(this) // borra lo descomprimido al salir del juego
    }

    private fun commitPlayTime() {
        if (resumedAt <= 0L) return
        val seconds = (SystemClock.elapsedRealtime() - resumedAt) / 1000
        resumedAt = 0L
        store.addPlay(game.id, seconds.coerceAtLeast(0), System.currentTimeMillis())
    }

    // ---- Acciones del menu ----

    private fun doSave(slot: String) {
        engine?.saveState(statePath(slot)) { ok ->
            flash(if (ok) "Estado guardado" else "No se pudo guardar")
            if (ok) runOnUiThread { PadInput.rumble(80) }
        }
    }

    private fun doLoad(slot: String) {
        engine?.loadState(statePath(slot)) { ok ->
            flash(if (ok) "Estado cargado" else "Esa ranura esta vacia")
        }
        setMenu(false)
    }

    private fun doScreenshot() {
        engine?.screenshot { argb ->
            if (argb == null) {
                flash("No se pudo capturar")
            } else {
                flash(if (saveScreenshot(argb)) "Captura guardada en Imagenes/GBALibrary" else "No se pudo guardar la captura")
            }
        }
    }

    private fun saveScreenshot(argb: IntArray): Boolean {
        return try {
            val vw = app.gbalibrary.emu.Native.videoWidth().coerceAtLeast(1)
            val vh = (argb.size / vw).coerceAtLeast(1)
            val small = Bitmap.createBitmap(argb, vw, vh, Bitmap.Config.ARGB_8888)
            val bmp = Bitmap.createScaledBitmap(small, vw * 4, vh * 4, false) // 4x sin suavizar
            val name = "GBA_${game.title.replace(Regex("[^A-Za-z0-9]+"), "_")}_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GBALibrary")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return false
                File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    // ---- Mando fisico y teclado ----

    /** Teclado fisico (el mando usa los perfiles de PadInput). */
    private fun keyMask(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> GbaKey.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> GbaKey.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> GbaKey.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> GbaKey.RIGHT
        KeyEvent.KEYCODE_X -> GbaKey.A
        KeyEvent.KEYCODE_Z -> GbaKey.B
        KeyEvent.KEYCODE_A -> GbaKey.L
        KeyEvent.KEYCODE_S -> GbaKey.R
        KeyEvent.KEYCODE_ENTER -> GbaKey.START
        KeyEvent.KEYCODE_DEL -> GbaKey.SELECT
        else -> 0
    }

    /** Boton de mando segun el perfil Mando 1 / Mando 2 (acciones, turbo, menu, guardado rapido). */
    private fun applyPadKey(deviceId: Int, code: Int, down: Boolean, first: Boolean) {
        val action = PadInput.actionFor(deviceId, code)
        if (action == 0) return
        if (action and GbaKey.MENU != 0) {
            if (down && first) setMenu(!menuOpen.value)
            return
        }
        if (action and GbaKey.QUICK != 0) {
            if (down && first && !menuOpen.value) doSave("quick")
            return
        }
        val src = if (PadInput.slotOf(deviceId) == 2) Engine.SOURCE_HARDWARE2 else Engine.SOURCE_HARDWARE
        engine?.setKey(src, action and (GbaKey.GBA_MASK or GbaKey.TURBO_A or GbaKey.TURBO_B), down)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (PadInput.isPadKey(event)) {
            applyPadKey(event.deviceId, keyCode, true, event.repeatCount == 0)
            return true
        }
        val mask = keyMask(keyCode)
        if (mask != 0) {
            engine?.setKey(Engine.SOURCE_KEYBOARD, mask, true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (PadInput.isPadKey(event)) {
            applyPadKey(event.deviceId, keyCode, false, false)
            return true
        }
        val mask = keyMask(keyCode)
        if (mask != 0) {
            engine?.setKey(Engine.SOURCE_KEYBOARD, mask, false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun trigger(deviceId: Int, code: Int, on: Boolean) {
        val was = code in trigHeld
        if (on == was) return
        if (on) trigHeld.add(code) else trigHeld.remove(code)
        applyPadKey(deviceId, code, on, true)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (isJoystick && event.action == MotionEvent.ACTION_MOVE) {
            // Stick izquierdo (y hat) = D-Pad, siempre.
            var x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            var y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if (x == 0f && y == 0f) {
                x = event.getAxisValue(MotionEvent.AXIS_X)
                y = event.getAxisValue(MotionEvent.AXIS_Y)
            }
            var dirs = 0
            if (x > 0.5f) dirs = dirs or GbaKey.RIGHT
            if (x < -0.5f) dirs = dirs or GbaKey.LEFT
            if (y > 0.5f) dirs = dirs or GbaKey.DOWN
            if (y < -0.5f) dirs = dirs or GbaKey.UP
            if (dirs != hardwareDirs) {
                hardwareDirs = dirs
                engine?.setDirections(Engine.SOURCE_AXIS, dirs)
            }
            // Gatillos analogicos -> LT / RT segun el perfil.
            val lt = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
            val rt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
            trigger(event.deviceId, KeyEvent.KEYCODE_BUTTON_L2, lt > 0.5f)
            trigger(event.deviceId, KeyEvent.KEYCODE_BUTTON_R2, rt > 0.5f)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    // ---- UI ----

    @Composable
    private fun EmulatorUi(eng: Engine) {
        LaunchedEffect(Settings.volume.intValue, Settings.muted.value) {
            eng.volume = Settings.volume.intValue
            eng.muted = Settings.muted.value
        }
        var fps by remember { mutableIntStateOf(0) }
        LaunchedEffect(Settings.showFps.value) {
            while (Settings.showFps.value) {
                fps = eng.fps
                delay(500)
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> GbaGlView(ctx).also { glView = it } },
                update = { v ->
                    v.filter = Settings.filter.intValue
                    v.scaleMode = Settings.scale.intValue
                    v.brightness = Settings.brightness.floatValue
                },
            )

            Controls(eng)

            if (Settings.showFps.value) {
                Text(
                    "$fps FPS",
                    color = Color(0xFF7CFC00),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .background(Color(0x99000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            message.value?.let { text ->
                LaunchedEffect(text) {
                    delay(1800)
                    if (message.value == text) message.value = null
                }
                Text(
                    text,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 56.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (editing.value) {
                ControlsEditorPanel(
                    layout = layout,
                    gameName = game.title,
                    onSaveGlobal = { layout.save("global"); layoutScope = "global"; flash("Layout global guardado") },
                    onSaveGame = { layout.save(game.id); layoutScope = game.id; flash("Layout guardado para este juego") },
                    onReset = { layout.reset() },
                    onDone = { finishEditing() },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
                )
            }

            if (menuOpen.value) {
                GameMenu(
                    title = game.title,
                    speed = speed.intValue,
                    layout = layout,
                    padConnected = PadInput.connected.value,
                    forceTouch = forceTouch.value,
                    actions = MenuActions(
                        close = { setMenu(false) },
                        save = { doSave(it) },
                        load = { doLoad(it) },
                        reset = {
                            eng.reset()
                            setMenu(false)
                            flash("Juego reiniciado")
                        },
                        screenshot = { doScreenshot() },
                        exit = { finish() },
                        editControls = { startEditing() },
                        saveGameLayout = { layout.save(game.id); layoutScope = game.id; flash("Layout guardado para este juego") },
                        saveGlobalLayout = { layout.save("global"); layoutScope = "global"; flash("Layout global guardado") },
                        resetLayout = {
                            layout.reset()
                            layout.save(layoutScope)
                            flash("Layout restablecido")
                        },
                        persistLayout = { layout.save(layoutScope) },
                        setForceTouch = { forceTouch.value = it },
                        setSpeed = {
                            speed.intValue = it
                            eng.speed = it
                        },
                    ),
                )
            }
        }
    }

    @Composable
    private fun Controls(eng: Engine) {
        ControlsCanvas(
            layout = layout,
            engine = eng,
            editing = editing.value,
            showPad = padShown(),
            onMenu = { setMenu(true) },
            onQuick = { doSave("quick") },
        )
    }
}
