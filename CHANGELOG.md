# Changelog

## 0.2.1

### P0 — Soporte .zip / .7z
- Al elegir un `.zip` o `.7z` (Ajustes > Biblioteca > Agregar archivos, o el + de la biblioteca) se descomprime solo en la caché, se busca el `.gba` y se abre.
- Si trae varios `.gba`, aparece un selector para elegir cuál.
- Las carpetas escaneadas también leen `.zip`/`.7z` (ahí se agregan todos los `.gba` que contengan).
- La caché (`cacheDir/roms`) se limpia al cerrar el juego, al salir de la app y al iniciar.
- `.zip` usa `java.util.zip`; `.7z` usa commons-compress + xz (nueva dependencia).

### P1 — Editor de controles, cruceta/joystick y mando
- Ajustes > Controles > "Editar controles" (global) y menú del juego > Controles (por juego o global).
- Agregar botones extra mapeables a A, B, L, R, Start, Select, Turbo A, Turbo B y D-Pad ↑↓←→; mover arrastrando, tamaño y opacidad por botón, borrar, restablecer.
- Tipo de movimiento: cruceta clásica o joystick analógico (zona muerta ajustable, movible y escalable). Se cambia en cualquier momento sin tocar los demás botones. Se guarda por juego y global.
- Mando físico: perfiles Mando 1 / Mando 2, diagrama estilo Xbox, remapeo (toca el botón del diagrama y presiona el botón físico: se intercambian), "Probar mando", vibración, stick izquierdo = D-Pad, gatillos, Turbo. Táctiles se ocultan al conectar mando (opción para mostrarlos).

### P1 — Vinilo y playlist
- Música: elegir una carpeta como playlist (mp3/m4a/ogg…), en orden o aleatorio, en bucle; la carpeta se guarda.
- Vinilo abajo a la derecha (solo biblioteca/ajustes): carátula y nombre 5 s, luego mini icono; tocar el mini icono muestra el nombre 5 s; se esconde al pausar.
- Sonido sintetizado de "cambio de cassette" entre canciones.

### P2 — Ajustes
- Nuevo layout con categorías laterales y una columna con scroll; botones y chips en FlowRow (nada encimado ni cortado).

### P3 — Guardado por ROM
- El tipo de guardado (SRAM / FLASH 64K / FLASH 128K / EEPROM) se detecta leyendo las cadenas de la librería de guardado dentro de la ROM y se fuerza en mGBA, no por código de juego.

### Límites conocidos
- Código escrito sin poder compilar en este entorno (sin Android SDK ni red): la primera build en GitHub Actions puede pedir correcciones menores.

## 0.3.0-beta
- Barra de Android oculta dentro del emulador (aparece al deslizar y se vuelve a esconder).
- Se elimino Eevee por completo.
- Ajustes y biblioteca: interfaz 0.2.0, barra superior sin encimarse, titulos en 2 lineas sin "...".
- El emulador abre .gba, .gbc y .gb (nucleo GB activado en mGBA; tamano de imagen dinamico).
- Disco de musica: giro de moneda, clic, deslizar arriba/abajo para cambiar de cancion.
