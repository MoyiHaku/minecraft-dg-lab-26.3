"""Build the client-only HUD mod from its corresponding source. Python 3.9+, JDK 25."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / ".build"
DEPS = BUILD / "dependencies"
RESOURCES = ROOT / "src/main/resources"
MODULES = ("fabric-api-base-", "fabric-key-mapping-api-", "fabric-lifecycle-events-", "fabric-rendering-v1-")


def download(entry):
    path = DEPS / entry["name"]
    if path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == entry["sha256"]:
        return path
    request = urllib.request.Request(entry["url"], headers={"User-Agent": "DGLabCraft-HUD-build/1.0"})
    with urllib.request.urlopen(request, timeout=90) as stream:
        data = stream.read()
    if hashlib.sha256(data).hexdigest() != entry["sha256"]:
        raise ValueError("SHA-256 mismatch for " + entry["name"])
    with tempfile.NamedTemporaryFile(dir=DEPS, delete=False) as temp:
        temp.write(data)
    Path(temp.name).replace(path)
    return path


def jdk_tool(name):
    executable = name + (".exe" if os.name == "nt" else "")
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / executable
        if candidate.is_file():
            return str(candidate)
    resolved = shutil.which(executable)
    if not resolved:
        raise RuntimeError("Install JDK 25 and set JAVA_HOME before building.")
    return resolved


def build():
    DEPS.mkdir(parents=True, exist_ok=True)
    entries = json.loads((ROOT / "build-dependencies.json").read_text("utf-8"))
    paths = []
    for entry in entries:
        path = download(entry)
        if path.name.startswith("fabric-api-"):
            with zipfile.ZipFile(path) as jar:
                for member in jar.namelist():
                    name = Path(member).name
                    if member.startswith("META-INF/jars/") and name.startswith(MODULES) and name.endswith(".jar"):
                        module = DEPS / name
                        module.write_bytes(jar.read(member))
                        paths.append(module)
        else:
            paths.append(path)
    sources = sorted((ROOT / "src/main/java").rglob("*.java"))
    output = BUILD / "DGLabCraft-HUD-26.3-fabric.jar"
    with tempfile.TemporaryDirectory(dir=BUILD) as temp:
        classes = Path(temp) / "classes"
        classes.mkdir()
        subprocess.run([jdk_tool("javac"), "--release", "25", "-encoding", "UTF-8", "-proc:none",
                        "-cp", os.pathsep.join(map(str, paths)), "-d", str(classes), *map(str, sources)], check=True)
        packaged = Path(temp) / "port.jar"
        subprocess.run([jdk_tool("jar"), "--create", "--date=2026-11-20T12:00:00Z", "--file", str(packaged),
                        "-C", str(classes), ".", "-C", str(RESOURCES), "."], check=True)
        packaged.replace(output)
    print(output)


if __name__ == "__main__":
    try:
        build()
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
