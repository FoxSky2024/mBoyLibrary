// Host libretro minimo: carga un nucleo (.so) con dlopen y lo expone por JNI con la misma
// forma que el puente de mGBA (load / step / copyFrame / estados...). Se usa para Nintendo DS
// (melonDS) y deja la puerta abierta a cualquier otro nucleo libretro.
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TAG "RetroHost"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define OUT_RATE 44100
#define MAX_FW 1024
#define MAX_FH 1024

// ---- Subconjunto de libretro.h ----
struct retro_game_info { const char* path; const void* data; size_t size; const char* meta; };
struct retro_game_geometry { unsigned base_width, base_height, max_width, max_height; float aspect_ratio; };
struct retro_system_timing { double fps; double sample_rate; };
struct retro_system_av_info { struct retro_game_geometry geometry; struct retro_system_timing timing; };
struct retro_variable { const char* key; const char* value; };
struct retro_system_info { const char* library_name; const char* library_version; const char* valid_extensions; bool need_fullpath; bool block_extract; };
typedef void (*retro_log_printf_t)(int level, const char* fmt, ...);
struct retro_log_callback { retro_log_printf_t log; };

typedef bool (*retro_environment_t)(unsigned cmd, void* data);
typedef void (*retro_video_refresh_t)(const void* data, unsigned width, unsigned height, size_t pitch);
typedef void (*retro_audio_sample_t)(int16_t left, int16_t right);
typedef size_t (*retro_audio_sample_batch_t)(const int16_t* data, size_t frames);
typedef void (*retro_input_poll_t)(void);
typedef int16_t (*retro_input_state_t)(unsigned port, unsigned device, unsigned index, unsigned id);

#define ENV_SET_ROTATION 1
#define ENV_GET_OVERSCAN 2
#define ENV_GET_CAN_DUPE 3
#define ENV_SET_MESSAGE 6
#define ENV_SET_PERFORMANCE_LEVEL 8
#define ENV_GET_SYSTEM_DIRECTORY 9
#define ENV_SET_PIXEL_FORMAT 10
#define ENV_SET_INPUT_DESCRIPTORS 11
#define ENV_GET_VARIABLE 15
#define ENV_SET_VARIABLES 16
#define ENV_GET_VARIABLE_UPDATE 17
#define ENV_SET_SUPPORT_NO_GAME 18
#define ENV_GET_INPUT_DEVICE_CAPABILITIES 24
#define ENV_GET_LOG_INTERFACE 27
#define ENV_GET_SAVE_DIRECTORY 31
#define ENV_SET_SYSTEM_AV_INFO 32
#define ENV_SET_CONTROLLER_INFO 35
#define ENV_SET_GEOMETRY 37
#define ENV_GET_USERNAME 38
#define ENV_GET_LANGUAGE 39
#define ENV_GET_CORE_OPTIONS_VERSION 52
#define ENV_SET_MINIMUM_AUDIO_LATENCY 63

#define DEV_JOYPAD 1
#define DEV_POINTER 6
#define JOYPAD_MASK_ID 256

// ---- Estado ----
static void* gLib = NULL;
static bool gLoaded = false;

static void (*r_set_environment)(retro_environment_t);
static void (*r_set_video_refresh)(retro_video_refresh_t);
static void (*r_set_audio_sample)(retro_audio_sample_t);
static void (*r_set_audio_sample_batch)(retro_audio_sample_batch_t);
static void (*r_set_input_poll)(retro_input_poll_t);
static void (*r_set_input_state)(retro_input_state_t);
static void (*r_init)(void);
static void (*r_deinit)(void);
static void (*r_get_system_info)(struct retro_system_info*);
static void (*r_get_system_av_info)(struct retro_system_av_info*);
static bool (*r_load_game)(const struct retro_game_info*);
static void (*r_unload_game)(void);
static void (*r_run)(void);
static void (*r_reset)(void);
static size_t (*r_serialize_size)(void);
static bool (*r_serialize)(void*, size_t);
static bool (*r_unserialize)(const void*, size_t);
static void* (*r_get_memory_data)(unsigned);
static size_t (*r_get_memory_size)(unsigned);

