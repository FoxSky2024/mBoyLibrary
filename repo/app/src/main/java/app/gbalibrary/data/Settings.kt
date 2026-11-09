package app.gbalibrary.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** Ajustes globales con estado observable por Compose, guardados en SharedPreferences. */
object Settings {
    private var sp: SharedPreferences? = null

    // Video
    val filter = mutableIntStateOf(0) // 0 pixel perfect, 1 suave, 2 CRT
    val brightness = mutableFloatStateOf(1f)
    val scale = mutableIntStateOf(0)
    val showFps = mutableStateOf(false)

    // Audio
    val volume = mutableIntStateOf(100)
    val muted = mutableStateOf(false)

    // Controles tactiles
    val padOpacity = mutableFloatStateOf(0.55f)
    val padSize = mutableFloatStateOf(1f)
    /** Nintendo DS: 0 arriba/abajo, 1 izquierda/derecha, 2 solo arriba, 3 solo abajo. */
    val dsLayout = mutableIntStateOf(1)
    val padVisible = mutableStateOf(true)
    val perGameLayout = mutableStateOf(false)

    // Apariencia
    val theme = mutableIntStateOf(0)
    val bgMode = mutableIntStateOf(0) // 0 por defecto, 1 imagen, 2 color
    val bgColor = mutableIntStateOf(0xFF1B2838.toInt())
    val bgStamp = mutableIntStateOf(0)
    val appName = mutableStateOf("GBA Library")
    val profileName = mutableStateOf("Jugador")
    val avatarStamp = mutableIntStateOf(0)

    // Musica y sonidos de la biblioteca
    val musicUri = mutableStateOf("")
    val musicOn = mutableStateOf(false)
    val musicMuted = mutableStateOf(false)
    val musicVolume = mutableFloatStateOf(0.5f)
    val uiSounds = mutableStateOf(false)
    val musicFolder = mutableStateOf("")
    val musicShuffle = mutableStateOf(false)


    // Mando fisico
    val padAutoHide = mutableStateOf(true) // ocultar tactiles al conectar mando
    val padRumble = mutableStateOf(true)

    // Escala de la interfaz (biblioteca y ajustes): 0.7 .. 1.25, por defecto 0.85 (compacto)
    val uiScale = mutableFloatStateOf(0.85f)

    // Opacidad de paneles / barras / tarjetas: 40%..100% (sin glass ni blur)
    val boxOpacity = mutableFloatStateOf(0.9f)

    // Animaciones / interfaz / musica en juego / rendimiento (v0.3.3)
    val entryAnim = mutableStateOf(true)
    val showStatus = mutableStateOf(true) // bateria + internet
    val musicInGame = mutableStateOf(false)
    val renderQuality = mutableIntStateOf(1) // 0 bajo, 1 medio, 2 alto
    val fpsLimit = mutableIntStateOf(60) // 30, 60, 0 = sin limite
    val ffSpeed = mutableIntStateOf(2) // 2, 3, 4
    val frameSkip = mutableStateOf(false)
    val audioSync = mutableStateOf(true)

    // Margenes de la interfaz (dp) y ventana de video PiP
    val uiMargin = mutableIntStateOf(0)
    val pipCorner = mutableIntStateOf(1) // 0 sup-izq, 1 sup-der, 2 inf-izq, 3 inf-der
    val pipSize = mutableIntStateOf(1) // 0 pequeno, 1 mediano, 2 grande
    val pipOpacity = mutableFloatStateOf(0.95f)
    val pipOnTop = mutableStateOf(true)

    // Portadas desde internet
    val autoCovers = mutableStateOf(true)
    val sgdbKey = mutableStateOf("") // clave opcional de SteamGridDB

    // Orden de la biblioteca (nombre del SortMode)
    val sortMode = mutableStateOf("RECENT")

    private var appCtx: Context? = null

    fun init(ctx: Context) {
        if (sp != null) return
        val app = ctx.applicationContext
        appCtx = app
        val p = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        sp = p
        // Instalacion nueva con una carpeta GBALibrary existente: se restauran los ajustes.
        Persist.restoreConfigIfNeeded(app)
        readAll(p)
        Persist.touch(app)
    }

    /** Vuelve a leer las preferencias (despues de importar una copia o de cambiar la carpeta de datos). */
    fun reload(ctx: Context) {
        val p = sp ?: return init(ctx)
        readAll(p)
    }

