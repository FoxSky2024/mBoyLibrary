package app.gbalibrary.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings

private enum class Net { WIFI, MOBILE, NONE }

/**
 * Bateria (porcentaje + icono por nivel, rayito al cargar, rojo y aviso bajo 20%) e internet
 * (WiFi / datos / sin conexion). Se actualiza en tiempo real con un receptor de bateria y un
 * callback de red. Pequeno y limpio, pensado para una esquina. Se oculta en Ajustes > Interfaz.
 */
@Composable
fun StatusIndicators(modifier: Modifier = Modifier, tint: Color = LocalPalette.current.text) {
    if (!Settings.showStatus.value) return
    val ctx = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    var net by remember { mutableStateOf(Net.NONE) }

    DisposableEffect(ctx) {
        val app = ctx.applicationContext
        fun read(i: Intent?) {
            if (i == null) return
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && s > 0) level = l * 100 / s
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = read(i)
        }
        read(app.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))

        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        fun readNet() {
            val caps = try { cm?.getNetworkCapabilities(cm.activeNetwork) } catch (e: Exception) { null }
            net = when {
                caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> Net.NONE
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Net.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Net.MOBILE
                else -> Net.WIFI
            }
        }
        readNet()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = readNet()
            override fun onLost(network: Network) = readNet()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = readNet()
        }
        try { cm?.registerDefaultNetworkCallback(cb) } catch (e: Exception) { /* sin callback: queda la lectura inicial */ }
        onDispose {
            try { app.unregisterReceiver(receiver) } catch (e: Exception) { /* ignorado */ }
            try { cm?.unregisterNetworkCallback(cb) } catch (e: Exception) { /* ignorado */ }
        }
    }

    val low = level in 0..19 && !charging
    // Aviso una sola vez al bajar de 20%.
    var warned by remember { mutableStateOf(false) }
    LaunchedEffect(low) {
        if (low && !warned) {
            warned = true
            Toast.makeText(ctx, "Batería baja ($level%)", Toast.LENGTH_SHORT).show()
        }
        if (!low) warned = false
    }

    val red = Color(0xFFFF5252)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NetIcon(net, tint, Modifier.size(16.dp))
        if (level >= 0) {
            Text("$level%", color = if (low) red else tint, fontSize = 11.sp, maxLines = 1)
            BatteryIcon(level, charging, if (low) red else tint, Modifier.size(width = 22.dp, height = 12.dp))
        }
    }
}

@Composable
private fun BatteryIcon(level: Int, charging: Boolean, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
            val w = size.width - 3.dp.toPx()
            val h = size.height
            val sw = 1.5.dp.toPx()
            drawRoundRect(color, Offset(sw / 2, sw / 2), Size(w - sw, h - sw), CornerRadius(2.dp.toPx()), style = Stroke(sw))
            // nivel (4 pasos visibles de relleno)
            val frac = (level.coerceIn(0, 100) / 100f)
            val inner = (w - sw * 3) * frac
            if (inner > 0f) drawRoundRect(color.copy(alpha = 0.85f), Offset(sw * 1.5f, sw * 1.5f), Size(inner, h - sw * 3), CornerRadius(1.dp.toPx()))
            // polo
            drawRoundRect(color, Offset(w + 0.5.dp.toPx(), h * 0.3f), Size(2.dp.toPx(), h * 0.4f), CornerRadius(1.dp.toPx()))
            if (charging) {
                // rayito
                val cx = w / 2
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx + 1.5.dp.toPx(), 1.dp.toPx())
                    lineTo(cx - 2.dp.toPx(), h * 0.55f)
                    lineTo(cx, h * 0.55f)
                    lineTo(cx - 1.5.dp.toPx(), h - 1.dp.toPx())
                    lineTo(cx + 2.dp.toPx(), h * 0.42f)
                    lineTo(cx, h * 0.42f)
                    close()
                }
                drawPath(path, Color(0xFFFFD54F))
            }
        }
    }
}

@Composable
private fun NetIcon(net: Net, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.width
        val sw = 1.6.dp.toPx()
        when (net) {
            Net.WIFI, Net.NONE -> {
                val c = if (net == Net.NONE) color.copy(alpha = 0.55f) else color
                val cx = s / 2
                val cy = s * 0.88f
                for (i in 1..3) {
                    val r = s * 0.28f * i
                    drawArc(c, 225f, 90f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
                }
                drawCircle(c, sw * 0.9f, Offset(cx, cy))
                if (net == Net.NONE) {
                    drawLine(Color(0xFFFF5252), Offset(s * 0.1f, s * 0.1f), Offset(s * 0.9f, s * 0.95f), strokeWidth = sw * 1.2f, cap = StrokeCap.Round)
                }
            }
            Net.MOBILE -> {
                val bw = s / 6f
                for (i in 0 until 4) {
                    val h = s * (0.3f + 0.2f * i)
                    drawRoundRect(color, Offset(i * (bw * 1.5f), s - h), Size(bw, h), CornerRadius(1.dp.toPx()))
                }
            }
        }
    }
}
