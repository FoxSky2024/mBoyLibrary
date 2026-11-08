package app.gbalibrary.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import app.gbalibrary.data.Settings
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Musica de fondo de la biblioteca: una CARPETA como playlist (mp3 / m4a / ogg ...), en orden o
 * aleatorio, en bucle. Tambien acepta el archivo suelto de la version anterior. Apagada por defecto.
 * Solo suena en la biblioteca (se pausa al entrar a un juego).
 */
object Music {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val EXT = setOf("mp3", "m4a", "ogg", "oga", "aac", "wav", "flac", "opus")

    // Estado observable (vinilo)
    val playing = mutableStateOf(false)
    val title = mutableStateOf("")
    val artist = mutableStateOf("")
    val art = mutableStateOf<ImageBitmap?>(null)
    val trackStamp = mutableIntStateOf(0)

    private var player: MediaPlayer? = null
    private var tracks: List<Uri> = emptyList()
    private var order: List<Int> = emptyList()
    private var pos = 0
    private var loadedKey = ""
    private var loading = false
    private var failures = 0
    private var appCtx: Context? = null

    /** El usuario pauso la musica a mano (boton flotante / menu del juego): sync() no la reanuda sola. */
    @Volatile var userPaused = false

    fun hasSource(): Boolean = Settings.musicOn.value && (Settings.musicFolder.value.isNotEmpty() || Settings.musicUri.value.isNotEmpty())

    fun applyVolume() {
        try { val v = volume(); player?.setVolume(v, v) } catch (e: Exception) { /* ignorado */ }
    }

    /** Pausa / reanuda a pedido del usuario. */
    fun toggle(ctx: Context) {
        if (playing.value) {
            userPaused = true
            pause()
        } else {
            userPaused = false
            sync(ctx)
        }
    }

    private fun currentKey(): String {
        val src = Settings.musicFolder.value.ifEmpty { Settings.musicUri.value }
        return src + "|" + Settings.musicShuffle.value
    }

    private fun volume() = if (Settings.musicMuted.value) 0f else Settings.musicVolume.floatValue

    /** Aplica los ajustes actuales: arranca, cambia de carpeta, ajusta volumen o apaga. Hilo principal. */
    fun sync(ctx: Context) {
        appCtx = ctx.applicationContext
        val hasSource = Settings.musicFolder.value.isNotEmpty() || Settings.musicUri.value.isNotEmpty()
        if (!Settings.musicOn.value || !hasSource) {
            release()
            return
        }
        val key = currentKey()
        if (key != loadedKey) {
            release()
            loadedKey = key
            loadPlaylist(ctx.applicationContext, key)
            return
        }
        val mp = player
        if (mp != null) {
            try {
                val v = volume()
                mp.setVolume(v, v)
                if (!userPaused) {
                    if (!mp.isPlaying) mp.start()
                    playing.value = true
                }
            } catch (e: Exception) {
                skip(false)
            }
        }
    }

    private fun loadPlaylist(ctx: Context, key: String) {
        if (loading) return
        loading = true
        val folder = Settings.musicFolder.value
        val single = Settings.musicUri.value
        val shuffle = Settings.musicShuffle.value
        io.execute {
            val found = ArrayList<Uri>()
            try {
                if (folder.isNotEmpty()) {
                    val root = DocumentFile.fromTreeUri(ctx, Uri.parse(folder))
                    fun walk(d: DocumentFile, depth: Int) {
                        if (depth > 3) return
                        val kids = try { d.listFiles() } catch (e: Exception) { emptyArray<DocumentFile>() }
                        kids.sortedBy { it.name?.lowercase() ?: "" }.forEach { f ->
                            if (f.isDirectory) walk(f, depth + 1)
                            else if ((f.name ?: "").substringAfterLast('.', "").lowercase() in EXT) found.add(f.uri)
                        }
                    }
                    if (root != null) walk(root, 0)
                } else if (single.isNotEmpty()) {
                    found.add(Uri.parse(single))
                }
            } catch (e: Exception) {
                // carpeta ilegible: lista vacia
            }
            main.post {
                loading = false
                if (loadedKey != key) {
                    // los ajustes cambiaron mientras se leia la carpeta
                    if (Settings.musicOn.value) sync(ctx)
                    return@post
                }
                tracks = found
                failures = 0
                order = if (shuffle) found.indices.shuffled() else found.indices.toList()
                pos = 0
                if (tracks.isEmpty()) {
                    playing.value = false
                } else if (Settings.musicOn.value) {
                    play(ctx, order[pos], cassette = false)
                }
            }
        }
    }

    private fun play(ctx: Context, index: Int, cassette: Boolean) {
        val uri = tracks.getOrNull(index) ?: return
        val key = loadedKey
        fun start() {
            if (key != loadedKey || !Settings.musicOn.value) return
            try {
                player?.release()
                val mp = MediaPlayer()
                mp.setDataSource(ctx, uri)
                mp.setOnCompletionListener { skip(true) }
                mp.setOnErrorListener { _, _, _ ->
                    failures++
                    if (failures <= tracks.size) skip(false) else release()
                    true
                }
                mp.isLooping = tracks.size == 1
                mp.prepare()
                val v = volume()
                mp.setVolume(v, v)
                player = mp
                if (!userPaused) {
                    mp.start()
                    playing.value = true
                }
                failures = 0
                readMetadata(ctx, uri)
            } catch (e: Exception) {
                failures++
                if (failures <= tracks.size) skip(false) else release()
            }
        }
        if (cassette) {
            try {
                player?.pause()
            } catch (e: Exception) {
                // ignorado
            }
            val ms = Cassette.play(volume())
            main.postDelayed({ start() }, ms)
        } else {
            start()
        }
    }

