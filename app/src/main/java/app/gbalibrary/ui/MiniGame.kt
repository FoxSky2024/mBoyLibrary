package app.gbalibrary.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Pawb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Visibilidad del minijuego (pantalla completa por encima de todo). */
object MiniNav { val show = mutableStateOf(false) }

/** Estado diario guardado en el dispositivo: 3 cofres por dia, aleatorios cada dia. */
object MiniStore {
    const val PER_DAY = 3
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("minigame", Context.MODE_PRIVATE)
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun ensureDay(ctx: Context) {
        val p = prefs(ctx)
        if (p.getString("day", "") != today()) p.edit().putString("day", today()).putInt("mask", 0).putInt("total", 0).apply()
    }
    fun mask(ctx: Context): Int { ensureDay(ctx); return prefs(ctx).getInt("mask", 0) }
    fun total(ctx: Context): Int { ensureDay(ctx); return prefs(ctx).getInt("total", 0) }
    fun found(ctx: Context) = Integer.bitCount(mask(ctx))
    fun left(ctx: Context) = PER_DAY - found(ctx)
    /** Recompensa de un cofre: 100 a 200 Pawb y un 0.001 % de ganar 1000. */
    fun roll(rnd: Random = Random.Default): Int = if (rnd.nextDouble() < 0.00001) 1000 else rnd.nextInt(100, 201)
    fun collect(ctx: Context, idx: Int): Int {
        ensureDay(ctx)
        val p = prefs(ctx)
        val m = p.getInt("mask", 0)
        if (m and (1 shl idx) != 0) return 0
        val r = roll()
        p.edit().putInt("mask", m or (1 shl idx)).putInt("total", p.getInt("total", 0) + r).apply()
        Pawb.earn(r)
        return r
    }
}

// ------------------------------------------------------------------------------------------ mundo y motor (raycaster estilo PS1)

private const val TEX = 32
private const val CHEST = 16
private val BAYER = intArrayOf(0, 4, 1, 5, 6, 2, 7, 3, 1, 5, 0, 4, 7, 3, 6, 2)

private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

private fun makeTex(kind: Int, seed: Int): IntArray {
    val t = IntArray(TEX * TEX)
    val r = Random(seed)
    for (y in 0 until TEX) for (x in 0 until TEX) {
        val n = r.nextInt(-14, 15)
        t[y * TEX + x] = when (kind) {
            0 -> { val v = 118 + n - (if ((x * 7 + y * 13) % 17 == 0) 25 else 0); rgb(v, v - 2, v - 7) }                 // concreto manchado
            1 -> {                                                                                                          // ladrillo
                val row = y / 8; val bx = (x + (if (row % 2 == 0) 0 else 8)) % 16
                if (y % 8 == 0 || bx == 0) rgb(78, 76, 72) else { val v = 120 + n; rgb(v + 22, v - 30, v - 42) }
            }
            2 -> {                                                                                                          // azulejo sucio
                if (x % 16 == 0 || y % 16 == 0) rgb(55, 60, 58) else { val v = 100 + n - (if (x % 9 == 3 && y > 6) 26 else 0); rgb(v - 12, v + 8, v + 2) }
            }
            3 -> { val v = (if (((x / 16) + (y / 16)) % 2 == 0) 78 else 58) + n / 2; rgb(v, v - 3, v - 8) }              // suelo
            else -> { val v = 42 + n / 2; rgb(v, v, v + 2) }                                                                // techo
        }
    }
    return t
}

/** Cofre de 16x16 (0 = transparente). */
private fun makeChest(): IntArray {
    val t = IntArray(CHEST * CHEST)
    val outline = rgb(30, 16, 8)
    for (y in 0 until CHEST) for (x in 0 until CHEST) {
        if (x < 1 || x > 14 || y < 3 || y > 14) continue
        var c = when {
            y <= 7 -> rgb(150, 88, 40)                       // tapa
            else -> rgb(112, 62, 28)                         // cuerpo
        }
        if (y == 10 || y == 12) c = rgb(88, 48, 22)          // vetas
        if (x == 1 || x == 14 || y == 3 || y == 14 || y == 8) c = outline
        if (y <= 4 && (x <= 2 || x >= 13)) c = 0              // esquinas redondeadas de la tapa
        if (c != 0 && (x == 7 || x == 8)) c = rgb(236, 190, 60) // banda dorada
        if (x in 6..9 && y in 7..10) c = rgb(255, 224, 110)    // cerradura
        t[y * CHEST + x] = c
    }
    return t
}

