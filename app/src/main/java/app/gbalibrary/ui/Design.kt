@file:OptIn(ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    icon: ImageVector? = null,
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
            .background(bg)
            .then(if (kind == BtnKind.SECONDARY) Modifier.border(1.dp, p.accent.copy(alpha = 0.55f), shape) else Modifier)
            .clickable(enabled = enabled) { Sfx.click(); onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Ic(icon, size = if (big) 26.dp else 20.dp, tint = fg)
            Text(
                text, color = fg, fontSize = (if (big) 20 else Ds.ButtonSp.toInt()).sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Etiqueta del sistema de un juego: GBA / GBC / GB. */
fun systemOf(g: Game): String {
    if (g.external.isNotEmpty()) return app.gbalibrary.data.Systems.externalById(g.external)?.short ?: g.external
    val name = (g.entry.ifEmpty { g.fileName }).lowercase()
    return when {
        name.endsWith(".nds") -> "DS"
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
        "DS" -> Color(0xFF8E24AA)
        else -> app.gbalibrary.data.Systems.byShort(system)?.accent?.copy(alpha = 0.85f) ?: Color(0xFF6D6D6D)
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
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(p.card), contentAlignment = Alignment.Center) {
            Ic(icon, size = 44.dp, tint = p.dim)
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
fun RailItem(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
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
        if (icon != null) Ic(icon, size = 20.dp, tint = if (selected) p.accent else p.dim)
        Text(label, color = if (selected) p.accent else p.text, fontSize = Ds.BodySp.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