    /** Cambia de cancion sin el sonido de cassette (lo usa el disco: [delta] +1 siguiente, -1 anterior). */
    fun change(delta: Int) {
        val ctx = appCtx ?: return
        val n = order.size
        if (n == 0) return
        pos = ((pos + delta) % n + n) % n
        play(ctx, order[pos], false)
    }

    /** Nombre de la cancion vecina (para la vista previa al arrastrar el disco). */
    fun peek(delta: Int): String {
        val ctx = appCtx ?: return ""
        val n = order.size
        if (n == 0) return ""
        val uri = tracks.getOrNull(order[((pos + delta) % n + n) % n]) ?: return ""
        return try {
            DocumentFile.fromSingleUri(ctx, uri)?.name?.substringBeforeLast('.') ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Siguiente cancion (con sonido de cambio de cassette). */
    fun next() {
        skip(true)
    }

    private fun skip(cassette: Boolean) {
        val ctx = appCtx ?: return
        if (tracks.isEmpty()) return
        pos++
        if (pos >= order.size) {
            pos = 0
            if (Settings.musicShuffle.value) order = tracks.indices.shuffled()
        }
        play(ctx, order[pos], cassette)
    }

    private fun readMetadata(ctx: Context, uri: Uri) {
        val fallback = try { DocumentFile.fromSingleUri(ctx, uri)?.name?.substringBeforeLast('.') } catch (e: Exception) { null }
        io.execute {
            var t: String? = null
            var a: String? = null
            var bmp: ImageBitmap? = null
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                t = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val pic = r.embeddedPicture
                if (pic != null) {
                    val o = BitmapFactory.Options().apply { inSampleSize = 4 }
                    bmp = BitmapFactory.decodeByteArray(pic, 0, pic.size, o)?.asImageBitmap()
                }
            } catch (e: Exception) {
                // sin metadatos
            } finally {
                try { r.release() } catch (e: Exception) { /* ignorado */ }
            }
            main.post {
                title.value = t?.takeIf { it.isNotBlank() } ?: fallback ?: "Música"
                artist.value = a ?: ""
                art.value = bmp
                trackStamp.intValue = trackStamp.intValue + 1
            }
        }
    }

    /** Pausa (el vinilo se detiene y se esconde). */
    fun pause() {
        try {
            if (player?.isPlaying == true) player?.pause()
        } catch (e: Exception) {
            // ignorado
        }
        playing.value = false
    }

    fun release() {
        try {
            player?.release()
        } catch (e: Exception) {
            // ignorado
        }
        player = null
        loadedKey = ""
        tracks = emptyList()
        order = emptyList()
        playing.value = false
        main.removeCallbacksAndMessages(null)
    }
}

/** Sonido sintetizado de "cambio de cassette": clic, motor de cinta y golpe final. */
object Cassette {
    private const val RATE = 22050

    private val pcm: ShortArray by lazy {
        val n = (RATE * 0.42).toInt()
        val rnd = Random(7)
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            var v = 0.0
            // clic inicial
            if (t < 0.05) v += (rnd.nextDouble() * 2 - 1) * exp(-t * 90) * 0.9
            // motor / cinta girando
            if (t in 0.05..0.32) {
                val env = if (t < 0.09) (t - 0.05) / 0.04 else if (t > 0.28) (0.32 - t) / 0.04 else 1.0
                v += (rnd.nextDouble() * 2 - 1) * 0.12 * env + sin(2 * PI * 95 * t) * 0.10 * env * (0.6 + 0.4 * sin(2 * PI * 18 * t))
            }
            // golpe de la tapa
            if (t >= 0.33) {
                val tt = t - 0.33
                v += sin(2 * PI * 130 * tt) * exp(-tt * 45) * 0.8 + (rnd.nextDouble() * 2 - 1) * exp(-tt * 120) * 0.5
            }
            out[i] = (v.coerceIn(-1.0, 1.0) * 28000).toInt().toShort()
        }
        out
    }

    /** Reproduce el sonido y devuelve cuanto dura (ms) para esperar antes de la nueva cancion. */
    fun play(volume: Float): Long {
        if (volume <= 0f) return 0L
        try {
            val data = pcm
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            track.setVolume(volume.coerceIn(0f, 1f))
            track.play()
            Handler(Looper.getMainLooper()).postDelayed({
                try { track.release() } catch (e: Exception) { /* ignorado */ }
            }, 700)
            return 430L
        } catch (e: Exception) {
            return 0L
        }
    }
}

/** Sonidos cortos de la interfaz (solo si estan activados en Ajustes). */
object Sfx {
    private var tone: ToneGenerator? = null

    private fun play(type: Int, ms: Int) {
        if (!Settings.uiSounds.value) return
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 35)
            tone?.startTone(type, ms)
        } catch (e: Exception) {
            tone = null
        }
    }

    fun click() = play(ToneGenerator.TONE_PROP_BEEP, 40)
    fun enter() = play(ToneGenerator.TONE_PROP_ACK, 120)
}

/** Clic corto del disco (res/raw/disc_click.wav), volumen bajo. */
object DiscClick {
    private var pool: android.media.SoundPool? = null
    private var id = 0
    private var ready = false

    fun init(ctx: Context) {
        if (pool != null) return
        val p = android.media.SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .build()
        p.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
        id = p.load(ctx.applicationContext, app.gbalibrary.R.raw.disc_click, 1)
        pool = p
    }

    fun play() {
        if (ready) pool?.play(id, 0.3f, 0.3f, 1, 0, 1f)
    }
}
