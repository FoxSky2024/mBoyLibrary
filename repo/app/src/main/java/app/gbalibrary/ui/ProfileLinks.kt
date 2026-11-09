package app.gbalibrary.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gbalibrary.data.Profile
import app.gbalibrary.data.ProfileNote
import app.gbalibrary.data.ProfileShare
import app.gbalibrary.data.Settings
import org.json.JSONObject
import java.util.UUID

/** Enlace mboy:// recibido (camara, navegador, chat o pegado a mano) pendiente de mostrar. */
object Incoming {
    val uri = mutableStateOf<Uri?>(null)
}

@Composable
fun IncomingHost() {
    val u = Incoming.uri.value ?: return
    val parsed = remember(u) { ProfileShare.parse(u) }
    val close = { Incoming.uri.value = null }
    when {
        parsed == null -> Dialog(onDismissRequest = close) {
            val p = LocalPalette.current
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(p.panel).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enlace no válido", color = p.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Este enlace de perfil o nota está dañado o incompleto.", color = p.dim)
                AppButton("Cerrar", close, Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY)
            }
        }
        parsed.first == "p" -> VisitorProfileDialog(parsed.second, close)
        else -> NoteIncomingDialog(parsed.second, close)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisitorProfileDialog(j: JSONObject, onClose: () -> Unit) {
    val p = LocalPalette.current
    val own = j.optString("i") == Profile.id.value
    val st = Statuses[j.optInt("s", 0).coerceIn(0, Statuses.lastIndex)]
    val banner = Banners[j.optInt("r", 0).coerceIn(0, Banners.lastIndex)].second
    val fc = FrameColors[j.optInt("f", 0).coerceIn(0, FrameColors.lastIndex)].second
    val ring = if (fc == Color.Unspecified) p.accent else fc
    val name = j.optString("n", "Jugador").ifBlank { "Jugador" }
    val badgeIds = j.optJSONArray("bd")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
    val top = j.optJSONArray("top")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
    var writing by remember { mutableStateOf(false) }
    var noteName by remember { mutableStateOf(Settings.profileName.value) }
    var noteText by remember { mutableStateOf("") }
    var noteLink by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).heightIn(max = 520.dp).clip(RoundedCornerShape(16.dp)).background(p.panel)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(banner)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.card)
                        .border(2.dp, ring, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(name.take(1).uppercase(), color = p.text, fontWeight = FontWeight.Bold, fontSize = 24.sp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1)
                    if (j.optString("h").isNotBlank()) Text(j.optString("h"), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    Text("${st.name}  ·  Nivel ${j.optInt("lv", 1)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (j.optString("l").isNotBlank()) Text(j.optString("l"), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (own) Text("Este es tu propio perfil.", color = p.accent, fontSize = 12.sp)
                if (j.optString("b").isNotBlank()) Text(j.optString("b"), color = p.text, fontSize = 14.sp)
                Text(
                    "${j.optInt("g")} juegos" + (if (j.has("t")) "  ·  ${formatPlayTime(j.optLong("t"))} jugadas" else ""),
                    color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
                if (badgeIds.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AllBadges.filter { it.id in badgeIds }.forEach { b -> BadgePill(b, true) }
                    }
                }
                if (top.isNotEmpty()) {
                    Text("ESCAPARATE", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    top.forEach { Text(it, color = p.text, fontSize = 14.sp, maxLines = 1) }
                }
                if (!writing) {
                    if (!own && j.optInt("nt", 1) == 1) {
                        AppButton("Dejar una nota", { writing = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Edit)
                    } else if (!own) {
                        Text("Este perfil no acepta notas.", color = p.dim, fontSize = 12.sp)
                    }
                } else {
                    OutlinedTextField(noteName, { noteName = it.take(24) }, label = { Text("Tu nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(noteText, { noteText = it.take(200) }, label = { Text("Nota (${noteText.length}/200)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    AppButton(
                        "Generar nota (QR y enlace)",
                        {
                            val n = JSONObject().put("v", 1).put("to", j.optString("i")).put("id", UUID.randomUUID().toString().take(12))
                                .put("n", noteName.ifBlank { "Anónimo" }).put("fi", Profile.id.value).put("x", noteText.trim()).put("ts", System.currentTimeMillis())
                            noteLink = ProfileShare.noteLink(n)
                        },
                        Modifier.fillMaxWidth(), kind = BtnKind.PRIMARY, enabled = noteText.isNotBlank(), icon = Icons.Filled.QrCode2,
                    )
                    Text("La nota se entrega mostrando ese QR (o enviando el enlace) a la persona dueña del perfil.", color = p.dim, fontSize = 11.sp)
                }
                AppButton("Cerrar", onClose, Modifier.fillMaxWidth())
            }
        }
    }
    val nl = noteLink
    if (nl != null) QrDialog("Nota para $name", "Pide que escaneen este QR con la cámara (con la app instalada) o envíales el enlace.", nl) { noteLink = null }
}

@Composable
fun NoteIncomingDialog(j: JSONObject, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val mine = j.optString("to") == Profile.id.value
    val from = j.optString("n", "Anónimo")
    val text = j.optString("x")
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(p.panel).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nota de $from", color = p.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            when {
                !mine -> Text("Esta nota va dirigida a otro perfil, no se puede guardar aquí.", color = p.dim)
                !Profile.acceptNotes.value -> Text("Tienes las notas desactivadas (Perfil > Editar > Privacidad).", color = p.dim)
                else -> Text(text, color = p.text)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mine && Profile.acceptNotes.value) {
                    AppButton("Guardar", {
                        val ok = Profile.addNote(ProfileNote(j.optString("id"), from, j.optString("fi"), text.take(200), j.optLong("ts")))
                        Toast.makeText(ctx, if (ok) "Nota guardada en tu perfil" else "Esa nota ya estaba guardada", Toast.LENGTH_SHORT).show()
                        onClose()
                    }, Modifier.weight(1f), kind = BtnKind.PRIMARY)
                }
                AppButton(if (mine && Profile.acceptNotes.value) "Descartar" else "Cerrar", onClose, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun BadgePill(b: BadgeDef, earned: Boolean) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (earned) p.accent.copy(alpha = 0.2f) else p.card)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Ic(b.icon, size = 16.dp, tint = if (earned) p.accent else p.dim.copy(alpha = 0.5f))
        Text(b.name, color = if (earned) p.text else p.dim.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
