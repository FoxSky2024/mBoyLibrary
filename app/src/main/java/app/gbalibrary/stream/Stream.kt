package app.gbalibrary.stream

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.gbalibrary.emu.Engine
import app.gbalibrary.emu.Native
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Mini transmision P2P: el telefono anfitrion abre un puerto y los amigos se conectan DIRECTO
 * (sin servidor, sin nube, sin guardar nada). Protocolo: [tipo:1][largo:4][datos]. El video son
 * fotogramas JPEG; el chat y los emotes van por el mismo socket.
 */
object Proto {
    const val PORT = 47821
    const val MAX_VIEWERS = 3
    const val HELLO = 1 // viewer -> host: nombre
    const val WELCOME = 2 // host -> viewer: nombre del anfitrion
    const val FRAME = 3 // host -> viewer: JPEG
    const val CHAT = 4 // ambos: texto ("nombre\ntexto" del host al viewer)
    const val EMOTE = 5 // ambos: emoji ("nombre\nemoji" del host al viewer)
    const val SYSTEM = 6 // host -> viewer: aviso
    const val KICK = 7 // host -> viewer: motivo; luego se cierra
    val EMOTES = listOf("❤️", "😂", "🔥", "👏", "😮", "🎉", "👍")
}

/** Codigo de 6 digitos: 5 digitos = ultimos 16 bits de la IP del anfitrion + 1 digito de control. */
object JoinCode {
    fun localIpv4(): String? {
        var best: Pair<Int, String>? = null
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) {
                    if (a is Inet4Address && a.isSiteLocalAddress) {
                        val prio = when {
                            ni.name.startsWith("wlan") || ni.name.startsWith("ap") || ni.name.startsWith("swlan") -> 0
                            ni.name.startsWith("eth") -> 1
                            else -> 2
                        }
                        if (best == null || prio < best.first) best = prio to (a.hostAddress ?: continue)
                    }
                }
            }
        } catch (e: Exception) {
            // sin interfaces
        }
        return best?.second
    }

    private fun check(n: Int): Int = n.toString().sumOf { it - '0' } % 10

    fun encode(ip: String): String? {
        val o = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (o.size != 4) return null
        val n = o[2] * 256 + o[3]
        return "%05d%d".format(n, check(n))
    }

    /** Devuelve la IP del anfitrion usando los 2 primeros numeros de la IP propia (misma red WiFi). */
    fun decode(code: String, myIp: String?): String? {
        val c = code.filter { it.isDigit() }
        if (c.length != 6 || myIp == null) return null
        val n = c.substring(0, 5).toIntOrNull() ?: return null
        if (n > 65535 || check(n) != c[5] - '0') return null
        val p = myIp.split('.')
        if (p.size != 4) return null
        return "${p[0]}.${p[1]}.${n / 256}.${n % 256}"
    }
}

class ChatLine(val id: Long, val who: String, val text: String, val system: Boolean = false)
class Emote(val id: Long, val glyph: String, val from: String)

class ViewerInfo(val id: Int, val name: String, val ip: String) {
    var muted by mutableStateOf(false)
}

private val ids = AtomicLong(1)
private val main = Handler(Looper.getMainLooper())

private fun DataOutputStream.msg(type: Int, data: ByteArray) {
    writeByte(type)
    writeInt(data.size)
    write(data)
    flush()
}

private fun clean(s: String, max: Int) = s.replace(Regex("[\\p{Cntrl}]"), " ").trim().take(max)

/** Anfitrion: acepta hasta 3 espectadores y reparte video, chat y emotes. */
class StreamHost(private val hostName: String) {
    val running = mutableStateOf(false)
    val viewers = mutableStateListOf<ViewerInfo>()
    val chat = mutableStateListOf<ChatLine>()
    val emotes = mutableStateListOf<Emote>()
    var ip: String? = null
        private set
    var code: String? = null
        private set

    private var server: ServerSocket? = null
    private val conns = HashMap<Int, Conn>()
    private val banned = HashSet<String>()
    private val mutedNames = HashSet<String>()
    private var nextId = 1
    @Volatile private var alive = false
    private val encoder = Executors.newSingleThreadExecutor()
    @Volatile private var capturePending = false

