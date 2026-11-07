package app.gbalibrary

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.gbalibrary.data.Covers
import app.gbalibrary.data.Game
import app.gbalibrary.data.GameStore
import app.gbalibrary.emu.Engine
import app.gbalibrary.emu.GbaKey
import app.gbalibrary.emu.Native
import app.gbalibrary.ui.ABCluster
import app.gbalibrary.ui.DPad
import app.gbalibrary.ui.GbaTheme
import app.gbalibrary.ui.MenuPill
import app.gbalibrary.ui.PadButton
import app.gbalibrary.ui.ShoulderButton
import kotlinx.coroutines.delay
import java.io.File

class EmulatorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_GAME_ID = "gameId"
    }

    private var engine: Engine? = null
    private lateinit var game: Game
    private lateinit var store: GameStore

    private var resumedAt = 0L
    private var activityResumed = false
    private var hardwareDirs = 0

    private val menuOpen = mutableStateOf(false)
    private val message = mutableStateOf<String?>(null)
    private val speed = mutableIntStateOf(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GameStore.get(this)
        val found = intent.getStringExtra(EXTRA_GAME_ID)?.let { store.get(it) }
        if (found == null) {
            finish()
            return
        }
        game = found

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        File(filesDir, "saves").mkdirs()
        File(filesDir, "states").mkdirs()

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
        engine = eng
        eng.start()

        onBackPressedDispatcher.addCallback(this) { setMenu(!menuOpen.value) }

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

    private fun syncPause() {
        engine?.paused = menuOpen.value || !activityResumed
    }

    private fun flash(text: String) {
        runOnUiThread { message.value = text }
    }

    private fun applyImmersive() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyImmersive()
    }

    // ---- Ciclo de vida: tiempo de juego, pausa y guardado automatico ----

    override fun onResume() {
        super.onResume()
        activityResumed = true
        resumedAt = SystemClock.elapsedRealtime()
        syncPause()
    }

    override fun onPause() {
        super.onPause()
        activityResumed = false
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
        val eng = engine ?: return
        val id = game.id
        val appContext = applicationContext
        val gameStore = store
        eng.shutdown { pixels ->
            if (pixels != null && !Covers.isBlank(pixels)) {
                Covers.save(appContext, id, pixels)
                gameStore.touchCover(id)
            }
        }
    }

    private fun commitPlayTime() {
        if (resumedAt <= 0L) return
        val seconds = (SystemClock.elapsedRealtime() - resumedAt) / 1000
        resumedAt = 0L
        store.addPlay(game.id, seconds.coerceAtLeast(0), System.currentTimeMillis())
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
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (landscape) LandscapeLayout(eng) else PortraitLayout(eng)

            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickable { setMenu(true) },
                contentAlignment = Alignment.Center,
            ) { Text("☰", color = Color.White) }

            message.value?.let { text ->
                LaunchedEffect(text) {
                    delay(1800)
                    if (message.value == text) message.value = null
                }
                Text(
                    text,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (menuOpen.value) MenuDialog(eng)
        }
    }

    @Composable
    private fun PortraitLayout(eng: Engine) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            GameSurface(Modifier.fillMaxWidth().aspectRatio(1.5f))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ShoulderButton("L", GbaKey.L, eng, Modifier.align(Alignment.TopStart).padding(16.dp))
                ShoulderButton("R", GbaKey.R, eng, Modifier.align(Alignment.TopEnd).padding(16.dp))
                DPad(eng, Modifier.align(Alignment.CenterStart).padding(start = 16.dp, top = 40.dp).size(150.dp))
                ABCluster(eng, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 40.dp))
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    MenuPill("SELECT", GbaKey.SELECT, eng)
                    MenuPill("START", GbaKey.START, eng)
                }
            }
        }
    }

    @Composable
    private fun LandscapeLayout(eng: Engine) {
        Box(Modifier.fillMaxSize().displayCutoutPadding()) {
            GameSurface(Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(1.5f))
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ShoulderButton("L", GbaKey.L, eng)
                DPad(eng, Modifier.size(150.dp))
            }
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ShoulderButton("R", GbaKey.R, eng)
                ABCluster(eng)
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                MenuPill("SELECT", GbaKey.SELECT, eng)
                MenuPill("START", GbaKey.START, eng)
            }
        }
    }

    @Composable
    private fun GameSurface(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.setFixedSize(240, 160)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            Native.setSurface(h.surface)
                        }

                        override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(h: SurfaceHolder) {
                            Native.setSurface(null)
                        }
                    })
                }
            },
        )
    }

    @Composable
    private fun MenuDialog(eng: Engine) {
        AlertDialog(
            onDismissRequest = { setMenu(false) },
            title = { Text(game.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton(onClick = { setMenu(false) }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { finish() }) { Text("Salir") } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Guardar estado")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { slot ->
                            OutlinedButton(onClick = {
                                eng.saveState(statePath("slot$slot")) { ok ->
                                    flash(if (ok) "Estado $slot guardado" else "No se pudo guardar")
                                }
                            }) { Text("$slot") }
                        }
                    }
                    Text("Cargar estado")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { slot ->
                            OutlinedButton(onClick = {
                                eng.loadState(statePath("slot$slot")) { ok ->
                                    flash(if (ok) "Estado $slot cargado" else "El estado $slot esta vacio")
                                }
                                setMenu(false)
                            }) { Text("$slot") }
                        }
                    }
                    OutlinedButton(onClick = {
                        speed.intValue = speed.intValue % 3 + 1
                        eng.speed = speed.intValue
                    }) { Text("Velocidad: x${speed.intValue}") }
                    OutlinedButton(onClick = {
                        eng.reset()
                        setMenu(false)
                        flash("Juego reiniciado")
                    }) { Text("Reiniciar juego") }
                }
            },
        )
    }
}
