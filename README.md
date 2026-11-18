# GBA Library (0.2.1)

Emulador de Game Boy Advance para Android (nucleo [mGBA](https://mgba.io/)) con la biblioteca
organizada como la de Steam: cuadricula con portadas, favoritos, colecciones, orden por tiempo
de juego y ficha de cada juego con boton **JUGAR**.

## Uso
1. Abre la app → **+** → *Agregar carpeta de ROMs* (o *archivos .gba*).
2. Toca un juego para ver su ficha, o el boton **▶** de la tarjeta para jugar directo.
3. En el juego: boton **☰** (o Atras) abre el menu: estados 1-3, velocidad x1/x2/x3, reiniciar.
   El progreso se guarda solo al salir y se retoma al volver a entrar.
4. La portada de cada juego es su ultima captura de pantalla.

Controles fisicos: cruceta/stick, A/B/X/Y, L1/R1, Start/Select; teclado: flechas, X=A, Z=B,
A=L, S=R, Enter=Start, Borrar=Select.

## Estado
Beta 0.2: sonido corregido, imagen pixel perfect (OpenGL), todo en horizontal, biblioteca estilo Steam, menu en juego y personalizacion. Mira `PROGRESS.md` para el detalle y lo que sigue.

## Legal
No incluye ROMs ni BIOS. Usa solo juegos que poseas. mGBA se distribuye bajo MPL-2.0.

## Nintendo DS (0.7.0)
- Motor: mGBA (GBA/GB/GBC, integrado) + host libretro (`cpp/retro_host.c`) que carga núcleos `.so`. DS usa melonDS.
- El workflow descarga `melonds_libretro_android.so` de buildbot.libretro.com a `app/src/main/jniLibs/<abi>/libmelonds.so` antes de compilar. Sin ese archivo la app compila igual y avisa al abrir un `.nds`.
- Si un juego no arranca, copia `bios7.bin`, `bios9.bin` y `firmware.bin` (de tu consola) a `GBALibrary/System/`.
- Pantalla inferior táctil: se toca la imagen. Distribución en Ajustes > Imagen > Nintendo DS.
- Licencias: melonDS es GPLv3; revisa que tu distribución lo cumpla.

## ShelfDeck (0.8.0)
- Vista **Estantería 3D** y vista **Steam** (cuadrícula): se cambian con el botón de la fila de sistemas.
- Sistemas extra (GameCube, Wii, PSP, PS2, 3DS, Switch, Vita...) se lanzan con su emulador instalado. Pon cada sistema en su carpeta (`GameCube/`, `Wii/`, `PSP/`...) para que los `.iso` se clasifiquen bien.
- Para los sistemas externos solo se guarda la referencia al archivo (no se copia a GBALibrary/Games).
- Compilar: igual que antes (GitHub Actions: `.github/workflows/build-apk.yml`, o Android Studio). El APK release sale firmado con la clave debug.
