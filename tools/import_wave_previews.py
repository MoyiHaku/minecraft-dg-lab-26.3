"""Generate coarse HUD thumbnails from the upstream authoring points. No device data is stored."""
import hashlib
import json
from pathlib import Path
import re
import urllib.request

COMMIT = "5cda0e8bdac35f61286430ec4d0c77947fcd4f4d"
URL = f"https://raw.githubusercontent.com/dungeonlab-open/dglab-websocket-simple/{COMMIT}/socket/DG_WAVES_V2_V3_simple.js"
DIGEST = "bfd15d647e32c63864e0053995c8dfeb925d3a63cb4fde77bc2944a1abf350ed"
OUTPUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/dglab/damage_wave_previews.json"
COLUMNS = 18
ROWS = 6

def thumbnail(authored):
    points = []
    for section in authored.partition("=")[2].split("+"):
        _, slash, drawing = section.partition("/")
        if not slash:
            points.append(0.0)
            continue
        for mark in drawing.split(","):
            match = re.fullmatch(r"(\d+(?:\.\d+)?)-\d+", mark.strip())
            if not match:
                raise ValueError("Unknown drawing point format")
            points.append(min(100.0, float(match[1])))
    if not points:
        raise ValueError("Empty preview")
    def at(t):
        i = int(t)
        return points[i] + (points[min(i + 1, len(points) - 1)] - points[i]) * (t - i)
    # Each column is a coarse average in drawing order, without timing or device units.
    return [round(sum(at((len(points)-1)*(x+(n+0.5)/4)/COLUMNS) for n in range(4))/4*ROWS/100)
            for x in range(COLUMNS)]

def main():
    with urllib.request.urlopen(urllib.request.Request(URL, headers={"User-Agent":"MC-HUD-Preview"}), timeout=25) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != DIGEST:
        raise ValueError("Unexpected source revision")
    items = json.loads(data)
    previews = {item["name"]: thumbnail(item["raw"]) for item in items}
    if len(previews) != len(items):
        raise ValueError("Duplicate preset name")
    OUTPUT.write_text(json.dumps(previews, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Imported {len(previews)} named HUD thumbnails")

if __name__ == "__main__":
    main()
