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
import app.gbalibrary.ui.Ctl
import app.gbalibrary.ui.ControlLayout
import app.gbalibrary.ui.DPad
import app.gbalibrary.ui.GameMenu
import app.gbalibrary.ui.GbaTheme
import app.gbalibrary.ui.MenuActions
import app.gbalibrary.ui.PadButton
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

    private var resumedAt = 0L
    private var activityResumed = false
    private var hardwareDirs = 0

    private val menuOpen = mutableStateOf(false)
    private val editing = mutableStateOf(false)
    private val message = mutableStateOf<String?>(null)
    private val speed = mutableIntStateOf(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        layout.load("global")
        if (Settings.perGameLayout.value && Settings.loadLayout(game.id) != null) layout.load(game.id)

        val romUri = Uri.parse(game.uri)
        val eng = Engine(
            romProvider = {
                try {
                    contentResolver.openInputStream(romUri)?.use { it.readBytes() }
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

    private fun layoutScope() = if (Settings.perGameLayout.value) game.id else "global"

    private fun setMenu(open: Boolean) {
        menuOpen.value = open
        syncPause()
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
        layout.save(layoutScope())
        editing.value = false
        syncPause()
        flash("Layout guardado")
    }

    private fun applyImmersive() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    // ---- Ciclo de vida: tiempo de juego, pausa y guardado automatico ----

    override fun onResume() {
        super.onResume()
        activityResumed = true
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
            val small = Bitmap.createBitmap(argb, 240, 160, Bitmap.Config.ARGB_8888)
            val bmp = Bitmap.createScaledBitmap(small, 960, 640, false) // 4x sin suavizar
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

    private fun keyMask(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> GbaKey.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> GbaKey.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> GbaKey.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> GbaKey.RIGHT
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_X -> GbaKey.A
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_Z -> GbaKey.B
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_A -> GbaKey.L
        KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_S -> GbaKey.R
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER -> GbaKey.START
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_DEL -> GbaKey.SELECT
        else -> 0
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val mask = keyMask(keyCode)
        if (mask != 0) {
            engine?.setKey(Engine.SOURCE_HARDWARE, mask, true)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            setMenu(!menuOpen.value)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val mask = keyMask(keyCode)
        if (mask != 0) {
            engine?.setKey(Engine.SOURCE_HARDWARE, mask, false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (isJoystick && event.action == MotionEvent.ACTION_MOVE) {
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
                engine?.setDirections(Engine.SOURCE_HARDWARE, dirs)
            }
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
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(Color(0xDD000000), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Arrastra los botones", color = Color.White)
                    OutlinedButton(onClick = { layout.reset() }) { Text("Restablecer") }
                    Button(onClick = { finishEditing() }) { Text("Listo") }
                }
            }

            if (menuOpen.value) {
                GameMenu(
                    title = game.title,
                    speed = speed.intValue,
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
                        saveLayout = {
                            layout.save(layoutScope())
                            flash("Layout guardado")
                        },
                        resetLayout = {
                            layout.reset()
                            layout.save(layoutScope())
                            flash("Layout restablecido")
                        },
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
        val isEditing = editing.value
        val show = Settings.padVisible.value || isEditing
        val k = Settings.padSize.floatValue
        val a = Settings.padOpacity.floatValue
        val live = !isEditing
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sw = constraints.maxWidth.toFloat()
            val sh = constraints.maxHeight.toFloat()

            // El boton de menu siempre esta visible (translucido).
            Placed(Ctl.MENU, 44, 44, sw, sh, k, maxOf(a, 0.45f), isEditing) {
                Box(
                    Modifier.fillMaxSize().clip(CircleShape).background(Color(0x66000000))
                        .clickable(enabled = live) { setMenu(true) },
                    contentAlignment = Alignment.Center,
                ) { Text("☰", color = Color.White) }
            }
            if (show) {
                Placed(Ctl.QUICK, 44, 44, sw, sh, k, a, isEditing) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(Color(0x66000000))
                            .clickable(enabled = live) { doSave("quick") },
                        contentAlignment = Alignment.Center,
                    ) { Text("⚡", color = Color.White) }
                }
                Placed(Ctl.DPAD, 150, 150, sw, sh, k, a, isEditing) { DPad(eng, live) }
                Placed(Ctl.A, 72, 72, sw, sh, k, a, isEditing) { PadButton("A", GbaKey.A, eng, live) }
                Placed(Ctl.B, 72, 72, sw, sh, k, a, isEditing) { PadButton("B", GbaKey.B, eng, live) }
                Placed(Ctl.L, 96, 46, sw, sh, k, a, isEditing) {
                    PadButton("L", GbaKey.L, eng, live, shape = RoundedCornerShape(14.dp))
                }
                Placed(Ctl.R, 96, 46, sw, sh, k, a, isEditing) {
                    PadButton("R", GbaKey.R, eng, live, shape = RoundedCornerShape(14.dp))
                }
                Placed(Ctl.SELECT, 84, 34, sw, sh, k, a, isEditing) {
                    PadButton("SELECT", GbaKey.SELECT, eng, live, shape = RoundedCornerShape(17.dp), fontSize = 11)
                }
                Placed(Ctl.START, 84, 34, sw, sh, k, a, isEditing) {
                    PadButton("START", GbaKey.START, eng, live, shape = RoundedCornerShape(17.dp), fontSize = 11)
                }
            }
        }
    }

    @Composable
    private fun Placed(
        id: String,
        wDp: Int,
        hDp: Int,
        sw: Float,
        sh: Float,
        k: Float,
        alpha: Float,
        isEditing: Boolean,
        content: @Composable () -> Unit,
    ) {
        val d = LocalDensity.current
        val wPx = with(d) { (wDp * k).dp.toPx() }
        val hPx = with(d) { (hDp * k).dp.toPx() }
        val c = layout.pos[id] ?: return
        Box(
            Modifier
                .offset { IntOffset((c.x * sw - wPx / 2f).roundToInt(), (c.y * sh - hPx / 2f).roundToInt()) }
                .size((wDp * k).dp, (hDp * k).dp)
                .alpha(alpha),
        ) {
            content()
            if (isEditing) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, Color(0xFFFFEB3B), RoundedCornerShape(8.dp))
                        .pointerInput(id) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                layout.move(id, drag.x / sw, drag.y / sh)
                            }
                        },
                )
            }
        }
    }
}