    private fun readAll(p: SharedPreferences) {
        filter.intValue = p.getInt("filter", 0)
        brightness.floatValue = p.getFloat("brightness", 1f)
        scale.intValue = p.getInt("scale", 0)
        showFps.value = p.getBoolean("showFps", false)
        volume.intValue = p.getInt("volume", 100)
        muted.value = p.getBoolean("muted", false)
        padOpacity.floatValue = p.getFloat("padOpacity", 0.55f)
        padSize.floatValue = p.getFloat("padSize", 1f)
        dsLayout.intValue = p.getInt("dsLayout", 1)
        padVisible.value = p.getBoolean("padVisible", true)
        perGameLayout.value = p.getBoolean("perGameLayout", false)
        theme.intValue = p.getInt("theme", 0)
        bgMode.intValue = p.getInt("bgMode", 0)
        bgColor.intValue = p.getInt("bgColor", 0xFF1B2838.toInt())
        bgStamp.intValue = p.getInt("bgStamp", 0)
        appName.value = p.getString("appName", "GBA Library") ?: "GBA Library"
        profileName.value = p.getString("profileName", "Jugador") ?: "Jugador"
        avatarStamp.intValue = p.getInt("avatarStamp", 0)
        musicUri.value = p.getString("musicUri", "") ?: ""
        musicOn.value = p.getBoolean("musicOn", false)
        musicMuted.value = p.getBoolean("musicMuted", false)
        musicVolume.floatValue = p.getFloat("musicVolume", 0.5f)
        uiSounds.value = p.getBoolean("uiSounds", false)
        musicFolder.value = p.getString("musicFolder", "") ?: ""
        musicShuffle.value = p.getBoolean("musicShuffle", false)
        padAutoHide.value = p.getBoolean("padAutoHide", true)
        padRumble.value = p.getBoolean("padRumble", true)
        uiScale.floatValue = p.getFloat("uiScale", 0.85f).coerceIn(0.7f, 1.25f)
        boxOpacity.floatValue = p.getFloat("boxOpacity", 0.9f).coerceIn(0.4f, 1f)
        entryAnim.value = p.getBoolean("entryAnim", true)
        showStatus.value = p.getBoolean("showStatus", true)
        musicInGame.value = p.getBoolean("musicInGame", false)
        renderQuality.intValue = p.getInt("renderQuality", 1).coerceIn(0, 2)
        fpsLimit.intValue = p.getInt("fpsLimit", 60)
        ffSpeed.intValue = p.getInt("ffSpeed", 2).coerceIn(2, 4)
        frameSkip.value = p.getBoolean("frameSkip", false)
        audioSync.value = p.getBoolean("audioSync", true)
        uiMargin.intValue = p.getInt("uiMargin", 0).coerceIn(0, 48)
        pipCorner.intValue = p.getInt("pipCorner", 1).coerceIn(0, 3)
        pipSize.intValue = p.getInt("pipSize", 1).coerceIn(0, 2)
        pipOpacity.floatValue = p.getFloat("pipOpacity", 0.95f).coerceIn(0.2f, 1f)
        pipOnTop.value = p.getBoolean("pipOnTop", true)
        autoCovers.value = p.getBoolean("autoCovers", true)
        sgdbKey.value = p.getString("sgdbKey", "") ?: ""
        sortMode.value = p.getString("sortMode", "RECENT") ?: "RECENT"
    }

