package app.gbalibrary.ui

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import app.gbalibrary.data.Settings

/** Musica de fondo de la biblioteca (archivo elegido por el usuario). Apagada por defecto. */
object Music {
    private var player: MediaPlayer? = null
    private var playingUri = ""

    /** Aplica los ajustes actuales: arranca, cambia de archivo, ajusta volumen o apaga. */
    fun sync(ctx: Context) {
        val uri = Settings.musicUri.value
        val want = Settings.musicOn.value && uri.isNotEmpty()
        if (!want) {
            release()
            return
        }
        try {
            if (player == null || playingUri != uri) {
                release()
                val mp = MediaPlayer()
                mp.setDataSource(ctx.applicationContext, Uri.parse(uri))
                mp.isLooping = true
                mp.prepare()
                player = mp
                playingUri = uri
            }
            val v = if (Settings.musicMuted.value) 0f else Settings.musicVolume.floatValue
            player?.setVolume(v, v)
            if (player?.isPlaying == false) player?.start()
        } catch (e: Exception) {
            release()
        }
    }

    fun pause() {
        try {
            if (player?.isPlaying == true) player?.pause()
        } catch (e: Exception) {
            // ignorado
        }
    }

    fun release() {
        try {
            player?.release()
        } catch (e: Exception) {
            // ignorado
        }
        player = null
        playingUri = ""
    }
}

/** Sonidos cortos de la interfaz (solo si estan activados en Ajustes). */
object Sfx {
    private var tone: ToneGenerator? = null

    private fun play(type: Int, ms: Int) {
        if (!Settings.uiSounds.value) return
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 35)
            tone?.startTone(type, ms)
        } catch (e: Exception) {
            tone = null
        }
    }

    fun click() = play(ToneGenerator.TONE_PROP_BEEP, 40)
    fun enter() = play(ToneGenerator.TONE_PROP_ACK, 120)
}
