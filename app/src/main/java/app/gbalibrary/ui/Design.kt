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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Game

/**
 * Sistema de diseno unico de la app. Todas las pantallas (biblioteca, ficha, pausa, ajustes)
 * usan estas medidas y estos componentes: nada de tamanos sueltos por pantalla.
 * Tamanos en dp, textos en sp.
 */
object Ds {
    // Espaciado
    val Gap = 8.dp
    val GapL = 12.dp
    val Pad = 16.dp

    // Radios
    val RadiusS = 8.dp
    val RadiusM = 12.dp

    // Altos tactiles
    val ButtonH = 52.dp
    val ButtonBigH = 64.dp
    val RowH = 48.dp

    // Texto (sp)
    const val TitleSp = 20f
    const val BodySp = 15f
    const val LabelSp = 13f
    const val CaptionSp = 12f
    const val ButtonSp = 16f
}

enum class BtnKind { PRIMARY, SECONDARY, DANGER }

/**
 * Boton unico de la app: esquinas redondeadas completas, alto minimo tactil, el texto nunca se
 * corta (hasta 2 lineas) y siempre tiene contraste suficiente con su fondo.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.SECONDARY,
    big: Boolean = false,
    enabled: Boolean = true,
    icon: String? = null,
) {
    val p = LocalPalette.current
    val bg = when (kind) {
        BtnKind.PRIMARY -> p.play
        BtnKind.SECONDARY -> p.card
        BtnKind.DANGER -> Color(0xFFB71C1C)
    }
    val fg = when (kind) {
        BtnKind.PRIMARY -> p.onPlay
        BtnKind.SECONDARY -> p.text
        BtnKind.DANGER -> Color.White
    }
    val shape = RoundedCornerShape(Ds.RadiusM)
    Box(
        modifier
            .heightIn(min = if (big) Ds.ButtonBigH else Ds.ButtonH)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .then(
                if (kind == BtnKind.PRIMARY) {
                    // Verde estilo Steam: degradado vertical claro -> oscuro
                    Modifier.background(Brush.verticalGradient(listOf(lerp(bg, Color.White, 0.14f), lerp(bg, Color.Black, 0.2f))))
                } else Modifier.background(bg),
            )
            .then(if (kind == BtnKind.SECONDARY) Modifier.border(1.dp, p.accent.copy(alpha = 0.55f), shape) else Modifier)
            .clickable(enabled = enabled) { Sfx.click(); onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Text(icon, color = fg, fontSize = (if (big) 24 else 18).sp)
            Text(
                text, color = fg, fontSize = (if (big) 20 else Ds.ButtonSp.toInt()).sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Etiqueta del sistema de un juego: GBA / GBC / GB. */
fun systemOf(g: Game): String {
    val name = (g.entry.ifEmpty { g.fileName }).lowercase()
    return when {
        name.endsWith(".gbc") -> "GBC"
        name.endsWith(".gb") -> "GB"
        name.endsWith(".gba") || name.endsWith(".agb") -> "GBA"
        else -> if (g.code.isNotEmpty()) "GBA" else "GB"
    }
}

/** Insignia pequena del sistema. */
@Composable
fun SystemBadge(system: String, modifier: Modifier = Modifier) {
    val bg = when (system) {
        "GBA" -> Color(0xFF3949AB)
        "GBC" -> Color(0xFF00897B)
        else -> Color(0xFF6D6D6D)
    }
    Text(
        system, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Chip de filtro grande y legible (no se corta). */
@Composable
fun AppChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    val bg = if (selected) p.accent else p.card
    val fg = if (selected) p.onAccent else p.text
    Text(
        label, color = fg, fontSize = Ds.LabelSp.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(bg)
            .clickable { Sfx.click(); onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Fila de chips que salta de linea en vez de cortarse. */
@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Ds.Gap), verticalArrangement = Arrangement.spacedBy(Ds.Gap)) {
        options.forEachIndexed { i, n -> AppChip(n, i == selected, { onSelect(i) }) }
    }
}

/** Tarjeta de seccion: fondo solido (nada se ve por detras), titulo de acento. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Ds.RadiusM))
            .background(p.panel)
            .border(1.dp, p.card, RoundedCornerShape(Ds.RadiusM))
            .padding(Ds.Pad),
        verticalArrangement = Arrangement.spacedBy(Ds.GapL),
    ) {
        Text(title.uppercase(), color = p.accent, fontSize = Ds.CaptionSp.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        content()
    }
}

/** Texto de ayuda tenue. */
@Composable
fun Hint(text: String) {
    Text(text, color = LocalPalette.current.dim, fontSize = Ds.CaptionSp.sp, lineHeight = 17.sp)
}

/** Interruptor con etiqueta; la etiqueta ocupa el ancho disponible y salta de linea. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Ds.RowH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ds.GapL),
    ) {
        Text(label, color = p.text, fontSize = Ds.BodySp.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onAccent, checkedTrackColor = p.accent,
                uncheckedThumbColor = p.dim, uncheckedTrackColor = p.card, uncheckedBorderColor = p.dim,
            ),
        )
    }
}

/** Slider con etiqueta y valor. */
@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onFinished: (() -> Unit)? = null, onChange: (Float) -> Unit) {
    val p = LocalPalette.current
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = p.text, fontSize = Ds.BodySp.sp, modifier = Modifier.weight(1f))
            Text(shown, color = p.accent, fontSize = Ds.BodySp.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, onValueChangeFinished = onFinished,
            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.card),
        )
    }
}

