package app.gbalibrary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.CustomBanner
import app.gbalibrary.data.Pawb

/** Acceso global a la tienda (se abre desde la barra superior o el perfil). */
object ShopNav {
    /** true = al cerrar la tienda, ir al perfil (para pegar stickers). */
    val openProfile = androidx.compose.runtime.mutableStateOf(false)
    val show = mutableStateOf(false)
}

/** Ventana propia de la musica. */
object MusicNav { val show = mutableStateOf(false) }

private val Swatches = listOf(
    0xFFE8892B, 0xFFD64B4B, 0xFFD86B9A, 0xFF8A4CD6, 0xFF3B7DD8, 0xFF2FBF9B,
    0xFF3FA56B, 0xFFF5B642, 0xFF1A1033, 0xFF123566, 0xFF14432B, 0xFF16202D,
).map { it.toInt() }

/** Tienda de Pawb: fondos, banners, paquetes aleatorios y creador de banners propios. */
@Composable
fun ShopScreen(onBack: () -> Unit) {
    val p = LocalPalette.current
    var tab by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var packOpen by remember { mutableStateOf<Triple<String, Color, List<String>>?>(null) }
    BackHandler { onBack() }
    val ctx = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { app.gbalibrary.data.Ads.init(ctx); app.gbalibrary.data.Ads.load(ctx) }

    Column(Modifier.fillMaxSize().screenBackdrop()) {
        ScreenHeader("Tienda de Pawb", onBack) {
            PawAmount("${Pawb.balance.intValue}", fontSize = 16.sp, iconSize = 18.dp)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Fondos", "Banners", "Marcos", "Skins", "Stickers", "Sobres", "Vitrina", "Gratis").forEachIndexed { i, t ->
                Text(
                    t, color = if (tab == i) p.onPlay else p.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (tab == i) p.accent else p.card).clickable { Sfx.click(); tab = i }.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Gana Pawb jugando (1 por cada 5 min) y con el bonus diario (+25).", color = p.dim, fontSize = 12.sp)
            }
            when (tab) {
                0 -> items(ShopCatalog.backgrounds, key = { it.id }) { b ->
                    ItemCard(
                        name = b.name, rarity = b.rarity, price = b.price, id = b.id, equipped = Pawb.bg.value == b.id,
                        onEquip = { Pawb.equipBg(if (Pawb.bg.value == b.id) "" else b.id) }, onMsg = { msg = it },
                    ) { Canvas(Modifier.fillMaxSize()) { drawStyled(b.colors, b.style) } }
                }
                1 -> {
                    items(ShopCatalog.banners, key = { it.id }) { b ->
                        ItemCard(
                            name = b.name, rarity = b.rarity, price = b.price, id = b.id, equipped = Pawb.banner.value == b.id,
                            onEquip = { Pawb.equipBanner(if (Pawb.banner.value == b.id) "" else b.id) }, onMsg = { msg = it },
                        ) { BannerArt(b.title, b.subtitle, b.colors, b.style, Modifier.fillMaxSize()) }
                    }
                    items(Pawb.custom.value, key = { it.id }) { b -> CustomCard(b, onMsg = { msg = it }) }
                }
                5 -> items(ShopCatalog.packs, key = { it.id }) { pk ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.panel).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(54.dp, 74.dp).clip(RoundedCornerShape(8.dp))
                                .background(Brush.verticalGradient(listOf(pk.color, pk.color.copy(alpha = 0.5f), Color(0xFF14101F))))
                                .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) { PawIcon(26.dp, Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text(pk.name, color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(pk.desc, color = p.dim, fontSize = 12.sp)
                            Text("Probabilidad: común 60% · raro 28% · épico 10% · legendario 2%", color = p.dim, fontSize = 10.sp)
                        }
                        PriceButton("${pk.price}", Pawb.balance.intValue >= pk.price, paw = true) {
                            val r = ShopCatalog.openPack(pk)
                            if (r == null) msg = if (Pawb.balance.intValue < pk.price) "No tienes suficientes Pawb" else "Ya tienes todo lo de este paquete" else packOpen = Triple(pk.name, pk.color, r)
                        }
                    }
                }
                3 -> items(ShopCatalog.skins, key = { it.id }) { k ->
                    ItemCard(
                        name = k.name, rarity = k.rarity, price = k.price, id = k.id, equipped = Pawb.skin.value == k.id, sub = k.theme,
                        onEquip = { Pawb.equipSkin(if (Pawb.skin.value == k.id) "" else k.id) }, onMsg = { msg = it },
                    ) {
                        Row(Modifier.fillMaxSize().background(Color(0xFF1B2430)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            listOf("A" to true, "B" to true, "L" to false, "▶" to false).forEach { (g, round) ->
                                val sh = skinShape(k, if (round) CircleShape else RoundedCornerShape(14.dp), round)
                                val w = if (round) 50.dp else 58.dp
                                Box(
                                    Modifier.size(w, if (round) 50.dp else 34.dp).clip(sh)
                                        .then(if (k.idle2 != null) Modifier.background(Brush.verticalGradient(listOf(k.idle, k.idle2))) else Modifier.background(k.idle))
                                        .then(if (k.pattern != 0) Modifier.drawBehind { skinPattern(k.pattern) } else Modifier)
                                        .border(2.dp, k.border, sh),
                                    contentAlignment = Alignment.Center,
                                ) { Text(g, color = k.glyph, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                            }
                        }
                    }
                }
                4 -> {
                    item {
                        Text("Compra stickers y pégalos en tu perfil, como pegatinas en una consola real. Un sticker se puede pegar las veces que quieras.", color = p.dim, fontSize = 12.sp)
                        var stSrc by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                        val stPick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
                            if (uri != null) stSrc = decodeForCrop(ctx, uri) ?: null.also { msg = "No se pudo leer la imagen" }
                        }
                        PriceButton("Crear sticker con mi imagen (gratis)", true) { stPick.launch("image/*") }
                        stSrc?.let { bm ->
                            BannerCropDialog(bm, aspect = 1f, outW = 512, outH = 512, png = true, round = true, title = "Recortar sticker",
                                onDone = { Pawb.addCustomSticker(ctx, it); stSrc = null; msg = "Sticker creado" }, onCancel = { stSrc = null })
                        }
                        if (Pawb.customStickers.value.isNotEmpty()) Text("MIS STICKERS", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Pawb.customStickers.value.forEach { cs ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    StickerArt(cs, 56.dp)
                                    Text("Pegar", color = p.accent, fontSize = 12.sp, modifier = Modifier.clickable { Pawb.placeSticker(cs); Pawb.stickerEdit.value = true; ShopNav.openProfile.value = true; ShopNav.show.value = false })
                                    Text("Borrar", color = p.dim, fontSize = 11.sp, modifier = Modifier.clickable { Pawb.deleteCustomSticker(ctx, cs) })
                                }
                            }
                        }
                        if (Pawb.placed.value.isNotEmpty()) PriceButton("Editar stickers pegados (${Pawb.placed.value.size})", true) { Pawb.stickerEdit.value = true; ShopNav.openProfile.value = true; ShopNav.show.value = false }
                    }
                    items(ShopCatalog.stickers, key = { it.id }) { st ->
                        ItemCard(
                            name = st.name, rarity = st.rarity, price = st.price, id = st.id, equipped = false, actionLabel = "Pegar",
                            onEquip = { Pawb.placeSticker(st.id); Pawb.stickerEdit.value = true; ShopNav.openProfile.value = true; ShopNav.show.value = false }, onMsg = { msg = it },
                        ) { Box(Modifier.fillMaxSize().background(Color(0xFF1B2430)), contentAlignment = Alignment.Center) { StickerArt(st.id, 56.dp) } }
                    }
                }
                7 -> item {
                    val act = generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
                    val left = Pawb.adsLeft.intValue
                    val ready = app.gbalibrary.data.Ads.ready.value
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ANUNCIO VOLUNTARIO", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Text("Mira un anuncio corto y gana ${Pawb.AD_REWARD} Pawb. Hoy te quedan $left de ${Pawb.AD_DAILY_CAP}.", color = p.text, fontSize = 13.sp)
                        PriceButton(if (left <= 0) "Vuelve mañana" else if (ready) "Ver anuncio  +${Pawb.AD_REWARD}" else "Cargando anuncio…", left > 0 && ready) {
                            if (act != null) app.gbalibrary.data.Ads.show(act) { msg = it }
                        }
                    }
                }
                2 -> items(ShopCatalog.frames, key = { it.id }) { f ->
                    ItemCard(
                        name = f.name, rarity = f.rarity, price = f.price, id = f.id, equipped = Pawb.vFrame.value == f.id,
                        onEquip = { Pawb.equipFrame(if (Pawb.vFrame.value == f.id) "" else f.id) }, onMsg = { msg = it },
                    ) { ItemPreview(f.id) }
                }
                else -> item { VitrinaEditor(onMsg = { msg = it }) }
            }
        }
    }
    msg?.let { m -> AlertDialog(onDismissRequest = { msg = null }, confirmButton = { TextButton({ msg = null }) { Text("OK") } }, text = { Text(m) }) }
    packOpen?.let { (name, color, ids) ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
        ) { PackOpening(name, color, ids) { packOpen = null } }
    }
}

