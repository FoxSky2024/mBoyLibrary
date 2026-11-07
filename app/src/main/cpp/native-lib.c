// Puente JNI entre la app (Kotlin) y el nucleo mGBA (MPL-2.0).
// Un unico nucleo global: la capa Kotlin garantiza que solo un hilo de
// emulacion lo usa a la vez (ver Engine.kt).
// Soporta GBA (240x160) y Game Boy / Game Boy Color (160x144): el nucleo se elige
// leyendo la cabecera de la ROM (mCoreFindVF), no por la extension del archivo.

#include <jni.h>
#include <android/log.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba/core/blip_buf.h>
#include <mgba/core/serialize.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/savedata.h>
#include <mgba-util/vfs.h>

#define LOG_TAG "gbalib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SAMPLE_RATE 44100
// Buffer maximo: la GBA (240x160) es lo mas grande salvo el borde de Super Game Boy (256x224).
#define MAX_W 256
#define MAX_H 224

static struct mCore* gCore = NULL;
static uint8_t* gRom = NULL;
// mGBA dibuja aqui con stride fijo MAX_W; el tamano real lo da baseVideoSize.
static uint32_t gVideo[MAX_W * MAX_H];
// Copia compacta (ancho real x alto real) del ultimo frame para el hilo de OpenGL.
static uint32_t gFrame[MAX_W * MAX_H];
static unsigned gFrameW = 240;
static unsigned gFrameH = 160;
static volatile int gFrameDirty = 0;
static pthread_mutex_t gFrameLock = PTHREAD_MUTEX_INITIALIZER;

static void unloadCore(void) {
    if (gCore) {
        mCoreConfigDeinit(&gCore->config);
        gCore->deinit(gCore);
        gCore = NULL;
    }
    if (gRom) {
        free(gRom);
        gRom = NULL;
    }
}

static void publishFrame(void) {
    unsigned w = 0, h = 0;
    gCore->baseVideoSize(gCore, &w, &h);
    if (w == 0 || h == 0 || w > MAX_W || h > MAX_H) return;
    pthread_mutex_lock(&gFrameLock);
    gFrameW = w;
    gFrameH = h;
    for (unsigned y = 0; y < h; y++) {
        memcpy(gFrame + (size_t) y * w, gVideo + (size_t) y * MAX_W, (size_t) w * 4);
    }
    gFrameDirty = 1;
    pthread_mutex_unlock(&gFrameLock);
}

