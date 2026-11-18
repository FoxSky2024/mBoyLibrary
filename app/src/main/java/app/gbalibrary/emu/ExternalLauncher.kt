package app.gbalibrary.emu

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.gbalibrary.data.ConsoleDef
import app.gbalibrary.data.EmulatorDef
import app.gbalibrary.data.Game

sealed interface ExternalResult {
    data class Ok(val emulator: EmulatorDef) : ExternalResult
    data class NoEmulatorAvailable(val console: ConsoleDef) : ExternalResult
    data class NotInstalled(val console: ConsoleDef) : ExternalResult
    data class Failed(val reason: String) : ExternalResult
}

/**
 * Lanza los juegos de sistemas que no ejecuta el motor propio, con el emulador real instalado.
 * Intent ACTION_VIEW + URI del juego con permiso de lectura concedido. Los nombres de paquete /
 * actividad estan en [app.gbalibrary.data.Systems] y pueden variar segun version o fork.
 */
object ExternalLauncher {

    fun isInstalled(ctx: Context, e: EmulatorDef): Boolean = try {
        ctx.packageManager.getPackageInfo(e.pkg, 0); true
    } catch (_: Exception) {
        false
    }

    /** Primer emulador instalado de la consola, o null. */
    fun resolve(ctx: Context, console: ConsoleDef): EmulatorDef? =
        console.emulators.firstOrNull { isInstalled(ctx, it) }

    fun launch(activity: Activity, game: Game, console: ConsoleDef): ExternalResult {
        if (console.emulators.isEmpty()) return ExternalResult.NoEmulatorAvailable(console)
        val emu = resolve(activity, console) ?: return ExternalResult.NotInstalled(console)

        fun build(withComponent: Boolean) = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(game.uri), "application/octet-stream")
            if (withComponent && emu.activity != null) component = ComponentName(emu.pkg, emu.activity)
            else setPackage(emu.pkg)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

        return try {
            activity.startActivity(build(true)); ExternalResult.Ok(emu)
        } catch (_: Exception) {
            // La actividad explicita puede no existir en esta version/fork: que Android resuelva dentro del paquete.
            try {
                activity.startActivity(build(false)); ExternalResult.Ok(emu)
            } catch (e: Exception) {
                ExternalResult.Failed(e.message ?: "No se pudo abrir ${emu.label}")
            }
        }
    }

    fun openStorePage(activity: Activity, e: EmulatorDef) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${e.pkg}")))
        } catch (_: Exception) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${e.pkg}")))
            } catch (_: Exception) {
            }
        }
    }
}
