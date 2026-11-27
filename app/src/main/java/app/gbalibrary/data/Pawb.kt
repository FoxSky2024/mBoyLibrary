package app.gbalibrary.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Banner creado por un jugador. Se comparte como enlace (mboyitem://...) y se "compra" importandolo. */
data class CustomBanner(
    val id: String,
    val name: String,
    val title: String,
    val subtitle: String,
    val colors: List<Int>,   // ARGB, 2 o 3
    val style: Int,          // 0 liso, 1 diagonal, 2 ondas, 3 puntos
    val creator: String,
    val price: Int,          // precio en Pawb que pide el creador al compartirlo
    val img: String = "",    // imagen de fondo opcional (JPEG pequeno en base64); viaja dentro del enlace
) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("title", title).put("subtitle", subtitle)
        .put("colors", JSONArray(colors)).put("style", style).put("creator", creator).put("price", price).put("img", img)

    companion object {
        fun fromJson(o: JSONObject): CustomBanner {
            val c = o.getJSONArray("colors")
            return CustomBanner(
                o.getString("id"), o.optString("name", "Banner"), o.optString("title"), o.optString("subtitle"),
                (0 until c.length()).map { c.getInt(it) }.ifEmpty { listOf(0xFF2A475E.toInt(), 0xFF16202D.toInt()) },
                o.optInt("style"), o.optString("creator", "Jugador"), o.optInt("price").coerceIn(0, 999), o.optString("img"),
            )
        }
    }
}

/**
 * Economia local de la app: moneda Pawb (icono PawIcon). Saldo, objetos comprados, fondo y banner equipados y banners propios.
 * Todo vive en el dispositivo (SharedPreferences "pawb", incluido en la copia de config.json). No hay servidor:
 * "comprar a otros jugadores" = importar su enlace pagando el precio que puso; el Pawb no se transfiere a nadie.
 */
object Pawb {
    private lateinit var p: SharedPreferences
    val balance = mutableIntStateOf(0)
    val owned = mutableStateOf<Set<String>>(emptySet())
    val bg = mutableStateOf("")        // id del fondo equipado ("" = ninguno)
    val banner = mutableStateOf("")    // id del banner equipado
    val custom = mutableStateOf<List<CustomBanner>>(emptyList())
    var playerName = "Jugador"