/** Fila de botones que salta de linea. */
@Composable
fun ButtonFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Ds.Gap), verticalArrangement = Arrangement.spacedBy(Ds.Gap)) { content() }
}

/** Estado vacio con icono grande, mensaje y acciones. */
@Composable
fun EmptyState(icon: String, title: String, body: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(p.card), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 40.sp)
        }
        Text(title, color = p.text, fontSize = Ds.TitleSp.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text(body, color = p.dim, fontSize = Ds.BodySp.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp).fillMaxWidth(0.9f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Ds.Gap)) { actions() }
    }
}

/** Cabecera de pantalla: boton volver + titulo (alto fijo, sin encimarse). */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(p.panel).padding(horizontal = Ds.GapL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ds.GapL),
    ) {
        Text(
            "← Volver", color = p.accent, fontSize = Ds.BodySp.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(Ds.RadiusS)).clickable { Sfx.click(); onBack() }.padding(horizontal = 10.dp, vertical = 12.dp),
        )
        Text(title, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing(this)
    }
}

/** Elemento de menu lateral (rail) de Ajustes. */
@Composable
fun RailItem(label: String, icon: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .background(if (selected) p.card else Color.Transparent)
            .clickable { Sfx.click(); onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(width = 4.dp, height = 24.dp).background(if (selected) p.accent else Color.Transparent))
        if (icon.isNotEmpty()) Text(icon, fontSize = 18.sp)
        Text(label, color = if (selected) p.accent else p.text, fontSize = Ds.BodySp.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Encabezado de seccion estilo Steam: texto en mayusculas tenue y una linea fina. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ds.GapL)) {
        Text(text.uppercase(), color = p.dim, fontSize = Ds.CaptionSp.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(p.card))
        trailing()
    }
}

/** Pestanas con linea inferior de acento (como las pestanas de la pagina de un juego en Steam). */
@Composable
fun SteamTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().background(p.panel).horizontalScroll(rememberScrollState())) {
        labels.forEachIndexed { i, l ->
            Column(
                Modifier.clickable { Sfx.click(); onSelect(i) }.padding(horizontal = 18.dp).heightIn(min = 46.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(l.uppercase(), color = if (i == selected) p.text else p.dim, fontSize = Ds.LabelSp.sp, fontWeight = FontWeight.Bold, maxLines = 1, letterSpacing = 0.5.sp, modifier = Modifier.padding(vertical = 12.dp))
                Box(Modifier.height(3.dp).fillMaxWidth().background(if (i == selected) p.accent else Color.Transparent))
            }
        }
    }
}

/** Bloque de dato (ULTIMA VEZ / TIEMPO DE JUEGO...) de la barra de acciones. */
@Composable
fun StatBlock(label: String, value: String) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), color = p.dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
        Text(value, color = p.text, fontSize = Ds.BodySp.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Miniatura de una ranura de guardado (PNG junto al estado). Si no existe, un cuadro con icono. */
@Composable
fun SlotThumb(path: String?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = if (path == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (java.io.File(path).exists()) android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() else null
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(modifier.size(width = 72.dp, height = 48.dp).clip(RoundedCornerShape(6.dp)).background(p.bg), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            androidx.compose.foundation.Image(b, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
        } else {
            Text("💾", fontSize = 18.sp)
        }
    }
}
