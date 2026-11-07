// Puente JNI entre la app (Kotlin) y el nucleo mGBA (MPL-2.0).
// Un unico nucleo global: la capa Kotlin garantiza que solo un hilo de
// emulacion lo usa a la vez (ver Engine.kt).

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
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

#define LOG_TAG "gbalib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SAMPLE_RATE 44100
#define VIDEO_W 240
#define VIDEO_H 160

static struct mCore* gCore = NULL;
static uint8_t* gRom = NULL;
static uint32_t gVideo[VIDEO_W * VIDEO_H];
static ANativeWindow* gWindow = NULL;
static pthread_mutex_t gWindowLock = PTHREAD_MUTEX_INITIALIZER;

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

static void drawFrame(void) {
    pthread_mutex_lock(&gWindowLock);
    if (gWindow) {
        ANativeWindow_Buffer buf;
        if (ANativeWindow_lock(gWindow, &buf, NULL) == 0) {
            uint32_t* dst = (uint32_t*) buf.bits;
            int w = buf.width < VIDEO_W ? buf.width : VIDEO_W;
            int h = buf.height < VIDEO_H ? buf.height : VIDEO_H;
            for (int y = 0; y < h; y++) {
                memcpy(dst + (size_t) y * buf.stride, gVideo + (size_t) y * VIDEO_W, (size_t) w * 4);
            }
            ANativeWindow_unlockAndPost(gWindow);
        }
    }
    pthread_mutex_unlock(&gWindowLock);
}

JNIEXPORT jboolean JNICALL
Java_app_gbalibrary_emu_Native_load(JNIEnv* env, jobject thiz, jbyteArray rom, jstring savePath) {
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
    mCoreLoadConfig(gCore);

    memset(gVideo, 0, sizeof(gVideo));
    gCore->setVideoBuffer(gCore, (void*) gVideo, VIDEO_W);
    gCore->setAudioBufferSize(gCore, 1024);
    blip_set_rates(gCore->getAudioChannel(gCore, 0), gCore->frequency(gCore), SAMPLE_RATE);
    blip_set_rates(gCore->getAudioChannel(gCore, 1), gCore->frequency(gCore), SAMPLE_RATE);

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
    if (render) drawFrame();

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

JNIEXPORT void JNICALL
Java_app_gbalibrary_emu_Native_setSurface(JNIEnv* env, jobject thiz, jobject surface) {
    (void) thiz;
    pthread_mutex_lock(&gWindowLock);
    if (gWindow) {
        ANativeWindow_release(gWindow);
        gWindow = NULL;
    }
    if (surface) {
        gWindow = ANativeWindow_fromSurface(env, surface);
        if (gWindow) {
            ANativeWindow_setBuffersGeometry(gWindow, VIDEO_W, VIDEO_H, WINDOW_FORMAT_RGBX_8888);
        }
    }
    pthread_mutex_unlock(&gWindowLock);
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
