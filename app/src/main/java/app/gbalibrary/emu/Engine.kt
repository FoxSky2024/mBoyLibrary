package app.gbalibrary.emu

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import app.gbalibrary.data.SaveType
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.locks.ReentrantLock

/**
 * Hilo de emulacion: carga la ROM, ejecuta un frame por iteracion, vuelca audio a un
 * AudioTrack (la escritura bloqueante marca el ritmo a 1x) y procesa comandos de la UI.
 */
class Engine(
    private val romProvider: () -> ByteArray?,
    private val savePath: String,
    private val autoStatePath: String,
    private val onError: (String) -> Unit,
    /** -1 = detectar por la ROM; 0..5 = forzar ese tipo de guardado (ver SaveType). */
    private val saveOverride: Int = -1,
    /** 0 = detectar por la ROM, 1 = forzar RTC, 2 = sin RTC. */
    private val rtcMode: Int = 0,
    /** Se llama una vez con lo que se uso al cargar: (tipo de guardado, RTC). */
    private val onDetected: (Int, Boolean) -> Unit = { _, _ -> },
) {
    companion object {
        const val SAMPLE_RATE = 44100
        const val SOURCE_TOUCH = 0
        const val SOURCE_HARDWARE = 1 // mando 1
        const val SOURCE_HARDWARE2 = 2 // mando 2
        const val SOURCE_KEYBOARD = 3
        /** Cada control tactil usa su propia ranura (4..63) para que dos botones A no se pisen. */
        const val SOURCE_AXIS = 4 // stick izquierdo / cruceta (hat) del mando
        const val SOURCE_WIDGET_BASE = 5
        const val SOURCE_COUNT = 64
        private const val FRAME_NANOS = 16_743_000L // 59.7275 fps

        // mGBA tiene un unico nucleo global: un solo Engine puede usarlo a la vez.
        private val coreLock = ReentrantLock()
    }

    private val keysBySource = AtomicIntegerArray(SOURCE_COUNT)
    private val commands = ConcurrentLinkedQueue<() -> Unit>()

    @Volatile
    private var running = true

    @Volatile
    var paused = false

    @Volatile
    var speed = 1

    /** Volumen 0..100 y silencio (se aplican al PCM antes de escribirlo al AudioTrack). */
    @Volatile
    var volume = 100

    @Volatile
    var muted = false

    // Rendimiento (se pueden cambiar en vivo desde la UI)
    /** 30 = dibuja un cuadro de cada dos, 60 = normal, 0 = sin limite de velocidad. */
    @Volatile
    var fpsLimit = 60

    /** Salta el dibujo de cuadros cuando el telefono no alcanza (solo dibujo, la emulacion sigue). */
    @Volatile
    var frameSkip = false

    /** ON: el ritmo lo marca el audio (como siempre). OFF: temporizador, el audio no bloquea. */
    @Volatile
    var audioSync = true

    /** Cuadros por segundo reales de emulacion (se actualiza cada segundo). */
    @Volatile
    var fps = 0

    fun start() {
        Thread({ loop() }, "gba-emu").start()
    }

    // ---- Entrada (se puede llamar desde cualquier hilo) ----

    fun setKey(source: Int, mask: Int, down: Boolean) {
        while (true) {
            val old = keysBySource.get(source)
            val next = if (down) old or mask else old and mask.inv()
            if (keysBySource.compareAndSet(source, old, next)) return
        }
    }

    fun setDirections(source: Int, dirs: Int) {
        while (true) {
            val old = keysBySource.get(source)
            val next = (old and GbaKey.DIRECTIONS.inv()) or dirs
            if (keysBySource.compareAndSet(source, old, next)) return
        }
    }

    fun releaseAll() {
        for (i in 0 until SOURCE_COUNT) keysBySource.set(i, 0)
    }

    private fun allKeys(): Int {
        var k = 0
        for (i in 0 until SOURCE_COUNT) k = k or keysBySource.get(i)
        return k
    }

    /** Tipo de guardado detectado en la ROM cargada (ver SaveType). */
    @Volatile
    var saveType = 0

    // ---- Comandos que se ejecutan en el hilo de emulacion ----

    fun post(command: () -> Unit) {
        commands.add(command)
    }

    fun saveState(path: String, done: (Boolean) -> Unit) = post { done(Native.saveState(path)) }

    fun loadState(path: String, done: (Boolean) -> Unit) =
        post { done(File(path).exists() && Native.loadState(path)) }

    fun reset() = post {
        Native.reset()
        File(autoStatePath).delete()
    }

    fun autoSave() = post { Native.saveState(autoStatePath) }

    /**
     * Guarda el estado automatico y espera (maximo [waitMs]) a que termine. Se usa al salir de la
     * pantalla: asi el progreso queda en disco antes de que Android pueda congelar o cerrar la app.
     */
    fun autoSaveSync(waitMs: Long = 1500L) {
        val latch = java.util.concurrent.CountDownLatch(1)
        post {
            try { Native.saveState(autoStatePath) } finally { latch.countDown() }
        }
        try { latch.await(waitMs, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { /* ignorado */ }
    }

    /** Guarda el estado automatico y detiene el hilo. */
    fun shutdown() = post {
        Native.saveState(autoStatePath)
        running = false
    }

    /** Captura del ultimo frame (ARGB) pedida desde la UI. */
    fun screenshot(done: (IntArray?) -> Unit) = post {
        done(try { Native.screenshot() } catch (t: Throwable) { null })
    }

    // ---- Bucle principal ----

    private fun createTrack(): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val builder = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBytes, 12288))
            .setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        val track = builder.build()
        track.setVolume(1.0f)
        return track
    }

    /**
     * Escribe todo el audio. Si el sistema mato la pista (cambio de salida, auriculares, Bluetooth,
     * servidor de audio reiniciado) devuelve false para que el bucle la recree: antes el sonido
     * se quedaba muerto hasta cerrar el juego.
     */
    private fun writeAudio(t: AudioTrack, buf: ShortArray, count: Int, blocking: Boolean): Boolean {
        var off = 0
        var stalls = 0
        while (off < count) {
            val w = t.write(buf, off, count - off, if (blocking) AudioTrack.WRITE_BLOCKING else AudioTrack.WRITE_NON_BLOCKING)
            if (w < 0) return false
            if (w == 0) {
                if (!blocking || ++stalls > 3) return true
                continue
            }
            off += w
        }
        return true
    }

    private fun rebuildTrack(old: AudioTrack): AudioTrack {
        Log.w("Engine", "AudioTrack invalida: se recrea")
        try { old.release() } catch (e: Throwable) { /* ignorado */ }
        val n = createTrack()
        n.play()
        return n
    }

    private fun loop() {
        coreLock.lock()
        var track: AudioTrack? = null
        try {
            val rom = romProvider()
            if (rom == null || rom.isEmpty()) {
                onError("No se pudo leer la ROM")
                return
            }
            val found = SaveType.analyze(rom, File(savePath).length())
            saveType = if (saveOverride >= 0) saveOverride else found.type
            val rtc = when (rtcMode) {
                1 -> true
                2 -> false
                else -> found.rtc
            }
            Log.i("Engine", "Guardado: ${SaveType.label(saveType)} (override=$saveOverride), RTC=$rtc (modo $rtcMode)")
            if (!Native.load(rom, savePath, saveType, rtc)) {
                onError(Native.lastError.ifEmpty { "mGBA no pudo cargar esta ROM" })
                return
            }
            try {
                onDetected(saveType, rtc)
            } catch (t: Throwable) {
                Log.w("Engine", "onDetected", t)
            }
            Native.setDsLayout(app.gbalibrary.data.Settings.dsLayout.intValue)
            if (File(autoStatePath).exists()) {
                Native.loadState(autoStatePath)
            }

            var t = createTrack()
            track = t
            t.play()
            var playing = true
            val audio = ShortArray(4096)
            var next = System.nanoTime()
            var frameCounter = 0L
            var lagging = false
            var skipStreak = 0
            var fpsCount = 0
            var fpsStamp = System.nanoTime()

            while (true) {
                while (true) {
                    val c = commands.poll() ?: break
                    c()
                }
                if (!running) break

                if (paused) {
                    if (playing) {
                        t.pause()
                        playing = false
                    }
                    Thread.sleep(16)
                    next = System.nanoTime()
                    continue
                }
                if (!playing) {
                    t.play()
                    playing = true
                }

                val sp = speed
                frameCounter++
                // Turbo A / B: alterna el boton cada 2 cuadros mientras se mantiene pulsado.
                var keys = allKeys()
                if (keys and (GbaKey.TURBO_A or GbaKey.TURBO_B) != 0) {
                    val on = (frameCounter / 2) % 2L == 0L
                    if (on && keys and GbaKey.TURBO_A != 0) keys = keys or GbaKey.A
                    if (on && keys and GbaKey.TURBO_B != 0) keys = keys or GbaKey.B
                }
                Native.setKeys(keys)
                val limit = fpsLimit
                var render = sp == 1 || frameCounter % sp == 0L
                if (render && limit == 30 && frameCounter % 2L == 1L) render = false
                if (render && frameSkip && lagging && skipStreak < 2) {
                    render = false
                    skipStreak++
                } else {
                    skipStreak = 0
                }
                val frameStart = System.nanoTime()
                val frames = Native.step(audio, render)

                lagging = System.nanoTime() - frameStart > FRAME_NANOS + FRAME_NANOS / 10
                if (Native.isDs) DsEmulator.onFrameTime((System.nanoTime() - frameStart) / 1_000_000f)
                fpsCount++
                val nowNs = System.nanoTime()
                if (nowNs - fpsStamp >= 1_000_000_000L) {
                    fps = fpsCount
                    fpsCount = 0
                    fpsStamp = nowNs
                }

                val unlimited = limit == 0 && sp == 1
                if (sp == 1 && audioSync && !unlimited) {
                    if (frames > 0) {
                        val gain = if (muted) 0 else volume.coerceIn(0, 100)
                        if (gain < 100) {
                            for (i in 0 until frames * 2) {
                                audio[i] = ((audio[i] * gain) / 100).toShort()
                            }
                        }
                        if (!writeAudio(t, audio, frames * 2, true)) {
                            t = rebuildTrack(t)
                            track = t
                        }
                    }
                    next = System.nanoTime()
                } else if (sp == 1) {
                    // Sin sincronizacion de audio (o sin limite): el audio no bloquea.
                    if (frames > 0) {
                        val gain = if (muted) 0 else volume.coerceIn(0, 100)
                        for (i in 0 until frames * 2) audio[i] = ((audio[i] * gain) / 100).toShort()
                        if (!writeAudio(t, audio, frames * 2, false)) {
                            t = rebuildTrack(t)
                            track = t
                        }
                    }
                    if (unlimited) {
                        next = System.nanoTime()
                    } else {
                        next += FRAME_NANOS
                        val wait = next - System.nanoTime()
                        if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                        else if (wait < -100_000_000L) next = System.nanoTime()
                    }
                } else {
                    next += FRAME_NANOS / sp
                    val wait = next - System.nanoTime()
                    if (wait > 0) {
                        Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                    } else if (wait < -100_000_000L) {
                        next = System.nanoTime()
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("Engine", "Error en el bucle de emulacion", t)
            onError("Error del emulador: ${t.message}")
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (t: Throwable) {
                Log.w("Engine", "AudioTrack", t)
            }
            try {
                Native.unload()
            } catch (t: Throwable) {
                Log.w("Engine", "unload", t)
            }
            coreLock.unlock()
        }
    }
}
