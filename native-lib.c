// Puente JNI entre la app (Kotlin) y el nucleo mGBA (MPL-2.0).
// Un unico nucleo global: la capa Kotlin garantiza que solo un hilo de
// emulacion lo usa a la vez (ver Engine.kt).

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
#include <mgba-util/vfs.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/savedata.h>

#define LOG_TAG "gbalib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SAMPLE_RATE 44100
#define VIDEO_W 240
#define VIDEO_H 160

static struct mCore* gCore = NULL;
static uint8_t* gRom = NULL;
static uint32_t gVideo[VIDEO_W * VIDEO_H];
// Copia estable del ultimo frame para el hilo de OpenGL (evita tearing).
static uint32_t gFrame[VIDEO_W * VIDEO_H];
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
    pthread_mutex_lock(&gFrameLock);
    memcpy(gFrame, gVideo, sizeof(gFrame));
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

    gCore = mCoreCreate(mPLATFORM_GBA);
    if (!gCore) {
        LOGE("mCoreCreate failed");
        vf->close(vf);
        unloadCore();
        return JNI_FALSE;
    }

    gCore->init(gCore);
    mCoreInitConfig(gCore, "gbalibrary");
    mCoreConfigSetIntValue(&gCore->config, "skipBios", 1);
    mCoreConfigSetIntValue(&gCore->config, "useBios", 0);
    // CAUSA DEL "SIN SONIDO": mGBA toma el volumen de la configuracion y, si no
    // existe, arranca en 0. Se fija el maximo (0x100) y se quita el mute.
    mCoreConfigSetIntValue(&gCore->config, "volume", 0x100);
    mCoreConfigSetIntValue(&gCore->config, "mute", 0);
    mCoreLoadConfig(gCore);

    memset(gVideo, 0, sizeof(gVideo));
    memset(gFrame, 0, sizeof(gFrame));
    gFrameDirty = 1;
    gCore->setVideoBuffer(gCore, (void*) gVideo, VIDEO_W);
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
    // Tasa de muestreo de salida: despues de reset para que no se pierda.
    blip_set_rates(gCore->getAudioChannel(gCore, 0), gCore->frequency(gCore), SAMPLE_RATE);
    blip_set_rates(gCore->getAudioChannel(gCore, 1), gCore->frequency(gCore), SAMPLE_RATE);

    // Tipo de guardado detectado leyendo la ROM (SRAM/FLASH/EEPROM), no por codigo de juego.
    // 1 SRAM, 2 FLASH512, 3 FLASH1M, 4 EEPROM; 0 = dejar la deteccion automatica de mGBA.
    if (saveType > 0 && gCore->board) {
        struct GBA* gba = (struct GBA*) gCore->board;
        GBASavedataForceType(&gba->memory.savedata, saveType);
    }
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

// Copia el ultimo frame (240x160, bytes R,G,B,X) al ByteBuffer directo `dst`.
// Devuelve true solo si hay un frame nuevo desde la ultima llamada.
JNIEXPORT jboolean JNICALL
Java_app_gbalibrary_emu_Native_copyFrame(JNIEnv* env, jobject thiz, jobject dst) {
    (void) thiz;
    void* p = (*env)->GetDirectBufferAddress(env, dst);
    jlong cap = (*env)->GetDirectBufferCapacity(env, dst);
    if (!p || cap < (jlong) sizeof(gFrame)) return JNI_FALSE;
    jboolean fresh = JNI_FALSE;
    pthread_mutex_lock(&gFrameLock);
    if (gFrameDirty) {
        memcpy(p, gFrame, sizeof(gFrame));
        gFrameDirty = 0;
        fresh = JNI_TRUE;
    }
    pthread_mutex_unlock(&gFrameLock);
    return fresh;
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

// Ultimo frame como ARGB_8888 (para la portada automatica).
JNIEXPORT jintArray JNICALL
Java_app_gbalibrary_emu_Native_screenshot(JNIEnv* env, jobject thiz) {
    (void) thiz;
    if (!gCore) return NULL;
    static jint pixels[VIDEO_W * VIDEO_H];
    for (int i = 0; i < VIDEO_W * VIDEO_H; i++) {
        uint32_t p = gVideo[i];
        uint32_t r = p & 0xFF;
        uint32_t g = (p >> 8) & 0xFF;
        uint32_t b = (p >> 16) & 0xFF;
        pixels[i] = (jint) (0xFF000000u | (r << 16) | (g << 8) | b);
    }
    jintArray arr = (*env)->NewIntArray(env, VIDEO_W * VIDEO_H);
    if (!arr) return NULL;
    (*env)->SetIntArrayRegion(env, arr, 0, VIDEO_W * VIDEO_H, pixels);
    return arr;
}
