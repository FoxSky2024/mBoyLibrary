# Changelog

## 0.6.0-beta
- Ficha del juego: carátula en 3D (arrastra para girarla; atrás muestra título/sistema/tiempo) y los botones JUGAR, Continuar, Guardados y Ajustes debajo de ella.
- Noticias (icono RSS en la biblioteca): agrega enlaces de páginas (RSS/Atom autodetectado, Steam app, GitHub releases, subreddits) y ve sus publicaciones estilo Steam; abrir una publicación la abre en el navegador. Widgets: estadísticas, último jugado, logros, reloj y nota rápida.
- Logros del emulador (10 insignias) con sonido y aviso arriba a la derecha estilo Steam; al conseguirlos todos suena el sonido de Platino y aparece el aviso dorado.
- Perfil rediseñado como Steam: avatar cuadrado, columna derecha con nivel, estado, insignias y contadores.
- Modo inmersivo: la barra de estado y de navegación se esconden a los 3 s y reaparecen al deslizar desde el borde.
## 0.5.0-beta
- **Perfil estilo Steam**: cabecera con avatar, marco, fondo, lema, estado (En línea/Ausente/No molestar/Invisible), nivel y XP. Pestañas General (resumen, estadísticas, insignias, escaparate), Juegos, Notas y Editar (nombre, foto, lema, ubicación, resumen, marco, fondo, escaparate de 3 juegos, privacidad).
- **Icono QR monocromo** arriba a la derecha del perfil: muestra un QR (y enlace `mboy://p?d=...`) con tu perfil. Quien lo abra ve tu perfil y puede **dejar una nota** (genera un QR/enlace de nota que se te entrega y se guarda en tu pestaña Notas). Sin servidor: todo viaja dentro del enlace. Notas solo se aceptan si van dirigidas a tu perfil, sin duplicados.
- **Todos los emojis reemplazados por iconos monocromos** (Material Icons) en biblioteca, ficha, galería, PiP, controles tactiles, ajustes y emulador.
- **Disco**: al deslizar para cambiar de canción se ven como maximo 3 a la vez y puedes seguir deslizando hasta soltar en la elegida.
- Dependencias: material-icons-extended, zxing core 3.5.3 (solo generar QR).

## 0.4.1-beta
- **Menú de pausa y ajustes dentro del juego, estilo Hollow Knight**: fondo oscuro, lista centrada de texto serif sin botones ni tarjetas, rombos ornamentales y punteros a los lados de la opción seleccionada (con brillo), valores con flechas ◂ ▸ y deslizadores de muescas. Pantallas: Pausa (Continuar, Guardar, Cargar, Reiniciar, Ajustes, Salir), Guardar/Cargar (ranuras), Ajustes > Video, Audio, Controles, Juego y Ventana de video. Se mantienen todas las opciones existentes (calidad de imagen, brillo, escala, FPS, sonido y volumen, botones táctiles, tamaño/opacidad, cruceta/joystick, zona muerta, editor de controles y layouts, velocidad, música dentro del juego, rendimiento, captura, PiP).
- Se maneja con toque, mando (D-Pad / stick = mover y cambiar valores, A = aceptar, B = atrás, Start = continuar) o teclado (flechas, Enter, Esc). El botón Atrás de Android vuelve un nivel y, en la pausa, continúa el juego.
- **Ajustes de la biblioteca**: vuelve el diseño clásico (barra "← Volver" y dos columnas con un solo scroll), conservando las opciones nuevas: Imagen, Sonido del juego, Rendimiento, Ventana PiP, Interfaz, Animaciones, Escala, etc.


## 0.4.0-beta
- Eliminado Transmitir/Streaming (StreamHost, visor, QR, permisos, dependencia ZXing) y el reproductor de YouTube. El video en ventana ahora solo reproduce videos de la galeria.
- Sistema de diseno unico (ui/Design.kt): botones, chips, tarjetas, interruptores y deslizadores comunes; temas con contraste verificado.
- Biblioteca: insignia GBA/GB/GBC, ultimo jugado, filtro por sistema, estado vacio.
- Ficha: portada grande, JUGAR, Continuar, Guardados (ranuras) y Ajustes del juego.
- Pausa: Continuar, Guardar, Cargar, Reiniciar, Ajustes, Salir (mismo diseno en vertical y horizontal).
- Ajustes: menu lateral Perfil/Controles/Video/Audio/Guardado/Tema, tarjetas solidas, temas en mosaicos.
- Controles: ocultar/mostrar cada boton; layout separado por orientacion.
- P0: autoguardado sincrono al salir y cada minuto; audio se recrea si el sistema lo corta.

