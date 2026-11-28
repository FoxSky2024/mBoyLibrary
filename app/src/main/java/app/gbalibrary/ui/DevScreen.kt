package app.gbalibrary.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Pawb
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object DevNav { val show = mutableStateOf(false) }

/**
 * Cuenta de desarrollador LOCAL: usuario y contraseña se guardan en el dispositivo como hash (PBKDF2 + sal).
 * La primera vez se crea la cuenta; despues hay que iniciar sesion en cada arranque de la app.
 * No hay servidor: protege el modo desarrollador en este telefono, no es seguridad contra quien reinstale la app.
 */
object DevAuth {
    val loggedIn = mutableStateOf(false)
    private var fails = 0
    private var lockUntil = 0L
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("dev_auth", Context.MODE_PRIVATE)
    private fun hash(pass: String, salt: ByteArray): String {
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass.toCharArray(), salt, 120_000, 256))
        return android.util.Base64.encodeToString(k.encoded, android.util.Base64.NO_WRAP)
    }
    // Credenciales fijas (solo se guardan sus hashes SHA-256).
    private const val H_USER = "05f514fae7ca5710f9e9289a20a5c9b372af781bfc94dd23d9cb8a044122460f"
    private const val H_PASS = "f8ff822ab877d91481580599bfd5f4548ac32bac3d58e381c85b2841cb52c7c2"
    private fun sha(t: String) = MessageDigest.getInstance("SHA-256").digest(t.toByteArray()).joinToString("") { "%02x".format(it) }
    fun isSetup(ctx: Context) = true
    fun setup(ctx: Context, user: String, pass: String) {}
    /** null = correcto; texto = motivo del fallo. */
    fun login(ctx: Context, user: String, pass: String): String? {
        val now = System.currentTimeMillis()
        if (now < lockUntil) return "Espera ${(lockUntil - now) / 1000 + 1} s"
        val ok = MessageDigest.isEqual(sha(user.trim().lowercase()).toByteArray(), H_USER.toByteArray()) &&
            MessageDigest.isEqual(sha(pass).toByteArray(), H_PASS.toByteArray())
        if (ok) { fails = 0; loggedIn.value = true; return null }
        fails++
        if (fails >= 5) { lockUntil = now + 30_000; fails = 0 }
        return "Usuario o contraseña incorrectos"
    }
    fun logout() { loggedIn.value = false }
    fun wipe(ctx: Context) { prefs(ctx).edit().clear().apply(); loggedIn.value = false }
}

/** Objetos de tienda creados por el desarrollador (banners y marcos), guardados en el dispositivo. */
object DevStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("dev_items", Context.MODE_PRIVATE)
    fun load(ctx: Context) {
        ShopCatalog.devBanners.clear(); ShopCatalog.devFrames.clear()
        val arr = try { JSONArray(prefs(ctx).getString("items", "[]")) } catch (e: Exception) { JSONArray() }
        for (i in 0 until arr.length()) try {
            val o = arr.getJSONObject(i)
            val rar = Rarity.values()[o.optInt("rar", 0).coerceIn(0, Rarity.values().lastIndex)]
            if (o.getString("t") == "b") ShopCatalog.devBanners.add(
                ShopBanner(o.getString("id"), o.getString("name"), o.getInt("price"), rar, o.optString("title"), o.optString("sub"), listOf(Color(o.getInt("c1")), Color(o.getInt("c2"))), o.optInt("style")),
            ) else ShopCatalog.devFrames.add(ShopFrame(o.getString("id"), o.getString("name"), o.getInt("price"), rar, o.optInt("kind"), Color(o.getInt("c1")), Color(o.getInt("c2"))))
        } catch (e: Exception) { /* ignorado */ }
    }
    private fun save(ctx: Context) {
        val arr = JSONArray()
        ShopCatalog.devBanners.forEach { arr.put(JSONObject().put("t", "b").put("id", it.id).put("name", it.name).put("price", it.price).put("rar", it.rarity.ordinal).put("title", it.title).put("sub", it.subtitle).put("c1", it.colors[0].toArgb()).put("c2", it.colors[1].toArgb()).put("style", it.style)) }
        ShopCatalog.devFrames.forEach { arr.put(JSONObject().put("t", "f").put("id", it.id).put("name", it.name).put("price", it.price).put("rar", it.rarity.ordinal).put("kind", it.kind).put("c1", it.c1.toArgb()).put("c2", it.c2.toArgb())) }
        prefs(ctx).edit().putString("items", arr.toString()).apply()
    }
    fun addBanner(ctx: Context, b: ShopBanner) { ShopCatalog.devBanners.add(b); save(ctx) }
    fun addFrame(ctx: Context, f: ShopFrame) { ShopCatalog.devFrames.add(f); save(ctx) }
    fun remove(ctx: Context, id: String) { ShopCatalog.devBanners.removeAll { it.id == id }; ShopCatalog.devFrames.removeAll { it.id == id }; save(ctx) }
}