private class MgEngine(val ctx: Context, val w: Int, val h: Int) {
    val mw = 36; val mh = 36
    val cells = IntArray(mw * mh) { 1 }
    private val rooms = ArrayList<IntArray>()
    val chests = ArrayList<IntArray>()
    var px = 2f; var py = 2f; var ang = 0f
    var time = 0f
    var found by mutableIntStateOf(0)
    var mask = 0

    private val wallTex = arrayOf(makeTex(0, 1), makeTex(1, 2), makeTex(2, 3))
    private val floorTex = makeTex(3, 4)
    private val ceilTex = makeTex(4, 5)
    private val chestTex = makeChest()
    private val zbuf = FloatArray(w)
    private val flash = FloatArray(w)
    private val vig = IntArray(w * h)
    val pix = IntArray(w * h)
    private var rs = 0x2545F491

    init {
        val r = Random(20240229)
        var tries = 0
        while (rooms.size < 9 && tries < 400) {
            tries++
            val rw = r.nextInt(4, 8); val rh = r.nextInt(4, 8)
            val rx = r.nextInt(1, mw - rw - 1); val ry = r.nextInt(1, mh - rh - 1)
            val ok = rooms.none { q -> rx < q[0] + q[2] + 1 && rx + rw + 1 > q[0] && ry < q[1] + q[3] + 1 && ry + rh + 1 > q[1] }
            if (ok) rooms.add(intArrayOf(rx, ry, rw, rh))
        }
        for (q in rooms) for (y in q[1] until q[1] + q[3]) for (x in q[0] until q[0] + q[2]) cells[y * mw + x] = 0
        for (i in 1 until rooms.size) link(rooms[i - 1], rooms[i])
        repeat(4) { val a = r.nextInt(rooms.size); val b = r.nextInt(rooms.size); if (a != b) link(rooms[a], rooms[b]) }
        for (i in 1 until rooms.size) {
            val q = rooms[i]
            if (q[2] >= 5 && q[3] >= 5) cells[(q[1] + 2 + r.nextInt(q[3] - 4)) * mw + (q[0] + 2 + r.nextInt(q[2] - 4))] = 1
        }
        for (i in cells.indices) if (cells[i] != 0) cells[i] = 1 + ((i % mw) / 5 + (i / mw) / 7) % 3
        val s = rooms[0]
        px = s[0] + s[2] / 2 + 0.5f; py = s[1] + s[3] / 2 + 0.5f
        // cofres del dia: 3 lugares al azar, en habitaciones distintas, distintos cada dia
        val dr = Random(MiniStore.today().hashCode().toLong() * 7919L + 13L)
        for (q in rooms.drop(1).shuffled(dr)) {
            if (chests.size >= MiniStore.PER_DAY) break
            val x = q[0] + dr.nextInt(q[2]); val y = q[1] + dr.nextInt(q[3])
            if (cells[y * mw + x] == 0) chests.add(intArrayOf(x, y))
        }
        var guard = 0
        while (chests.size < MiniStore.PER_DAY && guard++ < 800) {
            val x = dr.nextInt(1, mw - 1); val y = dr.nextInt(1, mh - 1)
            if (cells[y * mw + x] == 0 && abs(x - px) + abs(y - py) > 6 && chests.none { abs(it[0] - x) + abs(it[1] - y) < 5 }) chests.add(intArrayOf(x, y))
        }
        mask = MiniStore.mask(ctx); found = Integer.bitCount(mask)
        for (i in vig.indices) {
            val nx = ((i % w) - w / 2f) / (w / 2f); val ny = ((i / w) - h / 2f) / (h / 2f)
            vig[i] = (256 * (1f - 0.62f * (nx * nx + ny * ny) / 2f).coerceIn(0.28f, 1f)).toInt()
        }
    }

    private fun link(a: IntArray, b: IntArray) {
        val ax = a[0] + a[2] / 2; val ay = a[1] + a[3] / 2; val bx = b[0] + b[2] / 2; val by = b[1] + b[3] / 2
        for (x in min(ax, bx)..max(ax, bx)) cells[ay * mw + x] = 0
        for (y in min(ay, by)..max(ay, by)) cells[y * mw + bx] = 0
    }

