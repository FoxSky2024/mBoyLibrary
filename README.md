# GBA Library (beta 0.2)

Emulador de Game Boy Advance para Android (nucleo [mGBA](https://mgba.io/)) con la biblioteca
organizada como la de Steam: cuadricula con portadas, favoritos, colecciones, orden por tiempo
de juego y ficha de cada juego con boton **JUGAR**.

## Como obtener el APK

### Opcion A — GitHub Actions (sin instalar nada)
1. Crea un repositorio en GitHub y sube todo este proyecto (incluida la carpeta `.github`).
2. Ve a **Actions → Build APK → Run workflow** (tambien corre solo con cada push).
3. Cuando termine (~10-20 min la primera vez), abre la ejecucion y descarga el artefacto
   **gba-library-apk**. Dentro esta `app-release.apk` (firmado con clave debug, instalable).
4. Pasalo al telefono e instalalo (permite "instalar apps desconocidas").

### Opcion B — Android Studio
Abre la carpeta del proyecto, instala NDK 27.2.12479018 y CMake 3.22.1 desde el SDK Manager
y ejecuta **Build → Build APK(s)**. Necesita internet (CMake descarga mGBA).

### Opcion C — Linea de comandos
```
./gradlew :app:assembleRelease
# APK en app/build/outputs/apk/release/
```

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
