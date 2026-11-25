package app.gbalibrary.data

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * CONFIGURACION DE ANUNCIOS. Ahora usa los IDs de PRUEBA de Google (muestran anuncios de ejemplo, sin ingresos).
 * Para anuncios reales: crea tu cuenta de AdMob, y cambia REWARDED_UNIT aqui y el APPLICATION_ID en AndroidManifest.xml.
 */
object AdConfig {
    const val REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917" // TEST rewarded
}

/** Anuncios recompensados voluntarios: el usuario los ve a proposito para ganar Pawb (max. Pawb.AD_DAILY_CAP al dia). */
object Ads {
    private var ad: RewardedAd? = null
    private var inited = false
    val ready = mutableStateOf(false)
    private var loading = false

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        MobileAds.initialize(ctx.applicationContext) {}
    }

    fun load(ctx: Context) {
        if (ad != null || loading) return
        loading = true
        RewardedAd.load(ctx.applicationContext, AdConfig.REWARDED_UNIT, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(a: RewardedAd) { ad = a; ready.value = true; loading = false }
            override fun onAdFailedToLoad(e: LoadAdError) { ad = null; ready.value = false; loading = false }
        })
    }

    fun show(act: Activity, onMsg: (String) -> Unit) {
        val a = ad
        if (a == null) { load(act); onMsg("El anuncio aún no está listo. Inténtalo en unos segundos."); return }
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { ad = null; ready.value = false; load(act) }
        }
        ready.value = false
        a.show(act) {
            Pawb.adReward()
            onMsg("+${Pawb.AD_REWARD} Pawb")
        }
    }
}
