"""Rebuild the Fabric 26.3 client/server mod. Requires Python 3.9+ and JDK 25."""
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "build"
CACHE = OUT / "dependencies"
MOD_NAME = "Minecraft-DG-LAB-26.3-Fabric-Status.jar"
MODULES = ("fabric-api-base-", "fabric-entity-events-v1-", "fabric-command-api-v2-", "fabric-lifecycle-events-",
           "fabric-networking-api-v1-", "fabric-rendering-v1-")


def dependency(entry):
    path = CACHE / entry["name"]
    if path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == entry["sha256"]:
        return path
    request = urllib.request.Request(entry["url"], headers={"User-Agent": "Minecraft-DG-LAB-Status/1.0"})
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != entry["sha256"]:
        raise ValueError("SHA-256 mismatch for " + path.name)
    with tempfile.NamedTemporaryFile(dir=CACHE, delete=False) as tmp:
        tmp.write(data)
    Path(tmp.name).replace(path)
    return path


def put(archive, name, content):
    item = zipfile.ZipInfo(name, date_time=(2026, 11, 21, 0, 0, 0))
    item.compress_type = zipfile.ZIP_DEFLATED
    item.external_attr = 0o100644 << 16
    archive.writestr(item, content)


def nest_zxing(core):
    buffer = io.BytesIO()
    with zipfile.ZipFile(core) as original, zipfile.ZipFile(buffer, "w") as nested:
        for name in sorted(original.namelist()):
            if not name.endswith("/"):
                put(nested, name, original.read(name))
        meta = {"schemaVersion": 1, "id": "com_google_zxing_core", "version": "3.5.3",
                "name": "ZXing Core", "environment": "*", "license": "Apache-2.0"}
        put(nested, "fabric.mod.json", json.dumps(meta).encode())
        put(nested, "META-INF/licenses/ZXing-Apache-2.0.txt", (ROOT / "licenses/ZXing-Apache-2.0.txt").read_bytes())
    return buffer.getvalue()


def files_at(path):
    return sorted(f for f in path.rglob("*") if f.is_file())


def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    jars = []
    zxing = None
    for entry in json.loads((ROOT / "build-lock.json").read_text(encoding="utf8")):
        file = dependency(entry)
        if file.name.startswith("fabric-api-"):
            with zipfile.ZipFile(file) as outer:
                for member in outer.namelist():
                    name = Path(member).name
                    if member.startswith("META-INF/jars/") and name.startswith(MODULES) and name.endswith(".jar"):
                        module = CACHE / name
                        module.write_bytes(outer.read(member))
                        jars.append(module)
        else:
            jars.append(file)
        if file.name == "core-3.5.3.jar":
            zxing = file
    if zxing is None:
        raise ValueError("Missing ZXing in lock file")
    compiler = shutil.which("javac")
    if os.environ.get("JAVA_HOME"):
        compiler = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("javac.exe" if os.name == "nt" else "javac"))
    if not compiler:
        raise RuntimeError("Install JDK 25 and set JAVA_HOME.")
    with tempfile.TemporaryDirectory(dir=OUT) as temp:
        classes = Path(temp) / "classes"
        classes.mkdir()
        sources = sorted((ROOT / "src/main/java").rglob("*.java"))
        subprocess.run([compiler, "--release", "25", "-encoding", "UTF-8", "-proc:none",
                        "-cp", os.pathsep.join(map(str, jars)), "-d", str(classes), *map(str, sources)], check=True)
        jar = Path(temp) / MOD_NAME
        with zipfile.ZipFile(jar, "w") as artifact:
            for directory in [classes, ROOT / "src/main/resources"]:
                for f in files_at(directory):
                    put(artifact, f.relative_to(directory).as_posix(), f.read_bytes())
            put(artifact, "META-INF/jars/zxing-core-3.5.3.jar", nest_zxing(zxing))
            put(artifact, "LICENSE", (ROOT / "LICENSE").read_bytes())
            put(artifact, "README.md", (ROOT / "README.md").read_bytes())
            sources = files_at(ROOT / "src") + files_at(ROOT / "licenses") + files_at(ROOT / "tools")
            sources += [ROOT / n for n in ("LICENSE", "README.md", "build.py", "build-lock.json")]
            for f in sources:
                put(artifact, "source/" + f.relative_to(ROOT).as_posix(), f.read_bytes())
        jar.replace(OUT / MOD_NAME)
    print(OUT / MOD_NAME)


if __name__ == "__main__":
    main()
