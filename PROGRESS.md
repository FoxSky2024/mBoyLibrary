# PROGRESS — GBA Library (alpha)

> Este archivo es el punto de retomada. Si la sesion se corta (tokens, limite, etc.),
> abre una conversacion nueva, adjunta este proyecto y di: **"lee PROGRESS.md y continua
> exactamente en 'Siguiente paso'"**.

## Estado actual: ALPHA 0.1 — codigo completo, SIN COMPILAR

Todo el codigo fue escrito sin poder compilarlo (la sesion no tenia Android SDK ni red).
**El APK aun no existe**: hay que compilarlo con GitHub Actions o Android Studio (ver README.md).
Es probable que la primera compilacion tenga algun error menor; se corrige pegando el log.

### Hecho
- [x] Esqueleto Gradle (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01), wrapper 8.14.3
- [x] Workflow de CI `.github/workflows/build-apk.yml` (sube el APK como artefacto)
- [x] Nucleo nativo: `app/src/main/cpp/` — CMake descarga mGBA 0.10.5 (ExternalProject),
      lo compila estatico y `native-lib.c` lo expone por JNI
      (cargar ROM, frame, audio blip, teclas, surface, estados, captura)
- [x] Emulacion: `emu/Engine.kt` (hilo, AudioTrack, pausa, 1x/2x/3x, comandos), `Native.kt`
- [x] Pantalla de juego: `EmulatorActivity.kt` (SurfaceView, gamepad tactil, mando fisico,
      menu: estados 1-3, velocidad, reiniciar, salir; estado automatico al salir y al volver)
- [x] Biblioteca estilo Steam: cuadricula con portadas, panel lateral (tablet) o chips (movil),
      busqueda, orden (nombre / reciente / tiempo / agregado), favoritos, colecciones,
      ficha del juego con boton JUGAR y tiempo de juego
- [x] Importar: carpeta (SAF, recursiva) o archivos sueltos; lee cabecera GBA; id = SHA-1
- [x] Portada automatica = ultima captura del juego al salir

### Pendiente / ideas para despues del alpha (en este orden sugerido)
1. Compilar y corregir errores de la primera build (CI o Studio)
2. Probar en un dispositivo real: video, audio, controles, guardado (.sav), estados
3. Soporte .zip (leer la ROM dentro del zip)
4. Portadas oficiales (descarga opcional) y mas filtros (por genero/ano)
5. Ajustes: filtro de pantalla (nearest/bilinear/shaders), tamano y posicion de controles,
   transparencia, vibracion, mapeo de botones, volumen
6. Trucos (cheats), rebobinado, guardado en la nube, BIOS opcional
7. Icono propio, pantalla de ajustes, tema claro

### Riesgos conocidos (revisar si falla la build o el juego)
- `CMakeLists.txt`: opciones de mGBA (`LIBMGBA_ONLY`, `USE_*`) escritas de memoria; si CMake
  se queja de alguna, ajustar ahi. El `.a` se espera en `mgba-install/lib/libmgba.a`.
- `native-lib.c`: usa la API de mGBA 0.10 (`getAudioChannel` + blip). Si cambias a mGBA 0.11+
  habra que migrar a `mAudioBuffer`.
- Audio: latencia ~90 ms (buffer de 16 KB). Se puede bajar luego.
- Compose: se usan APIs estables de Material3 1.3; puede haber avisos de imports sin usar.

## Mapa de archivos
```
app/src/main/cpp/CMakeLists.txt      descarga/compila mGBA y enlaza gbalib
app/src/main/cpp/native-lib.c        JNI: load/unload/reset/setKeys/step/setSurface/estados/screenshot
app/src/main/java/app/gbalibrary/
  MainActivity.kt                    selector de carpeta/archivos + biblioteca
  EmulatorActivity.kt                pantalla de juego, menu, mando fisico
  emu/Native.kt                      declaraciones JNI + mascaras GbaKey
  emu/Engine.kt                      hilo de emulacion y audio
  data/Game.kt, GameStore.kt         modelo y persistencia (library.json)
  data/RomScanner.kt, RomHeader.kt   escaneo de ROMs y lectura de cabecera
  data/Covers.kt                     portadas (captura del juego)
  ui/LibraryScreen.kt, GameDetail.kt biblioteca y ficha del juego
  ui/LibraryViewModel.kt             filtros, orden, colecciones, importacion
  ui/Gamepad.kt                      cruceta y botones tactiles (multitouch)
  ui/CoverImage.kt, Theme.kt, Format.kt
```

## Siguiente paso
**Compilar el proyecto y pegar aqui cualquier error de la primera build.**
Luego probar en el telefono y marcar los puntos de "Pendiente".

## Notas legales
mGBA es MPL-2.0 (mantener su licencia y atribucion al distribuir). No se incluyen ROMs ni BIOS:
el usuario debe usar copias de juegos que posea. mGBA trae un BIOS de reemplazo (HLE).
