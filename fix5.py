import pathlib
p = pathlib.Path("app/src/main/java/app/gbalibrary/ui/Gallery.kt")
t = p.read_text()
viejo = "val ctx = LocalContext.current\n    val bmp = rememberShotBitmap(shot, 1600)"
nuevo = "val ctx = LocalContext.current\n    val p = LocalPalette.current\n    val bmp = rememberShotBitmap(shot, 1600)"
if viejo in t:
    t = t.replace(viejo, nuevo)
    print("agregado val p en visor")
else:
    print("no encontre el bloque, manda sed 110,130")
p.write_text(t)
print("OK Gallery")