JNIEXPORT jboolean JNICALL
Java_app_gbalibrary_emu_Native_load(JNIEnv* env, jobject thiz, jbyteArray rom, jstring savePath, jint saveType) {
    (void) thiz;
    unloadCore();

    jsize len = (*env)->GetArrayLength(env, rom);
    if (len <= 0) return JNI_FALSE;

    gRom = (uint8_t*) malloc((size_t) len);
    if (!gRom) return JNI_FALSE;
    (*env)->GetByteArrayRegion(env, rom, 0, len, (jbyte*) gRom);

    struct VFile* vf = VFileFromMemory(gRom, (size_t) len);
    if (!vf) {
        LOGE("VFileFromMemory failed");
        unloadCore();
        return JNI_FALSE;
    }

    // Detecta GBA / GB / GBC por la cabecera. Si no se reconoce, se intenta como GBA.
    gCore = mCoreFindVF(vf);
    if (!gCore) gCore = mCoreCreate(mPLATFORM_GBA);
    if (!gCore) {
        LOGE("mCoreFindVF/mCoreCreate failed");
        vf->close(vf);
        unloadCore();
        return JNI_FALSE;
    }

    gCore->init(gCore);
    mCoreInitConfig(gCore, "gbalibrary");
    mCoreConfigSetIntValue(&gCore->config, "skipBios", 1);
    mCoreConfigSetIntValue(&gCore->config, "useBios", 0);
    // Sin borde de Super Game Boy: la imagen queda en 160x144.
    mCoreConfigSetIntValue(&gCore->config, "sgb.borders", 0);
    // CAUSA DEL "SIN SONIDO": mGBA toma el volumen de la configuracion y, si no
    // existe, arranca en 0. Se fija el maximo (0x100) y se quita el mute.
    mCoreConfigSetIntValue(&gCore->config, "volume", 0x100);
    mCoreConfigSetIntValue(&gCore->config, "mute", 0);
    mCoreLoadConfig(gCore);

    memset(gVideo, 0, sizeof(gVideo));
    memset(gFrame, 0, sizeof(gFrame));
    gCore->setVideoBuffer(gCore, (void*) gVideo, MAX_W);
    gCore->setAudioBufferSize(gCore, 2048);

    if (!gCore->loadROM(gCore, vf)) {
        LOGE("loadROM failed");
        unloadCore();
        return JNI_FALSE;
    }

    const char* sp = (*env)->GetStringUTFChars(env, savePath, NULL);
    struct VFile* sv = VFileOpen(sp, O_CREAT | O_RDWR);
    (*env)->ReleaseStringUTFChars(env, savePath, sp);
    if (sv) {
        gCore->loadSave(gCore, sv);
    } else {
        LOGE("could not open save file");
    }

    gCore->reset(gCore);

    // Tamano real de la imagen de este nucleo (GBA 240x160, GB/GBC 160x144).
    unsigned w = 0, h = 0;
    gCore->baseVideoSize(gCore, &w, &h);
    pthread_mutex_lock(&gFrameLock);
    gFrameW = w ? w : 240;
    gFrameH = h ? h : 160;
    gFrameDirty = 1;
    pthread_mutex_unlock(&gFrameLock);

    // Tipo de guardado detectado leyendo la ROM (SRAM/FLASH/EEPROM), solo para GBA.
    // 1 SRAM, 2 FLASH512, 3 FLASH1M, 4 EEPROM; 0 = dejar la deteccion automatica de mGBA.
    if (saveType > 0 && gCore->platform(gCore) == mPLATFORM_GBA && gCore->board) {
        struct GBA* gba = (struct GBA*) gCore->board;
        GBASavedataForceType(&gba->memory.savedata, saveType);
    }

    // Tasa de muestreo de salida: despues de reset para que no se pierda.
    blip_set_rates(gCore->getAudioChannel(gCore, 0), gCore->frequency(gCore), SAMPLE_RATE);
    blip_set_rates(gCore->getAudioChannel(gCore, 1), gCore->frequency(gCore), SAMPLE_RATE);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_app_gbalibrary_emu_Native_unload(JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    unloadCore();
}

JNIEXPORT void JNICALL
Java_app_gbalibrary_emu_Native_reset(JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    if (gCore) gCore->reset(gCore);
}

JNIEXPORT void JNICALL
Java_app_gbalibrary_emu_Native_setKeys(JNIEnv* env, jobject thiz, jint keys) {
    (void) env;
    (void) thiz;
    if (gCore) gCore->setKeys(gCore, (uint32_t) keys);
}

// Ejecuta un frame, lo dibuja (si render) y vuelca el audio estereo
// intercalado en `audio`. Devuelve el numero de frames de audio escritos.
JNIEXPORT jint JNICALL
Java_app_gbalibrary_emu_Native_step(JNIEnv* env, jobject thiz, jshortArray audio, jboolean render) {
    (void) thiz;
    if (!gCore) return 0;

    gCore->runFrame(gCore);
    if (render) publishFrame();

    jsize cap = (*env)->GetArrayLength(env, audio) / 2;
    blip_t* left = gCore->getAudioChannel(gCore, 0);
    blip_t* right = gCore->getAudioChannel(gCore, 1);
    int avail = blip_samples_avail(left);
    if (avail > cap) avail = cap;
    if (avail <= 0) return 0;

    jshort* out = (*env)->GetShortArrayElements(env, audio, NULL);
    if (!out) return 0;
    blip_read_samples(left, out, avail, 1);
    blip_read_samples(right, out + 1, avail, 1);
    (*env)->ReleaseShortArrayElements(env, audio, out, 0);
    return avail;
}

// Copia el ultimo frame (bytes R,G,B,X, compacto ancho x alto) al ByteBuffer directo `dst`.
// Devuelve 0 si no hay frame nuevo; si lo hay, (ancho << 16) | alto.
JNIEXPORT jint JNICALL
Java_app_gbalibrary_emu_Native_copyFrame(JNIEnv* env, jobject thiz, jobject dst) {
    (void) thiz;
    void* p = (*env)->GetDirectBufferAddress(env, dst);
    jlong cap = (*env)->GetDirectBufferCapacity(env, dst);
    if (!p) return 0;
    jint result = 0;
    pthread_mutex_lock(&gFrameLock);
    size_t bytes = (size_t) gFrameW * gFrameH * 4;
    if (gFrameDirty && (jlong) bytes <= cap) {
        memcpy(p, gFrame, bytes);
        gFrameDirty = 0;
        result = (jint) ((gFrameW << 16) | gFrameH);
    }
    pthread_mutex_unlock(&gFrameLock);
    return result;
}

// Ancho / alto actual de la imagen (para capturas de pantalla).
JNIEXPORT jint JNICALL
Java_app_gbalibrary_emu_Native_videoWidth(JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    return (jint) gFrameW;
}

JNIEXPORT jint JNICALL
Java_app_gbalibrary_emu_Native_videoHeight(JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    return (jint) gFrameH;
}

JNIEXPORT jboolean JNICALL
Java_app_gbalibrary_emu_Native_saveState(JNIEnv* env, jobject thiz, jstring path) {
    (void) thiz;
    if (!gCore) return JNI_FALSE;
    const char* p = (*env)->GetStringUTFChars(env, path, NULL);
    struct VFile* vf = VFileOpen(p, O_CREAT | O_TRUNC | O_RDWR);
    (*env)->ReleaseStringUTFChars(env, path, p);
    if (!vf) return JNI_FALSE;
    bool ok = mCoreSaveStateNamed(gCore, vf, SAVESTATE_SAVEDATA | SAVESTATE_RTC);
    vf->close(vf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_gbalibrary_emu_Native_loadState(JNIEnv* env, jobject thiz, jstring path) {
    (void) thiz;
    if (!gCore) return JNI_FALSE;
    const char* p = (*env)->GetStringUTFChars(env, path, NULL);
    struct VFile* vf = VFileOpen(p, O_RDONLY);
    (*env)->ReleaseStringUTFChars(env, path, p);
    if (!vf) return JNI_FALSE;
    bool ok = mCoreLoadStateNamed(gCore, vf, SAVESTATE_RTC);
    vf->close(vf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// Ultimo frame como ARGB_8888 (ancho x alto segun videoWidth / videoHeight).
JNIEXPORT jintArray JNICALL
Java_app_gbalibrary_emu_Native_screenshot(JNIEnv* env, jobject thiz) {
    (void) thiz;
    if (!gCore) return NULL;
    static jint pixels[MAX_W * MAX_H];
    pthread_mutex_lock(&gFrameLock);
    int n = (int) (gFrameW * gFrameH);
    for (int i = 0; i < n; i++) {
        uint32_t p = gFrame[i];
        uint32_t r = p & 0xFF;
        uint32_t g = (p >> 8) & 0xFF;
        uint32_t b = (p >> 16) & 0xFF;
        pixels[i] = (jint) (0xFF000000u | (r << 16) | (g << 8) | b);
    }
    pthread_mutex_unlock(&gFrameLock);
    jintArray arr = (*env)->NewIntArray(env, n);
    if (!arr) return NULL;
    (*env)->SetIntArrayRegion(env, arr, 0, n, pixels);
    return arr;
}
