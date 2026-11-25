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
) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("title", title).put("subtitle", subtitle)
        .put("colors", JSONArray(colors)).put("style", style).put("creator", creator).put("price", price)

    companion object {
        fun fromJson(o: JSONObject): CustomBanner {
            val c = o.getJSONArray("colors")
            return CustomBanner(
                o.getString("id"), o.optString("name", "Banner"), o.optString("title"), o.optString("subtitle"),
                (0 until c.length()).map { c.getInt(it) }.ifEmpty { listOf(0xFF2A475E.toInt(), 0xFF16202D.toInt()) },
                o.optInt("style"), o.optString("creator", "Jugador"), o.optInt("price").coerceIn(0, 999),
            )
        }
    }
}

/**
 * Economia local de la app: moneda Pawb (🐾). Saldo, objetos comprados, fondo y banner equipados y banners propios.
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

    fun init(ctx: Context) {
        if (this::p.isInitialized) return
        p = ctx.applicationContext.getSharedPreferences("pawb", Context.MODE_PRIVATE)
        if (!p.contains("balance")) p.edit().putInt("balance", 100).apply() // regalo de bienvenida
        balance.intValue = p.getInt("balance", 0)
        owned.value = p.getString("owned", "").orEmpty().split(",").filter { it.isNotBlank() }.toSet()
        bg.value = p.getString("bg", "").orEmpty()
        banner.value = p.getString("banner", "").orEmpty()
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

    fun spend(n: Int): Boolean {
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

    fun createBanner(name: String, title: String, subtitle: String, colors: List<Int>, style: Int, price: Int): CustomBanner {
        val b = CustomBanner("u:" + UUID.randomUUID().toString().take(8), name.ifBlank { "Mi banner" }, title, subtitle, colors, style, playerName, price)
        custom.value = custom.value + b; saveCustom(); grant(b.id)
        return b
    }

    fun deleteCustom(id: String) {
        custom.value = custom.value.filter { it.id != id }; saveCustom()
        owned.value = owned.value - id; saveOwned()
        if (banner.value == id) equipBanner("")
    }

    fun shareLink(b: CustomBanner): String =
        "mboyitem://" + Base64.encodeToString(b.toJson().toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

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