    // ---- Guardado (cada setter actualiza el estado y persiste) ----

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp?.edit()?.apply { block(); apply() }
        appCtx?.let { Persist.touch(it) } // espejo en GBALibrary/config.json
    }

    fun setFilter(v: Int) { filter.intValue = v; edit { putInt("filter", v) } }
    fun setBrightness(v: Float) { brightness.floatValue = v; edit { putFloat("brightness", v) } }
    fun setScale(v: Int) { scale.intValue = v; edit { putInt("scale", v) } }
    fun setShowFps(v: Boolean) { showFps.value = v; edit { putBoolean("showFps", v) } }
    fun setVolume(v: Int) { volume.intValue = v; edit { putInt("volume", v) } }
    fun setMuted(v: Boolean) { muted.value = v; edit { putBoolean("muted", v) } }
    fun setPadOpacity(v: Float) { padOpacity.floatValue = v; edit { putFloat("padOpacity", v) } }
    fun setDsLayout(v: Int) { dsLayout.intValue = v; edit { putInt("dsLayout", v) } }
    fun setPadSize(v: Float) { padSize.floatValue = v; edit { putFloat("padSize", v) } }
    fun setPadVisible(v: Boolean) { padVisible.value = v; edit { putBoolean("padVisible", v) } }
    fun setPerGameLayout(v: Boolean) { perGameLayout.value = v; edit { putBoolean("perGameLayout", v) } }
    fun setTheme(v: Int) { theme.intValue = v; edit { putInt("theme", v) } }
    fun setBgMode(v: Int) { bgMode.intValue = v; edit { putInt("bgMode", v) } }
    fun setBgColor(v: Int) { bgColor.intValue = v; edit { putInt("bgColor", v) } }
    fun bumpBg() { bgStamp.intValue = bgStamp.intValue + 1; edit { putInt("bgStamp", bgStamp.intValue) } }
    fun setAppName(v: String) { appName.value = v; edit { putString("appName", v) } }
    fun setProfileName(v: String) { profileName.value = v; edit { putString("profileName", v) } }
    fun bumpAvatar() { avatarStamp.intValue = avatarStamp.intValue + 1; edit { putInt("avatarStamp", avatarStamp.intValue) } }
    fun setMusicUri(v: String) { musicUri.value = v; edit { putString("musicUri", v) } }
    fun setMusicOn(v: Boolean) { musicOn.value = v; edit { putBoolean("musicOn", v) } }
    fun setMusicMuted(v: Boolean) { musicMuted.value = v; edit { putBoolean("musicMuted", v) } }
    fun setMusicVolume(v: Float) { musicVolume.floatValue = v; edit { putFloat("musicVolume", v) } }
    fun setUiSounds(v: Boolean) { uiSounds.value = v; edit { putBoolean("uiSounds", v) } }

    fun setMusicFolder(v: String) { musicFolder.value = v; edit { putString("musicFolder", v) } }
    fun setMusicShuffle(v: Boolean) { musicShuffle.value = v; edit { putBoolean("musicShuffle", v) } }
    fun setPadAutoHide(v: Boolean) { padAutoHide.value = v; edit { putBoolean("padAutoHide", v) } }
    fun setPadRumble(v: Boolean) { padRumble.value = v; edit { putBoolean("padRumble", v) } }
    fun setUiScale(v: Float) { val c = v.coerceIn(0.7f, 1.25f); uiScale.floatValue = c; edit { putFloat("uiScale", c) } }
    fun setBoxOpacity(v: Float) { val c = v.coerceIn(0.4f, 1f); boxOpacity.floatValue = c; edit { putFloat("boxOpacity", c) } }
    fun setEntryAnim(v: Boolean) { entryAnim.value = v; edit { putBoolean("entryAnim", v) } }
    fun setShowStatus(v: Boolean) { showStatus.value = v; edit { putBoolean("showStatus", v) } }
    fun setMusicInGame(v: Boolean) { musicInGame.value = v; edit { putBoolean("musicInGame", v) } }
    fun setRenderQuality(v: Int) { renderQuality.intValue = v; edit { putInt("renderQuality", v) } }
    fun setFpsLimit(v: Int) { fpsLimit.intValue = v; edit { putInt("fpsLimit", v) } }
    fun setFfSpeed(v: Int) { ffSpeed.intValue = v; edit { putInt("ffSpeed", v) } }
    fun setFrameSkip(v: Boolean) { frameSkip.value = v; edit { putBoolean("frameSkip", v) } }
    fun setAudioSync(v: Boolean) { audioSync.value = v; edit { putBoolean("audioSync", v) } }
    fun setUiMargin(v: Int) { uiMargin.intValue = v.coerceIn(0, 48); edit { putInt("uiMargin", uiMargin.intValue) } }
    fun setPipCorner(v: Int) { pipCorner.intValue = v; edit { putInt("pipCorner", v) } }
    fun setPipSize(v: Int) { pipSize.intValue = v; edit { putInt("pipSize", v) } }
    fun setPipOpacity(v: Float) { pipOpacity.floatValue = v.coerceIn(0.2f, 1f); edit { putFloat("pipOpacity", pipOpacity.floatValue) } }
    fun setPipOnTop(v: Boolean) { pipOnTop.value = v; edit { putBoolean("pipOnTop", v) } }
    fun setAutoCovers(v: Boolean) { autoCovers.value = v; edit { putBoolean("autoCovers", v) } }
    fun setSgdbKey(v: String) { sgdbKey.value = v.trim(); edit { putString("sgdbKey", v.trim()) } }
    fun setSortMode(v: String) { sortMode.value = v; edit { putString("sortMode", v) } }

    fun loadRaw(key: String): String? = sp?.getString(key, null)
    fun saveRaw(key: String, data: String) = edit { putString(key, data) }

    // ---- Layout de controles (posiciones) como texto "id:x:y;id:x:y" ----

    fun loadLayout(scope: String): String? = sp?.getString("layout_$scope", null)
    fun saveLayout(scope: String, data: String) = edit { putString("layout_$scope", data) }
    fun clearLayout(scope: String) = edit { remove("layout_$scope") }
}