    private fun solid(x: Float, y: Float): Boolean {
        val cx = x.toInt(); val cy = y.toInt()
        if (cx < 0 || cy < 0 || cx >= mw || cy >= mh) return true
        return cells[cy * mw + cx] != 0
    }
    private fun blocked(x: Float, y: Float): Boolean {
        val r = 0.22f
        return solid(x - r, y - r) || solid(x + r, y - r) || solid(x - r, y + r) || solid(x + r, y + r)
    }

    /** Avanza el juego. Devuelve los Pawb de un cofre recien abierto, o -1. */
    fun update(dt: Float, joyX: Float, joyY: Float, look: Float): Int {
        time += dt
        ang += look * 0.0065f
        val dx = cos(ang); val dy = sin(ang)
        val fwd = -joyY; val side = joyX
        val sp = 2.3f * dt
        val mx = (dx * fwd + (-dy) * side) * sp
        val my = (dy * fwd + dx * side) * sp
        if (!blocked(px + mx, py)) px += mx
        if (!blocked(px, py + my)) py += my
        for (i in chests.indices) {
            if (mask and (1 shl i) != 0) continue
            val ddx = chests[i][0] + 0.5f - px; val ddy = chests[i][1] + 0.5f - py
            if (ddx * ddx + ddy * ddy < 0.75f) {
                val got = MiniStore.collect(ctx, i)
                mask = mask or (1 shl i); found = Integer.bitCount(mask)
                return got
            }
        }
        return -1
    }

    /** Distancia al cofre pendiente mas cercano (para el ruido de "radio"). */
    private fun nearest(): Float {
        var best = 99f
        for (i in chests.indices) {
            if (mask and (1 shl i) != 0) continue
            val ddx = chests[i][0] + 0.5f - px; val ddy = chests[i][1] + 0.5f - py
            best = min(best, kotlin.math.sqrt(ddx * ddx + ddy * ddy))
        }
        return best
    }

    private fun shade(c: Int, light: Float, fog: Float): Int {
        val fr = 22; val fg = 24; val fb = 30
        var r = ((c shr 16) and 255) * light; var g = ((c shr 8) and 255) * light; var b = (c and 255) * light
        r += (fr - r) * fog; g += (fg - g) * fog; b += (fb - b) * fog
        return rgb(r.toInt(), g.toInt(), b.toInt())
    }

