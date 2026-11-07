# PROGRESS — GBA Library (beta 0.2)

> Punto de retomada. Si la sesion se corta, abre una conversacion nueva, adjunta este proyecto y di: **"lee PROGRESS.md y continua exactamente en 'Siguiente paso'"**.

## Estado: 0.2.1 — codigo escrito, NO compilado ni probado (ver CHANGELOG.md)

0.2.1 agrega zip/7z, editor de controles, mando, vinilo+playlist, Eevee, Ajustes nuevos y guardado por ROM. Primer paso: build en GitHub Actions y pegar errores.

Esta version se escribio sin Android SDK (no se pudo compilar localmente). Se reviso a mano (llaves, nombres JNI, imports), pero la primera build en GitHub Actions puede mostrar algun error menor: pegar el log y se corrige.

## Que se arreglo / agrego

**1. Sonido (causa encontrada).** mGBA toma el volumen de su configuracion y, si no existe, arranca en 0 (`core.c`, `_GBACoreLoadConfig`). Ahora `native-lib.c` fija `volume=0x100` y `mute=0` antes de `mCoreLoadConfig`. Ademas: tasa de blip se fija despues de `reset`, buffer de audio mGBA 2048, AudioTrack con `setVolume(1f)` y buffer ~70 ms. Volumen 0-100 y silencio en el menu (se aplica al PCM en `Engine.kt`).

**2. Imagen pixel perfect.** El render ya no usa `ANativeWindow` (el compositor lo escalaba suave). Ahora `GbaGlView` (OpenGL ES 2.0) usa `GL_NEAREST` por defecto. Filtros en vivo: Pixel Perfect / Suave (linear) / CRT (nearest + scanlines por shader). Brillo y escala (Ajustar, Original entero, Estirar, Pantalla completa).

**3. Horizontal.** `sensorLandscape` en las dos Activities.

**4. Menu en el juego.** Boton flotante ☰ + boton Atras. Pausa al abrir, reanuda al cerrar. Pestanas: VIDEO, AUDIO, CONTROLES, JUEGO (guardado rapido, 3 ranuras, velocidad, reiniciar, captura a Imagenes/GBALibrary), SALIR (guarda solo).

**5. Biblioteca estilo Steam.** Barra superior (logo, buscador en vivo, orden, +, avatar/ajustes), panel lateral (Todos, Favoritos, Recientes, Mas jugados, colecciones), carrusel DESTACADOS, cuadricula 3:4 con titulo a 2 lineas, horas y ultima vez. Ficha: portada grande, JUGAR verde, Favorito, tiempo, tamano, codigo, archivo, descripcion, nota, renombrar, colecciones. Portada y fondo personalizados por juego (galeria). Sin portada = placeholder con nombre (ya no se usa captura del juego).

**6. Personalizacion.** 6 temas, fondo (defecto / imagen con blur / color), musica de fondo (mp3, volumen propio, mute, apagada por defecto), sonidos de interfaz (opcional), perfil (nombre + foto), nombre de la app editable.

**7. Controles.** D-Pad, A, B, L, R, Start, Select + Menu + Guardado rapido. Multitouch. Mover desde el menu ("Editar posicion"), tamano, opacidad, mostrar/ocultar, guardar/restablecer, layout global o por juego. Mando fisico y teclado conservados.

## Archivos tocados
- Nativo: `cpp/native-lib.c` (volumen, `copyFrame`, quitado `setSurface`)
- Motor: `emu/Native.kt`, `emu/Engine.kt`, **nuevo** `emu/GbaGlView.kt`
- Datos: `data/Settings.kt` (nuevo), `Game.kt`, `GameStore.kt`, `Covers.kt`
- UI: `ui/Theme.kt`, `Gamepad.kt`, `GameMenu.kt` (nuevo), `ControlLayout.kt` (nuevo), `Audio.kt` (nuevo), `SettingsScreen.kt` (nuevo), `LibraryScreen.kt`, `GameDetail.kt`, `CoverImage.kt`, `LibraryViewModel.kt`, `Format.kt`
- App: `EmulatorActivity.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `app/build.gradle.kts` (version 0.2.0-beta), `README.md`

## Siguiente paso
1. Subir a GitHub, esperar la build y **pegar aqui cualquier error**.
2. Probar en el telefono: sonido desde el segundo 0, imagen nitida, filtros en vivo, menu, editar botones, guardar/volver a entrar.

## Pendiente
- Soporte .zip; portadas oficiales (descarga opcional); filtros por genero/ano
- Mapeo de botones fisicos, vibracion, shaders extra, trucos, rebobinado, nube
- Latencia de audio configurable (ahora fija ~70 ms); audio en velocidad x2/x3 (ahora en silencio)
- Icono propio

## Riesgos conocidos
- Shader/GL escrito sin probar: si hay pantalla negra, revisar logcat (compilacion del shader).
- `blip_end_frame`/rates de mGBA 0.10.5: si sigue sin sonido, loguear `avail` en `Native.step`.
- Desenfoque del fondo = reduccion de imagen (funciona en todas las versiones de Android).
- mGBA es MPL-2.0 (mantener licencia). No se incluyen ROMs ni BIOS.
