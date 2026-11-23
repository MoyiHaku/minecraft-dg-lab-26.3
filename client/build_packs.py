"""Assemble the two .mrpack files from their manifests and locally built mod JARs."""

from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


CLIENT = Path(__file__).resolve().parent
ROOT = CLIENT.parent
OUT = ROOT / "build/client"


def pack(name, index, jar, overrides):
    if not jar.is_file():
        raise SystemExit(f"Build {jar.name} before packaging. See README.md.")
    OUT.mkdir(parents=True, exist_ok=True)
    destination = OUT / f"{name}.mrpack"
    temporary = destination.with_suffix(".tmp")
    try:
        with ZipFile(temporary, "w", ZIP_DEFLATED) as archive:
            archive.write(index, "modrinth.index.json")
            archive.write(jar, f"overrides/mods/{jar.name}")
            for target, source in overrides:
                archive.write(source, f"overrides/{target}")
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    print(destination)


def main():
    dg = ROOT / "mods/minecraft-dg-lab"
    hud = ROOT / "mods/dglabcraft-hud"
    mod = "Minecraft-DG-LAB-26.3-Fabric-Status"
    with_readme = [("Minecraft-DG-LAB-Readme.txt", dg / "README.md")]
    pack(mod, CLIENT / "minecraft-dg-lab.index.json", dg / f"build/{mod}.jar", with_readme)

    hud_files = [hud / name for name in ("LICENSE", "README.txt", "build-dependencies.json", "build.py")]
    hud_files.extend(p for p in sorted((hud / "src").rglob("*")) if p.is_file())
    source_files = [(f"dglabcraft-hud-source/{p.relative_to(hud).as_posix()}", p) for p in hud_files]
    pack("DGLabCraft-HUD-26.3", CLIENT / "dglabcraft-hud.index.json",
         hud / ".build/DGLabCraft-HUD-26.3-fabric.jar", source_files)


if __name__ == "__main__":
    main()