static char gSystemDir[512];
static char gSaveDir[512];
static char gSavePath[768];
static unsigned gPixFmt = 0; // 0 = 0RGB1555, 1 = XRGB8888, 2 = RGB565
static double gSampleRate = 32768.0;
static void* gRomCopy = NULL;

// Opciones del nucleo (SET_VARIABLES): clave, lista "a|b|c" y valor actual.
#define MAX_VARS 160
static struct { char key[64]; char opts[512]; char value[64]; } gVars[MAX_VARS];
static int gVarCount = 0;
static volatile int gVarsDirty = 0;

static uint32_t gFrame[MAX_FW * MAX_FH];
static unsigned gFW = 0, gFH = 0;
static int gFrameDirty = 0;
static volatile int gRender = 1;
static pthread_mutex_t gFrameLock = PTHREAD_MUTEX_INITIALIZER;

static int16_t gIn[32768 * 2];
static size_t gInN = 0;
static double gPos = 0.0;

static volatile int32_t gKeys = 0;
static volatile int gTouchX = 0, gTouchY = 0, gTouchDown = 0;

static uint8_t* gSaveShadow = NULL;
static size_t gSaveShadowSize = 0;
static int gFramesSinceSaveCheck = 0;

// ---- Callbacks ----
static void logCb(int level, const char* fmt, ...) {
    char buf[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    int pr = level == 0 ? ANDROID_LOG_DEBUG : level == 1 ? ANDROID_LOG_INFO : level == 2 ? ANDROID_LOG_WARN : ANDROID_LOG_ERROR;
    __android_log_print(pr, "RetroCore", "%s", buf);
}

static int findVar(const char* key) {
    for (int i = 0; i < gVarCount; i++) if (strcmp(gVars[i].key, key) == 0) return i;
    return -1;
}

static bool envCb(unsigned cmd, void* data) {
    cmd &= 0xFFFF; // quita los indicadores EXPERIMENTAL / PRIVATE
    switch (cmd) {
        case ENV_GET_CAN_DUPE: *(bool*) data = true; return true;
        case ENV_SET_PIXEL_FORMAT: gPixFmt = *(const unsigned*) data; return gPixFmt <= 2;
        case ENV_GET_SYSTEM_DIRECTORY: *(const char**) data = gSystemDir; return true;
        case ENV_GET_SAVE_DIRECTORY: *(const char**) data = gSaveDir; return true;
        case ENV_GET_LOG_INTERFACE: ((struct retro_log_callback*) data)->log = logCb; return true;
        case ENV_GET_INPUT_DEVICE_CAPABILITIES: *(uint64_t*) data = (1u << DEV_JOYPAD) | (1u << 2) | (1u << DEV_POINTER); return true;
        case ENV_SET_VARIABLES: {
            const struct retro_variable* v = (const struct retro_variable*) data;
            gVarCount = 0;
            for (; v && v->key && gVarCount < MAX_VARS; v++) {
                const char* semi = v->value ? strstr(v->value, "; ") : NULL;
                const char* opts = semi ? semi + 2 : "";
                int i = gVarCount++;
                snprintf(gVars[i].key, sizeof(gVars[i].key), "%s", v->key);
                snprintf(gVars[i].opts, sizeof(gVars[i].opts), "%s", opts);
                // valor por defecto = primera opcion
                const char* bar = strchr(opts, '|');
                size_t n = bar ? (size_t) (bar - opts) : strlen(opts);
                if (n >= sizeof(gVars[i].value)) n = sizeof(gVars[i].value) - 1;
                memcpy(gVars[i].value, opts, n);
                gVars[i].value[n] = 0;
            }
            return true;
        }
        case ENV_GET_VARIABLE: {
            struct retro_variable* v = (struct retro_variable*) data;
            int i = v && v->key ? findVar(v->key) : -1;
            if (i < 0) return false;
            v->value = gVars[i].value;
            return true;
        }
        case ENV_GET_VARIABLE_UPDATE: *(bool*) data = gVarsDirty != 0; gVarsDirty = 0; return true;
        case ENV_GET_USERNAME: *(const char**) data = "Jugador"; return true;
        case ENV_GET_LANGUAGE: *(unsigned*) data = 3; return true; // espanol
        case ENV_GET_CORE_OPTIONS_VERSION: *(unsigned*) data = 0; return true;
        case ENV_SET_GEOMETRY:
        case ENV_SET_SYSTEM_AV_INFO:
        case ENV_SET_ROTATION:
        case ENV_SET_PERFORMANCE_LEVEL:
        case ENV_SET_INPUT_DESCRIPTORS:
        case ENV_SET_CONTROLLER_INFO:
        case ENV_SET_SUPPORT_NO_GAME:
        case ENV_SET_MINIMUM_AUDIO_LATENCY:
        case ENV_SET_MESSAGE:
            return true;
        default:
            return false; // incluye SET_HW_RENDER: el nucleo usa render por software
    }
}

static void videoCb(const void* data, unsigned w, unsigned h, size_t pitch) {
    if (!data || !gRender) return;
    if (w == 0 || h == 0 || w > MAX_FW || h > MAX_FH) return;
    pthread_mutex_lock(&gFrameLock);
    for (unsigned y = 0; y < h; y++) {
        const uint8_t* src = (const uint8_t*) data + y * pitch;
        uint32_t* dst = gFrame + (size_t) y * w;
        if (gPixFmt == 1) {
            const uint32_t* s = (const uint32_t*) src;
            for (unsigned x = 0; x < w; x++) {
                uint32_t p = s[x];
                dst[x] = 0xFF000000u | ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF); // XRGB -> bytes R,G,B,A
            }
        } else if (gPixFmt == 2) {
            const uint16_t* s = (const uint16_t*) src;
            for (unsigned x = 0; x < w; x++) {
                uint16_t p = s[x];
                uint32_t r = (p >> 11) & 31, g = (p >> 5) & 63, b = p & 31;
                r = (r << 3) | (r >> 2); g = (g << 2) | (g >> 4); b = (b << 3) | (b >> 2);
                dst[x] = 0xFF000000u | (b << 16) | (g << 8) | r;
            }
        } else {
            const uint16_t* s = (const uint16_t*) src;
            for (unsigned x = 0; x < w; x++) {
                uint16_t p = s[x];
                uint32_t r = (p >> 10) & 31, g = (p >> 5) & 31, b = p & 31;
                r = (r << 3) | (r >> 2); g = (g << 3) | (g >> 2); b = (b << 3) | (b >> 2);
                dst[x] = 0xFF000000u | (b << 16) | (g << 8) | r;
            }
        }
    }
    gFW = w; gFH = h; gFrameDirty = 1;
    pthread_mutex_unlock(&gFrameLock);
}

