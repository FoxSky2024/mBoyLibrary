package app.gbalibrary.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Pawb
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Estado compartido del minijuego: visibilidad y el glitch de la pantalla de inicio cuando esta bloqueado. */
object MiniNav {
    val show = mutableStateOf(false)
    val glitchAt = mutableLongStateOf(0L)
    val glitchOn = mutableStateOf(false)
    val shakeX = mutableFloatStateOf(0f)
    val shakeY = mutableFloatStateOf(0f)
    /** Entrar: si el monstruo te atrapo hoy, la pantalla se glitchea 1 s y no te deja. */
    fun tryEnter(ctx: Context) {
        if (MiniStore.locked(ctx)) { if (glitchAt.longValue == 0L) glitchAt.longValue = SystemClock.uptimeMillis() }
        else show.value = true
    }
}

/** Guardado en el dispositivo: Pawb ganados hoy y bloqueo diario si el monstruo te atrapa. */
object MiniStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("minigame", Context.MODE_PRIVATE)
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun locked(ctx: Context) = prefs(ctx).getString("lock", "") == today()
    fun lock(ctx: Context) { prefs(ctx).edit().putString("lock", today()).apply() }
    /** Recompensa de un cofre: 100 a 200 Pawb y un 0.001 % de ganar 1000. */
    fun roll(rnd: Random = Random.Default): Int = if (rnd.nextDouble() < 0.00001) 1000 else rnd.nextInt(100, 201)
    fun collect(): Int { val r = roll(); Pawb.earn(r); return r }
}

// ------------------------------------------------------------------------------------------ sonido (sintetizado, sin archivos)

private class MgAudio(ctx: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val ids = IntArray(7)
    companion object { const val STEP1 = 0; const val STEP2 = 1; const val SPLASH = 2; const val CHIME = 3; const val HEART = 4; const val SCREAM = 5; const val GROWL = 6 }

    private fun wav(pcm: ShortArray, rate: Int = 22050): ByteArray {
        val bb = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + pcm.size * 2); bb.put("WAVE".toByteArray()); bb.put("fmt ".toByteArray())
        bb.putInt(16); bb.putShort(1); bb.putShort(1); bb.putInt(rate); bb.putInt(rate * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(pcm.size * 2)
        for (s in pcm) bb.putShort(s)
        return bb.array()
    }
    private fun synth(sec: Float, f: (Float, Int) -> Float): ShortArray {
        val n = (22050 * sec).toInt()
        return ShortArray(n) { i -> (f(i / 22050f, i).coerceIn(-1f, 1f) * 32000f).toInt().toShort() }
    }
    private fun load(dir: File, name: String, pcm: ShortArray, slot: Int) {
        runCatching { val f = File(dir, name); f.writeBytes(wav(pcm)); ids[slot] = pool.load(f.absolutePath, 1) }
    }

    init {
        val d = ctx.cacheDir
        val r = Random(7)
        var lp = 0f
        load(d, "mg_s1.wav", synth(0.16f) { t, _ -> lp = lp * 0.82f + (r.nextFloat() - 0.5f) * 0.18f; (lp * 5f + sin(2f * PI.toFloat() * (75f - t * 110f) * t) * 0.9f) * exp(-t * 24f) * 0.6f }, STEP1)
        load(d, "mg_s2.wav", synth(0.15f) { t, _ -> lp = lp * 0.8f + (r.nextFloat() - 0.5f) * 0.2f; (lp * 5f + sin(2f * PI.toFloat() * (88f - t * 120f) * t) * 0.9f) * exp(-t * 26f) * 0.55f }, STEP2)
        load(d, "mg_sp.wav", synth(0.3f) { t, _ -> lp = lp * 0.55f + (r.nextFloat() - 0.5f) * 0.45f; (lp * 1.6f + sin(2f * PI.toFloat() * (420f + 300f * sin(t * 40f)) * t) * 0.12f) * exp(-t * 11f) * 0.55f }, SPLASH)
        load(d, "mg_ch.wav", synth(0.8f) { t, _ -> (sin(2f * PI.toFloat() * 880f * t) * exp(-t * 5f) + 0.6f * sin(2f * PI.toFloat() * 1320f * t) * exp(-t * 6f) + 0.3f * sin(2f * PI.toFloat() * 1760f * t) * exp(-t * 8f)) * 0.5f }, CHIME)
        load(d, "mg_hb.wav", synth(0.5f) { t, _ -> val a = sin(2f * PI.toFloat() * 52f * t) * exp(-t * 20f); val t2 = max(0f, t - 0.19f); val b = sin(2f * PI.toFloat() * 46f * t2) * exp(-t2 * 22f) * (if (t > 0.19f) 1f else 0f); (a + 0.8f * b) * 0.9f }, HEART)
        var ph = 0f
        load(d, "mg_sc.wav", synth(1.5f) { t, _ ->
            val fr = 650f + 500f * sin(2f * PI.toFloat() * 8f * t) + 350f * t
            ph += fr / 22050f
            val saw = (ph - floor(ph)) * 2f - 1f
            val env = min(1f, t * 40f) * exp(-max(0f, t - 0.9f) * 5f)
            kotlin.math.tanh((saw * 1.4f + (r.nextFloat() - 0.5f) * 1.2f) * 2.5f) * env * 0.95f
        }, SCREAM)
        var gp = 0f
        load(d, "mg_gr.wav", synth(1.0f) { t, _ -> gp += (60f + 25f * sin(t * 9f)) / 22050f; val s = if ((gp - floor(gp)) < 0.5f) 1f else -1f; (s * 0.5f + (r.nextFloat() - 0.5f) * 0.4f) * exp(-t * 2.5f) * 0.6f }, GROWL)
    }
    fun play(slot: Int, vol: Float = 1f) { if (ids[slot] != 0) pool.play(ids[slot], vol, vol, 1, 0, 1f) }
    fun release() { runCatching { pool.release() } }
}