    fun render() {
        val dirX = cos(ang); val dirY = sin(ang)
        val planeX = -dirY * 0.66f; val planeY = dirX * 0.66f
        val flick = 1f + 0.05f * sin(time * 23f) + (if (sin(time * 7.3f) > 0.985f) -0.3f else 0f)
        for (x in 0 until w) { val q = 1f - abs(x - w / 2f) / (w * 0.55f); flash[x] = max(0f, q) * max(0f, q) * flick }
        val half = h / 2
        // suelo y techo
        for (y in half + 1 until h) {
            val rowDist = half.toFloat() / (y - half)
            val fog = 1f - exp(-0.19f * rowDist)
            val att = 1f / (1f + 0.07f * rowDist * rowDist)
            val rx0 = dirX - planeX; val ry0 = dirY - planeY
            val sx = rowDist * (2f * planeX) / w; val sy = rowDist * (2f * planeY) / w
            var fx = px + rowDist * rx0; var fy = py + rowDist * ry0
            val yc = h - y - 1
            for (x in 0 until w) {
                val cx = floor(fx); val cy = floor(fy)
                val tx = ((fx - cx) * TEX).toInt() and (TEX - 1); val ty = ((fy - cy) * TEX).toInt() and (TEX - 1)
                val light = 0.2f + 1.25f * flash[x] * att
                pix[y * w + x] = shade(floorTex[ty * TEX + tx], light, fog)
                pix[yc * w + x] = shade(ceilTex[ty * TEX + tx], light * 0.7f, min(1f, fog + 0.1f))
                fx += sx; fy += sy
            }
        }
        // paredes
        for (x in 0 until w) {
            val camX = 2f * x / w - 1f
            val rayX = dirX + planeX * camX; val rayY = dirY + planeY * camX
            var mapX = px.toInt(); var mapY = py.toInt()
            val ddx = if (rayX == 0f) 1e30f else abs(1f / rayX); val ddy = if (rayY == 0f) 1e30f else abs(1f / rayY)
            val stepX: Int; val stepY: Int; var sideX: Float; var sideY: Float
            if (rayX < 0) { stepX = -1; sideX = (px - mapX) * ddx } else { stepX = 1; sideX = (mapX + 1f - px) * ddx }
            if (rayY < 0) { stepY = -1; sideY = (py - mapY) * ddy } else { stepY = 1; sideY = (mapY + 1f - py) * ddy }
            var side = 0; var hit = 0; var guard = 0
            while (hit == 0 && guard++ < 64) {
                if (sideX < sideY) { sideX += ddx; mapX += stepX; side = 0 } else { sideY += ddy; mapY += stepY; side = 1 }
                hit = if (mapX < 0 || mapY < 0 || mapX >= mw || mapY >= mh) 1 else cells[mapY * mw + mapX]
            }
            val perp = max(0.05f, if (side == 0) sideX - ddx else sideY - ddy)
            zbuf[x] = perp
            var wx = if (side == 0) py + perp * rayY else px + perp * rayX
            wx -= floor(wx)
            var texX = (wx * TEX).toInt().coerceIn(0, TEX - 1)
            if ((side == 0 && rayX > 0) || (side == 1 && rayY < 0)) texX = TEX - 1 - texX
            val lineH = max(1, (h / perp).toInt())
            val start = half - lineH / 2
            val end = half + lineH / 2
            val tex = wallTex[(hit - 1).coerceIn(0, 2)]
            val fog = 1f - exp(-0.19f * perp)
            val light = (0.2f + 1.25f * flash[x] / (1f + 0.07f * perp * perp)) * (if (side == 1) 0.78f else 1f)
            for (y in max(0, start) until min(h, end)) {
                val texY = ((y - start) * TEX / lineH) and (TEX - 1)
                pix[y * w + x] = shade(tex[texY * TEX + texX], light, fog)
            }
        }
        // cofres (billboards)
        val inv = 1f / (planeX * dirY - dirX * planeY)
        val order = chests.indices.filter { mask and (1 shl it) == 0 }.sortedByDescending {
            val ex = chests[it][0] + 0.5f - px; val ey = chests[it][1] + 0.5f - py; ex * ex + ey * ey
        }
        for (i in order) {
            val sx = chests[i][0] + 0.5f - px; val sy = chests[i][1] + 0.5f - py
            val tx = inv * (dirY * sx - dirX * sy); val ty = inv * (-planeY * sx + planeX * sy)
            if (ty < 0.25f) continue
            val scrX = (w / 2f * (1f + tx / ty)).toInt()
            val unit = (h / ty).toInt()
            val size = max(3, (unit * 0.42f).toInt())
            val floorY = half + unit / 2
            val top = floorY - size
            val left = scrX - size / 2
            val fog = 1f - exp(-0.19f * ty)
            val pulse = 0.5f + 0.5f * sin(time * 3f)
            for (xx in left until left + size) {
                if (xx < 0 || xx >= w || ty >= zbuf[xx]) continue
                val texX = ((xx - left) * CHEST / size).coerceIn(0, CHEST - 1)
                val light = 0.55f + 1.1f * flash[xx] / (1f + 0.07f * ty * ty) + 0.25f * pulse
                for (yy in max(0, top) until min(h, floorY)) {
                    val texY = ((yy - top) * CHEST / size).coerceIn(0, CHEST - 1)
                    val c = chestTex[texY * CHEST + texX]
                    if (c != 0) pix[yy * w + xx] = shade(c, light, fog * 0.7f)
                }
            }
            // haz de luz dorado, solo cuando estas relativamente cerca
            if (ty < 7f) {
                val a0 = 0.32f * (1f - ty / 7f)
                for (xx in scrX - 1..scrX + 1) {
                    if (xx < 0 || xx >= w || ty >= zbuf[xx]) continue
                    val bt = top - size * 3
                    for (yy in max(0, bt) until min(h, top)) {
                        val a = a0 * (yy - bt).toFloat() / (size * 3f)
                        val c = pix[yy * w + xx]
                        val rr = ((c shr 16) and 255) + ((255 - ((c shr 16) and 255)) * a).toInt()
                        val gg = ((c shr 8) and 255) + ((210 - ((c shr 8) and 255)) * a).toInt()
                        val bb = (c and 255) + ((90 - (c and 255)) * a).toInt()
                        pix[yy * w + xx] = rgb(rr, gg, bb)
                    }
                }
            }
        }
        // acabado PS1: vineta, color de 15 bits con tramado ordenado y estatica de radio cerca de un cofre
        val near = nearest()
        val thr = if (near < 6f) ((6f - near) / 6f * 16f).toInt() else 0
        for (i in 0 until w * h) {
            val x = i % w; val y = i / w
            val c = pix[i]
            val v = vig[i]
            val d = BAYER[(y and 3) * 4 + (x and 3)]
            var r = min(255, (((c shr 16) and 255) * v shr 8) + d) and 0xF8
            var g = min(255, (((c shr 8) and 255) * v shr 8) + d) and 0xF8
            var b = min(255, ((c and 255) * v shr 8) + d) and 0xF8
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
    BackHandler { if (phase == 1) phase = 2 else onExit() }
    when (phase) {
        0 -> LoadingScreen(2000, onDone = { phase = if (MiniStore.left(ctx) <= 0) 2 else 1 })
        1 -> MiniGamePlay(onFinish = { phase = 2 })
        else -> {
            val total = MiniStore.total(ctx)
            val left = MiniStore.left(ctx)
            val msg = (if (total > 0) "Encontraste $total Pawb hoy" else "Hoy no has encontrado cofres") +
                (if (left <= 0) "\nYa abriste los ${MiniStore.PER_DAY} cofres. Vuelve mañana" else "\nTe quedan $left cofres por encontrar")
            LoadingScreen(2800, onDone = onExit, message = msg)
        }
    }
}

@Composable
private fun MiniGamePlay(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val wPx = with(dens) { maxWidth.toPx() }; val hPx = with(dens) { maxHeight.toPx() }
        val rw = 176
        val rh = (rw * hPx / wPx).roundToInt().coerceIn(80, 130)
        val eng = remember { MgEngine(ctx, rw, rh) }
        val bmp = remember { Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888) }
        val img = remember { bmp.asImageBitmap() }
        var tick by remember { mutableLongStateOf(0L) }
        var joy by remember { mutableStateOf(Offset.Zero) }
        var joyBase by remember { mutableStateOf<Offset?>(null) }
        val look = remember { LookAcc() }
        var toast by remember { mutableStateOf("") }
        var toastT by remember { mutableFloatStateOf(0f) }
        var hintT by remember { mutableFloatStateOf(7f) }
        val radius = with(dens) { 56.dp.toPx() }

        LaunchedEffect(eng) {
            var last = 0L
            var finishT = -1f
            while (true) {
                withFrameNanosCompat { t ->
                    val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceAtMost(0.05f)
                    last = t
                    val l = look.acc; look.acc = 0f
                    val ev = eng.update(dt, joy.x, joy.y, l)
                    if (ev >= 0) {
                        toast = if (ev >= 1000) "¡¡ +$ev Pawb !!" else "+$ev Pawb"; toastT = 2.2f
                        if (eng.found >= MiniStore.PER_DAY) finishT = 2.0f
                    }
                    if (finishT >= 0f) { finishT -= dt; if (finishT <= 0f) { finishT = -1f; onFinish() } }
                    if (toastT > 0f) toastT -= dt
                    if (hintT > 0f) hintT -= dt
                    eng.render()
                    bmp.setPixels(eng.pix, 0, rw, 0, 0, rw, rh)
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
        // interfaz minima
        Text(
            "COFRES  ${eng.found}/${MiniStore.PER_DAY}", color = Color(0xFFCFC8B8), fontFamily = FontFamily.Monospace, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
        Text(
            "SALIR", color = Color(0xFFCFC8B8), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).clickable { onFinish() }.padding(16.dp),
        )
        if (hintT > 0f) Text(
            "Izquierda: moverte · Derecha: mirar\nBusca los cofres dorados en la niebla", color = Color(0xFFCFC8B8).copy(alpha = min(1f, hintT)),
            fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
        if (toastT > 0f) Text(
            toast, color = Color(0xFFFFE082).copy(alpha = min(1f, toastT)), fontFamily = FontFamily.Monospace, fontSize = 26.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** withFrameNanos con su propio nombre para no chocar con otros imports del archivo. */
private suspend fun withFrameNanosCompat(block: (Long) -> Unit) { androidx.compose.runtime.withFrameNanos { block(it) } }
