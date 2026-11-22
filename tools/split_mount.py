# Splits charm_mount_geo.json into ring, screw and bar pieces by x position, so the bar can be stretched to the rail width.
# Usage: python split_mount.py <pack folder>
import json, sys, copy
from pathlib import Path
base = Path(sys.argv[1])
src = base / "assets/taczcharm/geo_models/attachment/charm_mount_geo.json"
d = json.load(open(src, encoding="utf-8"))
g = d["minecraft:geometry"][0]
def part(name, keep):
    dd = copy.deepcopy(d); gg = dd["minecraft:geometry"][0]
    gg["description"]["identifier"] = "geometry." + name
    for b in gg["bones"]:
        b["cubes"] = [c for c in b.get("cubes", []) if keep(c)]
    json.dump(dd, open(base / f"assets/taczcharm/geo_models/attachment/{name}_geo.json", "w", encoding="utf-8"))
    return sum(len(b["cubes"]) for b in gg["bones"])
RING_MIN, SCREW_MAX = -0.5, -2.19
print("ring", part("charm_mount_ring", lambda c: c["origin"][0] >= RING_MIN))
print("screw", part("charm_mount_screw", lambda c: c["origin"][0] + c["size"][0] <= SCREW_MAX + 0.02 or c["origin"][0] < -2.2))
print("bar", part("charm_mount_bar", lambda c: c["origin"][0] < RING_MIN and not (c["origin"][0] + c["size"][0] <= SCREW_MAX + 0.02 or c["origin"][0] < -2.2)))
xs = [(c["origin"][0], c["origin"][0]+c["size"][0]) for b in g["bones"] for c in b.get("cubes", [])]
bar = [x for x in xs if x[0] < RING_MIN and not (x[1] <= SCREW_MAX + 0.02 or x[0] < -2.2)]
print("bar x span", min(a for a,b in bar), max(b for a,b in bar))
