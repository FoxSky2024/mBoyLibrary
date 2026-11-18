import pathlib
p = pathlib.Path("app/src/main/java/app/gbalibrary/ui/Gallery.kt")
t = p.read_text()
# Si el tema se llama 'c', cambiamos p.accent por c.accent
if "val c =" in t or "val c=" in t:
    t = t.replace("p.accent", "c.accent")
    print("cambiado p.accent -> c.accent")
else:
    print("OJO: no encontre val c, manda el grep")
p.write_text(t)
print("OK Gallery")