    fun start(engine: Engine): Boolean {
        val myIp = JoinCode.localIpv4() ?: return false
        return try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(Proto.PORT))
            server = ss
            ip = myIp
            code = JoinCode.encode(myIp)
            alive = true
            running.value = true
            Thread({ acceptLoop(ss) }, "stream-accept").start()
            captureTick(engine)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        alive = false
        running.value = false
        try { server?.close() } catch (e: Exception) { /* ignorado */ }
        synchronized(conns) { conns.values.toList() }.forEach { it.close() }
        main.post { viewers.clear(); chat.clear(); emotes.clear() }
        encoder.shutdown()
    }

    // ---- Captura de video (~9 fps, solo si hay espectadores) ----

    private fun captureTick(engine: Engine) {
        if (!alive) return
        if (viewers.isNotEmpty() && !capturePending) {
            capturePending = true
            engine.screenshot { argb ->
                if (argb == null) { capturePending = false; return@screenshot }
                val vw = Native.videoWidth().coerceAtLeast(1)
                try {
                    encoder.execute {
                        try {
                            val small = Bitmap.createBitmap(argb, vw, (argb.size / vw).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            val big = Bitmap.createScaledBitmap(small, small.width * 2, small.height * 2, false)
                            val bos = ByteArrayOutputStream(24_000)
                            big.compress(Bitmap.CompressFormat.JPEG, 68, bos)
                            val jpeg = bos.toByteArray()
                            synchronized(conns) { conns.values.forEach { it.frame.set(jpeg); it.wake() } }
                        } catch (e: Exception) {
                            // cuadro perdido
                        } finally {
                            capturePending = false
                        }
                    }
                } catch (e: Exception) {
                    capturePending = false
                }
            }
        }
        main.postDelayed({ captureTick(engine) }, 110)
    }

    // ---- Conexiones ----

    private fun acceptLoop(ss: ServerSocket) {
        while (alive) {
            val s = try { ss.accept() } catch (e: Exception) { break }
            val id = synchronized(this) { nextId++ }
            Thread({ Conn(s, id).run() }, "stream-conn-$id").start()
        }
    }

    private fun addChat(who: String, text: String, system: Boolean = false) {
        main.post {
            chat.add(ChatLine(ids.getAndIncrement(), who, text, system))
            while (chat.size > 40) chat.removeAt(0)
        }
    }

    private fun broadcast(type: Int, data: ByteArray) {
        synchronized(conns) { conns.values.forEach { it.send(type, data) } }
    }

    private fun connOf(id: Int) = synchronized(conns) { conns[id] }

    // ---- Moderacion (dueno del stream) ----

    fun kick(id: Int) { connOf(id)?.let { it.send(Proto.KICK, "Te expulsaron de la transmisión".toByteArray()); it.closeSoon() } }

    fun ban(id: Int) {
        val c = connOf(id) ?: return
        banned.add(c.ip)
        c.send(Proto.KICK, "Fuiste baneado de esta transmisión".toByteArray())
        c.closeSoon()
    }

    fun mute(id: Int, on: Boolean) {
        val c = connOf(id) ?: return
        if (on) mutedNames.add(c.name) else mutedNames.remove(c.name)
        main.post { viewers.firstOrNull { it.id == id }?.muted = on }
        c.send(Proto.SYSTEM, (if (on) "El anfitrión te silenció en el chat" else "Ya puedes escribir en el chat").toByteArray())
    }

    fun sayAsHost(text: String) {
        val t = clean(text, 200)
        if (t.isEmpty()) return
        addChat(hostName, t)
        broadcast(Proto.CHAT, "$hostName\n$t".toByteArray())
    }

    private inner class Conn(val sock: Socket, val id: Int) {
        val ip: String = sock.inetAddress?.hostAddress ?: "?"
        var name = "?"
        val frame = AtomicReference<ByteArray?>(null)
        private val q = LinkedBlockingQueue<Pair<Int, ByteArray>>()
        @Volatile private var open = true

        fun send(type: Int, data: ByteArray) { q.offer(type to data) }
        fun wake() { q.offer(0 to ByteArray(0)) }
        fun closeSoon() { main.postDelayed({ close() }, 300) }
        fun close() {
            open = false
            q.offer(-1 to ByteArray(0))
            try { sock.close() } catch (e: Exception) { /* ignorado */ }
        }

        fun run() {
            try {
                sock.tcpNoDelay = true
                val inp = DataInputStream(BufferedInputStream(sock.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))
                sock.soTimeout = 8000
                if (inp.readUnsignedByte() != Proto.HELLO) return
                val hl = inp.readInt()
                if (hl !in 0..200) return
                val hb = ByteArray(hl).also { inp.readFully(it) }
                var base = clean(String(hb), 16).ifEmpty { "Invitado" }
                if (ip in banned) { out.msg(Proto.KICK, "Estás baneado de esta transmisión".toByteArray()); return }
                val full = synchronized(conns) { conns.size >= Proto.MAX_VIEWERS }
                if (full) { out.msg(Proto.KICK, "La transmisión está llena (máx. ${Proto.MAX_VIEWERS})".toByteArray()); return }
                synchronized(conns) {
                    var n = base
                    var k = 2
                    while (conns.values.any { it.name == n } || n == hostName) n = "$base$k".also { k++ }
                    name = n
                    conns[id] = this
                }
                sock.soTimeout = 0
                main.post { viewers.add(ViewerInfo(id, name, ip)) }
                addChat("", "$name se unió", true)
                out.msg(Proto.WELCOME, hostName.toByteArray())
                if (name in mutedNames) send(Proto.SYSTEM, "Estás silenciado en el chat".toByteArray())

                val writer = Thread({
                    try {
                        while (open) {
                            val m = q.poll(500, TimeUnit.MILLISECONDS)
                            if (m != null) {
                                if (m.first < 0) break
                                if (m.first > 0) out.msg(m.first, m.second)
                            }
                            val f = frame.getAndSet(null)
                            if (f != null) out.msg(Proto.FRAME, f)
                        }
                    } catch (e: Exception) {
                        // viewer desconectado
                    } finally {
                        close()
                    }
                }, "stream-write-$id")
                writer.start()

                while (open) {
                    val type = inp.readUnsignedByte()
                    val len = inp.readInt()
                    if (len !in 0..1024) break
                    val data = ByteArray(len).also { inp.readFully(it) }
                    when (type) {
                        Proto.CHAT -> {
                            val t = clean(String(data), 200)
                            if (t.isNotEmpty() && name !in mutedNames) {
                                addChat(name, t)
                                broadcast(Proto.CHAT, "$name\n$t".toByteArray())
                            }
                        }
                        Proto.EMOTE -> {
                            val g = String(data)
                            if (g in Proto.EMOTES) {
                                main.post { emotes.add(Emote(ids.getAndIncrement(), g, name)) }
                                broadcast(Proto.EMOTE, "$name\n$g".toByteArray())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // conexion terminada
            } finally {
                open = false
                try { sock.close() } catch (e: Exception) { /* ignorado */ }
                val had = synchronized(conns) { conns.remove(id) != null }
                if (had) {
                    main.post { viewers.removeAll { it.id == id } }
                    addChat("", "$name salió", true)
                }
            }
        }
    }
}

/** Espectador: se conecta al anfitrion, recibe video / chat / emotes y puede escribir. */
class StreamClient {
    val status = mutableStateOf("") // "" = sin conexion, "Conectando…", "En vivo", o el motivo del cierre
    val live = mutableStateOf(false)
    val frame = mutableStateOf<ImageBitmap?>(null)
    var hostName by mutableStateOf("")
    val chat = mutableStateListOf<ChatLine>()
    val emotes = mutableStateListOf<Emote>()

    @Volatile private var out: DataOutputStream? = null
    @Volatile private var sock: Socket? = null
    private val sendLock = Any()

    fun connect(host: String, name: String) {
        disconnect()
        status.value = "Conectando…"
        Thread({
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, Proto.PORT), 5000)
                s.tcpNoDelay = true
                sock = s
                val o = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                out = o
                val inp = DataInputStream(BufferedInputStream(s.getInputStream()))
                synchronized(sendLock) { o.msg(Proto.HELLO, clean(name, 16).ifEmpty { "Invitado" }.toByteArray()) }
                while (true) {
                    val type = inp.readUnsignedByte()
                    val len = inp.readInt()
                    if (len !in 0..600_000) break
                    val data = ByteArray(len).also { inp.readFully(it) }
                    when (type) {
                        Proto.WELCOME -> main.post { hostName = String(data); live.value = true; status.value = "En vivo" }
                        Proto.FRAME -> {
                            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                            if (bmp != null) main.post { frame.value = bmp.asImageBitmap() }
                        }
                        Proto.CHAT -> splitLine(data)?.let { (who, text) -> main.post { addLine(ChatLine(ids.getAndIncrement(), who, text)) } }
                        Proto.EMOTE -> splitLine(data)?.let { (who, g) -> main.post { emotes.add(Emote(ids.getAndIncrement(), g, who)) } }
                        Proto.SYSTEM -> main.post { addLine(ChatLine(ids.getAndIncrement(), "", String(data), true)) }
                        Proto.KICK -> { val why = String(data); main.post { end(why) }; return@Thread }
                    }
                }
                main.post { end("La transmisión terminó") }
            } catch (e: Exception) {
                main.post { if (status.value != "") end("No se pudo conectar / se perdió la conexión") }
            }
        }, "stream-client").start()
    }

    private fun splitLine(d: ByteArray): Pair<String, String>? {
        val s = String(d)
        val i = s.indexOf('\n')
        return if (i < 0) null else s.substring(0, i) to s.substring(i + 1)
    }

    private fun addLine(l: ChatLine) {
        chat.add(l)
        while (chat.size > 40) chat.removeAt(0)
    }

    private fun end(reason: String) {
        live.value = false
        status.value = reason
        try { sock?.close() } catch (e: Exception) { /* ignorado */ }
    }

    private fun write(type: Int, data: ByteArray) {
        val o = out ?: return
        Thread({ try { synchronized(sendLock) { o.msg(type, data) } } catch (e: Exception) { /* ignorado */ } }, "stream-send").start()
    }

    fun sendChat(text: String) { val t = clean(text, 200); if (t.isNotEmpty()) write(Proto.CHAT, t.toByteArray()) }
    fun sendEmote(glyph: String) { if (glyph in Proto.EMOTES) write(Proto.EMOTE, glyph.toByteArray()) }

    fun disconnect() {
        try { sock?.close() } catch (e: Exception) { /* ignorado */ }
        sock = null
        out = null
        live.value = false
        status.value = ""
        frame.value = null
        chat.clear()
        emotes.clear()
    }

    companion object {
        /** Conexion activa (la usa tambien la ventana PiP). */
        var current: StreamClient? = null
        /** Codigo recibido por enlace mboy://join/XXXXXX (QR). */
        val pendingCode = mutableStateOf<String?>(null)
    }
}