static void audioSampleCb(int16_t l, int16_t r) {
    if (gInN < 32768) { gIn[gInN * 2] = l; gIn[gInN * 2 + 1] = r; gInN++; }
}

static size_t audioBatchCb(const int16_t* data, size_t frames) {
    size_t room = 32768 - gInN;
    size_t n = frames < room ? frames : room;
    memcpy(gIn + gInN * 2, data, n * 4);
    gInN += n;
    return frames;
}

static void inputPollCb(void) {}

// Mascara propia (A=1 B=2 SELECT=4 START=8 RIGHT=16 LEFT=32 UP=64 DOWN=128 R=256 L=512 X=1<<14 Y=1<<15)
// a bits de RETRO_DEVICE_ID_JOYPAD (B0 Y1 SELECT2 START3 UP4 DOWN5 LEFT6 RIGHT7 A8 X9 L10 R11).
static int retroMask(int k) {
    int m = 0;
    if (k & 1) m |= 1 << 8;
    if (k & 2) m |= 1 << 0;
    if (k & 4) m |= 1 << 2;
    if (k & 8) m |= 1 << 3;
    if (k & 16) m |= 1 << 7;
    if (k & 32) m |= 1 << 6;
    if (k & 64) m |= 1 << 4;
    if (k & 128) m |= 1 << 5;
    if (k & 256) m |= 1 << 11;
    if (k & 512) m |= 1 << 10;
    if (k & (1 << 14)) m |= 1 << 9;
    if (k & (1 << 15)) m |= 1 << 1;
    return m;
}

