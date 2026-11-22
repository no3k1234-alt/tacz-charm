# Generates the taczcharm TACZ gun pack metadata: a placeholder test charm plus index/data/display/lang for the real charms.
# Usage: python make_test_pack.py <output folder>   (writes <output>/taczcharm)
import json
import sys
from pathlib import Path
from PIL import Image

out = Path(sys.argv[1]) / "taczcharm"
NS = "taczcharm"
ID = "test_tag"


def write(rel, data):
    p = out / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, Image.Image):
        data.save(p)
    else:
        p.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


write("gunpack.meta.json", {"namespace": NS, "dependencies": {"minecraft": "[1.20.1],[1.21.1]"}})
write(f"data/{NS}/index/attachments/{ID}.json", {
    "name": f"{NS}.attachment.{ID}", "display": f"{NS}:{ID}_display",
    "data": f"{NS}:{ID}_data", "type": "charm", "sort": 1, "hidden": True})
write(f"data/{NS}/data/attachments/{ID}_data.json", {"weight": 0})
write(f"assets/{NS}/display/attachments/{ID}_display.json", {
    "slot": f"{NS}:attachment/slot/{ID}", "model": f"{NS}:attachment/{ID}_geo",
    "texture": f"{NS}:attachment/uv/{ID}"})

# 16x16 texture: top left is metal (ring and chain), top right is olive (tag)
tex = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for x in range(16):
    for y in range(8):
        tex.putpixel((x, y), (150, 150, 155, 255) if x < 8 else (96, 104, 62, 255))
write(f"assets/{NS}/textures/attachment/uv/{ID}.png", tex)


def faces(u):
    f = {"uv": [u + 2, 2], "uv_size": [1, 1]}
    return {k: dict(f) for k in ("north", "east", "south", "west", "up", "down")}


METAL, TAG = 0, 8
cubes = [
    {"origin": [0.05, -0.3, -0.15], "size": [0.1, 0.3, 0.3], "uv": faces(METAL)},    # ring
    {"origin": [0.07, -0.9, -0.04], "size": [0.06, 0.6, 0.08], "uv": faces(METAL)},  # chain
    {"origin": [0.04, -1.9, -0.35], "size": [0.12, 1.0, 0.7], "uv": faces(TAG)},     # tag
]
write(f"assets/{NS}/geo_models/attachment/{ID}_geo.json", {
    "format_version": "1.12.0",
    "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{ID}", "texture_width": 16, "texture_height": 16,
                        "visible_bounds_width": 2, "visible_bounds_height": 2.5, "visible_bounds_offset": [0, -1, 0]},
        "bones": [{"name": ID, "pivot": [0, 0, 0], "cubes": cubes}]}]})

# 64x64 slot icon: a simple tag silhouette
slot = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
for x in range(28, 36):
    for y in range(6, 14):
        if (x - 32) ** 2 + (y - 10) ** 2 in range(9, 17):
            slot.putpixel((x, y), (170, 170, 175, 255))
for y in range(14, 26):
    slot.putpixel((32, y), (150, 150, 155, 255))
for x in range(22, 43):
    for y in range(26, 56):
        slot.putpixel((x, y), (96, 104, 62, 255) if 23 <= x <= 41 and 27 <= y <= 54 else (60, 66, 38, 255))
write(f"assets/{NS}/textures/attachment/slot/{ID}.png", slot)

# Real charms. Their geo and UV textures are exported from Blockbench straight into the pack;
# this script only writes index, data, display and the slot icon.
MODELS = {"mf_tag": ("MF 태그", "MF Tag"),
          "dogtag": ("해병대 군번줄", "Marine Dog Tags"),
          "akeo_tag": ("거꾸로 A 태그", "Inverted A Tag"),
          "cross": ("십자가", "Cross"),
          "casing": ("탄피", "Shell Casing")}
for sort, cid in enumerate(MODELS, start=2):
    write(f"data/{NS}/index/attachments/{cid}.json", {
        "name": f"{NS}.attachment.{cid}", "display": f"{NS}:{cid}_display",
        "data": f"{NS}:{cid}_data", "type": "charm", "sort": sort})
    write(f"data/{NS}/data/attachments/{cid}_data.json", {"weight": 0})
    write(f"assets/{NS}/display/attachments/{cid}_display.json", {
        "slot": f"{NS}:attachment/slot/{cid}", "model": f"{NS}:attachment/{cid}_geo",
        "texture": f"{NS}:attachment/uv/{cid}"})
    # Slot icon is rendered from the charm's own model.
    sys.path.insert(0, str(Path(__file__).parent))
    import make_slot_icons
    write(f"assets/{NS}/textures/attachment/slot/{cid}.png", make_slot_icons.render(out, cid, "+x"))

# Charm mount: a rail clamp with a ring, modeled in charm/charm_mount.bbmodel and exported from
# Blockbench (rotated 180 degrees on y to undo the Bedrock x mirror).
# Hidden attachment: never an item; TaczCharm draws it at charm_pos together with the charm.
# Split into ring, bar and screw block by split_mount.py so it can fit any rail width:
# the bar is stretched sideways and the screw block is moved.
for MOUNT in ("charm_mount", "charm_mount_ring", "charm_mount_bar", "charm_mount_screw"):
    write(f"data/{NS}/index/attachments/{MOUNT}.json", {
        "name": f"{NS}.attachment.{MOUNT}", "display": f"{NS}:{MOUNT}_display",
        "data": f"{NS}:{MOUNT}_data", "type": "charm", "sort": 99, "hidden": True})
    write(f"data/{NS}/data/attachments/{MOUNT}_data.json", {"weight": 0})
    write(f"assets/{NS}/display/attachments/{MOUNT}_display.json", {
        "slot": f"{NS}:attachment/slot/{ID}", "model": f"{NS}:attachment/{MOUNT}_geo",
        "texture": f"{NS}:attachment/uv/charm_mount"})

write(f"assets/{NS}/lang/ko_kr.json", {f"{NS}.attachment.{ID}": "시험 참 (임시)",
                                       **{f"{NS}.attachment.{k}": v[0] for k, v in MODELS.items()}})
write(f"assets/{NS}/lang/en_us.json", {f"{NS}.attachment.{ID}": "Test Charm (temp)",
                                       **{f"{NS}.attachment.{k}": v[1] for k, v in MODELS.items()}})
print("ok", out)
