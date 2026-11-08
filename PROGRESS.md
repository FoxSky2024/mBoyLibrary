# PROGRESS — GBA Library

> Punto de retomada. Si la sesión se corta, abre una conversación nueva, adjunta este proyecto y di: **"lee PROGRESS.md y continúa exactamente en 'Siguiente paso'"**.

## Versión: v0.3 - Compatibilidad RSIO + Escala UI + Carpeta Persistente + Glass + Portadas Auto
(app 0.3.1-beta, versionCode 4). Código escrito encima de tu 0.3.0-beta. **NO compilado ni probado** (no hay Android SDK aquí).

## Qué se arregló / agregó, punto por punto

1. **Compatibilidad (SRAM / FLASH 64K/128K / EEPROM / RTC).** Causa del "no guarda": los hacks cambian el código del juego y mGBA no los encuentra en su base de datos. Ahora `SaveType.analyze` lee las cadenas de la ROM (`FLASH1M_V`, `FLASH512_V`, `EEPROM_V`, `SRAM_V`, `SIIRTC_V`) y el tamaño del .sav, y fuerza el tipo (`GBASavedataForceType`) y el RTC tras cada reset. Nombres con `/` o caracteres raros se limpian. En la ficha del juego: sección Compatibilidad para forzar tipo de guardado y RTC (Auto/Sí/No).
2. **Escala UI.** Ajustes > Escala: slider 0.7–1.25 y chips 0.7/0.8/0.9/1.0/1.1/1.25; por defecto 0.85x. Multiplica la densidad de toda la biblioteca y Ajustes (no el juego). Cuadrícula, barras y tarjetas más compactas. Ficha: título sin duplicar (renombrar ahora es un diálogo) y botón JUGAR con ancho mínimo en FlowRow, sin cortarse.
3. **Carpeta persistente** `/Documents/GBALibrary/` con Games, Saves, States, Covers, `config.json`, `library.json`. Es la fuente de verdad; al reinstalar se restaura. Migra datos viejos de filesDir. Ajustes > Datos: Abrir carpeta de datos, Exportar copia (con opción de ROMs), Importar copia, botón de permiso.
4. **Estadísticas reales.** Por juego: horas acumuladas, última vez, veces abierto, favorito. Se guarda cada 30 s y al salir. Pasada 1 hora se muestra "1.2 h · Hoy".
5. **Glass y opacidad.** Ajustes > Cristal: Glass ON/OFF (barra lateral y superior translúcidas con borde blanco sutil) y opacidad de cajas 30–100% (barras, tarjetas, paneles). Se guarda en `config.json`. **Limitación:** no hay desenfoque real del fondo, solo translucidez.
6. **Portadas automáticas.** Libretro → SteamGridDB (si pones clave) → Wikipedia, por nombre limpio, con caché en `Covers/.cache`. Sin resultado queda la letra. Mantén pulsado un juego > Cambiar portada (galería / pegar URL / buscar / quitar). Ajustes > Portadas: automático, "Buscar portadas faltantes", clave SteamGridDB.

## Archivos tocados
- Nativo: `cpp/native-lib.c` (applyCompat, load con saveType/rtc), `emu/Native.kt`, `emu/Engine.kt`
- Datos (nuevos): `DataDir.kt`, `Persist.kt`, `RomImport.kt`, `CoverFetcher.kt`, `GbaApp.kt`
- Datos (cambiados): `SaveType.kt` (reescrito), `Game.kt`, `GameStore.kt`, `RomHeader.kt`, `RomScanner.kt`, `Covers.kt` (reescrito), `Settings.kt`
- UI: `Glass.kt` (nuevo: UiScale, glassPanel, cardPanel), `LibraryScreen.kt`, `GameDetail.kt`, `SettingsScreen.kt`, `Format.kt`, `LibraryViewModel.kt`
- App: `MainActivity.kt` (escala, permisos, backup), `EmulatorActivity.kt` (stats 30 s, carpetas), `AndroidManifest.xml` (permisos, GbaApp), `app/build.gradle.kts`

## Errores / pendientes conocidos
- Nada compilado: la primera build puede mostrar errores menores.
- RTC usa `GBAHardwareInitRTC` por símbolo débil; si el enlazado falla, revisar en `native-lib.c`.
- Juegos ya añadidos por SAF solo se copian a Games al re-escanear.
- Tras reinstalar hay que volver a elegir carpetas de ROMs y música (permisos SAF).
- `MANAGE_EXTERNAL_STORAGE` requiere instalar por APK (no Play Store).
- Archivos sueltos en la raíz (`native-lib.c`, `CMakeLists.txt`, etc.) no se usan.

## Siguiente paso
1. Subir a GitHub, esperar la build y **pegar aquí cualquier error**.
2. Probar: Pokémon Azoth/Amarilla (guardar y reabrir, reloj), escala 0.85x, Glass y opacidad, conceder permiso y reinstalar para ver que todo vuelve, portadas faltantes, exportar/importar copia.

## Cómo compilar y probar
- GitHub Actions: push, workflow `.github` ejecuta `./gradlew :app:assembleRelease`.
- Android Studio: abrir la carpeta, Sync, Run en un dispositivo (API 26+).
- Sin ROMs ni BIOS incluidos: usa solo juegos que poseas.
