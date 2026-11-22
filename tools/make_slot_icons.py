"""Renders each charm's 64x64 inventory/slot icon from its own geo model and texture.
Usage: python -I tools\\make_slot_icons.py <pack folder> [-side +x|-x] [-out <preview folder>]
Orthographic side view; each face is filled with the average color of its UV area and
flat-shaded by facing direction. No anti-aliasing, to keep a pixel-art look."""
import json, math, sys
from pathlib import Path
from PIL import Image, ImageDraw

NS = "taczcharm"
IDS = ["mf_tag", "dogtag", "akeo_tag", "cross", "casing"]
SIZE, PAD, TOP = 64, 3, 0.3


def rot(deg):
    x, y, z = (math.radians(a) for a in deg)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    mul = lambda a, b: [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(rz, mul(ry, rx))


def apply(m, p, piv):
    d = [p[i] - piv[i] for i in range(3)]
    return [piv[i] + sum(m[i][k] * d[k] for k in range(3)) for i in range(3)]


def face_color(tex, tw, th, f):
    if not f or "uv" not in f:
        return None
    u, v = f["uv"]
    us, vs = f.get("uv_size", [1, 1])
    sx, sy = tex.width / tw, tex.height / th
    x0, x1 = sorted((u * sx, (u + us) * sx))
    y0, y1 = sorted((v * sy, (v + vs) * sy))
    px = [tex.getpixel((min(tex.width - 1, int(x)), min(tex.height - 1, int(y))))
          for x in frange(x0, x1) for y in frange(y0, y1)]
    px = [p for p in px if p[3] > 0]
    if not px:
        return None
    return tuple(sum(p[i] for p in px) // len(px) for i in range(3))


def frange(a, b):
    n = max(1, int(round(b - a)))
    return [a + (b - a) * (i + 0.5) / n for i in range(n)]


def render(pack, cid, side):
    geo = json.loads((pack / f"assets/{NS}/geo_models/attachment/{cid}_geo.json").read_text("utf8"))
    g = geo["minecraft:geometry"][0]
    tw, th = g["description"]["texture_width"], g["description"]["texture_height"]
    tex = Image.open(pack / f"assets/{NS}/textures/attachment/uv/{cid}.png").convert("RGBA")
    bones = {b["name"]: b for b in g["bones"]}

    def chain(b):  # bone rotations, ordered from the bone up to the root
        out = []
        while b:
            if b.get("rotation"):
                out.append((rot(b["rotation"]), b.get("pivot", [0, 0, 0])))
            b = bones.get(b.get("parent"))
        return out

    quads = []  # (depth, screen points, color, is_tag)
    view = [1 if side == "+x" else -1, 0, 0]
    # Bedrock geo mirrors x, so the +x face uses the "west" UV.
    names = {(1, 0, 0): "west", (-1, 0, 0): "east", (0, 1, 0): "up", (0, -1, 0): "down", (0, 0, -1): "north", (0, 0, 1): "south"}
    tag = g["bones"][-1]["name"]  # the last bone is the hanging ornament
    for b in g["bones"]:
        is_tag = b["name"] == tag
        rots = chain(b)
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            inf = c.get("inflate", 0)
            o = [o[i] - inf for i in range(3)]
            s = [s[i] + 2 * inf for i in range(3)]
            cr = (rot(c["rotation"]), c.get("pivot", [0, 0, 0])) if c.get("rotation") else None
            uv = c.get("uv", {})

            def tf(p):
                if cr:
                    p = apply(cr[0], p, cr[1])
                for m, piv in rots:
                    p = apply(m, p, piv)
                return p

            for n, fname in names.items():
                ax = [i for i in range(3) if n[i]][0]
                corners = []
                others = [i for i in range(3) if i != ax]
                for a, bb in ((0, 0), (1, 0), (1, 1), (0, 1)):
                    p = list(o)
                    p[ax] = o[ax] + (s[ax] if n[ax] > 0 else 0)
                    p[others[0]] = o[others[0]] + s[others[0]] * a
                    p[others[1]] = o[others[1]] + s[others[1]] * bb
                    corners.append(tf(p))
                # Face normal after rotation, flipped to point away from the cube center
                e1 = [corners[1][i] - corners[0][i] for i in range(3)]
                e2 = [corners[3][i] - corners[0][i] for i in range(3)]
                nn = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]]
                ln = math.sqrt(sum(x * x for x in nn)) or 1
                nn = [x / ln for x in nn]
                cen = [sum(p[i] for p in corners) / 4 for i in range(3)]
                ctr = [o[i] + s[i] / 2 for i in range(3)]
                ctr = tf(ctr)
                if sum((cen[i] - ctr[i]) * nn[i] for i in range(3)) < 0:
                    nn = [-x for x in nn]
                facing = sum(nn[i] * view[i] for i in range(3))
                if facing <= 1e-3:
                    continue
                col = face_color(tex, tw, th, uv.get(fname)) if isinstance(uv, dict) else None
                if col is None:
                    continue
                shade = 1.0 if facing > 0.8 else (0.88 if nn[1] > 0.3 else 0.72)
                col = tuple(min(255, int(ch * shade)) for ch in col)
                depth = sum(cen[i] * view[i] for i in range(3))
                # Screen axes: horizontal = z, vertical = -y
                h = 1 if side == "+x" else -1  # orient so the MF lettering reads correctly from +x
                quads.append((depth, [(p[2] * h, -p[1]) for p in corners], col, is_tag))
    # Frame the ornament so it fills the icon, plus a bit of chain above it (TOP fraction).
    xs = [p[0] for q in quads if q[3] for p in q[1]]
    ys = [p[1] for q in quads if q[3] for p in q[1]]
    top = min(ys) - (max(ys) - min(ys)) * TOP
    w, hgt = max(xs) - min(xs), max(ys) - top
    sc = (SIZE - 2 * PAD) / max(w, hgt)
    ox = (SIZE - w * sc) / 2 - min(xs) * sc
    oy = (SIZE - hgt * sc) / 2 - top * sc
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _, pts, col, _t in sorted(quads, key=lambda q: q[0]):
        d.polygon([(x * sc + ox, y * sc + oy) for x, y in pts], fill=col + (255,))
    return img


def main():
    a = sys.argv[1:]
    pack = Path(a[0])
    side = a[a.index("-side") + 1] if "-side" in a else "+x"
    out = Path(a[a.index("-out") + 1]) if "-out" in a else None
    for cid in IDS:
        img = render(pack, cid, side)
        dst = (out / f"{cid}{side}.png") if out else pack / f"assets/{NS}/textures/attachment/slot/{cid}.png"
        dst.parent.mkdir(parents=True, exist_ok=True)
        img.save(dst)
        print(dst)


if __name__ == "__main__":
    main()
