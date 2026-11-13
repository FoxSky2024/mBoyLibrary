package app.gbalibrary.ui

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

/** Aviso estilo Steam en la esquina superior derecha, con sonido. */
@Composable
fun AchievementToastHost() {
    val cur = Achievements.queue.firstOrNull()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(cur) {
        if (cur != null) {
            AchSound.play(cur.platinum)
            visible = true
            delay(if (cur.platinum) 6000 else 4200)
            visible = false
            delay(450)
            if (Achievements.queue.isNotEmpty()) Achievements.queue.removeAt(0)
        }
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(
            visible = visible && cur != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            if (cur != null) {
                val gold = Color(0xFFFFD54F)
                val tint = if (cur.platinum) gold else Color(0xFF66C0F4)
                Row(
                    Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xF2171A21))
                        .border(1.5.dp, tint, RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.2f)).border(2.dp, tint, CircleShape), contentAlignment = Alignment.Center) {
                        Ic(cur.icon, size = 26.dp, tint = tint)
                    }
                    Column {
                        Text(if (cur.platinum) "LOGRO PLATINO" else "Logro desbloqueado", color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(cur.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(cur.desc, color = Color(0xFF8FA3B3), fontSize = 12.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}