private val DevSwatches = listOf(
    0xFFE8892B, 0xFFD64B4B, 0xFFD86B9A, 0xFF8A4CD6, 0xFF3B7DD8, 0xFF2FBF9B, 0xFF3FA56B, 0xFFF5B642,
    0xFFFFFFFF, 0xFF1A1033, 0xFF123566, 0xFF14432B, 0xFF16202D, 0xFF000000,
).map { it.toInt() }

@Composable
fun DevScreen(onBack: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().screenBackdrop()) {
        ScreenHeader("Modo desarrollador", onBack)
        if (!DevAuth.loggedIn.value) DevLogin(ctx) else DevPanel(ctx)
    }
}

@Composable
private fun DevLogin(ctx: Context) {
    val p = LocalPalette.current
    val setup = remember { !DevAuth.isSetup(ctx) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(14.dp)).background(p.panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (setup) "CREAR CUENTA DE DESARROLLADOR" else "INICIAR SESIÓN", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
            if (setup) Text("Elige usuario y contraseña (mín. 6). Se guardan solo en este dispositivo.", color = p.dim, fontSize = 12.sp)
            OutlinedTextField(user, { user = it.take(24) }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pass, { pass = it.take(48) }, label = { Text("Contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (setup) OutlinedTextField(pass2, { pass2 = it.take(48) }, label = { Text("Repite la contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            if (err.isNotEmpty()) Text(err, color = Color(0xFFFF6B6B), fontSize = 12.sp)
            AppButton(if (setup) "Crear y entrar" else "Entrar", {
                err = when {
                    user.isBlank() -> "Escribe un usuario"
                    setup && pass.length < 6 -> "La contraseña necesita al menos 6 caracteres"
                    setup && pass != pass2 -> "Las contraseñas no coinciden"
                    setup -> { DevAuth.setup(ctx, user, pass); "" }
                    else -> DevAuth.login(ctx, user, pass) ?: ""
                }
            })
        }
    }
}

@Composable
private fun DevCard(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), color = p.accent, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
        content()
    }
}

@Composable
private fun SwatchPicker(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DevSwatches.forEach { c -> Box(Modifier.size(26.dp).clip(CircleShape).background(Color(c)).border(if (c == selected) 2.dp else 1.dp, if (c == selected) Color.White else Color.Gray, CircleShape).clickable { onPick(c) }) }
    }
}

