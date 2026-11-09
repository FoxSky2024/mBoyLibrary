package app.gbalibrary.ui

import java.text.DateFormat
import java.util.Date

fun formatPlayTime(seconds: Long): String = when {
    seconds <= 0 -> "Sin jugar"
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> String.format(java.util.Locale.US, "%.1f h", seconds / 3600.0)
}

fun formatDate(ms: Long): String =
    if (ms <= 0) "Nunca"
    else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1048576.0)
}

/** "hoy", "ayer", "hace 5 dias" o "Nunca". */
fun formatAgo(ms: Long): String {
    if (ms <= 0) return "Nunca"
    val days = ((System.currentTimeMillis() - ms) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Hoy"
        days == 1 -> "Ayer"
        days < 30 -> "Hace $days dias"
        else -> formatDate(ms)
    }
}