// ------------------------------------------------------------------------------------------ texturas y variantes

private const val TEX = 32
private val BAYER = intArrayOf(0, 4, 1, 5, 6, 2, 7, 3, 1, 5, 0, 4, 7, 3, 6, 2)

private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
/** Color que no recibe luz ni niebla (linterna, ojos). */
private fun emi(r: Int, g: Int, b: Int) = (0xFE shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

private class Variant(
    val name: String, val backrooms: Boolean, val ambient: Float, val fogR: Int, val fogG: Int, val fogB: Int, val fogK: Float, val r2: Float,
    val walls: Array<IntArray>, val floor: IntArray, val ceil: IntArray, val lamp: IntArray, val water: IntArray,
)

private fun tex(seed: Int, f: (x: Int, y: Int, n: Int) -> Int): IntArray {
    val r = Random(seed)
    return IntArray(TEX * TEX) { i -> f(i % TEX, i / TEX, r.nextInt(-12, 13)) }
}

private fun makeVariant(backrooms: Boolean): Variant =
    if (backrooms) Variant(
        "BACKROOMS", true, 0.5f, 92, 84, 46, 0.3f, 30f,
        arrayOf(
            tex(1) { x, y, n -> val v = 175 + n - (if (x % 8 < 2) 16 else 0) - (if ((x * 5 + y * 3) % 23 == 0) 22 else 0); rgb(v + 12, v - 4, v - 80) },
            tex(2) { x, y, n -> val v = 168 + n - (if (x % 10 < 2) 18 else 0); rgb(v + 10, v - 6, v - 84) },
            tex(3) { x, y, n -> val v = 150 + n - (if (y > 20 && (x * 7 + y) % 5 == 0) 20 else 0) - (if (x % 8 < 2) 12 else 0); rgb(v + 14, v - 6, v - 76) },
        ),
        tex(4) { x, y, n -> val v = 118 + n + (if ((x + y) % 4 == 0) 8 else 0); rgb(v + 8, v - 6, v - 58) },
        tex(5) { x, y, n -> if (x % 16 == 0 || y % 16 == 0) rgb(90, 88, 70) else { val v = 150 + n / 2; rgb(v, v - 2, v - 26) } },
        tex(6) { x, y, n -> if (x < 3 || y < 3 || x > 28 || y > 28) rgb(110, 108, 90) else rgb(255, 252, 215 + n / 3) },
        tex(7) { _, _, n -> rgb(100 + n, 90 + n, 40) },
    ) else Variant(
        "POOLROOMS", false, 0.42f, 58, 98, 110, 0.28f, 30f,
        arrayOf(
            tex(11) { x, y, n -> if (x % 8 == 0 || y % 8 == 0) rgb(110, 150, 155) else { val v = 205 + n / 2; rgb(v - 10, v + 8, v + 8) } },
            tex(12) { x, y, n -> if (x % 8 == 0 || y % 8 == 0) rgb(105, 140, 160) else { val v = 200 + n / 2; rgb(v - 20, v, v + 14) } },
            tex(13) { x, y, n -> if (x % 8 == 0 || y % 8 == 0) rgb(100, 150, 140) else { val v = 196 + n / 2; rgb(v - 14, v + 10, v - 4) } },
        ),
        tex(14) { x, y, n -> if (x % 16 == 0 || y % 16 == 0) rgb(120, 160, 165) else { val v = 195 + n / 2; rgb(v - 8, v + 6, v + 6) } },
        tex(15) { x, y, n -> if (x % 16 == 0 || y % 16 == 0) rgb(120, 140, 145) else { val v = 200 + n / 2; rgb(v, v + 4, v + 4) } },
        tex(16) { x, y, n -> if (x < 3 || y < 3 || x > 28 || y > 28) rgb(130, 140, 140) else rgb(245, 255, 255 + n / 3) },
        tex(17) { x, y, n -> val v = n + (if ((x * 3 + y) % 9 < 2) 14 else 0); rgb(36 + v / 2, 150 + v, 178 + v) },
    )

// ------------------------------------------------------------------------------------------ sprites

private fun inTri(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Boolean {
    val d1 = (x - bx) * (ay - by) - (ax - bx) * (y - by)
    val d2 = (x - cx) * (by - cy) - (bx - cx) * (y - cy)
    val d3 = (x - ax) * (cy - ay) - (cx - ax) * (y - ay)
    val neg = d1 < 0 || d2 < 0 || d3 < 0; val pos = d1 > 0 || d2 > 0 || d3 > 0
    return !(neg && pos)
}

/** El personaje (gato negro con linterna), visto de espaldas. 16x24. */
private fun makePlayer(): IntArray {
    val tw = 16; val th = 24
    val t = IntArray(tw * th)
    fun body(x: Int, y: Int): Boolean {
        val fx = x + 0.5f; val fy = y + 0.5f
        val head = (fx - 7.5f) * (fx - 7.5f) + (fy - 8.5f) * (fy - 8.5f) <= 4.6f * 4.6f
        val earL = inTri(fx, fy, 3.2f, 6.5f, 3.4f, 0.6f, 7f, 4.6f)
        val earR = inTri(fx, fy, 11.8f, 6.5f, 11.6f, 0.6f, 8f, 4.6f)
        val torso = ((fx - 7.5f) / 4.4f) * ((fx - 7.5f) / 4.4f) + ((fy - 17f) / 6.4f) * ((fy - 17f) / 6.4f) <= 1f
        return head || earL || earR || torso
    }
    for (y in 0 until th) for (x in 0 until tw) {
        if (body(x, y)) t[y * tw + x] = if (!body(x + 1, y) && x > 8) rgb(46, 44, 56) else rgb(10, 10, 14)
    }
    for (y in 7..22) t[y * tw + 13] = rgb(70, 56, 40)                                  // baston
    for (a in 0 until 24) {                                                           // aro de la linterna
        val ang = a * 2.0 * PI / 24
        val x = (13.0 + 2.2 * cos(ang)).roundToInt(); val y = (6.0 + 2.2 * sin(ang)).roundToInt()
        if (x in 0 until tw && y in 0 until th) t[y * tw + x] = emi(255, 205, 110)
    }
    t[6 * tw + 13] = emi(255, 240, 190)
    return t
}

/** Monstruo alto y delgado de cara palida. 20x44. */
private fun makeMonster(): IntArray {
    val tw = 20; val th = 44
    val t = IntArray(tw * th)
    for (y in 0 until th) for (x in 0 until tw) {
        val fx = x + 0.5f; val fy = y + 0.5f
        val head = ((fx - 10f) / 5f) * ((fx - 10f) / 5f) + ((fy - 7f) / 6.5f) * ((fy - 7f) / 6.5f) <= 1f
        val neck = abs(fx - 10f) < 1.6f && fy in 12f..16f
        val robeW = 3f + (fy - 14f) * 0.16f
        val robe = fy in 14f..42f && abs(fx - 10f) < robeW + 1.5f
        val armL = fy in 15f..41f && abs(fx - (3.5f - (fy - 15f) * 0.05f)) < 0.9f
        val armR = fy in 15f..41f && abs(fx - (16.5f + (fy - 15f) * 0.05f)) < 0.9f
        t[y * tw + x] = when {
            head -> {
                val eyeL = ((fx - 7.6f) / 1.6f) * ((fx - 7.6f) / 1.6f) + ((fy - 6.5f) / 2.1f) * ((fy - 6.5f) / 2.1f) <= 1f
                val eyeR = ((fx - 12.4f) / 1.6f) * ((fx - 12.4f) / 1.6f) + ((fy - 6.5f) / 2.1f) * ((fy - 6.5f) / 2.1f) <= 1f
                val mouth = fy in 10f..12.5f && abs(fx - 10f) < 2.4f
                if ((abs(fx - 7.6f) < 0.6f || abs(fx - 12.4f) < 0.6f) && abs(fy - 6.8f) < 0.7f) emi(255, 40, 30)
                else if (eyeL || eyeR || mouth) rgb(4, 2, 4) else rgb(205, 200, 192)
            }
            neck -> rgb(150, 146, 140)
            robe -> if ((x + y / 3) % 5 == 0) rgb(34, 30, 36) else rgb(16, 14, 18)
            armL || armR -> rgb(150, 146, 140)
            else -> 0
        }
    }
    return t
}

private fun makeChest(): IntArray {
    val tw = 16
    val t = IntArray(tw * tw)
    val outline = rgb(30, 16, 8)
    for (y in 0 until tw) for (x in 0 until tw) {
        if (x < 1 || x > 14 || y < 3 || y > 14) continue
        var c = if (y <= 7) rgb(150, 88, 40) else rgb(112, 62, 28)
        if (y == 10 || y == 12) c = rgb(88, 48, 22)
        if (x == 1 || x == 14 || y == 3 || y == 14 || y == 8) c = outline
        if (y <= 4 && (x <= 2 || x >= 13)) c = 0
        if (c != 0 && (x == 7 || x == 8)) c = rgb(236, 190, 60)
        if (x in 6..9 && y in 7..10) c = emi(255, 224, 110)
        t[y * tw + x] = c
    }
    return t
}

// ------------------------------------------------------------------------------------------ mundo y motor (raycaster estilo PS1, tercera persona)

private class MgEngine(val ctx: Context, val w: Int, val h: Int, val v: Variant, val audio: MgAudio) {
    val mw = 44; val mh = 44
    val cells = IntArray(mw * mh) { 1 }
    private val water = BooleanArray(mw * mh)
    private val corr = BooleanArray(mw * mh)
    private val rooms = ArrayList<IntArray>()
    private var floors = IntArray(0)
    val chests = ArrayList<IntArray>()
    var px = 2f; var py = 2f; var ang = 0f
    var time = 0f
    var session by mutableIntStateOf(0)
    var got = 0
    var caught by mutableStateOf(false)
    var mActive = false; var mX = 0f; var mY = 0f
    var monsterDist = 99f
    private val dist = IntArray(mw * mh)
    private val queue = IntArray(mw * mh)
    private var bfsT = 0f; private var stepT = 0f; private var stepAlt = false; private var hbT = 0f; private var growlT = 6f
    private val rnd = Random(SystemClock.elapsedRealtime())

    private val playerTex = makePlayer(); private val monsterTex = makeMonster(); private val chestTex = makeChest()
    private val zbuf = FloatArray(w); private val vig = IntArray(w * h)
    val pix = IntArray(w * h)
    private var rs = 0x2545F491
    private var camX = 0f; private var camY = 0f; private var dirX = 1f; private var dirY = 0f; private var planeX = 0f; private var planeY = 0.66f; private var invDet = 1f
    private var flick = 1f

    init {
        val bk = v.backrooms
        val rMin = if (bk) 5 else 7; val rMax = if (bk) 10 else 12; val nRooms = if (bk) 11 else 8
        var tries = 0
        while (rooms.size < nRooms && tries < 600) {
            tries++
            val rw = rnd.nextInt(rMin, rMax + 1); val rh = rnd.nextInt(rMin, rMax + 1)
            val rx = rnd.nextInt(1, mw - rw - 1); val ry = rnd.nextInt(1, mh - rh - 1)
            if (rooms.none { q -> rx < q[0] + q[2] + 1 && rx + rw + 1 > q[0] && ry < q[1] + q[3] + 1 && ry + rh + 1 > q[1] }) rooms.add(intArrayOf(rx, ry, rw, rh))
        }
        for (q in rooms) for (y in q[1] until q[1] + q[3]) for (x in q[0] until q[0] + q[2]) cells[y * mw + x] = 0
        for (i in 1 until rooms.size) link(rooms[i - 1], rooms[i])
        repeat(5) { val a = rnd.nextInt(rooms.size); val b = rnd.nextInt(rooms.size); if (a != b) link(rooms[a], rooms[b]) }
        for (q in rooms) for (y in q[1] until q[1] + q[3]) for (x in q[0] until q[0] + q[2]) {
            val i = y * mw + x
            if (corr[i]) continue
            if (bk) { if (x % 3 == 1 && y % 3 == 1) cells[i] = 1 }
            else {
                if (x % 4 == 2 && y % 4 == 2) cells[i] = 1
                else if (x > q[0] && y > q[1] && x < q[0] + q[2] - 1 && y < q[1] + q[3] - 1) water[i] = true
            }
        }
        for (i in cells.indices) if (cells[i] != 0) cells[i] = 1 + ((i % mw) / 5 + (i / mw) / 7) % 3
        floors = (0 until mw * mh).filter { cells[it] == 0 }.toIntArray()
        val s = rooms[0]
        px = s[0] + s[2] / 2 + 0.5f; py = s[1] + s[3] / 2 + 0.5f
        repeat(3) { spawnChest()?.let { c -> chests.add(c) } }
        for (i in vig.indices) {
            val nx = ((i % w) - w / 2f) / (w / 2f); val ny = ((i / w) - h / 2f) / (h / 2f)
            vig[i] = (256 * (1f - 0.6f * (nx * nx + ny * ny) / 2f).coerceIn(0.3f, 1f)).toInt()
        }
        bfs()
    }

    private fun link(a: IntArray, b: IntArray) {
        val ax = a[0] + a[2] / 2; val ay = a[1] + a[3] / 2; val bx = b[0] + b[2] / 2; val by = b[1] + b[3] / 2
        for (x in min(ax, bx)..max(ax, bx)) { cells[ay * mw + x] = 0; corr[ay * mw + x] = true }
        for (y in min(ay, by)..max(ay, by)) { cells[y * mw + bx] = 0; corr[y * mw + bx] = true }
    }

    /** Un cofre nuevo en un lugar aleatorio del mapa, lejos de ti y de los demas cofres. */
    private fun spawnChest(): IntArray? {
        var best: IntArray? = null
        for (k in 0 until 300) {
            val c = floors[rnd.nextInt(floors.size)]
            val x = c % mw; val y = c / mw
            val d = sqrt((x + 0.5f - px) * (x + 0.5f - px) + (y + 0.5f - py) * (y + 0.5f - py))
            if (d < (if (k < 200) 12f else 6f)) continue
            if (chests.any { abs(it[0] - x) + abs(it[1] - y) < 6 }) continue
            if (mActive && abs(mX - x) + abs(mY - y) < 6f) continue
            best = intArrayOf(x, y); break
        }
        return best
    }

    private fun solid(x: Float, y: Float): Boolean {
        val cx = x.toInt(); val cy = y.toInt()
        if (x < 0f || y < 0f || cx >= mw || cy >= mh) return true
        return cells[cy * mw + cx] != 0
    }
    private fun blocked(x: Float, y: Float): Boolean { val r = 0.22f; return solid(x - r, y - r) || solid(x + r, y - r) || solid(x - r, y + r) || solid(x + r, y + r) }

    /** Mapa de distancias desde el jugador (para que el monstruo encuentre el camino). */
    private fun bfs() {
        java.util.Arrays.fill(dist, -1)
        var head = 0; var tail = 0
        val s = py.toInt() * mw + px.toInt()
        dist[s] = 0; queue[tail++] = s
        while (head < tail) {
            val c = queue[head++]; val x = c % mw; val y = c / mw
            if (x > 0 && dist[c - 1] < 0 && cells[c - 1] == 0) { dist[c - 1] = dist[c] + 1; queue[tail++] = c - 1 }
            if (x < mw - 1 && dist[c + 1] < 0 && cells[c + 1] == 0) { dist[c + 1] = dist[c] + 1; queue[tail++] = c + 1 }
            if (y > 0 && dist[c - mw] < 0 && cells[c - mw] == 0) { dist[c - mw] = dist[c] + 1; queue[tail++] = c - mw }
            if (y < mh - 1 && dist[c + mw] < 0 && cells[c + mw] == 0) { dist[c + mw] = dist[c] + 1; queue[tail++] = c + mw }
        }
    }

    private fun spawnMonster() {
        var bestC = -1; var bestD = -1
        for (k in 0 until 200) {
            val c = floors[rnd.nextInt(floors.size)]
            if (dist[c] > bestD) { bestD = dist[c]; bestC = c }
            if (bestD >= 20) break
        }
        if (bestC < 0) return
        mX = bestC % mw + 0.5f; mY = bestC / mw + 0.5f; mActive = true
        audio.play(MgAudio.GROWL, 0.8f)
    }

    /** Avanza el juego. Devuelve los Pawb de un cofre recien abierto, o -1. */
    fun update(dt: Float, joyX: Float, joyY: Float, look: Float): Int {
        time += dt
        ang += look * 0.0065f
        val dx = cos(ang); val dy = sin(ang)
        val mag = min(1f, sqrt(joyX * joyX + joyY * joyY))
        val sp = 2.3f * dt
        val mx = (dx * -joyY + (-dy) * joyX) * sp
        val my = (dy * -joyY + dx * joyX) * sp
        var moved = false
        if (!blocked(px + mx, py)) { px += mx; moved = moved || abs(mx) > 1e-4f }
        if (!blocked(px, py + my)) { py += my; moved = moved || abs(my) > 1e-4f }
        // pasos
        if (moved && mag > 0.15f) {
            stepT += dt * (0.5f + mag)
            if (stepT >= 0.5f) {
                stepT = 0f; stepAlt = !stepAlt
                val inWater = water[py.toInt().coerceIn(0, mh - 1) * mw + px.toInt().coerceIn(0, mw - 1)]
                audio.play(if (inWater) MgAudio.SPLASH else if (stepAlt) MgAudio.STEP1 else MgAudio.STEP2, 0.8f)
            }
        } else stepT = 0.35f
        // monstruo
        bfsT -= dt
        if (bfsT <= 0f) { bfs(); bfsT = 0.2f }
        if (!mActive && time > 9f) spawnMonster()
        if (mActive) {
            val spd = (1.45f + 0.07f * got).coerceAtMost(2.0f)
            val ddx = px - mX; val ddy = py - mY
            val dd = sqrt(ddx * ddx + ddy * ddy)
            monsterDist = dd
            var tx = px; var ty = py
            if (dd > 1.6f) {
                val c = mY.toInt().coerceIn(0, mh - 1) * mw + mX.toInt().coerceIn(0, mw - 1)
                var bestN = c; var bd = if (dist[c] >= 0) dist[c] else Int.MAX_VALUE
                for (n in intArrayOf(c - 1, c + 1, c - mw, c + mw)) if (n in 0 until mw * mh && dist[n] in 0 until bd) { bd = dist[n]; bestN = n }
                if (bestN != c) { tx = bestN % mw + 0.5f; ty = bestN / mw + 0.5f }
            }
            val vx = tx - mX; val vy = ty - mY; val vl = sqrt(vx * vx + vy * vy)
            if (vl > 1e-3f) { val st = min(vl, spd * dt); mX += vx / vl * st; mY += vy / vl * st }
            if (dd < 0.7f && !caught) { caught = true; audio.play(MgAudio.SCREAM, 1f) }
            if (dd < 9f) { hbT -= dt; if (hbT <= 0f) { audio.play(MgAudio.HEART, 0.9f); hbT = 0.4f + dd * 0.09f } }
            growlT -= dt
            if (growlT <= 0f) { audio.play(MgAudio.GROWL, if (dd < 12f) 0.6f else 0.25f); growlT = 8f + rnd.nextFloat() * 6f }
        }
        // cofres
        for (i in chests.indices) {
            val ddx = chests[i][0] + 0.5f - px; val ddy = chests[i][1] + 0.5f - py
            if (ddx * ddx + ddy * ddy < 0.75f) {
                val r = MiniStore.collect()
                session += r; got++
                audio.play(MgAudio.CHIME, 1f)
                val nc = spawnChest()
                if (nc != null) chests[i] = nc else chests.removeAt(i)
                if (chests.size < 3) spawnChest()?.let { chests.add(it) }
                return r
            }
        }
        return -1
    }

    private fun shade(c: Int, light: Float, fog: Float): Int {
        var r = ((c shr 16) and 255) * light; var g = ((c shr 8) and 255) * light; var b = (c and 255) * light
        r += (v.fogR - r) * fog; g += (v.fogG - g) * fog; b += (v.fogB - b) * fog
        return rgb(r.toInt(), g.toInt(), b.toInt())
    }
    private fun lightAt(d2: Float): Float { val t = max(0f, 1f - d2 / v.r2); return v.ambient + 1.55f * t * t * flick }

    /** Dibuja un sprite de cara a la camara, ocultandolo tras las paredes. */
    private fun billboard(t: IntArray, tw: Int, th: Int, wx: Float, wy: Float, hCells: Float, boost: Float, fogMul: Float, bob: Int) {
        val sx = wx - camX; val sy = wy - camY
        val ty = invDet * (-planeY * sx + planeX * sy); val tx = invDet * (dirY * sx - dirX * sy)
        if (ty < 0.12f) return
        val half = h / 2
        val scrX = (w / 2f * (1f + tx / ty)).toInt()
        val unit = (h / ty).toInt()
        val sh = max(3, (unit * hCells).toInt()); val sw = max(2, sh * tw / th)
        val floorY = half + unit / 2 - bob
        val top = floorY - sh; val left = scrX - sw / 2
        val d2 = (wx - px) * (wx - px) + (wy - py) * (wy - py)
        val light = max(0.4f, lightAt(d2)) * boost
        val fog = (1f - exp(-v.fogK * ty)) * fogMul
        for (xx in left until left + sw) {
            if (xx < 0 || xx >= w || ty >= zbuf[xx]) continue
            val tX = ((xx - left) * tw / sw).coerceIn(0, tw - 1)
            for (yy in max(0, top) until min(h, floorY)) {
                val tY = ((yy - top) * th / sh).coerceIn(0, th - 1)
                val c = t[tY * tw + tX]
                if (c == 0) continue
                pix[yy * w + xx] = if ((c ushr 24) == 0xFE) rgb((c shr 16) and 255, (c shr 8) and 255, c and 255) else shade(c, light, fog)
            }
        }
    }

    fun render() {
        dirX = cos(ang); dirY = sin(ang)
        planeX = -dirY * 0.66f; planeY = dirX * 0.66f
        // camara de tercera persona: detras del jugador, sin atravesar paredes
        var cd = 0f
        for (k in 1..9) { val d = 1.35f * k / 9f; if (blocked(px - dirX * d, py - dirY * d)) break; cd = d }
        camX = px - dirX * cd; camY = py - dirY * cd
        invDet = 1f / (planeX * dirY - dirX * planeY)
        flick = 1f + 0.05f * sin(time * 23f) + (if (sin(time * 7.3f) > 0.985f) -0.3f else 0f)
        val half = h / 2
        // suelo, agua y techo
        for (y in half + 1 until h) {
            val rowDist = half.toFloat() / (y - half)
            val fog = 1f - exp(-v.fogK * rowDist)
            val rx0 = dirX - planeX; val ry0 = dirY - planeY
            val sx = rowDist * (2f * planeX) / w; val sy = rowDist * (2f * planeY) / w
            var fx = camX + rowDist * rx0; var fy = camY + rowDist * ry0
            val yc = h - y - 1
            for (x in 0 until w) {
                val cxI = floor(fx).toInt(); val cyI = floor(fy).toInt()
                val tx = ((fx - cxI) * TEX).toInt() and (TEX - 1); val ty = ((fy - cyI) * TEX).toInt() and (TEX - 1)
                val ddx = fx - px; val ddy = fy - py
                var light = lightAt(ddx * ddx + ddy * ddy)
                val inMap = cxI in 0 until mw && cyI in 0 until mh
                val c: Int
                if (inMap && water[cyI * mw + cxI]) {
                    // agua: refraccion leve (la textura se desplaza con olas) y caustica
                    val u = fx + 0.06f * sin(fy * 6f + time * 1.7f); val vv = fy + 0.06f * cos(fx * 6f + time * 1.4f)
                    val wtx = ((u - floor(u)) * TEX).toInt() and (TEX - 1); val wty = ((vv - floor(vv)) * TEX).toInt() and (TEX - 1)
                    val caus = 0.5f + 0.5f * sin((fx + fy) * 8f + time * 2.2f) * sin((fx - fy) * 6f - time * 1.7f)
                    light *= 0.78f + 0.5f * caus
                    var wc = v.water[wty * TEX + wtx]
                    if (caus > 0.9f) wc = rgb(((wc shr 16) and 255) + 60, ((wc shr 8) and 255) + 60, (wc and 255) + 50)
                    c = wc
                } else c = v.floor[ty * TEX + tx]
                pix[y * w + x] = shade(c, light, fog)
                val lampCell = inMap && ((cxI * 7 + cyI * 3) % 4 == 0)
                pix[yc * w + x] = shade(if (lampCell) v.lamp[ty * TEX + tx] else v.ceil[ty * TEX + tx], light * (if (lampCell) 1.4f else 0.8f), min(1f, fog + 0.05f))
                fx += sx; fy += sy
            }
        }
        // paredes
        for (x in 0 until w) {
            val camXr = 2f * x / w - 1f
            val rayX = dirX + planeX * camXr; val rayY = dirY + planeY * camXr
            var mapX = camX.toInt(); var mapY = camY.toInt()
            val ddx = if (rayX == 0f) 1e30f else abs(1f / rayX); val ddy = if (rayY == 0f) 1e30f else abs(1f / rayY)
            val stepX: Int; val stepY: Int; var sideX: Float; var sideY: Float
            if (rayX < 0) { stepX = -1; sideX = (camX - mapX) * ddx } else { stepX = 1; sideX = (mapX + 1f - camX) * ddx }
            if (rayY < 0) { stepY = -1; sideY = (camY - mapY) * ddy } else { stepY = 1; sideY = (mapY + 1f - camY) * ddy }
            var side = 0; var hit = 0; var guard = 0
            while (hit == 0 && guard++ < 64) {
                if (sideX < sideY) { sideX += ddx; mapX += stepX; side = 0 } else { sideY += ddy; mapY += stepY; side = 1 }
                hit = if (mapX < 0 || mapY < 0 || mapX >= mw || mapY >= mh) 1 else cells[mapY * mw + mapX]
            }
            val perp = max(0.05f, if (side == 0) sideX - ddx else sideY - ddy)
            zbuf[x] = perp
            var wx = if (side == 0) camY + perp * rayY else camX + perp * rayX
            wx -= floor(wx)
            var texX = (wx * TEX).toInt().coerceIn(0, TEX - 1)
            if ((side == 0 && rayX > 0) || (side == 1 && rayY < 0)) texX = TEX - 1 - texX
            val lineH = max(1, (h / perp).toInt())
            val start = half - lineH / 2; val end = half + lineH / 2
            val tx = v.walls[(hit - 1).coerceIn(0, 2)]
            val fog = 1f - exp(-v.fogK * perp)
            val hx = camX + perp * rayX - px; val hy = camY + perp * rayY - py
            val light = lightAt(hx * hx + hy * hy) * (if (side == 1) 0.78f else 1f)
            for (y in max(0, start) until min(h, end)) {
                val texY = ((y - start) * TEX / lineH) and (TEX - 1)
                pix[y * w + x] = shade(tx[texY * TEX + texX], light, fog)
            }
        }
        // sprites: cofres, monstruo y jugador, del mas lejano al mas cercano
        val kinds = ArrayList<FloatArray>() // [tipo, indice, dist2]
        for (i in chests.indices) { val ex = chests[i][0] + 0.5f - camX; val ey = chests[i][1] + 0.5f - camY; kinds.add(floatArrayOf(0f, i.toFloat(), ex * ex + ey * ey)) }
        if (mActive) { val ex = mX - camX; val ey = mY - camY; kinds.add(floatArrayOf(1f, 0f, ex * ex + ey * ey)) }
        kinds.add(floatArrayOf(2f, 0f, (px - camX) * (px - camX) + (py - camY) * (py - camY)))
        kinds.sortByDescending { it[2] }
        val pulse = 0.5f + 0.5f * sin(time * 3f)
        val bob = if (stepT < 0.25f && stepT > 0f) 1 else 0
        for (k in kinds) {
            when (k[0].toInt()) {
                0 -> {
                    val c = chests[k[1].toInt()]
                    billboard(chestTex, 16, 16, c[0] + 0.5f, c[1] + 0.5f, 0.42f, 1f + 0.4f * pulse, 0.6f, 0)
                    // haz dorado hacia arriba, visible de cerca
                    val sx = c[0] + 0.5f - camX; val sy = c[1] + 0.5f - camY
                    val ty = invDet * (-planeY * sx + planeX * sy); val tx = invDet * (dirY * sx - dirX * sy)
                    if (ty > 0.2f && ty < 9f) {
                        val scrX = (w / 2f * (1f + tx / ty)).toInt(); val unit = (h / ty).toInt()
                        val floorY = half + unit / 2; val top = floorY - (unit * 0.42f).toInt()
                        val bt = top - unit
                        val a0 = 0.3f * (1f - ty / 9f)
                        for (xx in scrX - 1..scrX + 1) {
                            if (xx < 0 || xx >= w || ty >= zbuf[xx]) continue
                            for (yy in max(0, bt) until min(h, top)) {
                                val a = a0 * (yy - bt).toFloat() / max(1, top - bt)
                                val cc = pix[yy * w + xx]
                                pix[yy * w + xx] = rgb(((cc shr 16) and 255) + ((255 - ((cc shr 16) and 255)) * a).toInt(), ((cc shr 8) and 255) + ((210 - ((cc shr 8) and 255)) * a).toInt(), (cc and 255) + ((90 - (cc and 255)) * a).toInt())
                            }
                        }
                    }
                }
                1 -> billboard(monsterTex, 20, 44, mX, mY, 1.25f, 1.2f, 0.55f, (sin(time * 5f) * 1.2f).toInt())
                else -> billboard(playerTex, 16, 24, px, py, 0.62f, 1.7f, 0.1f, bob)
            }
        }
        // acabado PS1: vineta, color de 15 bits con tramado ordenado y estatica cuando el monstruo esta cerca
        val thr = if (mActive && monsterDist < 8f) ((8f - monsterDist) / 8f * 22f).toInt() else 0
        for (i in 0 until w * h) {
            val x = i % w; val y = i / w
            val c = pix[i]; val vg = vig[i]
            val d = BAYER[(y and 3) * 4 + (x and 3)]
            var r = min(255, (((c shr 16) and 255) * vg shr 8) + d) and 0xF8
            var g = min(255, (((c shr 8) and 255) * vg shr 8) + d) and 0xF8
            var b = min(255, ((c and 255) * vg shr 8) + d) and 0xF8
            if (thr > 0) {
                rs = rs xor (rs shl 13); rs = rs xor (rs ushr 17); rs = rs xor (rs shl 5)
                if ((rs and 0xFF) < thr) { val gr = 70 + ((rs shr 8) and 127); r = gr; g = gr; b = gr }
            }
            pix[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

// ------------------------------------------------------------------------------------------ pantallas

private class LookAcc { var acc = 0f }

@Composable
fun MiniGameScreen(onExit: () -> Unit) {
    val ctx = LocalContext.current
    var phase by remember { mutableIntStateOf(0) } // 0 carga, 1 jugando, 2 resultado
    var died by remember { mutableStateOf(false) }
    val sess = remember { mutableIntStateOf(0) }
    BackHandler { if (phase == 1) phase = 2 else onExit() }
    when (phase) {
        0 -> LoadingScreen(2000, onDone = { phase = if (MiniStore.locked(ctx)) 2 else 1 })
        1 -> MiniGamePlay(sess, onFinish = { d -> if (d) MiniStore.lock(ctx); died = d; phase = 2 })
        else -> {
            val s = sess.intValue
            val msg = when {
                died -> "El monstruo te atrapó\nConseguiste $s Pawb\nNo puedes volver hasta mañana"
                MiniStore.locked(ctx) && s == 0 -> "Hoy ya no puedes entrar\nVuelve mañana"
                else -> "Saliste del juego\nConseguiste $s Pawb"
            }
            LoadingScreen(2800, onDone = onExit, message = msg)
        }
    }
}

@Composable
private fun MiniGamePlay(sess: MutableIntState, onFinish: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current
    val audio = remember { MgAudio(ctx.applicationContext) }
    DisposableEffect(Unit) { onDispose { audio.release() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val wPx = with(dens) { maxWidth.toPx() }; val hPx = with(dens) { maxHeight.toPx() }
        val rw = 176
        val rh = (rw * hPx / wPx).roundToInt().coerceIn(80, 130)
        val variant = remember { makeVariant(Random.nextBoolean()) }
        val eng = remember { MgEngine(ctx, rw, rh, variant, audio) }
        val bmp = remember { Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888) }
        val img = remember { bmp.asImageBitmap() }
        var tick by remember { mutableLongStateOf(0L) }
        var joy by remember { mutableStateOf(Offset.Zero) }
        var joyBase by remember { mutableStateOf<Offset?>(null) }
        val look = remember { LookAcc() }
        var toast by remember { mutableStateOf("") }
        var toastT by remember { mutableFloatStateOf(0f) }
        var hintT by remember { mutableFloatStateOf(7f) }
        var scareT by remember { mutableFloatStateOf(-1f) }
        val radius = with(dens) { 56.dp.toPx() }

        LaunchedEffect(eng) {
            var last = 0L
            while (true) {
                withFrameNanosCompat { t ->
                    val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceAtMost(0.05f)
                    last = t
                    if (scareT < 0f) {
                        val l = look.acc; look.acc = 0f
                        val ev = eng.update(dt, joy.x, joy.y, l)
                        if (ev >= 0) { toast = if (ev >= 1000) "¡¡ +$ev Pawb !!" else "+$ev Pawb"; toastT = 2.2f; sess.intValue = eng.session }
                        if (eng.caught) scareT = 0f
                        eng.render()
                        bmp.setPixels(eng.pix, 0, rw, 0, 0, rw, rh)
                    } else {
                        scareT += dt
                        if (scareT > 1.8f) { scareT = -2f; onFinish(true) }
                    }
                    if (toastT > 0f) toastT -= dt
                    if (hintT > 0f) hintT -= dt
                    tick = t
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.let { }
            drawImage(img, srcSize = IntSize(rw, rh), dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None)
            joyBase?.let { b ->
                drawCircle(Color.White.copy(alpha = 0.18f), radius, b, style = Stroke(3f))
                drawCircle(Color.White.copy(alpha = 0.35f), radius * 0.4f, Offset(b.x + joy.x * radius, b.y + joy.y * radius))
            }
        }
        // zona izquierda: mover. zona derecha: mirar.
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.5f).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val origin = down.position
                    joyBase = origin
                    do {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        val d = ch.position - origin
                        val len = d.getDistance()
                        joy = if (len > radius) Offset(d.x / len, d.y / len) else Offset(d.x / radius, d.y / radius)
                        ch.consume()
                    } while (ev.changes.any { it.pressed })
                    joy = Offset.Zero; joyBase = null
                }
            },
        )
        Box(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f).pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var lastPos = down.position
                    do {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        look.acc += ch.position.x - lastPos.x
                        lastPos = ch.position
                        ch.consume()
                    } while (ev.changes.any { it.pressed })
                }
            },
        )
        Text(
            "PAWB  +${eng.session}", color = Color(0xFFCFC8B8), fontFamily = FontFamily.Monospace, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
        Text(
            "SALIR", color = Color(0xFFCFC8B8), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).clickable { if (scareT < 0f) onFinish(false) }.padding(16.dp),
        )
        if (hintT > 0f) Text(
            "${variant.name}\nIzquierda: moverte · Derecha: mirar\nBusca los cofres dorados. Algo te persigue.", color = Color(0xFFCFC8B8).copy(alpha = min(1f, hintT)),
            fontFamily = FontFamily.Monospace, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
        if (toastT > 0f) Text(
            toast, color = Color(0xFFFFE082).copy(alpha = min(1f, toastT)), fontFamily = FontFamily.Monospace, fontSize = 26.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center),
        )
        if (scareT >= 0f) ScareOverlay(scareT)
    }
}

/** El screamer: la cara del monstruo llena la pantalla, con sacudidas, destellos rojos y estatica. */
@Composable
private fun ScareOverlay(t: Float) {
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        val rnd = Random((t * 60f).toInt() * 31 + 7)
        val hh = size.height; val cx = size.width / 2f; val cy = hh * 0.52f
        withTransform({
            translate((rnd.nextFloat() - 0.5f) * 50f, (rnd.nextFloat() - 0.5f) * 36f)
            scale(1f + 0.3f * min(1f, t * 3f), 1f + 0.3f * min(1f, t * 3f), Offset(cx, cy))
        }) {
            drawRect(Color(0xFF1C0000))
            drawOval(Color(0xFFD9D3C9), Offset(cx - hh * 0.40f, cy - hh * 0.56f), Size(hh * 0.80f, hh * 1.12f))
            drawOval(Color.Black, Offset(cx - hh * 0.30f, cy - hh * 0.22f), Size(hh * 0.21f, hh * 0.30f))
            drawOval(Color.Black, Offset(cx + hh * 0.09f, cy - hh * 0.22f), Size(hh * 0.21f, hh * 0.30f))
            drawCircle(Color(0xFFFF2A1E), hh * 0.025f, Offset(cx - hh * 0.195f, cy - hh * 0.08f))
            drawCircle(Color(0xFFFF2A1E), hh * 0.025f, Offset(cx + hh * 0.195f, cy - hh * 0.08f))
            val mouth = Path().apply {
                moveTo(cx - hh * 0.26f, cy + hh * 0.22f)
                for (i in 1..10) lineTo(cx - hh * 0.26f + hh * 0.052f * i, cy + hh * (if (i % 2 == 0) 0.22f else 0.30f))
                lineTo(cx + hh * 0.26f, cy + hh * 0.46f); lineTo(cx - hh * 0.26f, cy + hh * 0.46f); close()
            }
            drawPath(mouth, Color.Black)
        }
        if ((t * 22f).toInt() % 2 == 0) drawRect(Color.Red.copy(alpha = 0.28f))
        if ((t * 11f).toInt() % 3 == 0) drawRect(Color.White.copy(alpha = 0.18f))
        repeat(260) { drawRect(Color.White.copy(alpha = 0.5f), Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * hh), Size(4f, 4f)) }
        repeat(8) { drawRect(Color.Black.copy(alpha = 0.7f), Offset(0f, rnd.nextFloat() * hh), Size(size.width, 6f + rnd.nextFloat() * 14f)) }
    }
}