    private var appCtx: Context? = null
    private val mirror = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> appCtx?.let { Persist.touch(it) } }

    fun init(ctx: Context) {
        if (this::p.isInitialized) return
        appCtx = ctx.applicationContext
        p = ctx.applicationContext.getSharedPreferences("pawb", Context.MODE_PRIVATE)
        p.registerOnSharedPreferenceChangeListener(mirror) // cada cambio se copia a GBALibrary/config.json
        load()
    }

    /** Vuelve a leer todo (despues de restaurar una copia / config.json). */
    fun reload() { if (this::p.isInitialized) load() }

    private fun load() {
        if (!p.contains("balance")) p.edit().putInt("balance", 100).apply() // regalo de bienvenida
        balance.intValue = p.getInt("balance", 0); devUnlimited.value = p.getBoolean("devunl", false)
        owned.value = p.getString("owned", "").orEmpty().split(",").filter { it.isNotBlank() }.toSet()
        bg.value = p.getString("bg", "").orEmpty()
        banner.value = p.getString("banner", "").orEmpty()
        skin.value = p.getString("skin", "").orEmpty()
        kcDx.value = p.getFloat("kcx", 0f); kcDy.value = p.getFloat("kcy", 0f)
        keychain.value = p.getString("kchain", "").orEmpty(); aFrame.value = p.getString("aframe", "").orEmpty(); vFrame.value = p.getString("vframe", "").orEmpty(); vTitle.value = p.getString("vtitle", "").orEmpty()
        vSub.value = p.getString("vsub", "").orEmpty(); vImg.value = p.getString("vimg", "").orEmpty()
        vFx.value = p.getString("vfx", "").orEmpty(); vW.floatValue = p.getFloat("vw", 1f); vH.intValue = p.getInt("vh", 0); vAlign.intValue = p.getInt("valign", 0); vDy.intValue = p.getInt("vdy", 0); vVideo.value = p.getString("vvid", "").orEmpty()
        placed.value = try {
            val a = JSONArray(p.getString("placed", "[]"))
            (0 until a.length()).map { val o = a.getJSONObject(it); PlacedSticker(o.getString("u"), o.getString("s"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("k").toFloat(), o.getDouble("r").toFloat()) }
        } catch (e: Exception) { emptyList() }
        customStickers.value = p.getString("cstk", "").orEmpty().split(",").filter { it.isNotBlank() }
        refreshAds()
        custom.value = try {
            val a = JSONArray(p.getString("custom", "[]"))
            (0 until a.length()).map { CustomBanner.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    private fun saveOwned() { p.edit().putString("owned", owned.value.joinToString(",")).apply() }

    fun earn(n: Int) {
        if (n <= 0) return
        balance.intValue += n
        p.edit().putInt("balance", balance.intValue).apply()
    }

    /** Modo desarrollador: saldo ilimitado (no descuenta nada). */
    val devUnlimited = mutableStateOf(false)
    fun setDevUnlimited(v: Boolean) { devUnlimited.value = v; p.edit().putBoolean("devunl", v).apply() }
    fun canAfford(n: Int) = devUnlimited.value || balance.intValue >= n
    fun setBalance(n: Int) { balance.intValue = n.coerceAtLeast(0); p.edit().putInt("balance", balance.intValue).apply() }
    fun revokeAll() { owned.value = emptySet(); saveOwned() }

    fun spend(n: Int): Boolean {
        if (devUnlimited.value) return true
        if (n > balance.intValue) return false
        balance.intValue -= n
        p.edit().putInt("balance", balance.intValue).apply()
        return true
    }

    /** 1 Pawb por cada 5 minutos jugados (se llama al cerrar cada sesion). */
    fun earnFromPlay(seconds: Long) { if (this::p.isInitialized) earn((seconds / 300).toInt()) }

    /** Bonus diario (+25) la primera vez que se abre la app cada dia; devuelve lo ganado (0 si ya se cobro). */
    fun dailyBonus(): Int {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (p.getString("daily", "") == today) return 0
        p.edit().putString("daily", today).apply()
        earn(25)
        return 25
    }

    fun has(id: String) = id in owned.value

    /** Compra un objeto del catalogo. */
    fun buy(id: String, price: Int): Boolean {
        if (has(id)) return true
        if (!spend(price)) return false
        owned.value = owned.value + id; saveOwned()
        return true
    }

    fun grant(id: String) { if (!has(id)) { owned.value = owned.value + id; saveOwned() } }

    fun equipBg(id: String) { bg.value = id; p.edit().putString("bg", id).apply() }
    fun equipBanner(id: String) { banner.value = id; p.edit().putString("banner", id).apply() }

    // ---- banners propios / de otros jugadores ----
    private fun saveCustom() { p.edit().putString("custom", JSONArray(custom.value.map { it.toJson() }).toString()).apply() }

    // ---- vitrina del perfil: marco equipado + textos/imagen editados dentro del marco ----
    val vFrame = mutableStateOf("")
    val vTitle = mutableStateOf("")
    val vSub = mutableStateOf("")
    val vImg = mutableStateOf("")   // base64 JPEG (alta calidad)
    /** Tamaño de la vitrina: ancho como fraccion (0.3..1) y alto en dp (0 = automatico segun la imagen/video). */
    val vW = androidx.compose.runtime.mutableFloatStateOf(1f)
    val vH = mutableIntStateOf(0)
    fun setVitrinaSize(w: Float, h: Int) { vW.floatValue = w.coerceIn(0.3f, 1f); vH.intValue = h.coerceIn(0, 320); p.edit().putFloat("vw", vW.floatValue).putInt("vh", vH.intValue).apply() }
    val vAlign = mutableIntStateOf(0)  // 0 izquierda, 1 centro, 2 derecha
    val vDy = mutableIntStateOf(0)     // separacion superior en dp
    fun setVitrinaPos(a: Int, dy: Int) { vAlign.intValue = a.coerceIn(0, 2); vDy.intValue = dy.coerceIn(0, 80); p.edit().putInt("valign", vAlign.intValue).putInt("vdy", vDy.intValue).apply() }
    val vFx = mutableStateOf("")      // efecto animado: holo, destellos, nieve, brasas, lluvia, glitch
    val vVideo = mutableStateOf("")   // ruta del video de la vitrina (copiado a GBALibrary/Banners)
    fun setVitrinaFx(fx: String) { vFx.value = fx; p.edit().putString("vfx", fx).apply() }
    fun setVitrinaVideo(path: String) { vVideo.value = path; p.edit().putString("vvid", path).apply() }
    fun bannerDir(ctx: Context) = java.io.File(DataDir.root(ctx), "Banners").apply { mkdirs() }
    /** Llavero equipado (cuelga y se balancea en el perfil). */
    val keychain = mutableStateOf("")
    /** Posicion del llavero en el perfil (dp desde la posicion por defecto). */
    val kcDx = mutableStateOf(0f)
    val kcDy = mutableStateOf(0f)
    fun setKeychainPos(x: Float, y: Float) { kcDx.value = x; kcDy.value = y; p.edit().putFloat("kcx", x).putFloat("kcy", y).apply() }
    fun equipKeychain(id: String) { keychain.value = id; p.edit().putString("kchain", id).apply() }
    /** Marco equipado en la foto de perfil (avatar). */
    val aFrame = mutableStateOf("")
    fun equipAvatarFrame(id: String) { aFrame.value = id; p.edit().putString("aframe", id).apply() }
    fun equipFrame(id: String) { vFrame.value = id; p.edit().putString("vframe", id).apply() }
    fun setVitrina(title: String, sub: String, img: String) {
        vTitle.value = title; vSub.value = sub; vImg.value = img
        p.edit().putString("vtitle", title).putString("vsub", sub).putString("vimg", img).apply()
    }

    // ---- skins del emulador ----
    val skin = mutableStateOf("")      // id de la skin de botones equipada
    fun equipSkin(id: String) { skin.value = id; p.edit().putString("skin", id).apply() }

    // ---- stickers pegados en pantalla (x, y en fraccion 0..1 de la pantalla) ----
    class PlacedSticker(val uid: String, val sid: String, val x: Float, val y: Float, val scale: Float, val rot: Float)
    val placed = mutableStateOf<List<PlacedSticker>>(emptyList())
    val stickerEdit = mutableStateOf(false)
    val stickerSel = mutableStateOf("")

    private fun savePlaced() {
        val a = JSONArray()
        placed.value.forEach { a.put(JSONObject().put("u", it.uid).put("s", it.sid).put("x", it.x.toDouble()).put("y", it.y.toDouble()).put("k", it.scale.toDouble()).put("r", it.rot.toDouble())) }
        p.edit().putString("placed", a.toString()).apply()
    }
    fun placeSticker(sid: String) {
        val st = PlacedSticker(UUID.randomUUID().toString().take(8), sid, 0.5f, 0.45f, 1.4f, 0f)
        placed.value = placed.value + st; stickerSel.value = st.uid; savePlaced()
    }
    fun updateSticker(uid: String, x: Float, y: Float, scale: Float, rot: Float) {
        placed.value = placed.value.map { if (it.uid == uid) PlacedSticker(uid, it.sid, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), scale.coerceIn(0.4f, 5f), rot) else it }
    }
    fun commitStickers() = savePlaced()

    // stickers propios (imagenes del usuario): PNG en GBALibrary/Stickers/<id>.png, ids "cs:<id>"
    val customStickers = mutableStateOf<List<String>>(emptyList())
    fun stickerDir(ctx: Context) = java.io.File(DataDir.root(ctx), "Stickers").apply { mkdirs() }
    fun addCustomSticker(ctx: Context, png: ByteArray): String {
        val id = UUID.randomUUID().toString().take(8)
        java.io.File(stickerDir(ctx), "$id.png").writeBytes(png)
        customStickers.value = customStickers.value + "cs:$id"
        p.edit().putString("cstk", customStickers.value.joinToString(",")).apply()
        return "cs:$id"
    }
    fun deleteCustomSticker(ctx: Context, sid: String) {
        runCatching { java.io.File(stickerDir(ctx), sid.removePrefix("cs:") + ".png").delete() }
        customStickers.value = customStickers.value - sid
        p.edit().putString("cstk", customStickers.value.joinToString(",")).apply()
        placed.value = placed.value.filter { it.sid != sid }; savePlaced()
    }
    fun removeSticker(uid: String) { placed.value = placed.value.filter { it.uid != uid }; stickerSel.value = ""; savePlaced() }

    // ---- anuncios voluntarios con recompensa ----
    const val AD_REWARD = 15
    const val AD_DAILY_CAP = 5
    val adsLeft = mutableIntStateOf(AD_DAILY_CAP)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    private fun refreshAds() {
        adsLeft.intValue = if (p.getString("adsDay", "") == today()) (AD_DAILY_CAP - p.getInt("adsN", 0)).coerceAtLeast(0) else AD_DAILY_CAP
    }
    /** Se llama SOLO cuando AdMob confirma que el usuario completo el anuncio. */
    fun adReward() {
        val n = if (p.getString("adsDay", "") == today()) p.getInt("adsN", 0) else 0
        if (n >= AD_DAILY_CAP) return
        p.edit().putString("adsDay", today()).putInt("adsN", n + 1).apply()
        earn(AD_REWARD); refreshAds()
    }

    const val CREATE_COST = 200

    /** Crear un banner cuesta [CREATE_COST] Pawb. Devuelve null si no alcanza el saldo. */
    fun createBanner(name: String, title: String, subtitle: String, colors: List<Int>, style: Int, price: Int, img: String = ""): CustomBanner? {
        if (!spend(CREATE_COST)) return null
        val b = CustomBanner("u:" + UUID.randomUUID().toString().take(8), name.ifBlank { "Mi banner" }, title, subtitle, colors, style, playerName, price, img)
        custom.value = custom.value + b; saveCustom(); grant(b.id)
        return b
    }

    fun deleteCustom(id: String) {
        custom.value = custom.value.filter { it.id != id }; saveCustom()
        owned.value = owned.value - id; saveOwned()
        if (banner.value == id) equipBanner("")
    }

    /** La imagen se guarda en alta calidad; en el enlace viaja reducida (480x160) para que quepa. */
    private fun smallImg(b64: String): String = try {
        if (b64.isEmpty()) "" else {
            val raw = Base64.decode(b64, Base64.DEFAULT)
            val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)
            val sm = android.graphics.Bitmap.createScaledBitmap(bmp, 480, 160, true)
            val bos = java.io.ByteArrayOutputStream(); sm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        }
    } catch (e: Exception) { "" }

    fun shareLink(b: CustomBanner): String =
        "mboyitem://" + Base64.encodeToString(b.copy(img = smallImg(b.img)).toJson().toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun parseLink(text: String): CustomBanner? = try {
        val s = text.trim().removePrefix("mboyitem://")
        val o = JSONObject(String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
        CustomBanner.fromJson(o).let { it.copy(id = "i:" + it.id.removePrefix("u:").removePrefix("i:")) }
    } catch (e: Exception) { null }

    /** Importa (paga el precio que pidio el creador). Devuelve null si ok o un mensaje de error. */
    fun importBanner(b: CustomBanner): String? {
        if (custom.value.any { it.id == b.id }) return "Ya tienes este banner"
        if (!spend(b.price)) return "Te faltan ${b.price - balance.intValue} Pawb"
        custom.value = custom.value + b; saveCustom(); grant(b.id)
        return null
    }
}
