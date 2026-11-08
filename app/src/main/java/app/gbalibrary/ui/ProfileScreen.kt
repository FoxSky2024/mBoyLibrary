package app.gbalibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.DataDir
import app.gbalibrary.data.Game
import app.gbalibrary.data.Settings
import app.gbalibrary.data.Shots
import java.io.File

/** "Mi Perfil": avatar, nombre, totales y la lista de juegos con horas, ultima vez y progreso. */
@Composable
fun ProfileScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpenGame: (Game) -> Unit, onGallery: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val all by vm.games.collectAsState()
    val savesDir = remember { DataDir.saves(ctx) }
    val shotCount = remember(all) { Shots.list(ctx).size }
    val games = remember(all) { all.sortedWith(compareByDescending<Game> { it.playSeconds }.thenBy { it.title.lowercase() }) }
    val totalSeconds = all.sumOf { it.playSeconds }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).background(p.panel.copy(alpha = Settings.boxOpacity.floatValue)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("← Volver", color = p.accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
            Text("Mi Perfil", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).opacityPanel(p.panel, RoundedCornerShape(12.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Avatar(80)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Settings.profileName.value.ifBlank { "Jugador" }, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${all.size} juegos en total", color = p.text, fontSize = 14.sp)
                        Text("Horas jugadas: ${if (totalSeconds <= 0) "0 h" else formatPlayTime(totalSeconds)}", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) { Text("Galería de capturas ($shotCount)") }
            }
            item { Text("MIS JUEGOS", color = p.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
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
                        Text("⏱ ${formatPlayTime(g.playSeconds)}   ·   Última vez: ${formatAgo(g.lastPlayed)}", color = p.dim, fontSize = 12.sp)
                        if (g.progress >= 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(progress = { g.progress / 100f }, modifier = Modifier.weight(1f).height(6.dp), color = p.accent)
                                Text("${g.progress}%", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(if (hasSave) "Con partida guardada · progreso sin definir" else "Sin partida guardada", color = p.dim, fontSize = 11.sp)
                        }
                        if (g.progress >= 0 && hasSave) Text("Con partida guardada", color = p.dim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
