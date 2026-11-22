# Moves the engraving cubes from the mount ring piece to the bar piece and halves the bar height.
# Usage: python halve_bar.py <pack folder>   (edits the geo files in place)
import json, sys
from pathlib import Path
base = Path(sys.argv[1]) / "assets/taczcharm/geo_models/attachment"
ring = json.load(open(base / "charm_mount_ring_geo.json", encoding="utf-8"))
bar = json.load(open(base / "charm_mount_bar_geo.json", encoding="utf-8"))
BOT, TOP = -0.18, 0.12
moved = 0
# The engraving (on the top face, x < -0.35) ended up in the ring piece; it belongs to the bar.
for rb, bb in zip(ring["minecraft:geometry"][0]["bones"], bar["minecraft:geometry"][0]["bones"]):
    keep = []
    for c in rb.get("cubes", []):
        if c["origin"][1] >= TOP - 0.001 and c["origin"][0] < -0.35:
            bb.setdefault("cubes", []).append(c); moved += 1
        else:
            keep.append(c)
    rb["cubes"] = keep
# Halve the bar height, keeping the bottom face at BOT.
for b in bar["minecraft:geometry"][0]["bones"]:
    for c in b.get("cubes", []):
        o, s = c["origin"], c["size"]
        y0 = BOT + (o[1] - BOT) * 0.5
        c["origin"] = [o[0], round(y0, 4), o[2]]
        c["size"] = [s[0], round(max(0.004, s[1] * 0.5), 4), s[2]]
json.dump(ring, open(base / "charm_mount_ring_geo.json", "w", encoding="utf-8"))
json.dump(bar, open(base / "charm_mount_bar_geo.json", "w", encoding="utf-8"))
print("moved engrave", moved)