static int16_t inputStateCb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0) return 0;
    if (device == DEV_JOYPAD) {
        int m = retroMask(gKeys);
        if (id == JOYPAD_MASK_ID) return (int16_t) m;
        return id < 16 ? (int16_t) ((m >> id) & 1) : 0;
    }
    if (device == DEV_POINTER && index == 0) {
        if (id == 0) return (int16_t) gTouchX;
        if (id == 1) return (int16_t) gTouchY;
        if (id == 2) return (int16_t) (gTouchDown ? 1 : 0);
    }
    return 0;
}

// ---- Utilidades ----
static bool readFile(const char* path, uint8_t** out, size_t* size) {
    FILE* f = fopen(path, "rb");
    if (!f) return false;
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (n <= 0) { fclose(f); return false; }
    *out = (uint8_t*) malloc((size_t) n);
    if (!*out) { fclose(f); return false; }
    *size = fread(*out, 1, (size_t) n, f);
    fclose(f);
    return true;
}

static bool writeFileAtomic(const char* path, const void* data, size_t size) {
    char tmp[800];
    snprintf(tmp, sizeof(tmp), "%s.tmp", path);
    FILE* f = fopen(tmp, "wb");
    if (!f) return false;
    size_t w = fwrite(data, 1, size, f);
    fclose(f);
    if (w != size) { remove(tmp); return false; }
    return rename(tmp, path) == 0;
}

static void flushSave(bool force) {
    if (!gLoaded || !r_get_memory_data || !r_get_memory_size) return;
    void* p = r_get_memory_data(0); // RETRO_MEMORY_SAVE_RAM
    size_t n = r_get_memory_size(0);
    if (!p || n == 0 || !gSavePath[0]) return;
    if (!force && gSaveShadow && gSaveShadowSize == n && memcmp(gSaveShadow, p, n) == 0) return;
    if (writeFileAtomic(gSavePath, p, n)) {
        if (gSaveShadowSize != n) { free(gSaveShadow); gSaveShadow = (uint8_t*) malloc(n); gSaveShadowSize = gSaveShadow ? n : 0; }
        if (gSaveShadow) memcpy(gSaveShadow, p, n);
    }
}

#define SYM(name) do { *(void**) (&r_##name) = dlsym(gLib, "retro_" #name); if (!r_##name) { LOGE("falta retro_" #name); ok = false; } } while (0)

static const char* jstr(JNIEnv* env, jstring s, char* buf, size_t cap) {
    const char* c = (*env)->GetStringUTFChars(env, s, NULL);
    snprintf(buf, cap, "%s", c ? c : "");
    if (c) (*env)->ReleaseStringUTFChars(env, s, c);
    return buf;
}

// ---- JNI ----
JNIEXPORT jstring JNICALL
Java_app_gbalibrary_emu_Retro_available(JNIEnv* env, jobject thiz, jstring lib) {
    (void) thiz;
    char name[256];
    jstr(env, lib, name, sizeof(name));
    void* h = dlopen(name, RTLD_NOW | RTLD_LOCAL);
    if (!h) { const char* e = dlerror(); return (*env)->NewStringUTF(env, e ? e : "dlopen"); }
    dlclose(h);
    return NULL; // null = disponible
}

