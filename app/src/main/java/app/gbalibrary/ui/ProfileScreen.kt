@file:OptIn(ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.DataDir
import app.gbalibrary.data.Game
import app.gbalibrary.data.Profile
import app.gbalibrary.data.ProfileShare
import app.gbalibrary.data.Settings
import app.gbalibrary.data.Shots
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Perfil estilo Steam: cabecera con QR, nivel, insignias, escaparate, juegos, notas y edicion completa. */
@Composable
fun ProfileScreen(
    vm: LibraryViewModel,
    onPickAvatar: () -> Unit,
    onBack: () -> Unit,
    onOpenGame: (Game) -> Unit,
    onGallery: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val all by vm.games.collectAsState()
    val savesDir = remember { DataDir.saves(ctx) }
    val shotCount = remember(all) { Shots.list(ctx).size }
    val saveCount = remember(all) { all.count { g -> File(savesDir, "${g.id}.sav").let { it.exists() && it.length() > 0 } } }
    val games = remember(all) { all.sortedWith(compareByDescending<Game> { it.playSeconds }.thenBy { it.title.lowercase() }) }
    val st = remember(all, shotCount, saveCount) { ProfileStats.of(all, shotCount, saveCount) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showQr by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf(false) }

    val fc = FrameColors[Profile.frame.intValue.coerceIn(0, FrameColors.lastIndex)].second
    val ring = if (fc == Color.Unspecified) p.accent else fc
    val banner = Banners[Profile.banner.intValue.coerceIn(0, Banners.lastIndex)].second
    val status = Statuses[Profile.status.intValue.coerceIn(0, Statuses.lastIndex)]

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(banner[0].copy(alpha = 0.6f), Color.Transparent)))) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).background(p.panel.copy(alpha = Settings.boxOpacity.floatValue)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.clickable(onClick = onBack).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Ic(Icons.Filled.ChevronLeft, tint = p.accent)
                Text("Volver", color = p.accent, fontWeight = FontWeight.Bold)
            }
            Text("Mi Perfil", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ---- Cabecera (estilo Steam) ----
            item {
                Box(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Avatar(116, ring, RoundedCornerShape(4.dp))
                        Column(Modifier.weight(1f).padding(end = 44.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(Settings.profileName.value.ifBlank { "Jugador" }, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (Profile.location.value.isNotBlank()) Text(Profile.location.value, color = p.dim, fontSize = 12.sp)
                            if (Profile.headline.value.isNotBlank()) Text(Profile.headline.value, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            if (Profile.bio.value.isNotBlank()) Text(Profile.bio.value, color = p.dim, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // Icono QR monocromo, arriba a la derecha del perfil.
                    Ic(
                        Icons.Filled.QrCode2, size = 28.dp, tint = p.text,
                        modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(8.dp)).clickable { Sfx.click(); showQr = true }.padding(6.dp),
                    )
                }
            }
            // ---- Pestañas ----
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("General", "Juegos", "Notas (${Profile.notes.size})", "Editar").forEachIndexed { i, t ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (tab == i) p.accent else p.card)
                                .clickable { Sfx.click(); tab = i }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(t, color = if (tab == i) p.onAccent else p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
            when (tab) {
                0 -> {
                    if (Profile.bio.value.isNotBlank()) item { Panel { Text(Profile.bio.value, color = p.text, fontSize = 14.sp) } }
                    item {
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Nivel ${st.level}", color = p.accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Text("${st.xp} XP  ·  faltan ${st.xpToNext} para el siguiente", color = p.dim, fontSize = 12.sp)
                            }
                            LinearProgressIndicator(progress = { st.levelFrac }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = p.accent)
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox(Icons.Filled.SportsEsports, "${st.games}", "Juegos", Modifier.weight(1f))
                            StatBox(Icons.Filled.Schedule, if (st.seconds <= 0) "0 h" else formatPlayTime(st.seconds), "Jugado", Modifier.weight(1f))
                            StatBox(Icons.Filled.PhotoCamera, "${st.shots}", "Capturas", Modifier.weight(1f))
                            StatBox(Icons.Filled.FavoriteBorder, "${st.favorites}", "Favoritos", Modifier.weight(1f))
                        }
                    }
                    item {
                        Panel {
                            Text("INSIGNIAS  ${st.badges.size}/${AllBadges.size}", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val got = st.badges.map { it.id }
                                AllBadges.forEach { BadgePill(it, it.id in got) }
                            }
                            Text("Bloqueadas: " + (AllBadges.filter { b -> st.badges.none { it.id == b.id } }.joinToString(" · ") { "${it.name} (${it.desc})" }.ifEmpty { "ninguna" }), color = p.dim, fontSize = 11.sp)
                        }
                    }
                    item {
                        Panel {
                            Text("ESCAPARATE", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            val ids = Profile.showcaseIds()
                            val picked = ids.mapNotNull { id -> all.firstOrNull { it.id == id } }
                            if (!Profile.showGames.value) Text("Los juegos están ocultos para otras personas.", color = p.dim, fontSize = 12.sp)
                            if (picked.isEmpty()) Text("Elige hasta 3 juegos en Editar > Escaparate.", color = p.dim, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                picked.forEach { g ->
                                    Box(Modifier.width(84.dp).height(112.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpenGame(g) }) { CoverImage(g, Modifier.fillMaxSize()) }
                                }
                            }
                        }
                    }
                    item { AppButton("Galería de capturas ($shotCount)", onGallery, Modifier.fillMaxWidth(), icon = Icons.Filled.PhotoLibrary) }
                }
                1 -> {
                    if (games.isEmpty()) item { Text("Aún no tienes juegos en la biblioteca.", color = p.dim) }
                    items(games, key = { it.id }) { g ->
                        val hasSave = remember(g.id) { File(savesDir, "${g.id}.sav").let { it.exists() && it.length() > 0 } }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).opacityPanel(p.card, RoundedCornerShape(10.dp))
                                .clickable { onOpenGame(g) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.width(56.dp).height(76.dp).clip(RoundedCornerShape(6.dp))) { CoverImage(g, Modifier.fillMaxSize()) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(g.title, color = p.text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Ic(Icons.Filled.Schedule, size = 14.dp, tint = p.dim)
                                    Text("${formatPlayTime(g.playSeconds)}   ·   Última vez: ${formatAgo(g.lastPlayed)}", color = p.dim, fontSize = 12.sp)
                                }
                                if (g.progress >= 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LinearProgressIndicator(progress = { g.progress / 100f }, modifier = Modifier.weight(1f).height(6.dp), color = p.accent)
                                        Text("${g.progress}%", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(if (hasSave) "Con partida guardada · progreso sin definir" else "Sin partida guardada", color = p.dim, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        Panel {
                            Text("Muestra tu QR a otra persona: verá tu perfil y podrá dejarte una nota. Cuando te entregue la nota (QR o enlace) se guarda aquí.", color = p.dim, fontSize = 12.sp)
                            AppButton("Mostrar mi QR", { showQr = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.QrCode2)
                            OutlinedTextField(
                                pasted, { pasted = it; pasteError = false }, label = { Text("Pegar enlace mboy:// de perfil o nota") },
                                singleLine = true, isError = pasteError, modifier = Modifier.fillMaxWidth(),
                            )
                            AppButton("Abrir enlace", {
                                val r = ProfileShare.parse(pasted)
                                if (r == null) pasteError = true else { Incoming.uri.value = android.net.Uri.parse(pasted.trim()); pasted = "" }
                            }, Modifier.fillMaxWidth(), enabled = pasted.isNotBlank())
                        }
                    }
                    if (Profile.notes.isEmpty()) item { Text("Aún no tienes notas.", color = p.dim) }
                    items(Profile.notes.toList(), key = { it.id }) { n ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).opacityPanel(p.card, RoundedCornerShape(10.dp)).padding(12.dp),
                            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(n.from, color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(n.text, color = p.text, fontSize = 14.sp)
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(n.ts)), color = p.dim, fontSize = 11.sp)
                            }
                            Ic(Icons.Filled.Delete, tint = p.dim, modifier = Modifier.clip(CircleShape).clickable { Sfx.click(); Profile.removeNote(n.id) }.padding(6.dp))
                        }
                    }
                }
                else -> {
                    item {
                        Panel {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.clickable { onPickAvatar() }) { Avatar(64, ring) }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        Settings.profileName.value, { Settings.setProfileName(it.take(24)) },
                                        label = { Text("Nombre de perfil") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                    )
                                    AppButton("Elegir foto", onPickAvatar)
                                }
                            }
                            OutlinedTextField(Profile.headline.value, { Profile.setHeadline(it.take(40)) }, label = { Text("Lema") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(Profile.location.value, { Profile.setLocation(it.take(30)) }, label = { Text("Ubicación") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(Profile.bio.value, { Profile.setBio(it.take(200)) }, label = { Text("Resumen (${Profile.bio.value.length}/200)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        Panel {
                            Text("ESTADO", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Statuses.forEachIndexed { i, s -> Pill(s.name, Profile.status.intValue == i) { Sfx.click(); Profile.setStatus(i) } }
                            }
                            Text("MARCO DEL AVATAR", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                FrameColors.forEachIndexed { i, f ->
                                    val c = if (f.second == Color.Unspecified) p.accent else f.second
                                    Box(
                                        Modifier.size(34.dp).clip(CircleShape).background(p.card)
                                            .border(if (Profile.frame.intValue == i) 4.dp else 2.dp, c, CircleShape)
                                            .clickable { Sfx.click(); Profile.setFrame(i) },
                                    )
                                }
                            }
                            Text("FONDO DEL PERFIL", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Banners.forEachIndexed { i, b ->
                                    Box(
                                        Modifier.width(54.dp).height(34.dp).clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(b.second))
                                            .border(if (Profile.banner.intValue == i) 3.dp else 0.dp, p.text, RoundedCornerShape(8.dp))
                                            .clickable { Sfx.click(); Profile.setBanner(i) },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Panel {
                            Text("ESCAPARATE (hasta 3 juegos)", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (all.isEmpty()) Text("Añade juegos a la biblioteca para elegirlos.", color = p.dim, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                all.forEach { g -> Pill(g.title.take(22), g.id in Profile.showcaseIds()) { Sfx.click(); Profile.toggleShowcase(g.id) } }
                            }
                        }
                    }
                    item {
                        Panel {
                            Text("PRIVACIDAD (lo que ve quien escanee tu QR)", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            SwitchRow("Mostrar mis juegos", Profile.showGames.value) { Profile.setShowGames(it) }
                            SwitchRow("Mostrar horas jugadas", Profile.showTime.value) { Profile.setShowTime(it) }
                            SwitchRow("Permitir notas", Profile.acceptNotes.value) { Profile.setAcceptNotes(it) }
                        }
                    }
                }
            }
        }
        ProfileSidebar(st, status, ring, shotCount, onTab = { tab = it }, onGallery = onGallery, modifier = Modifier.width(250.dp).fillMaxHeight())
        }
    }
    if (showQr) {
        val link = remember(all, st, showQr, Profile.status.intValue, Profile.headline.value, Profile.bio.value, Profile.location.value, Profile.frame.intValue, Profile.banner.intValue, Profile.showcase.value, Profile.showGames.value, Profile.showTime.value, Profile.acceptNotes.value, Settings.profileName.value) {
            ProfileShare.profileLink(profileSnapshot(all, st))
        }
        QrDialog(
            "Mi perfil", "Quien lo escanee (con la app instalada) verá tu perfil y podrá dejarte una nota. Tu foto no se comparte.",
            link,
        ) { showQr = false }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).opacityPanel(p.panel, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun StatBox(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).opacityPanel(p.card, RoundedCornerShape(10.dp)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Ic(icon, size = 20.dp, tint = p.accent)
        Text(value, color = p.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        Text(label, color = p.dim, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) p.accent else p.card).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, color = if (selected) p.onAccent else p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

/** Columna derecha estilo Steam: nivel, estado, insignias y contadores. */
@Composable
private fun ProfileSidebar(st: ProfileStats, status: StatusDef, ring: Color, shots: Int, onTab: (Int) -> Unit, onGallery: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.background(p.panel.copy(alpha = 0.55f)).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nivel", color = Color.White, fontSize = 22.sp)
            Box(Modifier.clip(RoundedCornerShape(14.dp)).border(2.dp, ring, RoundedCornerShape(14.dp)).padding(horizontal = 9.dp, vertical = 2.dp)) {
                Text("${st.level}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        LinearProgressIndicator(progress = { st.levelFrac }, modifier = Modifier.fillMaxWidth().height(4.dp), color = ring)
        // Insignia destacada (la ultima conseguida), como "Years of Service" en Steam.
        val feat = st.badges.lastOrNull()
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0x66000000)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(p.card), contentAlignment = Alignment.Center) {
                Ic(feat?.icon ?: Icons.Filled.EmojiEvents, size = 28.dp, tint = if (Profile.platinumDone) Color(0xFFFFD54F) else p.accent)
            }
            Column {
                Text(if (Profile.platinumDone) "Platino" else feat?.name ?: "Sin insignias", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${st.xp} XP", color = p.dim, fontSize = 12.sp)
            }
        }
        Text("Editar perfil", color = p.text, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(p.card).clickable { Sfx.click(); onTab(3) }.padding(horizontal = 10.dp, vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(status.color))
            Text(if (status.name == "En línea") "Conectado ahora" else status.name, color = status.color, fontSize = 20.sp)
        }
        SideCount("Logros", "${Profile.unlocked.size}/${AllBadges.size}") { onTab(0) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val got = st.badges
            got.take(8).forEach { b -> Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(p.card), contentAlignment = Alignment.Center) { Ic(b.icon, size = 26.dp, tint = p.accent) } }
            if (Profile.platinumDone) Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33FFD54F)), contentAlignment = Alignment.Center) { Ic(Icons.Filled.EmojiEvents, size = 26.dp, tint = Color(0xFFFFD54F)) }
        }
        SideCount("Juegos", "${st.games}") { onTab(1) }
        SideCount("Capturas de pantalla", "$shots") { onGallery() }
        SideCount("Notas", "${Profile.notes.size}") { onTab(2) }
        SideCount("Favoritos", "${st.favorites}") { }
        SideCount("Fuentes de noticias", "${app.gbalibrary.data.Feeds.sources.size}") { }
        SideCount("Juegos completados", "${st.completed}") { }
    }
}

@Composable
private fun SideCount(label: String, value: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
        Text(value, color = p.dim, fontSize = 20.sp)
    }
}
