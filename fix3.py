import pathlib
p = pathlib.Path("app/src/main/java/app/gbalibrary/ui/BootScreen.kt")
t = p.read_text()
if "runtime.getValue" not in t:
    t = t.replace("import androidx.compose.runtime.Composable", "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue")
    print("imports agregados")
else:
    print("ya tenia getValue")
p.write_text(t)
print("OK BootScreen")