JNIEXPORT jboolean JNICALL
Java_app_gbalibrary_emu_Retro_load(JNIEnv* env, jobject thiz, jstring coreLib, jbyteArray rom,
                                   jstring romPath, jstring sysDir, jstring savePath) {
    (void) thiz;
    if (gLoaded) return JNI_FALSE;
    char core[256], rpath[768];
    jstr(env, coreLib, core, sizeof(core));
    jstr(env, romPath, rpath, sizeof(rpath));
    jstr(env, sysDir, gSystemDir, sizeof(gSystemDir));
    jstr(env, savePath, gSavePath, sizeof(gSavePath));
    snprintf(gSaveDir, sizeof(gSaveDir), "%s", gSystemDir);

    gLib = dlopen(core, RTLD_NOW | RTLD_LOCAL);
    if (!gLib) { LOGE("dlopen %s: %s", core, dlerror()); return JNI_FALSE; }
    bool ok = true;
    SYM(set_environment); SYM(set_video_refresh); SYM(set_audio_sample); SYM(set_audio_sample_batch);
    SYM(set_input_poll); SYM(set_input_state); SYM(init); SYM(deinit); SYM(get_system_info);
    SYM(get_system_av_info); SYM(load_game); SYM(unload_game); SYM(run); SYM(reset);
    SYM(serialize_size); SYM(serialize); SYM(unserialize); SYM(get_memory_data); SYM(get_memory_size);
    if (!ok) { dlclose(gLib); gLib = NULL; return JNI_FALSE; }

    gVarCount = 0; gPixFmt = 0; gInN = 0; gPos = 0; gFW = gFH = 0; gFrameDirty = 0; gKeys = 0; gTouchDown = 0;
    r_set_environment(envCb);
    r_set_video_refresh(videoCb);
    r_set_audio_sample(audioSampleCb);
    r_set_audio_sample_batch(audioBatchCb);
    r_set_input_poll(inputPollCb);
    r_set_input_state(inputStateCb);
    r_init();

    struct retro_system_info si;
    memset(&si, 0, sizeof(si));
    r_get_system_info(&si);
    LOGI("nucleo: %s %s", si.library_name ? si.library_name : "?", si.library_version ? si.library_version : "?");

    jsize len = (*env)->GetArrayLength(env, rom);
    struct retro_game_info gi;
    memset(&gi, 0, sizeof(gi));
    gi.path = rpath;
    if (si.need_fullpath) {
        // El nucleo lee el archivo por ruta: se escribe la ROM en la cache.
        jbyte* b = (*env)->GetByteArrayElements(env, rom, NULL);
        bool w = b && writeFileAtomic(rpath, b, (size_t) len);
        if (b) (*env)->ReleaseByteArrayElements(env, rom, b, JNI_ABORT);
        if (!w) { LOGE("no se pudo escribir la ROM temporal"); r_deinit(); dlclose(gLib); gLib = NULL; return JNI_FALSE; }
        gi.data = NULL; gi.size = (size_t) len;
    } else {
        gRomCopy = malloc((size_t) len);
        if (!gRomCopy) { r_deinit(); dlclose(gLib); gLib = NULL; return JNI_FALSE; }
        (*env)->GetByteArrayRegion(env, rom, 0, len, (jbyte*) gRomCopy);
        gi.data = gRomCopy; gi.size = (size_t) len;
    }
    if (!r_load_game(&gi)) {
        LOGE("retro_load_game fallo");
        r_deinit(); dlclose(gLib); gLib = NULL; free(gRomCopy); gRomCopy = NULL;
        return JNI_FALSE;
    }
    struct retro_system_av_info av;
    memset(&av, 0, sizeof(av));
    r_get_system_av_info(&av);
    gSampleRate = av.timing.sample_rate > 1000 ? av.timing.sample_rate : 32768.0;
    pthread_mutex_lock(&gFrameLock);
    gFW = av.geometry.base_width ? av.geometry.base_width : 256;
    gFH = av.geometry.base_height ? av.geometry.base_height : 192;
    if (gFW > MAX_FW) gFW = MAX_FW;
    if (gFH > MAX_FH) gFH = MAX_FH;
    memset(gFrame, 0, sizeof(gFrame));
    gFrameDirty = 1;
    pthread_mutex_unlock(&gFrameLock);

    // Partida guardada (SRAM / cartucho DS).
    uint8_t* sd = NULL; size_t sn = 0;
    void* mem = r_get_memory_data(0);
    size_t msz = r_get_memory_size(0);
    if (mem && msz && readFile(gSavePath, &sd, &sn)) {
        memcpy(mem, sd, sn < msz ? sn : msz);
        free(sd);
    }
    free(gSaveShadow); gSaveShadow = NULL; gSaveShadowSize = 0; gFramesSinceSaveCheck = 0;
    gLoaded = true;
    LOGI("cargado %ux%u, %.2f fps, audio %.0f Hz", gFW, gFH, av.timing.fps, gSampleRate);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_app_gbalibrary_emu_Retro_unload(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (!gLoaded) return;
    flushSave(true);
    r_unload_game();
    r_deinit();
    dlclose(gLib);
    gLib = NULL;
    gLoaded = false;
    free(gRomCopy); gRomCopy = NULL;
    free(gSaveShadow); gSaveShadow = NULL; gSaveShadowSize = 0;
}

JNIEXPORT void JNICALL
Java_app_gbalibrary_emu_Retro_reset(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    if (gLoaded) r_reset();
}

JNIEXPORT void JNICALL
Java_app_gbalibrary_emu_Retro_setKeys(JNIEnv* env, jobject thiz, jint keys) {
    (void) env; (void) thiz;
    gKeys = keys;
}

// nx, ny normalizados 0..1 sobre TODA la imagen (el nucleo reparte segun su distribucion de pantallas).
JNIEXPORT void JNICALL
Java_app_gbalibrary_emu_Retro_setTouch(JNIEnv* env, jobject thiz, jfloat nx, jfloat ny, jboolean down) {
    (void) env; (void) thiz;
    if (nx < 0) nx = 0;
    if (nx > 1) nx = 1;
    if (ny < 0) ny = 0;
    if (ny > 1) ny = 1;
    gTouchX = (int) (nx * 65535.0f - 32768.0f);
    gTouchY = (int) (ny * 65535.0f - 32768.0f);
    gTouchDown = down ? 1 : 0;
}

// Cambia una opcion del nucleo. `wanted` se compara sin distinguir mayusculas ni simbolos
// ("leftright" encuentra "Left/Right" o "left-right"). Devuelve true si se aplico.
JNIEXPORT jboolean JNICALL
Java_app_gbalibrary_emu_Retro_setOption(JNIEnv* env, jobject thiz, jstring key, jstring wanted) {
    (void) thiz;
    char k[64], w[64];
    jstr(env, key, k, sizeof(k));
    jstr(env, wanted, w, sizeof(w));
    int i = findVar(k);
    if (i < 0) return JNI_FALSE;
    char want[64]; int wn = 0;
    for (const char* c = w; *c && wn < 63; c++) if ((*c >= 'a' && *c <= 'z') || (*c >= 'A' && *c <= 'Z') || (*c >= '0' && *c <= '9')) want[wn++] = (char) ((*c >= 'A' && *c <= 'Z') ? *c + 32 : *c);
    want[wn] = 0;
    const char* p = gVars[i].opts;
    while (*p) {
        const char* bar = strchr(p, '|');
        size_t n = bar ? (size_t) (bar - p) : strlen(p);
        char norm[64]; int nn = 0;
        for (size_t j = 0; j < n && nn < 63; j++) { char c = p[j]; if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) norm[nn++] = c; else if (c >= 'A' && c <= 'Z') norm[nn++] = (char) (c + 32); }
        norm[nn] = 0;
        if (strcmp(norm, want) == 0 && n < sizeof(gVars[i].value)) {
            memcpy(gVars[i].value, p, n); gVars[i].value[n] = 0;
            gVarsDirty = 1;
            return JNI_TRUE;
        }
        if (!bar) break;
        p = bar + 1;
    }
    return JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_app_gbalibrary_emu_Retro_step(JNIEnv* env, jobject thiz, jshortArray audio, jboolean render) {
    (void) thiz;
    if (!gLoaded) return 0;
    gRender = render ? 1 : 0;
    gInN = 0;
    r_run();
    if (++gFramesSinceSaveCheck >= 1800) { gFramesSinceSaveCheck = 0; flushSave(false); }

    jsize cap = (*env)->GetArrayLength(env, audio) / 2;
    if (gInN == 0) return 0;
    double ratio = gSampleRate / (double) OUT_RATE;
    jshort* out = (*env)->GetShortArrayElements(env, audio, NULL);
    if (!out) return 0;
    jsize o = 0;
    while (o < cap) {
        size_t idx = (size_t) gPos;
        if (idx >= gInN) break;
        double frac = gPos - (double) idx;
        size_t n1 = idx + 1 < gInN ? idx + 1 : idx;
        for (int c = 0; c < 2; c++) {
            double a = gIn[idx * 2 + c], b = gIn[n1 * 2 + c];
            out[o * 2 + c] = (jshort) (a + (b - a) * frac);
        }
        o++;
        gPos += ratio;
    }
    gPos -= (double) gInN;
    if (gPos < 0) gPos = 0;
    (*env)->ReleaseShortArrayElements(env, audio, out, 0);
    return o;
}

JNIEXPORT jint JNICALL
Java_app_gbalibrary_emu_Retro_copyFrame(JNIEnv* env, jobject thiz, jobject dst) {
    (void) thiz;
    void* p = (*env)->GetDirectBufferAddress(env, dst);
    jlong cap = (*env)->GetDirectBufferCapacity(env, dst);
    if (!p) return 0;
    jint result = 0;
    pthread_mutex_lock(&gFrameLock);
    size_t bytes = (size_t) gFW * gFH * 4;
    if (gFrameDirty && gFW && gFH && (jlong) bytes <= cap) {
        memcpy(p, gFrame, bytes);
        gFrameDirty = 0;
        result = (jint) ((gFW << 16) | gFH);
    }
    pthread_mutex_unlock(&gFrameLock);
    return result;
}

JNIEXPORT jint JNICALL Java_app_gbalibrary_emu_Retro_videoWidth(JNIEnv* env, jobject thiz) { (void) env; (void) thiz; return (jint) gFW; }
JNIEXPORT jint JNICALL Java_app_gbalibrary_emu_Retro_videoHeight(JNIEnv* env, jobject thiz) { (void) env; (void) thiz; return (jint) gFH; }

JNIEXPORT jboolean JNICALL
Java_app_gbalibrary_emu_Retro_saveState(JNIEnv* env, jobject thiz, jstring path) {
    (void) thiz;
    if (!gLoaded) return JNI_FALSE;
    char p[768];
    jstr(env, path, p, sizeof(p));
    flushSave(false);
    size_t n = r_serialize_size();
    if (n == 0) return JNI_FALSE;
    void* buf = malloc(n);
    if (!buf) return JNI_FALSE;
    bool ok = r_serialize(buf, n) && writeFileAtomic(p, buf, n);
    free(buf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_gbalibrary_emu_Retro_loadState(JNIEnv* env, jobject thiz, jstring path) {
    (void) thiz;
    if (!gLoaded) return JNI_FALSE;
    char p[768];
    jstr(env, path, p, sizeof(p));
    uint8_t* d = NULL; size_t n = 0;
    if (!readFile(p, &d, &n)) return JNI_FALSE;
    bool ok = r_unserialize(d, n);
    free(d);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL
Java_app_gbalibrary_emu_Retro_screenshot(JNIEnv* env, jobject thiz) {
    (void) thiz;
    if (!gLoaded) return NULL;
    pthread_mutex_lock(&gFrameLock);
    int n = (int) (gFW * gFH);
    jint* px = (jint*) malloc((size_t) n * 4 + 4);
    if (!px) { pthread_mutex_unlock(&gFrameLock); return NULL; }
    for (int i = 0; i < n; i++) {
        uint32_t p = gFrame[i];
        px[i] = (jint) (0xFF000000u | ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF));
    }
    pthread_mutex_unlock(&gFrameLock);
    jintArray arr = (*env)->NewIntArray(env, n);
    if (arr) (*env)->SetIntArrayRegion(env, arr, 0, n, px);
    free(px);
    return arr;
}

JNIEXPORT void JNICALL
Java_app_gbalibrary_emu_Retro_flushSave(JNIEnv* env, jobject thiz) {
    (void) env; (void) thiz;
    flushSave(false);
}
