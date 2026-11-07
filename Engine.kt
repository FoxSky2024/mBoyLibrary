package app.gbalibrary.emu

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
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
) {
    companion object {
        const val SAMPLE_RATE = 44100
        const val SOURCE_TOUCH = 0
        const val SOURCE_HARDWARE = 1
        private const val FRAME_NANOS = 16_743_000L // 59.7275 fps

        // mGBA tiene un unico nucleo global: un solo Engine puede usarlo a la vez.
        private val coreLock = ReentrantLock()
    }

    private val keysBySource = AtomicIntegerArray(2)
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
        keysBySource.set(SOURCE_TOUCH, 0)
        keysBySource.set(SOURCE_HARDWARE, 0)
    }

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

    private fun loop() {
        coreLock.lock()
        var track: AudioTrack? = null
        try {
            val rom = romProvider()
            if (rom == null || rom.isEmpty()) {
                onError("No se pudo leer la ROM")
                return
            }
            if (!Native.load(rom, savePath)) {
                onError("mGBA no pudo cargar esta ROM")
                return
            }
            if (File(autoStatePath).exists()) {
                Native.loadState(autoStatePath)
            }

            val t = createTrack()
            track = t
            t.play()
            var playing = true
            val audio = ShortArray(4096)
            var next = System.nanoTime()
            var frameCounter = 0L
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

                Native.setKeys(keysBySource.get(SOURCE_TOUCH) or keysBySource.get(SOURCE_HARDWARE))
                val sp = speed
                frameCounter++
                val render = sp == 1 || frameCounter % sp == 0L
                val frames = Native.step(audio, render)

                fpsCount++
                val nowNs = System.nanoTime()
                if (nowNs - fpsStamp >= 1_000_000_000L) {
                    fps = fpsCount
                    fpsCount = 0
                    fpsStamp = nowNs
                }

                if (sp == 1) {
                    if (frames > 0) {
                        val gain = if (muted) 0 else volume.coerceIn(0, 100)
                        if (gain < 100) {
                            for (i in 0 until frames * 2) {
                                audio[i] = ((audio[i] * gain) / 100).toShort()
                            }
                        }
                        t.write(audio, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
                    }
                    next = System.nanoTime()
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