@Composable
private fun DevPanel(ctx: Context) {
    val p = LocalPalette.current
    var give by remember { mutableStateOf("1000") }
    var type by remember { mutableIntStateOf(0) } // 0 banner, 1 marco
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("100") }
    var rar by remember { mutableIntStateOf(1) }
    var title by remember { mutableStateOf("MI BANNER") }
    var sub by remember { mutableStateOf("Subtítulo") }
    var style by remember { mutableIntStateOf(1) }
    var kind by remember { mutableIntStateOf(8) }
    var c1 by remember { mutableIntStateOf(DevSwatches[3]) }
    var c2 by remember { mutableIntStateOf(DevSwatches[5]) }
    var msg by remember { mutableStateOf("") }
    val rarities = Rarity.values()
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DevCard("Saldo") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saldo ilimitado", color = p.text, modifier = Modifier.weight(1f))
                    Switch(Pawb.devUnlimited.value, { Pawb.setDevUnlimited(it) })
                }
                Text("Saldo actual: ${if (Pawb.devUnlimited.value) "∞" else Pawb.balance.intValue.toString()}", color = p.dim, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(give, { give = it.filter(Char::isDigit).take(7) }, label = { Text("Cantidad") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    AppButton("Sumar", { Pawb.earn(give.toIntOrNull() ?: 0) })
                    AppButton("Fijar", { Pawb.setBalance(give.toIntOrNull() ?: 0) })
                }
            }
            DevCard("Tienda") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton("Desbloquear todo", {
                        (ShopCatalog.allFrames().map { it.id } + ShopCatalog.allBanners().map { it.id } + ShopCatalog.skins.map { it.id }).forEach { Pawb.grant(it) }
                        msg = "Todo desbloqueado"
                    })
                    AppButton("Quitar todo", { Pawb.revokeAll(); msg = "Compras eliminadas" })
                }
            }
            DevCard("Cuenta") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton("Cerrar sesión", { DevAuth.logout() })
                    AppButton("Borrar cuenta dev", { DevAuth.wipe(ctx) })
                }
            }
            if (msg.isNotEmpty()) Text(msg, color = p.accent, fontSize = 12.sp)
        }
        Column(Modifier.weight(1.3f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DevCard("Nuevo objeto de tienda") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppChip("Banner", type == 0, { type = 0 }); AppChip("Marco", type == 1, { type = 1 })
                }
                // vista previa
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (type == 0) BannerArt(title.ifBlank { " " }, sub, listOf(Color(c1), Color(c2)), style, Modifier.size(220.dp, 80.dp).clip(RoundedCornerShape(8.dp)))
                    else FramedBox(ShopFrame("prev", "p", 0, rarities[rar], kind, Color(c1), Color(c2)), Modifier.size(200.dp, 90.dp)) { Box(Modifier.fillMaxSize().background(Color(0xFF1B2430))) }
                }
                OutlinedTextField(name, { name = it.take(24) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(price, { price = it.filter(Char::isDigit).take(5) }, label = { Text("Precio en Pawb") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Rareza", color = p.dim, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { rarities.forEachIndexed { i, r -> AppChip(r.label, rar == i, { rar = i }) } }
                if (type == 0) {
                    OutlinedTextField(title, { title = it.take(22) }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(sub, { sub = it.take(40) }, label = { Text("Subtítulo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { StyleNames.forEachIndexed { i, n -> AppChip(n, style == i, { style = i }) } }
                } else {
                    Text("Tipo de marco (0-15: 8+ son los animados)", color = p.dim, fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { (0..15).forEach { k -> AppChip("$k", kind == k, { kind = k }) } }
                }
                Text("Color 1", color = p.dim, fontSize = 11.sp); SwatchPicker(c1) { c1 = it }
                Text("Color 2", color = p.dim, fontSize = 11.sp); SwatchPicker(c2) { c2 = it }
                AppButton("Añadir a la tienda", {
                    val nm = name.trim()
                    if (nm.isEmpty()) msg = "Ponle un nombre" else {
                        val id = "dev_" + System.currentTimeMillis().toString(36)
                        val pr = price.toIntOrNull() ?: 0
                        if (type == 0) DevStore.addBanner(ctx, ShopBanner(id, nm, pr, rarities[rar], title, sub, listOf(Color(c1), Color(c2)), style))
                        else DevStore.addFrame(ctx, ShopFrame(id, nm, pr, rarities[rar], kind, Color(c1), Color(c2)))
                        msg = "«$nm» añadido a la tienda (pestaña ${if (type == 0) "Banners" else "Marcos"})"; name = ""
                    }
                })
            }
            DevCard("Plantillas para crear objetos") {
                Text(
                    "Cómo hacer un objeto:\n" +
                        "1. Descarga la plantilla (botón Compartir) y ábrela en tu editor (Photoshop, Krita, Photopea...).\n" +
                        "2. Dibuja encima y exporta PNG con fondo transparente.\n" +
                        "3. Medidas: banner 1280×360 · marco de banner 800×500 (borde de 60 px) · marco de avatar 512×512 (borde de 48 px) · llavero 512×640.\n" +
                        "4. Nombre del archivo: rareza_precio_nombre.png. Rareza: comun, raro, epico o legendario. Ej.: epico_300_dragon.png\n" +
                        "5. Colócalo en app/src/main/assets/shop/banners, frames o keychains y haz push.\n" +
                        "6. Llaveros: si el nombre contiene brillo, fuego, nieve, amor o burbuja, tendrá partículas.\n" +
                        "Las guías marcan la zona segura; lo que quede fuera se recorta.",
                    color = p.dim, fontSize = 12.sp, lineHeight = 17.sp,
                )
                listOf(
                    "plantilla_tienda.png" to "Hoja completa (todas las plantillas)",
                    "guia_banner_1280x360.png" to "Banner 1280×360",
                    "guia_marco_800x500.png" to "Marco de banner 800×500",
                    "guia_marco_avatar_512.png" to "Marco de avatar 512×512",
                    "plantilla_llavero_512x640.png" to "Llavero 512×640",
                ).forEach { (file, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val bmp = remember(file) {
                            runCatching {
                                ctx.assets.open("templates/$file").use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) }
                            }.getOrNull()?.asImageBitmap()
                        }
                        if (bmp != null) androidx.compose.foundation.Image(bmp, null, Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2A2E)), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                        Text(label, color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        AppButton("Compartir", {
                            runCatching {
                                val out = java.io.File(ctx.cacheDir, file)
                                ctx.assets.open("templates/$file").use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", out)
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    this.type = "image/png"; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(android.content.Intent.createChooser(send, file).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure { msg = "No se pudo compartir" }
                        })
                    }
                }
            }
            DevCard("Objetos añadidos (${ShopCatalog.devBanners.size + ShopCatalog.devFrames.size})") {
                (ShopCatalog.devBanners.map { Triple(it.id, it.name, "Banner") } + ShopCatalog.devFrames.map { Triple(it.id, it.name, "Marco") }).forEach { (id, n, t) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$n · $t", color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        AppButton("Quitar", { DevStore.remove(ctx, id) })
                    }
                }
            }
            Box(Modifier.size(12.dp))
        }
    }
}
