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
    val padVisible = mutableStateOf(true)
    val perGameLayout = mutableStateOf(false)

    // Apariencia
    val theme = mutableIntStateOf(0)
    val bgMode = mutableIntStateOf(0) // 0 por defecto, 1 imagen, 2 color
    val bgColor = mutableIntStateOf(0xFF1B2838.toInt())
    val bgBlur = mutableStateOf(true)
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
n    // Eevee compañero
    val eveeOn = mutableStateOf(true)
    val eveeSide = mutableIntStateOf(0)

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        sp = p
        filter.intValue = p.getInt("filter", 0)
        brightness.floatValue = p.getFloat("brightness", 1f)
        scale.intValue = p.getInt("scale", 0)
        showFps.value = p.getBoolean("showFps", false)
        volume.intValue = p.getInt("volume", 100)
        muted.value = p.getBoolean("muted", false)
        padOpacity.floatValue = p.getFloat("padOpacity", 0.55f)
        padSize.floatValue = p.getFloat("padSize", 1f)
        padVisible.value = p.getBoolean("padVisible", true)
        perGameLayout.value = p.getBoolean("perGameLayout", false)
        theme.intValue = p.getInt("theme", 0)
        bgMode.intValue = p.getInt("bgMode", 0)
        bgColor.intValue = p.getInt("bgColor", 0xFF1B2838.toInt())
        bgBlur.value = p.getBoolean("bgBlur", true)
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
        eveeOn.value = p.getBoolean("eveeOn", true)
        eveeSide.intValue = p.getInt("eveeSide", 0)
    }

    // ---- Guardado (cada setter actualiza el estado y persiste) ----

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp?.edit()?.apply { block(); apply() }
    }

    fun setFilter(v: Int) { filter.intValue = v; edit { putInt("filter", v) } }
    fun setBrightness(v: Float) { brightness.floatValue = v; edit { putFloat("brightness", v) } }
    fun setScale(v: Int) { scale.intValue = v; edit { putInt("scale", v) } }
    fun setShowFps(v: Boolean) { showFps.value = v; edit { putBoolean("showFps", v) } }
    fun setVolume(v: Int) { volume.intValue = v; edit { putInt("volume", v) } }
    fun setMuted(v: Boolean) { muted.value = v; edit { putBoolean("muted", v) } }
    fun setPadOpacity(v: Float) { padOpacity.floatValue = v; edit { putFloat("padOpacity", v) } }
    fun setPadSize(v: Float) { padSize.floatValue = v; edit { putFloat("padSize", v) } }
    fun setPadVisible(v: Boolean) { padVisible.value = v; edit { putBoolean("padVisible", v) } }
    fun setPerGameLayout(v: Boolean) { perGameLayout.value = v; edit { putBoolean("perGameLayout", v) } }
    fun setTheme(v: Int) { theme.intValue = v; edit { putInt("theme", v) } }
    fun setBgMode(v: Int) { bgMode.intValue = v; edit { putInt("bgMode", v) } }
    fun setBgColor(v: Int) { bgColor.intValue = v; edit { putInt("bgColor", v) } }
    fun setBgBlur(v: Boolean) { bgBlur.value = v; edit { putBoolean("bgBlur", v) } }
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
    fun setEveeOn(v: Boolean) { eveeOn.value = v; edit { putBoolean("eveeOn", v) } }
    fun setEveeSide(v: Int) { eveeSide.intValue = v; edit { putInt("eveeSide", v) } }

    fun loadRaw(key: String): String? = sp?.getString(key, null)
    fun saveRaw(key: String, data: String) = edit { putString(key, data) }

    // ---- Layout de controles (posiciones) como texto "id:x:y;id:x:y" ----

    fun loadLayout(scope: String): String? = sp?.getString("layout_$scope", null)
    fun saveLayout(scope: String, data: String) = edit { putString("layout_$scope", data) }
    fun clearLayout(scope: String) = edit { remove("layout_$scope") }
}
