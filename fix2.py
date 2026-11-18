import pathlib
p = pathlib.Path("app/src/main/java/app/gbalibrary/ui/ProfileScreen.kt")
t = p.read_text()
t = t.replace("private fun Ic(", "fun Ic(")
p.write_text(t)
print("Ic publico OK")
# Ver si EmulatorActivity necesita import porque esta en otro paquete
e = pathlib.Path("app/src/main/java/app/gbalibrary/EmulatorActivity.kt")
et = e.read_text()
if "import app.gbalibrary.ui.Ic" not in et:
    # ponerlo despues del package
    et = et.replace("package app.gbalibrary", "package app.gbalibrary\n\nimport app.gbalibrary.ui.Ic", 1)
    e.write_text(et)
    print("import agregado a EmulatorActivity")
else:
    print("import ya estaba")
