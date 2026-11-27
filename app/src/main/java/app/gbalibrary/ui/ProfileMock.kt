package app.gbalibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Pawb
import app.gbalibrary.data.Settings

/**
 * Vista previa "en tu perfil": muestra el objeto (marco, banner o llavero) puesto sobre las cosas que ya
 * tienes equipadas, con foto, banner y argollas, para ver como quedaria de verdad.
 */
@Composable
fun ProfileMock(previewId: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val fr = ShopCatalog.frame(previewId)
    val bn = ShopCatalog.banner(previewId)
    val kc = ShopCatalog.keychain(previewId)
    val avatarFrame = if (fr != null) previewId else Pawb.aFrame.value
    val bannerFrame = ShopCatalog.frame(if (fr != null) previewId else Pawb.vFrame.value)
    val slots = Pawb.kcSlots.toMutableList().also {
        if (kc != null && previewId !in it) { val f = it.indexOf(""); it[if (f >= 0) f else 2] = previewId }
    }
    val name = Settings.profileName.value.ifBlank { "Jugador" }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(p.panel, p.bg))).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Avatar(76, p.accent, RoundedCornerShape(4.dp), frameId = avatarFrame)
            FramedBox(bannerFrame, Modifier.weight(1f).height(86.dp)) {
                Box(Modifier.fillMaxSize()) {
                    if (bn != null) BannerArt(bn.title, bn.subtitle, bn.colors, bn.style, Modifier.fillMaxSize(), asset = bn.img)
                    else VitrinaContent(Modifier.fillMaxSize())
                    Text(
                        name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(1f, 2f), 6f)),
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 6.dp, end = 10.dp),
                    )
                }
            }
        }
        // argollas con los llaveros (los equipados y, si lo que miras es un llavero, ese tambien)
        Row(Modifier.align(Alignment.TopEnd).padding(end = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0..2) {
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape))
                    val id = slots[i]
                    if (id.isNotEmpty()) KeychainView(id, Modifier.offset(y = (-6).dp), scale = 0.5f)
                }
            }
        }
    }
}