## 0.3.4-beta
- **Márgenes de la interfaz**: Ajustes > Interfaz (0–48 dp) para el menú y los botones táctiles del juego.
- **Portadas/fondos/avatar**: ahora usan el selector de fotos del sistema (abre en Imágenes, no en Descargas).

## 0.3.3-beta
- **Mi Perfil** (toca tu nombre/avatar en la barra superior): avatar, nombre, juegos totales, horas totales y lista de juegos con portada, horas, última vez, progreso (%) y si hay partida guardada. Tocar un juego abre su detalle. Las horas ya se guardan por juego en `library.json` y no se reinician. El % de progreso lo defines tú en el detalle del juego (no se puede leer del save de forma genérica).
- **Galería de capturas**: las capturas se guardan en `GBALibrary/Screenshots/[Juego]/`. Sección "Galería" en el detalle de cada juego, galería global (icono 🖼 de la barra superior o desde Perfil), visor con compartir y borrar.
- **Animación de entrada** al juego (fade + zoom, ~0,7 s, sin retrasar la carga). Ajustes > Animaciones para desactivarla.
- **Escaneo de ROMs más rápido**: solo se miran .gba/.gb/.gbc/.zip/.7z (por nombre, sin abrir nada más), una consulta por carpeta, los juegos aparecen mientras se encuentran y los reescaneos solo procesan archivos nuevos/cambiados (índice en `scan_index.tsv`). Juegos cuyo archivo original se borró se quitan solo si no tienen copia en GBALibrary/Games.
- **Música dentro del juego**: Ajustes > Música > "Seguir escuchando música dentro del juego". ON: sigue sonando, botón flotante ♪ y controles en el menú de pausa (pausar, siguiente, volumen de música aparte del volumen del juego). OFF: se pausa al entrar y vuelve al salir.
- **Optimización / Rendimiento** (Ajustes y menú de pausa, se aplica al instante): calidad de renderizado Bajo/Medio/Alto, límite de FPS 30/60/Sin límite, Fast-Forward x2/x3/x4, saltar frames, sincronización de audio. Se guardan en SharedPreferences.
- **Indicadores de batería e internet** en el menú principal y en la pausa (rojo + aviso bajo 20%, rayito al cargar, WiFi / datos / sin conexión en tiempo real). Ajustes > Interfaz para ocultarlos.

## 0.3.2-beta
### Navegación con mando en el menú (estilo Steam Big Picture)
- D-Pad / stick izquierdo mueven el foco entre la barra lateral, los destacados y la cuadrícula; A abre el juego (ficha), B vuelve al foco de la cuadrícula, Start abre Ajustes. En la ficha: A = jugar, B = volver. En Ajustes: D-Pad mueve el foco, A pulsa, B vuelve.
- El elemento enfocado hace zoom suave (1.08x), proyecta sombra, pasa por encima de los demás y lleva borde de acento; el foco se mantiene siempre dentro de una zona válida y la lista se desplaza sola para mostrarlo.
- Teclado físico: flechas, Enter y Esc funcionan igual (no interfiere con la escritura en la búsqueda). Tocar la pantalla oculta el foco si no hay mando conectado.

### Sin glass: solo opacidad
- Eliminados el modo Glass, el desenfoque del fondo y `Glass.kt` (ahora `Panels.kt`).
- Ajustes > Apariencia > "Opacidad de paneles": slider 40%–100% (barras, tarjetas y paneles, fondo sólido oscuro).

## 0.3 (app 0.3.1-beta)
- Compatibilidad: detección de SRAM/FLASH 64K/128K/EEPROM y RTC; ajuste manual por juego.
- Escala UI 0.7–1.25x (defecto 0.85x), ficha de juego sin título duplicado ni JUGAR cortado.
- Carpeta persistente Documents/GBALibrary (Games, Saves, States, Covers, config.json, library.json); copia de seguridad.
- Estadísticas reales (horas, última vez, veces abierto), guardado cada 30 s.
- Glass y opacidad de cajas 30–100%.
- Portadas automáticas (Libretro, SteamGridDB, Wikipedia) y cambio manual con pulsación larga.

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