/** Glitch de 1 s de la pantalla de inicio cuando intentas entrar al minijuego estando bloqueado. */
@Composable
fun MiniGlitchOverlay() {
    val at = MiniNav.glitchAt.longValue
    if (at != 0L) GlitchBody(at)
}

@Composable
private fun GlitchBody(at: Long) {
    var t by remember(at) { mutableFloatStateOf(0f) }
    LaunchedEffect(at) {
        MiniNav.glitchOn.value = true
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val n = androidx.compose.runtime.withFrameNanos { it }
            t = (n - start) / 1e9f
            val rr = Random((t * 40f).toInt() + 3)
            MiniNav.shakeX.floatValue = (rr.nextFloat() - 0.5f) * 36f * (1f - 0.5f * t)
            MiniNav.shakeY.floatValue = (rr.nextFloat() - 0.5f) * 14f
            if (t >= 1f) break
        }
        MiniNav.shakeX.floatValue = 0f; MiniNav.shakeY.floatValue = 0f
        MiniNav.glitchOn.value = false
        MiniNav.glitchAt.longValue = 0L
    }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        val rnd = Random((t * 30f).toInt() * 7919 + 1)
        val cols = listOf(Color(0xFF00FFFF), Color(0xFFFF00AA), Color.White, Color.Black)
        repeat(18) {
            val y = rnd.nextFloat() * size.height; val hh = 4f + rnd.nextFloat() * 46f
            drawRect(cols[rnd.nextInt(cols.size)].copy(alpha = 0.32f), Offset((rnd.nextFloat() - 0.5f) * 120f, y), Size(size.width, hh))
        }
        repeat(3) { drawRect(Color.Black.copy(alpha = 0.92f), Offset(0f, rnd.nextFloat() * size.height), Size(size.width, 8f + rnd.nextFloat() * 30f)) }
        var y = 0f
        while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, y), Size(size.width, 2f)); y += 6f }
        repeat(220) { drawRect(Color.White.copy(alpha = 0.45f), Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height), Size(3f, 3f)) }
        if ((t * 14f).toInt() % 2 == 0) drawRect(Color.White.copy(alpha = 0.10f))
    }
}

/** withFrameNanos con su propio nombre para no chocar con otros imports del archivo. */
private suspend fun withFrameNanosCompat(block: (Long) -> Unit) { androidx.compose.runtime.withFrameNanos { block(it) } }
