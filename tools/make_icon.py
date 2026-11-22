# Draws the 32x32 charm slot icon for the gun modification screen.
# Usage: python make_icon.py <output png>
# The locked-slot icon is TACZ's own at runtime; TACZ assets are not copied here (CC BY-NC-ND).
import sys
from PIL import Image

out = sys.argv[1]
img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))

BODY = (219, 219, 219, 255)
LIGHT = (251, 251, 251, 255)
MID = (192, 192, 192, 255)
DARK = (112, 112, 112, 255)
px = img.load()


def put(x, y, c):
    px[x, y] = c


# Ring, top center, diameter 7
ring = ["..###..", ".#...#.", "#.....#", "#.....#", "#.....#", ".#...#.", "..###.."]
for j, row in enumerate(ring):
    for i, ch in enumerate(row):
        if ch == "#":
            put(13 + i, 3 + j, MID if j > 3 else BODY)
# Ball chain
for j in range(10, 17):
    put(16, j, DARK if j % 2 else MID)
# Tag, 9x11 plate with rounded corners
for j in range(11):
    for i in range(9):
        if (i in (0, 8)) and (j in (0, 10)):
            continue
        edge = i == 0 or j == 0 or i == 8 or j == 10
        c = DARK if (i == 8 or j == 10) else (LIGHT if (i == 0 or j == 0) else BODY)
        put(12 + i, 17 + j, c)
# Hole
put(16, 18, DARK)
# Two lines of text
for i in range(3, 7):
    put(12 + i, 22, MID)
    put(12 + i, 24, MID)

img.save(out)