@Composable
private fun VitrinaEditor(onMsg: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(Pawb.vTitle.value) }
    var sub by remember { mutableStateOf(Pawb.vSub.value) }
    var crop by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) crop = decodeForCrop(ctx, uri) ?: null.also { onMsg("No se pudo leer la imagen") }
    }
    crop?.let { bm ->
        BannerCropDialog(bm, onDone = { Pawb.setVitrina(title, sub, android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)); crop = null }, onCancel = { crop = null })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tu vitrina se muestra en tu perfil. Elige un marco de tus sobres y cambia lo que hay dentro.", color = p.dim, fontSize = 12.sp)
        Vitrina(Modifier.fillMaxWidth().height(120.dp))
        Text("MARCO", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val sel = Pawb.vFrame.value
            Text("Sin marco", color = if (sel.isEmpty()) p.onPlay else p.text, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (sel.isEmpty()) p.accent else p.card).clickable { Pawb.equipFrame("") }.padding(horizontal = 14.dp, vertical = 22.dp))
            ShopCatalog.frames.filter { Pawb.has(it.id) }.forEach { f ->
                Box(Modifier.size(92.dp, 64.dp).clip(RoundedCornerShape(10.dp)).border(if (sel == f.id) 2.dp else 0.dp, p.accent, RoundedCornerShape(10.dp)).clickable { Pawb.equipFrame(f.id) }) { ItemPreview(f.id) }
            }
        }
        if (ShopCatalog.frames.none { Pawb.has(it.id) }) Text("Aún no tienes marcos: consíguelos en la pestaña Marcos o en los sobres.", color = p.dim, fontSize = 11.sp)
        Text("DISEÑO DE BASE", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cur = Pawb.banner.value
            Text("Ninguno", color = if (cur.isEmpty()) p.onPlay else p.text, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (cur.isEmpty()) p.accent else p.card).clickable { Pawb.equipBanner("") }.padding(horizontal = 14.dp, vertical = 8.dp))
            ShopCatalog.banners.filter { Pawb.has(it.id) || it.price == 0 }.forEach { b ->
                Text(b.name, color = if (cur == b.id) p.onPlay else p.text, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (cur == b.id) p.accent else p.card).clickable { Pawb.equipBanner(b.id) }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
            Pawb.custom.value.forEach { b ->
                Text(b.name, color = if (cur == b.id) p.onPlay else p.text, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (cur == b.id) p.accent else p.card).clickable { Pawb.equipBanner(b.id) }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        androidx.compose.material3.OutlinedTextField(title, { title = it; Pawb.setVitrina(it, sub, Pawb.vImg.value) }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        androidx.compose.material3.OutlinedTextField(sub, { sub = it; Pawb.setVitrina(title, it, Pawb.vImg.value) }, label = { Text("Subtítulo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceButton(if (Pawb.vImg.value.isEmpty()) "Imagen de fondo" else "Cambiar / recortar imagen", true) { pick.launch("image/*") }
            if (Pawb.vImg.value.isNotEmpty()) PriceButton("Quitar imagen", true) { Pawb.setVitrina(title, sub, "") }
        }
    }
}

@Composable
private fun PriceButton(text: String, enabled: Boolean, paw: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val c = if (enabled) p.onPlay else p.dim
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (enabled) p.play else p.card).clickable { Sfx.click(); onClick() }.padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (paw) PawIcon(14.dp, c)
        Text(text, color = c, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ItemCard(
    name: String, rarity: Rarity, price: Int, id: String, equipped: Boolean,
    onEquip: () -> Unit, onMsg: (String) -> Unit, actionLabel: String? = null, sub: String? = null, preview: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val owned = Pawb.has(id) || price == 0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.panel).border(1.dp, rarity.color.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(8.dp))) { preview() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(name, color = p.text, fontWeight = FontWeight.Bold)
                Text(rarity.label + if (sub != null) " · $sub" else "", color = rarity.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            if (owned) {
                PriceButton(actionLabel ?: if (equipped) "✓ Equipado" else "Equipar", true) { onEquip() }
            } else {
                PriceButton("$price", Pawb.balance.intValue >= price, paw = true) {
                    if (Pawb.buy(id, price)) onMsg("Compraste «$name»") else onMsg("Te faltan ${price - Pawb.balance.intValue} Pawb")
                }
            }
        }
    }
}

@Composable
private fun CustomCard(b: CustomBanner, onMsg: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.panel).border(1.dp, p.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BannerArt(b.title, b.subtitle, b.colors.map { Color(it) }, b.style, Modifier.fillMaxWidth().height(86.dp), b.img)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(b.name, color = p.text, fontWeight = FontWeight.Bold)
                Text("de ${b.creator} · precio al compartir ${b.price}", color = p.dim, fontSize = 11.sp)
            }
            PriceButton(if (Pawb.banner.value == b.id) "✓ Equipado" else "Equipar", true) { Pawb.equipBanner(if (Pawb.banner.value == b.id) "" else b.id) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceButton("Copiar enlace", true) { clip.setText(AnnotatedString(Pawb.shareLink(b))); onMsg("Enlace copiado. Quien lo importe pagará ${b.price}.") }
            PriceButton("Eliminar", false) { Pawb.deleteCustom(b.id) }
        }
    }
}

@Composable
private fun BannerCreator(onMsg: (String) -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("MI BANNER") }
    var sub by remember { mutableStateOf("Hecho por mí") }
    var c1 by remember { mutableIntStateOf(Swatches[3]) }
    var c2 by remember { mutableIntStateOf(Swatches[9]) }
    var style by remember { mutableIntStateOf(1) }
    var price by remember { mutableStateOf("50") }
    var link by remember { mutableStateOf("") }
    var img by remember { mutableStateOf("") }
    var cropSrc by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val ctx = LocalContext.current
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) cropSrc = decodeForCrop(ctx, uri) ?: null.also { onMsg("No se pudo leer la imagen") }
    }
    cropSrc?.let { bm -> BannerCropDialog(bm, onDone = { img = android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP); cropSrc = null }, onCancel = { cropSrc = null }) }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("CREAR BANNER", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        BannerArt(title.ifBlank { " " }, sub, listOf(Color(c1), Color(c2)), style, Modifier.fillMaxWidth().height(96.dp), img)
        OutlinedTextField(name, { name = it.take(24) }, label = { Text("Nombre del objeto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(title, { title = it.take(22) }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(sub, { sub = it.take(40) }, label = { Text("Subtítulo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Color 1", color = p.dim, fontSize = 11.sp)
        SwatchRow(c1) { c1 = it }
        Text("Color 2", color = p.dim, fontSize = 11.sp)
        SwatchRow(c2) { c2 = it }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StyleNames.forEachIndexed { i, n ->
                Text(n, color = if (style == i) p.onPlay else p.text, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (style == i) p.accent else p.card).clickable { style = i }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        OutlinedTextField(price, { price = it.filter(Char::isDigit).take(3) }, label = { Text("Precio en Pawb al compartirlo (0-999)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceButton(if (img.isEmpty()) "Elegir imagen de fondo" else "Cambiar / recortar imagen", true) { pick.launch("image/*") }
            if (img.isNotEmpty()) PriceButton("Quitar imagen", false) { img = "" }
        }
        PriceButton("Guardar banner  ${Pawb.CREATE_COST}", Pawb.balance.intValue >= Pawb.CREATE_COST, paw = true) {
            val b = Pawb.createBanner(name, title, sub, listOf(c1, c2), style, price.toIntOrNull() ?: 0, img)
            onMsg(if (b == null) "Crear un banner cuesta ${Pawb.CREATE_COST}. Te faltan ${Pawb.CREATE_COST - Pawb.balance.intValue}." else "Banner guardado (−${Pawb.CREATE_COST}). Está en la pestaña Banners: equípalo o copia su enlace.")
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(p.panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("COMPRAR A OTRO JUGADOR", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text("Pega el enlace mboyitem:// que te compartió otro jugador.", color = p.dim, fontSize = 12.sp)
        OutlinedTextField(link, { link = it }, label = { Text("Enlace mboyitem://") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        PriceButton("Importar", link.isNotBlank()) {
            val b = Pawb.parseLink(link)
            if (b == null) onMsg("Enlace no válido") else {
                val err = Pawb.importBanner(b)
                onMsg(err ?: "Importaste «${b.name}» de ${b.creator} por ${b.price}"); if (err == null) link = ""
            }
        }
    }
}

@Composable
private fun SwatchRow(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Swatches.forEach { c ->
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(c)).border(if (c == selected) 2.dp else 0.dp, Color.White, CircleShape).clickable { onPick(c) })
        }
    }
}

/** Imagen de fondo de banner: recorte centrado 3:1, 480x160, JPEG ligero en base64 (cabe en el enlace para compartir). */
object BannerImages {
    fun fromUri(ctx: android.content.Context, uri: android.net.Uri): String? = try {
        val src = ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
        val w = 480; val h = 160
        val scale = maxOf(w / src.width.toFloat(), h / src.height.toFloat())
        val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val m = android.graphics.Matrix().apply { postScale(scale, scale); postTranslate((w - src.width * scale) / 2f, (h - src.height * scale) / 2f) }
        c.drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        val bos = java.io.ByteArrayOutputStream()
        out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 55, bos)
        android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
    } catch (e: Exception) { null }
}
