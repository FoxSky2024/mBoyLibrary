package app.gbalibrary.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawWithContent
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Profile
import kotlinx.coroutines.delay

class AchToast(val title: String, val desc: String, val icon: ImageVector, val platinum: Boolean)

/** Sonidos de logro: res/raw/achievement.wav y platinum.wav (acorde largo, como un platino). */
object AchSound {
    private var pool: SoundPool? = null
    private var idA = 0
    private var idP = 0
    private var loaded = 0

    fun init(ctx: Context) {
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(2)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        p.setOnLoadCompleteListener { _, _, st -> if (st == 0) loaded++ }
        idA = p.load(ctx.applicationContext, app.gbalibrary.R.raw.achievement, 1)
        idP = p.load(ctx.applicationContext, app.gbalibrary.R.raw.platinum, 1)
        pool = p
    }

    fun play(platinum: Boolean) {
        if (loaded >= 2) pool?.play(if (platinum) idP else idA, 0.8f, 0.8f, 1, 0, 1f)
    }
}

object Achievements {
    val queue = mutableStateListOf<AchToast>()

    /** Revisa los logros con las estadisticas actuales; encola avisos de los nuevos y del platino. */
    fun check(st: ProfileStats) {
        st.badges.forEach { b ->
            if (Profile.unlock(b.id)) queue.add(AchToast(b.name, b.desc, b.icon, false))
        }
        if (!Profile.platinumDone && AllBadges.all { Profile.unlocked.containsKey(it.id) }) {
            Profile.markPlatinum()
            queue.add(AchToast("¡Platino!", "Has conseguido todos los logros", Icons.Filled.EmojiEvents, true))
        }
    }
}

/** Aviso estilo Steam (esquina superior derecha): tarjeta con barra de color, icono con brillo, barrido de luz, recompensa en Pawb y sonido. */
@Composable
fun AchievementToastHost() {
    val cur = Achievements.queue.firstOrNull()
    var visible by remember { mutableStateOf(false) }
    val shine = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(cur) {
        if (cur != null) {
            val reward = if (cur.platinum) 50 else 10
            app.gbalibrary.data.Pawb.earn(reward)
            AchSound.play(cur.platinum)
            visible = true
            shine.snapTo(0f)
            shine.animateTo(1f, androidx.compose.animation.core.tween(1100, delayMillis = 350))
            delay(if (cur.platinum) 5000 else 3300)
            visible = false
            delay(450)
            if (Achievements.queue.isNotEmpty()) Achievements.queue.removeAt(0)
        }
    }
    Box(Modifier.fillMaxSize().safeNoCutout().padding(12.dp), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(
            visible = visible && cur != null,
            enter = slideInHorizontally(androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it } + fadeIn() +
                androidx.compose.animation.scaleIn(initialScale = 0.9f),
            exit = slideOutHorizontally(androidx.compose.animation.core.tween(300)) { it } + fadeOut(),
        ) {
            if (cur != null) {
                val gold = Color(0xFFFFD54F)
                val tint = if (cur.platinum) gold else Color(0xFF66C0F4)
                val shape = RoundedCornerShape(12.dp)
                val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "achPulse")
                val glow by pulse.animateFloat(0.35f, 0.9f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
                Row(
                    Modifier.widthIn(min = 190.dp, max = 250.dp).clip(shape)
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xF2232B36), Color(0xF2121820))))
                        .border(1.dp, tint.copy(alpha = 0.6f), shape)
                        .drawWithContent {
                            drawContent()
                            // barrido de luz diagonal
                            val x = (size.width + 120f) * shine.value - 120f
                            drawRect(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                                    start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x + 120f, size.height),
                                ),
                            )
                            // barra de color a la izquierda
                            drawRect(tint, size = androidx.compose.ui.geometry.Size(5f, size.height))
                        }
                        .padding(start = 12.dp, top = 7.dp, end = 10.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0.10f))))
                            .border(2.dp, tint.copy(alpha = glow), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Ic(cur.icon, size = 20.dp, tint = tint) }
                    Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(if (cur.platinum) "LOGRO PLATINO" else "LOGRO DESBLOQUEADO", color = tint, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = AppFonts.Display)
                        Text(cur.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, fontFamily = AppFonts.Display)
                        Text(cur.desc, color = Color(0xFF9FB1C1), fontSize = 10.sp, maxLines = 1)
                        PawAmount("+${if (cur.platinum) 50 else 10}", fontSize = 10.sp, iconSize = 11.dp)
                    }
                }
            }
        }
    }
}
