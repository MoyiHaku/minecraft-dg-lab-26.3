"""Compile the Windows pack's launcher with JDK 25."""

import os
from pathlib import Path
import subprocess
import tempfile

SERVER = Path(__file__).resolve().parent
ROOT = SERVER.parent
SOURCE = SERVER / "Carpet-26.3/tools/src/ServerStart.java"
OUT = ROOT / "build/server/tools/server-start.jar"


def jdk_tool(name):
    java_home = os.environ.get("JAVA_HOME")
    if not java_home:
        return name
    executable = name + ".exe" if os.name == "nt" else name
    return str(Path(java_home) / "bin" / executable)


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=OUT.parent) as temp:
        classes = Path(temp) / "classes"
        classes.mkdir()
        subprocess.run([jdk_tool("javac"), "-encoding", "UTF-8", "-d", str(classes), str(SOURCE)], check=True)
        built = Path(temp) / OUT.name
        subprocess.run([jdk_tool("jar"), "--create", "--file", str(built),
                        "--main-class", "ServerStart", "-C", str(classes), "."], check=True)
        built.replace(OUT)
    print(OUT)


if __name__ == "__main__":
    main()
