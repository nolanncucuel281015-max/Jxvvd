#!/usr/bin/env python3
"""Génère toutes les ressources du mod Evo Tool (textures, modèles 3D, données JSON).

Usage : python3 tools/generate_assets.py
Les fichiers sont écrits dans src/main/resources/. Le script est déterministe (graine fixe).
"""
import colorsys
import json
import math
import os
import random

from PIL import Image

MOD = "evotool"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", MOD)
DATA = os.path.join(ROOT, "data", MOD)

rng = random.Random(1337)


# --------------------------------------------------------------------------- utilitaires

def check_model_bounds(path, obj):
    """Minecraft refuse les éléments de modèle hors de [-16, 32]."""
    for el in obj.get("elements", []) if isinstance(obj, dict) else []:
        for v in el["from"] + el["to"]:
            assert -16 <= v <= 32, f"{path}: élément hors limites {el['from']} -> {el['to']}"
        rot = el.get("rotation")
        if rot:
            assert rot["angle"] in (-45, -22.5, 0, 22.5, 45), f"{path}: angle {rot['angle']}"


def write_json(path, obj):
    check_model_bounds(path, obj)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save_png(img, *parts):
    path = os.path.join(ASSETS, "textures", *parts) + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(color, f):
    """f > 1 éclaircit, f < 1 assombrit."""
    r, g, b = color[:3]
    if f >= 1:
        t = f - 1
        return (clamp(r + (255 - r) * t), clamp(g + (255 - g) * t), clamp(b + (255 - b) * t), 255)
    return (clamp(r * f), clamp(g * f), clamp(b * f), 255)


def lerp(c1, c2, t):
    return tuple(clamp(a + (b - a) * t) for a, b in zip(c1[:3], c2[:3])) + (255,)


def hexc(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def value_noise(w, h, scale, seed):
    r = random.Random(seed)
    gw, gh = w // scale + 2, h // scale + 2
    grid = [[r.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            gx, gy = x / scale, y / scale
            x0, y0 = int(gx), int(gy)
            tx, ty = gx - x0, gy - y0
            tx = tx * tx * (3 - 2 * tx)
            ty = ty * ty * (3 - 2 * ty)
            a = grid[y0][x0] + (grid[y0][x0 + 1] - grid[y0][x0]) * tx
            b = grid[y0 + 1][x0] + (grid[y0 + 1][x0 + 1] - grid[y0 + 1][x0]) * tx
            out[y][x] = a + (b - a) * ty
    return out


# --------------------------------------------------------------------------- palettes

EVOLITE = [hexc("063b37"), hexc("13a891"), hexc("2fe6c8"), hexc("9dfff0"), hexc("ffffff")]

GEMS = {
    # sombre, moyen, clair, très clair, éclat
    "evolium": [hexc("2a0b4f"), hexc("6a1fc4"), hexc("a64dff"), hexc("5ef2ff"), hexc("ffffff")],
    "ruby": [hexc("4a0710"), hexc("a3122a"), hexc("e8324d"), hexc("ff8a9a"), hexc("fff0f2")],
    "sapphire": [hexc("0a1650"), hexc("1d44b8"), hexc("3f7cff"), hexc("9cc4ff"), hexc("f2f8ff")],
}


# --------------------------------------------------------------------------- pierres

def stone_base(seed):
    n1 = value_noise(16, 16, 4, seed)
    n2 = value_noise(16, 16, 2, seed + 1)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = 0.55 * n1[y][x] + 0.45 * n2[y][x]
            g = 104 + (v - 0.5) * 60 + rng.uniform(-6, 6)
            img.putpixel((x, y), (clamp(g), clamp(g), clamp(g + 2), 255))
    return img


def deepslate_base(seed):
    n = value_noise(16, 16, 3, seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        band = math.sin(y * 1.9 + seed) * 6
        for x in range(16):
            g = 70 + (n[y][x] - 0.5) * 34 + band + rng.uniform(-5, 5)
            if (y % 5 == 0 and rng.random() < 0.55):
                g -= 14
            img.putpixel((x, y), (clamp(g - 2), clamp(g), clamp(g + 5), 255))
    return img


def end_stone_base(seed):
    n = value_noise(16, 16, 3, seed)
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            v = n[y][x]
            base = (221, 223, 165)
            f = 0.92 + (v - 0.5) * 0.22 + r.uniform(-0.03, 0.03)
            if r.random() < 0.07:
                f *= 0.86
            img.putpixel((x, y), shade(base + (255,), f))
    return img


def ingot_sprite(palette):
    """Lingot vu en perspective (comme les lingots vanilla)."""
    dark, mid, light, bright, spark = palette
    rows = [
        "................",
        "................",
        "................",
        "................",
        "......ooooo.....",
        "....oollllloo...",
        "..oollllllllloo.",
        ".olmmmmlllllllo.",
        ".ommmmmmmmmlllo.",
        ".odmmmmmmmmmmmo.",
        ".oddddmmmmmmmdo.",
        "..oodddddmmmdo..",
        "....oooddddoo...",
        "........oooo....",
        "................",
        "................",
    ]
    colors = {"o": shade(dark, 0.6), "d": dark, "m": mid, "l": light}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch in colors:
                img.putpixel((x, y), colors[ch])
    for p in ((6, 5), (7, 5), (4, 6)):
        img.putpixel(p, bright)
    img.putpixel((8, 5), spark)
    return img


ORE_CLUSTERS = [
    [(3, 2), (4, 2), (3, 3), (4, 3), (5, 3), (4, 4)],
    [(10, 4), (11, 4), (11, 5), (12, 5), (10, 5), (11, 6)],
    [(5, 9), (6, 9), (6, 10), (7, 10), (5, 10), (6, 11), (7, 11)],
    [(11, 11), (12, 11), (12, 12), (13, 12), (11, 12)],
    [(2, 13), (3, 13), (2, 12)],
]


def ore_texture(base, palette, glow=False):
    img = base.copy()
    dark, mid, light, bright, spark = palette
    cells = set(p for c in ORE_CLUSTERS for p in c)
    # contour sombre
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if 0 <= p[0] < 16 and 0 <= p[1] < 16 and p not in cells:
                img.putpixel(p, lerp(img.getpixel(p), dark, 0.75))
    for cluster in ORE_CLUSTERS:
        top = min(y for _, y in cluster)
        left = min(x for x, _ in cluster)
        for (x, y) in cluster:
            t = (y - top) + (x - left) * 0.5
            c = light if t < 1 else mid if t < 2.5 else dark
            img.putpixel((x, y), lerp(c, mid, 0.15))
        hx, hy = min(cluster, key=lambda p: p[0] + p[1])
        img.putpixel((hx, hy), bright)
        if glow:
            img.putpixel((hx, hy), spark)
            sx, sy = cluster[len(cluster) // 2]
            img.putpixel((sx, sy), bright)
    return img


def storage_block(palette):
    dark, mid, light, bright, spark = palette
    img = Image.new("RGBA", (16, 16))
    n = value_noise(16, 16, 4, 77)
    for y in range(16):
        for x in range(16):
            # facettes en losange
            d = (abs(x - 7.5) + abs(y - 7.5)) / 15.0
            c = lerp(light, mid, d + (n[y][x] - 0.5) * 0.4)
            if (x + y) % 6 == 0 or (x - y) % 6 == 0:
                c = lerp(c, bright, 0.25)
            img.putpixel((x, y), c)
    for i in range(16):
        for p in ((i, 0), (0, i)):
            img.putpixel(p, bright)
        for p in ((i, 15), (15, i)):
            img.putpixel(p, dark)
    for i in range(1, 15):
        img.putpixel((i, 1), lerp(bright, light, 0.5))
        img.putpixel((i, 14), lerp(dark, mid, 0.4))
    img.putpixel((3, 3), spark)
    img.putpixel((4, 3), bright)
    img.putpixel((3, 4), bright)
    img.putpixel((11, 10), spark)
    return img


# --------------------------------------------------------------------------- gemme (sprite d'item)

def gem_sprite(palette):
    dark, mid, light, bright, spark = palette
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # forme : couronne (lignes 3-6) puis pavillon jusqu'à la pointe (ligne 14)
    rows = {3: (5, 10), 4: (4, 11), 5: (3, 12), 6: (2, 13), 7: (2, 13)}
    for y in range(8, 15):
        half = 5.5 * (14 - y) / 7.0
        rows[y] = (round(7.5 - half), round(7.5 + half))
    mask = set()
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            mask.add((x, y))
    for (x, y) in mask:
        if y <= 4:
            c = bright if 6 <= x <= 9 else light
        elif y <= 7:
            c = light if x < 6 else mid if x < 10 else lerp(mid, dark, 0.4)
        else:
            c = lerp(light, mid, 0.4) if x < 7.5 else lerp(mid, dark, 0.5)
            if abs(x - 7.5) < 1:
                c = mid
        img.putpixel((x, y), c)
    for (x, y) in mask:
        edge = any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            img.putpixel((x, y), shade(dark, 0.7))
    for p in ((6, 4), (5, 5), (7, 3)):
        img.putpixel(p, spark)
    img.putpixel((8, 6), bright)
    return img


# --------------------------------------------------------------------------- textures de la pioche

def gem_palette(mid_hex):
    mid = hexc(mid_hex)
    return [shade(mid, 0.35), mid, shade(mid, 1.35), shade(mid, 1.7), hexc("ffffff")]


# Une forme tous les 5 niveaux : 21 formes (niv. 0, 5, 10, ..., 100).
# Les noms sont dans les fichiers de langue (stage.evotool.<n>).
STAGE_THEMES = [
    # nom (pour référence)  manche     cuir       métal      gemme      options
    ("Fer brut",          "6b4423", "5a2e17", "a9b2bd", "6a1fc4", {}),
    ("Fer forgé",         "6b4423", "4a2a14", "c8cfd8", "7a2fd4", {}),
    ("Braises",           "4a2e1a", "3a1a0e", "7d7570", "ff6a00", {}),
    ("Doré",              "5a3a1e", "7a1426", "e8b830", "e8324d", {}),
    ("Diamant",           "3b2417", "2a3f6b", "6fd6d0", "4fe3ff", {}),
    ("Émeraude",          "3b2417", "1e4a2a", "9fb8a8", "2fd16a", {}),
    ("Améthyste",         "2e2238", "4a2a6b", "b8a0d8", "a64dff", {}),
    ("Prismarine",        "2a3a3a", "1f4a4a", "6fa89a", "7ff0d8", {}),
    ("Netherite",         "241b1b", "3a1a1a", "4a4245", "ffc23a", {}),
    ("Blaze",             "3a1a0a", "8a2a0a", "d97a1a", "ffd23a", {}),
    ("Évolium",           "241b33", "1b2a6b", "4a3f5c", "6a1fc4", {}),
    ("Givre",             "c8d8e8", "5a7aa8", "d8f0ff", "9cf0ff", {}),
    ("Sculk",             "0f1f24", "0a3a40", "1f4a52", "2fe8e8", {"stars": True}),
    ("Magma",             "1a1010", "4a0a0a", "2a2224", "ff4a0a", {"cracks": True}),
    ("End",               "6b4a7a", "d8d0a0", "e0dca8", "c84dff", {}),
    ("Céleste",           "f0f0ff", "6aa0ff", "cfe4ff", "7fd0ff", {}),
    ("Néant",             "0a0a12", "2a0a3a", "1a1424", "9a2aff", {"stars": True}),
    ("Prisme",            "3a3a4a", "ff4ad8", "e0e0ff", "4affd0", {"rainbow": True}),
    ("Stellaire",         "0a1030", "1a2a6b", "2a3a7a", "ffe080", {"stars": True}),
    ("Divin",             "f5f0e0", "ffffff", "ffe48a", "ffd84a", {}),
    ("Ultime",            "efe6d0", "f5f0ff", "f2c14e", "d94dff", {"rainbow": True}),
]

STAGES = [
    {"wood": hexc(w), "grip": hexc(g), "metal": hexc(m), "gem": gem_palette(c), **opts}
    for (_, w, g, m, c, opts) in STAGE_THEMES
]


def wood_texture(base, seed):
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for x in range(16):
        stripe = 0.85 + 0.25 * math.sin(x * 1.7 + r.random())
        for y in range(16):
            f = stripe + r.uniform(-0.05, 0.05)
            if (x * 7 + y * 3 + seed) % 23 == 0:
                f *= 0.75
            img.putpixel((x, y), shade(base, f))
    return img


def grip_texture(base, seed):
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            band = (x + y) % 4
            f = (1.18, 1.05, 0.92, 0.7)[band] + r.uniform(-0.04, 0.04)
            img.putpixel((x, y), shade(base, f))
    return img


def metal_texture(st, seed):
    base = st["metal"]
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        row = r.uniform(-0.06, 0.06)
        for x in range(16):
            f = 1.0 + row + r.uniform(-0.03, 0.03) - (y / 15.0) * 0.18
            if y == 0 or x == 0:
                f = 1.35
            elif y == 15 or x == 15:
                f = 0.65
            img.putpixel((x, y), shade(base, f))
    if st.get("cracks"):
        # fissures de lave
        x, y = 3, 1
        for _ in range(22):
            img.putpixel((x % 16, y % 16), st["gem"][2])
            x += r.choice((1, 1, 0, -1))
            y += r.choice((1, 1, 0))
    if st.get("stars"):
        for _ in range(7):
            img.putpixel((r.randrange(1, 15), r.randrange(1, 15)), st["gem"][3])
    img.putpixel((2, 2), shade(base, 1.6))
    img.putpixel((3, 2), shade(base, 1.4))
    return img


def gem_texture(st, seed):
    dark, mid, light, bright, spark = st["gem"]
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            u, v = (x % 8) - 3.5, (y % 8) - 3.5
            facet = (u > 0) * 1 + (v > 0) * 2
            c = (bright, light, mid, dark)[facet]
            if abs(abs(u) - abs(v)) < 0.6:
                c = lerp(c, light, 0.5)
            c = lerp(c, mid, r.uniform(0, 0.15))
            if st.get("rainbow"):
                h = ((x + y) / 30.0) % 1.0
                rr, gg, bb = colorsys.hsv_to_rgb(h, 0.6, 1.0)
                c = lerp(c, (int(rr * 255), int(gg * 255), int(bb * 255)), 0.55)
            img.putpixel((x, y), c)
    for p in ((2, 2), (10, 10), (10, 2)):
        img.putpixel(p, spark)
    return img


# --------------------------------------------------------------------------- modèle 3D de la pioche

def element(frm, to, tex, rot=None, uv_seed=0):
    faces = {}
    dims = [to[i] - frm[i] for i in range(3)]

    def uv(w, h):
        w, h = min(abs(w), 16), min(abs(h), 16)
        ox = (uv_seed * 5) % max(1, int(16 - w) + 1)
        oy = (uv_seed * 3) % max(1, int(16 - h) + 1)
        return [ox, oy, round(ox + w, 3), round(oy + h, 3)]

    sizes = {
        "north": (dims[0], dims[1]), "south": (dims[0], dims[1]),
        "east": (dims[2], dims[1]), "west": (dims[2], dims[1]),
        "up": (dims[0], dims[2]), "down": (dims[0], dims[2]),
    }
    for face, (w, h) in sizes.items():
        faces[face] = {"uv": uv(w, h), "texture": "#" + tex}
    el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}
    if rot:
        axis, angle, origin = rot
        el["rotation"] = {"angle": angle, "axis": axis, "origin": [round(v, 3) for v in origin]}
    return el


def pickaxe_elements(stage):
    E, add = builder()
    sym = sym8(add)
    handle(add, 10.5)
    add([6.75, 9.5, 6.75], [9.25, 11, 9.25], "trim")                   # virole dorée
    sym([6.9, 6.5, 7.6], [7.25, 10, 8.4], "dark")                      # langues de métal

    # --- Tête
    add([6, 11, 6.5], [10, 15.5, 9.5], "dark")                         # douille gravée
    add([5.8, 11.15, 6.3], [10.2, 11.55, 9.7], "trim")                 # liserés
    add([5.8, 14.95, 6.3], [10.2, 15.35, 9.7], "trim")
    if stage >= 20:
        add([6.25, 11.5, 5.75], [9.75, 15, 10.25], "gem")              # coeur surchargé
    else:
        add([6.5, 11.75, 6], [9.5, 14.75, 10], "gem")                  # coeur d'évolium
    sym([2, 12.25, 7], [6, 14.75, 9], "metal")                         # bras
    sym([2.25, 14.75, 7.25], [6, 15.1, 8.75], "edge")                  # arête polie
    sym([2.25, 11.9, 7.25], [6, 12.25, 8.75], "dark")                  # dessous des bras
    # lames recourbées : segment puis pointe polie
    sym([-1.5, 11.75, 7.2], [2.5, 13.75, 8.8], "metal", ["z", 22.5, [2, 12.75, 8]])
    sym([-1.25, 13.75, 7.4], [2.25, 14.1, 8.6], "edge", ["z", 22.5, [2, 12.75, 8]])
    tip = 4.0 if stage < 8 else 5.5
    sym([-0.5 - tip, 10.6, 7.55], [-0.5, 11.9, 8.45], "edge", ["z", 45, [-0.5, 11.3, 8]])
    sym([-0.25 - tip * 0.8, 10.95, 7.48], [-0.6, 11.55, 8.52], "metal", ["z", 45, [-0.5, 11.3, 8]])

    if stage >= 1:   # niv. 5 : arêtes de renfort et rivets
        sym([3, 15.1, 7.5], [6, 15.5, 8.5], "trim")
        sym([4.6, 13.1, 6.85], [5.2, 13.9, 9.15], "trim")
    if stage >= 2:   # niv. 10 : veines d'énergie dans les bras (auto-smelt)
        sym([2.4, 13.35, 6.92], [5.9, 13.65, 9.08], "energy")
        add([6.6, 9.75, 6.6], [9.4, 10.5, 9.4], "gem")
    if stage >= 3:   # niv. 15 : incrustations de gemmes (Fortune)
        sym([3, 12.75, 6.75], [4.4, 14.25, 9.25], "gem")
    if stage >= 4:   # niv. 20 : couronne
        add([7, 15.5, 7], [9, 17, 9], "gem")
        add([6.6, 15.35, 6.6], [9.4, 15.75, 9.4], "trim")
    if stage >= 5:   # niv. 25 : veines dans les lames recourbées, clous de gemme
        sym([-1.0, 12.6, 7.12], [2.3, 12.9, 8.88], "energy", ["z", 22.5, [2, 12.75, 8]])
        add([7.25, 12.5, 5.5], [8.75, 14, 6], "gem")
        add([7.25, 12.5, 10], [8.75, 14, 10.5], "gem")
    if stage >= 6:   # niv. 30 : ailerons
        sym([4.75, 10.5, 7.5], [6, 13, 8.5], "metal", ["z", -22.5, [6, 11, 8]])
        sym([5.0, 10.9, 7.45], [5.4, 12.6, 8.55], "energy", ["z", -22.5, [6, 11, 8]])
    if stage >= 7:   # niv. 35 : pommeau en gemme
        add([7.25, -5.9, 7.25], [8.75, -5.1, 8.75], "gem")
    if stage >= 9:   # niv. 45 : garde au milieu du manche
        add([6.5, 0.75, 6.5], [9.5, 1.5, 9.5], "trim")
    if stage >= 10:  # niv. 50 : pointe en losange au sommet
        add([7.25, 16.5, 7.25], [8.75, 19.5, 8.75], "gem", ["y", 45, [8, 18, 8]])
        add([7.85, 17, 7.85], [8.15, 20.5, 8.15], "energy")
    if stage >= 11:  # niv. 55 : barbes sous les bras
        sym([3.5, 10.75, 7.6], [4.5, 12.25, 8.4], "edge", ["z", 45, [4, 12.25, 8]])
    if stage >= 12:  # niv. 60 : runes lumineuses sur le manche
        for y in (6.75, 8.25):
            add([7.75, y, 7.05], [8.25, y + 0.75, 7.2], "energy")
            add([7.75, y, 8.8], [8.25, y + 0.75, 8.95], "energy")
    if stage >= 13:  # niv. 65 : épines sur les bras
        sym([1.75, 14.25, 7.6], [2.75, 16.25, 8.4], "edge", ["z", 22.5, [2.25, 14.25, 8]])
    if stage >= 14:  # niv. 70 : deux premiers cristaux flottants
        crystal(add, 1.5, 17.5)
        crystal(add, 14.5, 17.5)
    if stage >= 15:  # niv. 75 : ailes au-dessus de la tête
        sym([4, 15, 7.7], [6, 18.5, 8.3], "metal", ["z", 22.5, [6, 15, 8]])
        sym([4.6, 15.4, 7.65], [5.2, 18.1, 8.35], "energy", ["z", 22.5, [6, 15, 8]])
    if stage >= 16:  # niv. 80 : cristal au zénith
        crystal(add, 8, 22)
    if stage >= 17:  # niv. 85 : cristaux latéraux
        crystal(add, -2.5, 15.5)
        crystal(add, 18.5, 15.5)
    if stage >= 18:  # niv. 90 : cristal sous le pommeau
        add([7.6, -7.6, 7.6], [8.4, -6.35, 8.4], "gem", ["y", 45, [8, -7, 8]])
    if stage >= 19:  # niv. 95 : diadème
        add([5.5, 15.35, 7.75], [10.5, 15.75, 8.25], "gem")
    if stage >= 20:  # niv. 100 : halo d'énergie autour de la tête
        halo(add, 8, 13.5, 8.5, z=10.7, depth=0.2)
        crystal(add, -3.5, 9.5)
        crystal(add, 19.5, 9.5)
    return E


def crystal(add, cx, cy):
    """Cristal flottant lumineux : losange de gemme et coeur d'énergie."""
    add([cx - 0.6, cy - 1.1, 7.4], [cx + 0.6, cy + 1.1, 8.6], "gem", ["y", 45, [cx, cy, 8]])
    add([cx - 0.2, cy - 1.5, 7.8], [cx + 0.2, cy + 1.5, 8.2], "energy")


def display(height):
    """Transformations d'affichage. Les modèles sont construits verticaux (manche en bas) ;
    on les remet en diagonale comme les outils vanilla. L'échelle dépend de la hauteur du modèle."""
    k = 27.0 / height

    def sc(v):
        v = round(v * k, 3)
        return [v, v, v]

    return {
        "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 3.5, 0.5], "scale": sc(0.6)},
        "thirdperson_lefthand": {"rotation": [0, 90, -10], "translation": [0, 3.5, 0.5], "scale": sc(0.6)},
        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.6, 1.13], "scale": sc(0.5)},
        "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 3.6, 1.13], "scale": sc(0.5)},
        "gui": {"rotation": [15, -25, -45], "translation": [0, 0, 0], "scale": sc(0.52)},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": sc(0.35)},
        "fixed": {"rotation": [0, 180, 45], "translation": [0, 0, 0], "scale": sc(0.6)},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": sc(0.8)},
    }


def pickaxe_model(stage):
    return {
        "credit": "Evo Tool - modèle 3D généré par tools/generate_assets.py",
        "gui_light": "side",
        "textures": stage_textures(stage),
        "elements": finalize(pickaxe_elements(stage), stage),
        "display": display(28),
    }


def mace_elements(stage):
    E, add = builder()
    sym = sym8(add)
    handle(add, 10)
    add([6.5, 9, 6.5], [9.5, 10.5, 9.5], "trim")
    # --- Tête : bloc d'évolium serti dans une cage de métal
    add([4.5, 10.5, 4.5], [11.5, 17.5, 11.5], "dark")
    if stage >= 20:
        add([5, 11, 3.75], [11, 17, 12.25], "gem")
        add([3.75, 11, 5], [12.25, 17, 11], "gem")
    else:
        add([5.5, 11.5, 4], [10.5, 16.5, 12], "gem")
        add([4, 11.5, 5.5], [12, 16.5, 10.5], "gem")
    add([4, 10, 4], [12, 11, 12], "metal")
    add([4, 17, 4], [12, 18, 12], "metal")
    add([4.25, 11, 4.25], [5, 17, 5], "edge")                          # montants de la cage
    add([11, 11, 4.25], [11.75, 17, 5], "edge")
    add([4.25, 11, 11], [5, 17, 11.75], "edge")
    add([11, 11, 11], [11.75, 17, 11.75], "edge")
    add([3.8, 10.85, 3.8], [12.2, 11.15, 12.2], "trim")
    add([3.8, 17.85, 3.8], [12.2, 18.15, 12.2], "trim")

    if stage >= 1:   # niv. 5 : garde sur le manche
        add([6.5, 0.75, 6.5], [9.5, 1.5, 9.5], "trim")
    if stage >= 2:   # niv. 10 : 4 pointes en losange + ceinture d'énergie
        for (x, z) in ((2.5, 8), (13.5, 8), (8, 2.5), (8, 13.5)):
            add([x - 1, 13, z - 1], [x + 1, 15, z + 1], "edge", ["y", 45, [x, 14, z]])
        halo_flat(add, 8, 8, 14, 4.25, depth=0.15, t=0.15)
    if stage >= 3:   # niv. 15 : pommeau en gemme
        add([7.25, -5.9, 7.25], [8.75, -5.1, 8.75], "gem")
    if stage >= 4:   # niv. 20 : cristal au sommet
        add([7, 18, 7], [9, 21, 9], "gem", ["y", 45, [8, 19.5, 8]])
        add([7.8, 18.5, 7.8], [8.2, 22, 8.2], "energy")
    if stage >= 5:   # niv. 25 : clous aux 4 coins de la tête
        for (x, z) in ((4, 4), (11, 4), (4, 11), (11, 11)):
            add([x - 0.1, 13.5, z - 0.1], [x + 1.1, 14.5, z + 1.1], "gem")
    if stage >= 6:   # niv. 30 : pointes diagonales gauche / droite
        sym([3.5, 17, 7.5], [5, 20, 8.5], "edge", ["z", 22.5, [5, 17, 8]])
    if stage >= 7:   # niv. 35 : pointes diagonales avant / arrière
        add([7.5, 17, 3.5], [8.5, 20, 5], "edge", ["x", -22.5, [8, 17, 5]])
        add([7.5, 17, 11], [8.5, 20, 12.5], "edge", ["x", 22.5, [8, 17, 11]])
    if stage >= 8:   # niv. 40 : pointes sous la tête
        sym([3.5, 8, 7.5], [5, 11, 8.5], "edge", ["z", -22.5, [5, 11, 8]])
    if stage >= 9:   # niv. 45 : runes lumineuses sur le manche
        for y in (6.25, 7.75):
            add([7.75, y, 7.05], [8.25, y + 0.75, 7.2], "energy")
            add([7.75, y, 8.8], [8.25, y + 0.75, 8.95], "energy")
    if stage >= 10:  # niv. 50 : pointes latérales doublées
        for (x, z) in ((0.5, 8), (15.5, 8), (8, 0.5), (8, 15.5)):
            add([x - 0.6, 13.4, z - 0.6], [x + 0.6, 14.6, z + 0.6], "gem", ["y", 45, [x, 14, z]])
    if stage >= 11:  # niv. 55 : lames verticales lumineuses sur les 4 faces
        add([3.5, 11, 7.75], [4, 17, 8.25], "energy")
        add([12, 11, 7.75], [12.5, 17, 8.25], "energy")
        add([7.75, 11, 3.5], [8.25, 17, 4], "energy")
        add([7.75, 11, 12], [8.25, 17, 12.5], "energy")
    if stage >= 12:  # niv. 60 : couronne
        add([5, 18, 5], [11, 18.5, 11], "trim")
        for (x, z) in ((5, 5), (10.25, 5), (5, 10.25), (10.25, 10.25)):
            add([x, 18.5, z], [x + 0.75, 19.75, z + 0.75], "gem")
    if stage >= 13:  # niv. 65 : cristal sous le pommeau
        add([7.6, -7.6, 7.6], [8.4, -6.35, 8.4], "gem", ["y", 45, [8, -7, 8]])
    if stage >= 14:  # niv. 70 : deux cristaux flottants
        crystal(add, 0, 19)
        crystal(add, 16, 19)
    if stage >= 15:  # niv. 75 : ailes
        sym([2, 16, 7.7], [4, 20, 8.3], "metal", ["z", 22.5, [4, 16, 8]])
        sym([2.6, 16.4, 7.65], [3.2, 19.6, 8.35], "energy", ["z", 22.5, [4, 16, 8]])
    if stage >= 16:  # niv. 80 : cristal au zénith
        crystal(add, 8, 25)
    if stage >= 17:  # niv. 85 : cristaux latéraux
        crystal(add, -2.5, 11)
        crystal(add, 18.5, 11)
    if stage >= 18:  # niv. 90 : deuxième garde sertie
        add([6.4, 5.75, 6.4], [9.6, 6.25, 9.6], "trim")
        sym([6.1, 5.6, 7.6], [6.4, 6.4, 8.4], "gem")
    if stage >= 19:  # niv. 95 : second anneau d'énergie
        halo_flat(add, 8, 8, 11.5, 5.25, depth=0.15, t=0.15)
    if stage >= 20:  # niv. 100 : grand halo
        halo_flat(add, 8, 8, 16.5, 7.5, depth=0.2, t=0.2)
        crystal(add, -1, 4)
        crystal(add, 17, 4)
    return E


def mace_model(stage):
    model = pickaxe_model(stage)
    model["credit"] = "Evo Tool - masse 3D générée par tools/generate_assets.py"
    model["elements"] = finalize(mace_elements(stage), stage)
    return model


# --------------------------------------------------------------------------- épée, hache, pelle, houe

def builder():
    E = []
    n = [0]

    def add(frm, to, tex, rot=None):
        n[0] += 1
        E.append(element(frm, to, tex, rot, n[0]))

    return E, add


def handle(add, top, grip_top=5.5):
    """Manche commun : bois, tressage en cuir, bagues dorées et pommeau."""
    add([7.25, -3, 7.25], [8.75, top, 8.75], "handle")
    add([6.85, -4.6, 6.85], [9.15, -3, 9.15], "dark")                  # pommeau
    add([7.1, -5.1, 7.1], [8.9, -4.6, 8.9], "trim")                    # capuchon
    for y in (-2.5, -1.25, 0, 1.25, 2.5, 3.75):                         # tressage
        if y + 0.75 <= grip_top:
            add([6.95, y, 6.95], [9.05, y + 0.75, 9.05], "grip")
    add([7.1, -2.75, 7.1], [8.9, grip_top, 8.9], "grip")
    add([6.9, grip_top, 6.9], [9.1, grip_top + 0.35, 9.1], "trim")     # bagues
    add([6.9, -3.0, 6.9], [9.1, -2.75, 9.1], "trim")


def common_decor(add, stage, runes, crystals, second_guard_y=5.75, aura=None):
    """Décorations communes à tous les outils, qui s'ajoutent forme après forme."""
    if stage >= 3:   # niv. 15 : pommeau en gemme
        add([7.25, -5.9, 7.25], [8.75, -5.1, 8.75], "gem")
    if stage >= 9:   # niv. 45 : garde sur le manche + anneaux d'énergie
        add([6.5, 0.75, 6.5], [9.5, 1.5, 9.5], "trim")
        add([7.0, -1.6, 7.0], [9.0, -1.4, 9.0], "energy")
    if stage >= 12:  # niv. 60 : runes lumineuses
        for (y, z0, z1) in runes:
            add([7.75, y, z0], [8.25, y + 0.75, z1], "energy")
    if stage >= 13:  # niv. 65 : cristal sous le pommeau
        add([7.6, -7.6, 7.6], [8.4, -6.35, 8.4], "gem", ["y", 45, [8, -7, 8]])
    if stage >= 14:  # niv. 70 : deux cristaux flottants
        for c in crystals[0:2]:
            crystal(add, *c)
    if stage >= 16:  # niv. 80 : cristal au zénith
        crystal(add, *crystals[2])
    if stage >= 17:  # niv. 85 : cristaux latéraux
        for c in crystals[3:5]:
            crystal(add, *c)
    if stage >= 18:  # niv. 90 : deuxième garde sertie
        y = second_guard_y
        add([6.4, y, 6.4], [9.6, y + 0.5, 9.6], "trim")
        add([6.1, y - 0.15, 7.6], [6.4, y + 0.65, 8.4], "gem")
        add([9.6, y - 0.15, 7.6], [9.9, y + 0.65, 8.4], "gem")
    if stage >= 20:  # niv. 100 : halo complet
        for c in crystals[5:7]:
            crystal(add, *c)
        if aura:
            halo(add, *aura, z=10.6, depth=0.2)


def handle_runes(*ys):
    return [r for y in ys for r in ((y, 7.05, 7.2), (y, 8.8, 8.95))]


def sword_elements(stage):
    """Épée de chevalier : lame à gorge lumineuse, garde ciselée, ailes et halo aux dernières formes."""
    E, add = builder()
    sym = sym8(add)
    bt = 24 + (1 if stage >= 6 else 0) + (2 if stage >= 8 else 0)      # haut de la lame
    # --- poignée
    add([7.3, -4, 7.3], [8.7, 4.5, 8.7], "grip")                       # fusée
    for y in (-3.5, -2.0, -0.5, 1.0, 2.5):                              # tressage
        add([7.15, y, 7.15], [8.85, y + 0.6, 8.85], "handle")
    add([6.9, -5.25, 6.9], [9.1, -4, 9.1], "dark")                      # pommeau
    add([7.15, -5.75, 7.15], [8.85, -5.25, 8.85], "trim")
    add([7.4, -5.0, 6.7], [8.6, -4.25, 9.3], "gem")                     # gemme du pommeau
    add([7.0, 4.5, 7.0], [9.0, 5.25, 9.0], "trim")                      # bague
    # --- garde
    add([3.75, 5.25, 7.15], [12.25, 6.75, 8.85], "metal")
    add([4.25, 6.75, 7.4], [11.75, 7.15, 8.6], "edge")
    add([4.75, 4.85, 7.4], [11.25, 5.25, 8.6], "dark")
    add([6.5, 4.5, 6.75], [9.5, 7.5, 9.25], "dark")                     # coeur de la garde
    add([7.2, 5.2, 6.5], [8.8, 6.8, 9.5], "gem")                        # gemme centrale
    sym([2.9, 4.9, 7.0], [3.75, 7.1, 9.0], "trim")                      # embouts
    # --- lame
    add([6.2, 7.15, 7.45], [9.8, 9.25, 8.55], "dark")                   # talon gravé
    add([6.6, 9.25, 7.6], [9.4, bt, 8.4], "metal")                      # plat de la lame
    sym([6.0, 9.25, 7.75], [6.6, bt - 0.4, 8.25], "edge")               # tranchants polis
    add([7.55, 8.5, 7.52], [8.45, bt - 2.5, 8.48], "dark")              # gorge
    add([7.8, 9.0, 7.47], [8.2, bt - 3.0, 8.53], "energy")              # ligne d'énergie
    add([6.75, bt - 1.3, 7.6], [9.25, bt + 1.2, 8.4], "metal", ["z", 45, [8, bt, 8]])   # pointe
    add([7.35, bt + 0.6, 7.7], [8.65, bt + 1.9, 8.3], "edge", ["z", 45, [8, bt + 1.25, 8]])

    if stage >= 1:   # niv. 5 : rivets dorés sur la garde
        sym([4.55, 5.55, 7.05], [4.95, 6.45, 8.95], "trim")
    if stage >= 2:   # niv. 10 : ligne d'énergie le long de la garde
        add([4.5, 5.85, 7.08], [11.5, 6.15, 8.92], "energy")
    if stage >= 3:   # niv. 15 : gemmes latérales de la garde
        sym([5.0, 5.4, 6.95], [5.9, 6.6, 9.05], "gem")
    if stage >= 4:   # niv. 20 : quillons recourbés
        sym([0.6, 6.0, 7.4], [3.0, 7.0, 8.6], "metal", ["z", -22.5, [3.0, 6.5, 8]])
        sym([-0.5, 6.55, 7.55], [0.8, 7.45, 8.45], "trim", ["z", -45, [0.8, 7.0, 8]])
    if stage >= 5:   # niv. 25 : fils d'énergie entre les tranchants et la lame
        sym([6.45, 10, 7.7], [6.65, bt - 1.5, 8.3], "energy")
    if stage >= 6:   # niv. 30 : runes lumineuses sur le talon
        sym([6.7, 7.55, 7.4], [7.2, 8.85, 8.6], "energy")
    if stage >= 7:   # niv. 35 : ailes de garde
        sym([2.0, 7.0, 7.7], [4.0, 10.5, 8.3], "metal", ["z", 22.5, [4.0, 7.0, 8]])
        sym([2.7, 7.5, 7.65], [3.3, 10.0, 8.35], "energy", ["z", 22.5, [4.0, 7.0, 8]])
    if stage >= 8:   # niv. 40 : lame plus longue, dents près du talon
        for y in (10.4, 12.0):
            sym([5.5, y, 7.8], [6.2, y + 0.7, 8.2], "edge", ["z", 45, [5.85, y + 0.35, 8]])
    if stage >= 9:   # niv. 45 : pointe de pommeau, anneaux lumineux sur la fusée
        add([7.6, -7.0, 7.6], [8.4, -5.75, 8.4], "trim", ["y", 45, [8, -6.4, 8]])
        for y in (-1.25, 1.75):
            add([7.05, y, 7.05], [8.95, y + 0.25, 8.95], "energy")
    if stage >= 10:  # niv. 50 : losange de gemme à la base de la lame
        add([7.1, 9.8, 7.3], [8.9, 11.6, 8.7], "gem", ["z", 45, [8, 10.7, 8]])
    if stage >= 11:  # niv. 55 : gorges jumelles
        sym([7.0, 12, 7.5], [7.25, bt - 4, 8.5], "energy")
    if stage >= 12:  # niv. 60 : glyphes flottants autour de la lame
        for y in (13, 17, 21):
            sym([3.6, y, 7.85], [4.4, y + 1.2, 8.15], "energy")
    if stage >= 13:  # niv. 65 : cornes sous la garde
        sym([4.5, 2.5, 7.6], [5.3, 5.0, 8.4], "dark", ["z", -22.5, [5.3, 5.0, 8]])
        sym([4.7, 2.9, 7.55], [5.0, 4.6, 8.45], "energy", ["z", -22.5, [5.3, 5.0, 8]])
    if stage >= 14:  # niv. 70 : deux cristaux flottants
        crystal(add, 2.5, 15)
        crystal(add, 13.5, 15)
    if stage >= 15:  # niv. 75 : grandes ailes
        sym([0.0, 8.5, 7.75], [2.0, 14.5, 8.25], "metal", ["z", 22.5, [2.0, 8.5, 8]])
        sym([0.6, 9.0, 7.7], [1.3, 14.0, 8.3], "energy", ["z", 22.5, [2.0, 8.5, 8]])
        sym([-1.6, 9.5, 7.8], [0.0, 13.0, 8.2], "edge", ["z", 45, [0.0, 9.5, 8]])
    if stage >= 16:  # niv. 80 : cristal au zénith, lignes d'aura le long de la lame
        crystal(add, 8, bt + 3)
        sym([5.2, 14, 7.95], [5.45, bt - 2, 8.05], "energy")
    if stage >= 17:  # niv. 85 : cristaux latéraux
        crystal(add, 0.5, 21)
        crystal(add, 15.5, 21)
    if stage >= 18:  # niv. 90 : deuxième garde sertie sur la fusée
        add([6.9, -1.0, 6.9], [9.1, -0.5, 9.1], "trim")
        sym([6.65, -1.1, 7.6], [6.9, -0.4, 8.4], "gem")
    if stage >= 19:  # niv. 95 : diadème au-dessus de la garde
        add([5.5, 7.15, 7.65], [10.5, 7.6, 8.35], "trim")
        sym([5.6, 7.6, 7.75], [6.2, 8.4, 8.25], "gem")
    if stage >= 20:  # niv. 100 : halo d'énergie autour de la lame
        halo(add, 8, bt - 6, 5.2)
        crystal(add, 2, 2)
        crystal(add, 14, 2)
    return E


def axe_elements(stage):
    E, add = builder()
    handle(add, 11)
    add([6.75, 9.5, 6.75], [9.25, 11, 9.25], "trim")                   # virole
    add([6.5, 11, 6.5], [9.5, 16, 9.5], "dark")                        # douille
    add([6.35, 11.2, 6.35], [9.65, 11.6, 9.65], "trim")
    add([6.35, 15.4, 6.35], [9.65, 15.8, 9.65], "trim")
    if stage >= 20:
        add([6.75, 11.5, 6], [9.25, 15.5, 10], "gem")
    else:
        add([7, 12, 6.25], [9, 15, 9.75], "gem")
    add([2.5, 10.5, 7.4], [6.5, 16.5, 8.6], "metal")                   # corps de la lame
    add([3.0, 11.2, 7.32], [6.0, 15.8, 8.68], "dark")                  # plaque gravée
    add([1, 9.5, 7.6], [2.5, 17.5, 8.4], "edge")                       # tranchant poli
    add([0.5, 16.5, 7.65], [2.5, 18, 8.35], "edge", ["z", -22.5, [2.5, 17.25, 8]])
    add([0.5, 8, 7.65], [2.5, 9.5, 8.35], "edge", ["z", 22.5, [2.5, 8.75, 8]])
    if stage < 10:
        add([9.5, 12.5, 7.25], [11, 14.5, 8.75], "metal")              # talon
    if stage >= 1:   # niv. 5 : liserés dorés
        add([2.5, 16.5, 7.5], [6.5, 16.9, 8.5], "trim")
        add([2.5, 10.1, 7.5], [6.5, 10.5, 8.5], "trim")
    if stage >= 2:   # niv. 10 : fil d'énergie derrière le tranchant
        add([2.4, 10.5, 7.52], [2.7, 16.5, 8.48], "energy")
    if stage >= 4:   # niv. 20 : cristal sur la douille
        add([7, 16, 7], [9, 17.5, 9], "gem")
    if stage >= 5:   # niv. 25 : gemme au centre de la plaque
        add([4.0, 13.0, 7.22], [5.0, 14.0, 8.78], "gem")
    if 6 <= stage < 10:  # bec à l'arrière
        add([11, 12.75, 7.5], [14, 14.25, 8.5], "edge", ["z", -22.5, [11, 13.5, 8]])
    if stage >= 6:   # niv. 30 : cercle runique lumineux autour de la gemme
        halo(add, 4.5, 13.5, 1.4, t=0.14, depth=0.75)
    if stage >= 8:   # niv. 40 : corne au-dessus du tranchant
        add([1, 17.5, 7.7], [2, 20, 8.3], "edge", ["z", 22.5, [1.5, 17.5, 8]])
    if stage >= 10:  # niv. 50 : hache double
        add([9.5, 11, 7.45], [13, 16, 8.55], "metal")
        add([10, 11.6, 7.38], [12.6, 15.4, 8.62], "dark")
        add([13, 10, 7.6], [14.5, 17, 8.4], "edge")
        add([12.9, 10.6, 7.55], [13.15, 16.4, 8.45], "energy")
        add([14.5, 10.5, 7.7], [15, 16.5, 8.3], "gem")
    if stage >= 11:  # niv. 55 : barbes sous la lame
        add([3, 9.25, 7.6], [4, 10.5, 8.4], "edge", ["z", 45, [3.5, 10.5, 8]])
    if stage >= 15:  # niv. 75 : ailes
        add([5.5, 16, 7.7], [7, 19.5, 8.3], "metal", ["z", 22.5, [7, 16, 8]])
        add([9, 16, 7.7], [10.5, 19.5, 8.3], "metal", ["z", -22.5, [9, 16, 8]])
        add([6.0, 16.4, 7.65], [6.5, 19.1, 8.35], "energy", ["z", 22.5, [7, 16, 8]])
        add([9.5, 16.4, 7.65], [10.0, 19.1, 8.35], "energy", ["z", -22.5, [9, 16, 8]])
    if stage >= 19:  # niv. 95 : diadème
        add([6, 16, 7.75], [10, 16.5, 8.25], "gem")
    common_decor(add, stage, handle_runes(6.75, 8.25),
                 [(4, 21), (12, 21), (8, 24), (-2, 13), (18, 13), (-1, 4), (17, 4)], aura=(7, 13.5, 8))
    return E


def shovel_elements(stage):
    E, add = builder()
    top = 19 if stage < 8 else 20                                    # haut de la lame
    handle(add, 9)
    add([6.75, 9, 6.75], [9.25, 10.5, 9.25], "trim")                 # virole
    add([7.25, 10.5, 7.5], [8.75, 12, 8.5], "dark")                  # col
    add([5, 12, 7.5], [11, top, 8.5], "metal")                       # lame
    add([5.6, 12.6, 7.42], [10.4, top - 0.6, 8.58], "dark")          # plaque gravée
    add([4.75, 12, 7.6], [5, top, 8.4], "edge")
    add([11, 12, 7.6], [11.25, top, 8.4], "edge")
    add([5.5, top, 7.55], [10.5, top + 1, 8.45], "edge")             # bout arrondi poli
    add([6.5, top + 1, 7.6], [9.5, top + 2, 8.4], "edge")
    add([7.25, top + 2, 7.65], [8.75, top + 2.75, 8.35], "edge")
    if stage >= 20:
        add([6.25, 13.25, 7.2], [9.75, 16.75, 8.8], "gem")
    else:
        add([6.5, 13.5, 7.25], [9.5, 16.5, 8.75], "gem")
    if stage >= 1:   # nervure au dos + liseré
        add([7.75, 12, 8.5], [8.25, top, 8.75], "trim")
        add([5, 12, 7.45], [11, 12.4, 8.55], "trim")
    if stage >= 2:   # lignes d'énergie qui encadrent la gemme
        add([5.9, 17, 7.38], [10.1, 17.3, 8.62], "energy")
        add([5.9, 12.7, 7.38], [10.1, 13.0, 8.62], "energy")
    if stage >= 4:   # pointe en gemme
        add([7.5, top + 2.5, 7.7], [8.5, top + 3.5, 8.3], "gem", ["z", 45, [8, top + 3, 8]])
    if stage >= 5:   # clous
        add([5.5, 17.6, 7.3], [6.25, 18.35, 8.7], "gem")
        add([9.75, 17.6, 7.3], [10.5, 18.35, 8.7], "gem")
    if stage >= 6:   # épaulettes
        add([3.5, 11.5, 7.7], [5, 14, 8.3], "metal", ["z", -22.5, [5, 12, 8]])
        add([11, 11.5, 7.7], [12.5, 14, 8.3], "metal", ["z", 22.5, [11, 12, 8]])
    if stage >= 7:   # collier
        add([6, 11.75, 7.25], [10, 12.25, 8.75], "trim")
    if stage >= 8:   # veines d'énergie verticales
        add([5.3, 13, 7.5], [5.55, top - 0.5, 8.5], "energy")
        add([10.45, 13, 7.5], [10.7, top - 0.5, 8.5], "energy")
    if stage >= 10:  # grand losange de gemme
        add([6.9, 15, 7.15], [9.1, 17.2, 8.85], "gem", ["z", 45, [8, 16.1, 8]])
    if stage >= 11:  # pointes latérales
        add([3.75, 15, 7.7], [4.75, 16, 8.3], "edge", ["z", 45, [4.25, 15.5, 8]])
        add([11.25, 15, 7.7], [12.25, 16, 8.3], "edge", ["z", 45, [11.75, 15.5, 8]])
    if stage >= 15:  # ailes
        add([4, 8.5, 7.7], [6, 12, 8.3], "metal", ["z", 22.5, [6, 9, 8]])
        add([10, 8.5, 7.7], [12, 12, 8.3], "metal", ["z", -22.5, [10, 9, 8]])
        add([4.6, 9.0, 7.65], [5.2, 11.6, 8.35], "energy", ["z", 22.5, [6, 9, 8]])
        add([10.8, 9.0, 7.65], [11.4, 11.6, 8.35], "energy", ["z", -22.5, [10, 9, 8]])
    if stage >= 19:  # diadème
        add([5, top - 0.5, 7.4], [11, top, 8.6], "gem")
    common_decor(add, stage, handle_runes(6, 7.5),
                 [(2, 20), (14, 20), (8, top + 6), (-1, 14), (17, 14), (0, 4), (16, 4)], aura=(8, 16, 7))
    return E


def hoe_elements(stage):
    E, add = builder()
    bottom = 9.5 if stage < 8 else 8.5                               # bas de la lame
    handle(add, 13)
    add([6.75, 11.5, 6.75], [9.25, 13, 9.25], "trim")                # virole
    add([6.5, 13, 6.5], [9.5, 16, 9.5], "dark")                      # douille
    add([6.35, 15.6, 6.35], [9.65, 16.0, 9.65], "trim")
    if stage >= 20:
        add([6.75, 13.25, 6], [9.25, 15.75, 10], "gem")
    else:
        add([7, 13.5, 6.25], [9, 15.5, 9.75], "gem")
    add([1.5, 13.75, 7.25], [6.5, 15.75, 8.75], "metal")             # bras
    add([2.0, 14.2, 7.18], [6.0, 15.3, 8.82], "dark")
    add([0, bottom, 6.5], [2.5, 15.75, 9.5], "metal")                # lame
    add([0.25, bottom - 1, 6.75], [2.25, bottom, 9.25], "edge")      # tranchant
    add([9.5, 14, 7.25], [11.5, 15.5, 8.75], "dark")                 # contrepoids
    if stage >= 1:   # arête du bras
        add([2, 15.75, 7.5], [6.5, 16.25, 8.5], "trim")
    if stage >= 2:   # fil d'énergie le long du bras et de la lame
        add([2.2, 14.6, 7.12], [5.8, 14.9, 8.88], "energy")
        add([-0.25, bottom - 0.5, 6.6], [0.05, 15, 9.4], "energy")
    if stage >= 4:   # cristal sur la douille
        add([7, 16, 7], [9, 17.5, 9], "gem")
    if stage >= 5:   # clou sur le bras
        add([3.5, 13.5, 7.05], [4.5, 14.5, 8.95], "gem")
    if stage >= 6:   # pointe du contrepoids
        add([11.5, 14.25, 7.5], [14, 15.25, 8.5], "edge", ["z", 22.5, [11.5, 14.75, 8]])
    if stage >= 7:   # incrustation sur la lame
        add([0.6, 11, 6.35], [2, 13, 9.65], "gem")
    if stage >= 10:  # dents
        for (z0, z1) in ((6.75, 7.5), (7.6, 8.4), (8.5, 9.25)):
            add([0.5, bottom - 3, z0], [1.75, bottom - 1, z1], "edge")
    if stage >= 11:  # barbe sous le bras
        add([3, 12.5, 7.6], [4, 13.75, 8.4], "edge", ["z", 45, [3.5, 13.75, 8]])
    if stage >= 15:  # ailes
        add([5.5, 16, 7.7], [7, 19.5, 8.3], "metal", ["z", 22.5, [7, 16, 8]])
        add([9, 16, 7.7], [10.5, 19.5, 8.3], "metal", ["z", -22.5, [9, 16, 8]])
        add([6.0, 16.4, 7.65], [6.5, 19.1, 8.35], "energy", ["z", 22.5, [7, 16, 8]])
        add([9.5, 16.4, 7.65], [10.0, 19.1, 8.35], "energy", ["z", -22.5, [9, 16, 8]])
    if stage >= 19:  # diadème
        add([6, 16, 7.75], [10, 16.5, 8.25], "gem")
    common_decor(add, stage, handle_runes(7, 8.5),
                 [(4, 20), (13, 19.5), (8, 22.5), (-3, 12), (18, 12), (-2, 3), (17, 3)], aura=(6, 14, 7))
    return E


def spear_elements(stage):
    """Lance : long manche, fer en feuille aux lignes d'énergie, ailerons ; s'enrichit à chaque forme."""
    E, add = builder()
    sym = sym8(add)
    top = 27.5 if stage < 8 else 29.5                                # pointe du fer
    # manche
    add([7.4, -10, 7.4], [8.6, 18, 8.6], "handle")
    add([7.1, -11.5, 7.1], [8.9, -10, 8.9], "dark")                  # talon
    add([7.25, -11.75, 7.25], [8.75, -11.5, 8.75], "trim")
    for y in (-3, -1.5, 0, 1.5):
        add([7.2, y, 7.2], [8.8, y + 0.9, 8.8], "grip")
    add([7.3, -3.5, 7.3], [8.7, 3, 8.7], "grip")
    add([7.15, 3, 7.15], [8.85, 3.35, 8.85], "trim")
    add([7, 17, 7], [9, 19, 9], "dark")                              # douille
    add([6.85, 18.6, 6.85], [9.15, 19.0, 9.15], "trim")
    # fer en forme de feuille
    add([6.5, 19, 7.6], [9.5, 22, 8.4], "metal")
    add([6, 22, 7.65], [10, top - 3.5, 8.35], "metal")
    sym([5.7, 22.3, 7.75], [6.0, top - 3.8, 8.25], "edge")           # tranchants polis
    add([6.75, top - 3.5, 7.7], [9.25, top - 1, 8.3], "edge")
    add([7.1, top - 1.9, 7.7], [8.9, top - 0.1, 8.3], "edge", ["z", 45, [8, top - 1, 8]])
    add([7.6, 22.5, 7.58], [8.4, top - 3.0, 8.42], "dark")           # arête centrale
    if stage >= 20:
        add([7, 20.5, 7.35], [9, 24, 8.65], "gem")
    else:
        add([7.25, 21, 7.4], [8.75, 23.5, 8.6], "gem")
    if stage >= 1:   # niv. 5 : anneaux de douille
        add([6.8, 16.5, 6.8], [9.2, 17, 9.2], "trim")
    if stage >= 2:   # niv. 10 : nervure d'énergie
        add([7.82, 23.5, 7.53], [8.18, top - 1.5, 8.47], "energy")
    if stage >= 3:   # niv. 15 : talon serti
        add([7.4, -12.6, 7.4], [8.6, -11.75, 8.6], "gem")
    if stage >= 4:   # niv. 20 : ailerons
        add([4.5, 18.5, 7.6], [11.5, 19.5, 8.4], "metal")
        add([4.8, 18.85, 7.55], [11.2, 19.15, 8.45], "energy")
    if stage >= 5:   # niv. 25 : rubans
        add([6.4, 13, 7.6], [6.9, 16.5, 8.4], "grip")
        add([9.1, 13.5, 7.6], [9.6, 16.5, 8.4], "grip")
    if stage >= 6:   # niv. 30 : ailerons relevés
        sym([2.5, 18.5, 7.65], [4.5, 19.5, 8.35], "edge", ["z", -22.5, [4.5, 19, 8]])
    if stage >= 7:   # niv. 35 : gemmes sur les ailerons
        sym([5, 18.25, 7.4], [6, 19.75, 8.6], "gem")
    if stage >= 9:   # niv. 45 : garde sur le manche
        add([7, 4, 7], [9, 4.75, 9], "trim")
    if stage >= 10:  # niv. 50 : lames latérales (pertuisane)
        sym([4, 20, 7.7], [6.5, 21.25, 8.3], "edge", ["z", 45, [6.5, 20.5, 8]])
        sym([4.6, 20.45, 7.65], [6.2, 20.8, 8.35], "energy", ["z", 45, [6.5, 20.5, 8]])
    if stage >= 11:  # niv. 55 : crochets
        sym([5.25, 22.5, 7.7], [6, 24, 8.3], "edge", ["z", 22.5, [6, 22.5, 8]])
    if stage >= 12:  # niv. 60 : runes lumineuses sur le manche
        for y in (7, 9.5, 12):
            add([7.8, y, 7.25], [8.2, y + 0.75, 7.4], "energy")
            add([7.8, y, 8.6], [8.2, y + 0.75, 8.75], "energy")
    if stage >= 13:  # niv. 65 : pointe de talon
        add([7.6, -14.5, 7.6], [8.4, -12.6, 8.4], "edge", ["y", 45, [8, -13.5, 8]])
    if stage >= 15:  # niv. 75 : ailes
        sym([3, 19.5, 7.7], [5, 24, 8.3], "metal", ["z", 22.5, [5, 19.5, 8]])
        sym([3.6, 20.0, 7.65], [4.3, 23.5, 8.35], "energy", ["z", 22.5, [5, 19.5, 8]])
    if stage >= 18:  # niv. 90 : deuxième garde sertie
        add([7, 5.5, 7], [9, 6, 9], "trim")
        sym([6.7, 5.4, 7.6], [7, 6.1, 8.4], "gem")
    if stage >= 19:  # niv. 95 : diadème
        add([6, 19, 7.5], [10, 19.4, 8.5], "gem")
    crystals = [(3, 27), (13, 27), (8, 30.3), (1, 15), (15, 15), (2, 5), (14, 5)]
    if stage >= 14:
        for c in crystals[0:2]:
            crystal(add, *c)
    if stage >= 16:
        crystal(add, *crystals[2])
    if stage >= 17:
        for c in crystals[3:5]:
            crystal(add, *c)
    if stage >= 20:
        for c in crystals[5:7]:
            crystal(add, *c)
        halo(add, 8, 23, 5, z=8.0, depth=0.2)
    return E


# --------------------------------------------------------------------------- arc (même disposition que l'arc vanilla)

BOW_DEPTH = {  # profondeur de chaque matière (z de / à)
    "L": (7.25, 8.75), "M": (7.0, 9.0), "G": (7.0, 9.0), "S": (7.8, 8.2),
    "A": (7.6, 8.4), "H": (7.4, 8.6), "F": (7.5, 8.5), "D": (6.75, 9.25),
}
BOW_T1 = (14.5, 1.5)   # pointe du haut (coordonnées pixel, y vers le bas)
BOW_T2 = (1.5, 14.5)   # pointe du bas
BOW_C = (1.0, 1.0)     # point de contrôle : l'arc bombe vers le haut à gauche, comme l'arc vanilla


def bow_arc(t):
    a, b, c = (1 - t) ** 2, 2 * (1 - t) * t, t ** 2
    return (a * BOW_T1[0] + b * BOW_C[0] + c * BOW_T2[0], a * BOW_T1[1] + b * BOW_C[1] + c * BOW_T2[1])


def bow_line(grid, p, q, mat, override=False):
    n = int(max(abs(q[0] - p[0]), abs(q[1] - p[1])) * 4) + 1
    for i in range(n + 1):
        x = p[0] + (q[0] - p[0]) * i / n
        y = p[1] + (q[1] - p[1]) * i / n
        cell = (int(x), int(y))
        if 0 <= cell[0] < 16 and 0 <= cell[1] < 16 and (override or cell not in grid):
            grid[cell] = mat


def bow_grid(stage, pull):
    """Grille 16x16 de l'arc. pull = None (au repos), 0, 1 ou 2 (tension croissante)."""
    grid = {}

    def arc_cell(t, out=0):
        x, y = bow_arc(t)
        ox, oy = (0, -out) if t < 0.5 else (-out, 0)      # vers l'extérieur : en haut / à gauche
        return int(x) + ox, int(y) + oy

    steps = 200
    for i in range(steps + 1):
        t = i / steps
        mat = "M" if t < 0.07 or t > 0.93 else "G" if 0.42 < t < 0.58 else "L"
        x, y = bow_arc(t)
        for cx, cy in ((int(x), int(y)), (int(x + 0.8), int(y + 0.8))):
            if 0 <= cx < 16 and 0 <= cy < 16:
                grid[(cx, cy)] = mat
        if stage >= 7 and 0.1 < t < 0.9:                     # niv. 35 : branches plus épaisses
            c = arc_cell(t, 1)
            grid.setdefault(c, "L")

    def put(cell, mat):
        if 0 <= cell[0] < 16 and 0 <= cell[1] < 16:
            grid[cell] = mat

    if stage >= 1:   # niv. 5 : pointes renforcées
        put((15, 1), "M")
        put((1, 15), "M")
    if stage >= 3:   # niv. 15 : gemme sur la poignée
        put(arc_cell(0.5), "D")
    if stage >= 4:   # niv. 20 : incrustations
        put(arc_cell(0.25), "D")
        put(arc_cell(0.75), "D")
    if stage >= 5:   # niv. 25 : bagues de métal
        put(arc_cell(0.35), "M")
        put(arc_cell(0.65), "M")
    if stage >= 6:   # niv. 30 : pointes acérées
        put((14, 0), "M")
        put((0, 14), "M")
    if stage >= 8:   # niv. 40 : lames sur le dos de l'arc
        for t in (0.17, 0.22, 0.27, 0.73, 0.78, 0.83):
            put(arc_cell(t, 2 if stage >= 7 else 1), "M")
    if stage >= 10:  # niv. 50 : gros cristal derrière la poignée
        put(arc_cell(0.5, 1), "D")
        put(arc_cell(0.5, 2), "D")
    if stage >= 11:  # niv. 55 : runes
        put(arc_cell(0.12), "D")
        put(arc_cell(0.88), "D")
    if stage >= 12:  # niv. 60 : pointes en gemme
        put((14, 1), "D")
        put((1, 14), "D")
    if stage >= 15:  # niv. 75 : ailes aux extrémités
        for t, out in ((0.08, 2), (0.12, 3), (0.92, 2), (0.88, 3)):
            put(arc_cell(t, out), "M")
    if stage >= 18:  # niv. 90 : branches serties
        for t in (0.2, 0.3, 0.4, 0.6, 0.7, 0.8):
            put(arc_cell(t), "D")

    # corde : droite au repos, tirée vers le bas à droite quand on bande l'arc
    if pull is None:
        bow_line(grid, BOW_T1, BOW_T2, "S")
    else:
        d = (1.5, 2.5, 3.5)[pull]
        nock = (8 + d, 8 + d)
        bow_line(grid, BOW_T1, nock, "S")
        bow_line(grid, nock, BOW_T2, "S")
        # flèche : de l'encoche vers le coin en haut à gauche
        k = int(nock[0])
        for i in range(2, k):
            grid[(i, i)] = "A"
        for cell in ((1, 1), (2, 1), (1, 2)):
            grid[cell] = "H"
        for cell in ((k, k), (k - 1, k), (k, k - 1)):
            if 0 <= cell[0] < 16 and 0 <= cell[1] < 16:
                grid[cell] = "F"
    return grid


def bow_normal(t):
    """Direction « extérieure » de l'arc (vers le haut à gauche, à l'opposé de la corde), en pixels."""
    dx = 2 * (1 - t) * (BOW_C[0] - BOW_T1[0]) + 2 * t * (BOW_T2[0] - BOW_C[0])
    dy = 2 * (1 - t) * (BOW_C[1] - BOW_T1[1]) + 2 * t * (BOW_T2[1] - BOW_C[1])
    nx, ny = -dy, dx
    if nx + ny > 0:          # on veut pointer vers (-1, -1)
        nx, ny = -nx, -ny
    length = math.hypot(nx, ny) or 1.0
    return nx / length, ny / length


def bow_elements(stage, pull):
    E, add = builder()
    grid = bow_grid(stage, pull)
    tex = {"L": "handle", "M": "edge" if stage >= 3 else "metal", "G": "grip", "S": "energy" if stage >= 2 else "string",
           "A": "handle", "H": "gem" if stage >= 9 else "metal", "F": "gem" if stage >= 13 else "grip", "D": "gem"}
    for y in range(16):
        x = 0
        while x < 16:
            mat = grid.get((x, y))
            if mat is None:
                x += 1
                continue
            end = x
            while end + 1 < 16 and grid.get((end + 1, y)) == mat:
                end += 1
            z0, z1 = BOW_DEPTH[mat]
            if mat == "S" and stage >= 12:          # niv. 60 : corde d'énergie, plus épaisse
                z0, z1 = 7.55, 8.45
            add([x, 15 - y, z0], [end + 1, 16 - y, z1], tex[mat])
            x = end + 1

    def cell(px, py, z0, z1, texture, size=1.0):
        """Un cube sur la case pixel (px, py) (y vers le bas), centré sur la case."""
        cx, cy = px + 0.5, 15.5 - py
        h = size / 2
        add([round(cx - h, 3), round(cy - h, 3), z0], [round(cx + h, 3), round(cy + h, 3), z1], texture)

    def along(t, out):
        x, y = bow_arc(t)
        nx, ny = bow_normal(t)
        return x + nx * out, y + ny * out

    if stage >= 4:   # niv. 20 : poignée blindée, capuchons de gemme des deux côtés
        gx, gy = bow_arc(0.5)
        add([round(gx - 1, 3), round(15.5 - gy - 1, 3), 6.25], [round(gx + 1, 3), round(15.5 - gy + 1, 3), 9.75], "metal")
        add([round(gx - 0.6, 3), round(15.5 - gy - 0.6, 3), 5.9], [round(gx + 0.6, 3), round(15.5 - gy + 0.6, 3), 10.1], "gem")
    if stage >= 1:   # niv. 5 : veines d'énergie le long des branches, sur les deux faces
        for a0, b0 in ((0.1, 0.42), (0.58, 0.9)):
            for k in range(13):
                t = a0 + (b0 - a0) * k / 12
                x, y = bow_arc(t)
                cx, cy = round(x + 0.5, 3), round(15.5 - y, 3)
                for z0, z1 in ((7.15, 7.25), (8.75, 8.85)):
                    add([cx - 0.25, cy - 0.25, z0], [cx + 0.25, cy + 0.25, z1], "energy")
    if stage >= 6:   # niv. 30 : plaques d'armure superposées sur les branches
        for i, t in enumerate([0.12, 0.2, 0.28, 0.36, 0.64, 0.72, 0.8, 0.88]):
            x, y = bow_arc(t)
            size = 1.4 if i % 2 == 0 else 1.1
            cell(int(x), int(y), 6.6, 9.4, "metal", size)
    # Formes pleines (ailerons, ailes) : on remplit une grille de pixels puis on fusionne par rangées.
    deco = {}

    def paint(px, py, kind):
        cellxy = (int(math.floor(px)), int(math.floor(py)))
        if cellxy not in grid and (kind in ("edge", "frame") or cellxy not in deco):
            deco[cellxy] = kind

    if stage >= 8:   # niv. 40 : ailerons tranchants le long de l'arc
        for a0, b0 in ((0.14, 0.36), (0.64, 0.86)):
            for k in range(121):
                t = a0 + (b0 - a0) * k / 120
                height = 0.6 + 2.6 * math.sin(math.pi * k / 120)
                d = 0.6
                while d <= height:
                    px, py = along(t, d + 0.6)
                    paint(px, py, "edge" if d > height - 0.5 else "fin")
                    d += 0.2
    if stage >= 10:  # niv. 50 : ailes de dragon aux extrémités (cadre en métal, membrane en gemme)
        for tip_t, sign in ((0.0, 1), (1.0, -1)):
            for k in range(81):
                f = k / 80
                t = tip_t + sign * f * 0.18
                span = 5.5 * (1 - f) + 0.8
                d = 0.6
                while d <= span:
                    px, py = along(t, d + 0.6)
                    paint(px, py, "frame" if d > span - 0.6 or k < 4 else "membrane")
                    d += 0.2
    depth = {"fin": (7.6, 8.4, "metal"), "edge": (7.4, 8.6, "energy"), "frame": (7.45, 8.55, "trim"), "membrane": (7.8, 8.2, "gem")}
    for y in range(min([c[1] for c in deco] or [0]), max([c[1] for c in deco] or [-1]) + 1):
        xs = sorted(c[0] for c in deco if c[1] == y)
        i = 0
        while i < len(xs):
            kind = deco[(xs[i], y)]
            j = i
            while j + 1 < len(xs) and xs[j + 1] == xs[j] + 1 and deco[(xs[j + 1], y)] == kind:
                j += 1
            z0, z1, texture = depth[kind]
            add([xs[i], 15 - y, z0], [xs[j] + 1, 16 - y, z1], texture)
            i = j + 1
    if stage >= 12:  # niv. 60 : noeuds d'énergie aux extrémités de la corde
        for (px, py) in ((14, 1), (1, 14)):
            cell(px, py, 7.0, 9.0, "gem", 1.6)
    if stage >= 16:  # niv. 80 : halo magique derrière l'arc
        cx, cy = 7.5, 8.5
        for i in range(28):
            a = 2 * math.pi * i / 28
            x, y = cx + math.cos(a) * 9.5, cy + math.sin(a) * 9.5
            add([round(x - 0.45, 3), round(y - 0.45, 3), 4.4], [round(x + 0.45, 3), round(y + 0.45, 3), 5.2],
                "gem" if i % 4 == 0 else "energy" if i % 2 == 0 else "trim")
    if stage >= 18:  # niv. 90 : anneau runique devant l'arc
        cx, cy = 7.5, 8.5
        for i in range(16):
            a = 2 * math.pi * i / 16 + 0.2
            x, y = cx + math.cos(a) * 6.5, cy + math.sin(a) * 6.5
            add([round(x - 0.35, 3), round(y - 0.35, 3), 10.8], [round(x + 0.35, 3), round(y + 0.35, 3), 11.4], "energy")
    if stage >= 20:  # niv. 100 : couronne de pointes de lumière autour du halo
        cx, cy = 7.5, 8.5
        for i in range(8):
            a = 2 * math.pi * i / 8 + math.pi / 8
            x, y = cx + math.cos(a) * 11.5, cy + math.sin(a) * 11.5
            angle = 45 if i % 2 == 0 else -45
            add([round(x - 0.4, 3), round(y - 1.6, 3), 4.5], [round(x + 0.4, 3), round(y + 1.6, 3), 5.1], "gem",
                ["z", angle, [round(x, 3), round(y, 3), 4.8]])
    for (cx, cy, s) in ((-1.5, 17.5, 14), (17.5, -1.5, 14), (-2.5, 13, 16), (13, 18.5, 17), (18.5, 3, 17), (6, 20, 20), (20, 6, 20)):
        if stage >= s:
            crystal(add, cx, cy)
    return E


def bow_gui_scale(elements):
    """Réduit l'icône si les décorations dépassent du cadre (halo, cristaux)."""
    extent = 8.0
    for el in elements:
        for i in (0, 1):
            extent = max(extent, abs(el["from"][i] - 8), abs(el["to"][i] - 8))
    return round(min(1.0, 8.0 / extent), 3)


def bow_model(stage, pull):
    return {
        "credit": "Evo Tool - arc 3D généré par tools/generate_assets.py",
        "gui_light": "front",
        "textures": stage_textures(stage, {"string": f"{MOD}:item/bow/string"}),
        "elements": (elements := finalize(bow_elements(stage, pull), stage)),
        # mêmes transformations que l'arc vanilla (models/item/bow.json + item/generated)
        "display": {
            "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [bow_gui_scale(elements)] * 3},
        },
    }


def gen_bow():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = 228 - ((x + y) % 3) * 10
            img.putpixel((x, y), (v, v, v + 4, 255))
    save_png(img, "item", "bow", "string")
    names = {None: "rest", 0: "pulling_0", 1: "pulling_1", 2: "pulling_2"}

    def ref(i, pull):
        return {"type": "minecraft:model", "model": f"{MOD}:item/evolving_bow_stage{i}_{names[pull]}"}

    for i in range(len(STAGES)):
        for pull in names:
            write_json(os.path.join(ASSETS, "models", "item", f"evolving_bow_stage{i}_{names[pull]}.json"), bow_model(i, pull))

    def by_stage(i):
        # comme items/bow.json : au repos, puis 3 étapes de tension selon la durée d'utilisation.
        # Le drapeau custom_model_data n°0 force l'arc bandé (utilisé par l'arc géant de l'arc céleste).
        return {
            "type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0,
            "on_true": ref(i, 2),
            "on_false": {
                "type": "minecraft:condition", "property": "minecraft:using_item",
                "on_false": ref(i, None),
                "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                            "fallback": ref(i, 0),
                            "entries": [{"threshold": 0.65, "model": ref(i, 1)}, {"threshold": 0.9, "model": ref(i, 2)}]},
            },
        }

    write_json(os.path.join(ASSETS, "items", "evolving_bow.json"), {
        "model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                  "fallback": by_stage(0),
                  "entries": [{"threshold": i, "model": by_stage(i)} for i in range(len(STAGES))]}
    })


TOOL_MODELS = {
    # id: (éléments, hauteur approximative pour l'échelle d'affichage)
    "evolving_sword": (sword_elements, 36),
    "evolving_axe": (axe_elements, 28),
    "evolving_shovel": (shovel_elements, 30),
    "evolving_hoe": (hoe_elements, 27),
    "evolving_spear": (spear_elements, 36),
}


def spear_display():
    """Reprend la tenue de la lance vanilla (item/spear_in_hand).
    Le sprite vanilla en main (32x32) est dessiné pointe en HAUT À GAUCHE (axe à 135°),
    notre modèle est vertical (axe à 90°) : on ajoute +45° autour de Z (rotation appliquée en premier ;
    pour la main gauche le jeu inverse Z, d'où -45°). Échelle : le sprite fait ~22,6 unités en diagonale,
    notre lance ~45 unités, donc on divise l'échelle vanilla par 2."""
    d = display(36)
    d["thirdperson_righthand"] = {"rotation": [5, 270, 5], "translation": [0, 2, 2], "scale": [0.85, 0.85, 0.85]}
    d["thirdperson_lefthand"] = {"rotation": [5, -270, -5], "translation": [0, 2, 2], "scale": [0.85, 0.85, 0.85]}
    d["firstperson_righthand"] = {"rotation": [-20, 90, 10], "translation": [3.13, 2, 0.13], "scale": [0.68, 0.68, 0.68]}
    d["firstperson_lefthand"] = {"rotation": [-20, -90, -10], "translation": [3.13, 2, 0.13], "scale": [0.68, 0.68, 0.68]}
    return d


def tool_model(item, stage):
    elements, height = TOOL_MODELS[item]
    model = pickaxe_model(stage)
    model["credit"] = "Evo Tool - modèle 3D généré par tools/generate_assets.py"
    model["elements"] = finalize(elements(stage), stage)
    model["display"] = spear_display() if item == "evolving_spear" else display(height)
    return model


# --------------------------------------------------------------------------- armure de chevalier

def horn_texture(st, seed):
    """Corne : ivoire teinté par la couleur de la forme, avec des anneaux."""
    base = lerp(hexc("e8dcc0"), st["metal"], 0.35)
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        ring = 0.78 if y % 4 == 0 else 1.0
        for x in range(16):
            f = ring * (1.12 - 0.3 * (x / 15.0)) + r.uniform(-0.04, 0.04)
            img.putpixel((x, y), shade(base, f))
    return img


def visor_texture():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), (14 + (x * 3 + y) % 6, 10, 20 + (x + y) % 8, 255))
    return img


def mirror_x(add):
    """Ajoute un élément et son symétrique de l'autre côté du casque (x -> 16 - x, angle z inversé)."""
    def both(frm, to, tex, rot=None):
        add(frm, to, tex, rot)
        mfrm = [16 - to[0], frm[1], frm[2]]
        mto = [16 - frm[0], to[1], to[2]]
        mrot = None
        if rot:
            axis, angle, origin = rot
            mrot = [axis, -angle if axis in ("z", "y") else angle, [16 - origin[0], origin[1], origin[2]]]
        add(mfrm, mto, tex, mrot)
    return both


def helmet_elements(stage):
    """Heaume de chevalier à cornes aux yeux lumineux, face avant = nord. 16 unités ~ 1,25 tête une fois porté."""
    E, add = builder()
    sym = mirror_x(add)
    add([1, 0.5, 1], [15, 14.5, 15], "metal")                        # coque
    add([2, 14.5, 2], [14, 16, 14], "metal")                         # dôme
    add([1.5, 0.5, 0.5], [14.5, 13.5, 1], "dark")                    # plaque frontale gravée
    add([2.5, 8, 0.25], [13.5, 9.25, 0.5], "energy" if stage >= 12 else "visor")   # fente des yeux
    sym([3.5, 8.3, 0.1], [6.5, 8.95, 0.25], "energy")                # yeux lumineux
    add([1.25, 9.25, 0.3], [14.75, 9.65, 0.55], "trim")              # sourcils dorés
    for x in (9.5, 11, 12.5):                                        # trous d'aération
        for y in (3, 4.75):
            add([x, y, 0.25], [x + 0.75, y + 0.75, 0.5], "visor")
    add([7.5, 9.65, 0.25], [8.5, 13.5, 0.5], "edge")                 # arête du nez
    add([7.5, 0.5, 0.25], [8.5, 8, 0.5], "edge")
    add([0.85, 0.5, 0.85], [15.15, 1.0, 15.15], "trim")              # rebord
    # cornes : base, segment montant, puis (plus tard) pointe recourbée et bout en gemme
    sym([-1.75, 9.75, 6.5], [1, 12.75, 9.5], "horn")
    sym([-4.75, 11, 6.9], [-1.5, 13.25, 9.1], "horn", ["z", -22.5, [-1.5, 12.1, 8]])
    if stage >= 1:   # bandeau doré
        add([0.75, 9.75, 0.75], [15.25, 10.25, 15.25], "trim")
    if stage >= 2:   # protège-joues
        sym([0.5, 1, 2], [1, 8, 10], "dark")
        sym([0.35, 1, 1.75], [0.6, 8, 2.25], "trim")
    if stage >= 3:   # gemme frontale
        add([7, 10.5, -0.25], [9, 12.5, 0.25], "gem")
    if stage >= 4:   # cornes plus longues
        sym([-6, 12.5, 7.2], [-3.75, 17.5, 8.8], "horn", ["z", 22.5, [-4.9, 12.5, 8]])
    if stage >= 5:   # rivets sertis + ligne d'énergie autour du bandeau
        for z in (4, 11):
            sym([0.75, 12, z], [1, 13, z + 1], "gem")
        add([0.7, 10.85, 0.7], [15.3, 11.1, 15.3], "energy")
    if stage >= 6:   # crête
        add([7.25, 16, 3], [8.75, 17.5, 13], "metal")
        add([7.6, 17.5, 3.5], [8.4, 17.75, 12.5], "energy")
    if stage >= 7:   # protège-nuque
        add([1.5, 0, 15], [14.5, 4, 15.75], "dark")
        add([1.5, 3.6, 15.1], [14.5, 3.9, 15.85], "trim")
    if stage >= 8:   # pointes sur la crête, anneaux lumineux à la base des cornes
        for z in (5, 8, 11):
            add([7.5, 17.75, z], [8.5, 19 + (0.5 if z == 8 else 0), z + 1], "edge")
        sym([-1.05, 9.6, 6.35], [-0.7, 12.9, 9.65], "energy")
    if stage >= 9:   # mentonnière
        add([2.5, 0, 0], [13.5, 1.5, 0.5], "trim")
    if stage >= 10:  # bouts de cornes en gemme
        sym([-7.25, 17, 7.5], [-5.75, 20, 8.5], "gem", ["z", 45, [-6.5, 17, 8]])
    if stage >= 11:  # petites cornes frontales
        sym([3, 14, 1.5], [4, 16.5, 2.5], "horn", ["z", 22.5, [3.5, 14, 2]])
    if stage >= 13:  # pointes à l'arrière du casque
        for y in (6, 10):
            add([7.5, y, 15], [8.5, y + 1, 17], "edge")
    if stage >= 15:  # ailes
        sym([-1, 13, 9.5], [1, 18, 10], "metal", ["z", 22.5, [1, 13, 9.75]])
        sym([-0.4, 13.5, 9.45], [0.3, 17.5, 10.05], "energy", ["z", 22.5, [1, 13, 9.75]])
    if stage >= 18:  # couronne de gemmes
        for (x, z) in ((3, 3), (12, 3), (3, 12), (12, 12)):
            add([x, 16, z], [x + 1, 17, z + 1], "gem")
    if stage >= 19:  # diadème
        add([2, 13.5, 0.25], [14, 14, 0.5], "gem")
    crystals = [(-1, 20), (17, 20), (8, 23), (-6, 9), (22, 9), (-4, 3), (20, 3)]
    if stage >= 14:
        for c in crystals[0:2]:
            crystal(add, *c)
    if stage >= 16:
        crystal(add, *crystals[2])
    if stage >= 17:
        for c in crystals[3:5]:
            crystal(add, *c)
    if stage >= 20:
        for c in crystals[5:7]:
            crystal(add, *c)
        halo_flat(add, 8, 8, 19.5, 6.5, depth=0.2, t=0.2)
    return E


def helmet_model(stage):
    return {
        "credit": "Evo Tool - heaume 3D généré par tools/generate_assets.py",
        "gui_light": "side",
        "textures": stage_textures(stage, {"horn": f"{MOD}:item/armor/stage{stage}_horn",
                                           "visor": f"{MOD}:item/armor/visor"}),
        "elements": finalize(helmet_elements(stage), stage),
        "display": armor_display(0.48),
    }


BODY = (16, 16, 8, 12, 4)
ARM = (40, 16, 4, 12, 4)
LEG = (0, 16, 4, 12, 4)


def box_faces(box):
    u, v, w, h, d = box
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


SIDES = ("front", "back", "right", "left")


def armor_textures(stage):
    """Textures portées HD (256x128) + masques des lignes lumineuses.
    Chaque forme (tous les 5 niveaux) ajoute de nouveaux détails aux précédents."""
    st = STAGES[stage]
    H = ArmorPainter(st, stage, 10 + stage)        # couche « humanoid » : plastron, bras, bottes
    L = ArmorPainter(st, stage, 50 + stage)        # couche « humanoid_leggings » : ceinture, cuissards
    metal = st["metal"]

    # --- Bases (niv. 0)
    for face in SIDES:

        # plastron : plaque haute (rangées 0-6) puis braconnière (7-11)
        H.plate(BODY, face, range(12), 1.08 if face == "front" else 0.95)
        H.seam(BODY, face, 7)
        H.seam(BODY, face, 9)
        H.trim(BODY, face, 11)
        if face == "front":
            H.fill(BODY, "front", 3.5, 0, 1, 7, lambda hx, hy, W, Hh: shade(metal, 1.45 - 0.1 * (hx > 1)))
        # bras : épaulière (0-4), cotte de mailles (5-7), liseré (8), canon d'avant-bras (9-11)
        H.plate(ARM, face, range(0, 5), 1.15)
        H.seam(ARM, face, 2)
        H.seam(ARM, face, 4)
        H.chain(ARM, face, range(5, 8))
        H.trim(ARM, face, 8)
        H.plate(ARM, face, range(9, 12), 1.0)
        # bottes : tige (6-8), liseré (6), pied (9-10), semelle (11)
        H.plate(LEG, face, range(6, 11), 1.0)
        H.trim(LEG, face, 6)
        H.fill(LEG, face, 0, 11, 4, 1, lambda hx, hy, W, Hh: shade(metal, 0.3 + 0.1 * (hy == 0)))
        if face == "front":
            H.fill(LEG, "front", 0, 9, 4, 2, lambda hx, hy, W, Hh: shade(metal, 1.4 - 0.25 * hy / Hh))
        # ceinture (jambières, rangées 7-11) et cuissards (0-9)
        L.cloth(BODY, face, range(7, 12))
        L.trim(BODY, face, 7)
        L.trim(BODY, face, 11)
        L.plate(LEG, face, range(0, 10), 1.0)
        L.seam(LEG, face, 3)
        L.seam(LEG, face, 9)
        if face == "front":
            L.fill(LEG, "front", 0, 5, 4, 2, lambda hx, hy, W, Hh: shade(metal, 1.5 - 0.3 * abs(hy - Hh / 2) / Hh))

    for painter, boxes in ((H, (BODY, ARM)), (L, (LEG,))):
        for box in boxes:
            for face, f in (("top", 1.25), ("bottom", 0.55)):
                x0, y0, w, h = painter.rect(box, face)
                for hy in range(h):
                    for hx in range(w):
                        painter.img.putpixel((x0 + hx, y0 + hy), shade(metal, f + painter.r.uniform(-0.04, 0.04)))
    x0, y0, w, h = H.rect(LEG, "bottom")
    for hy in range(h):
        for hx in range(w):
            H.img.putpixel((x0 + hx, y0 + hy), shade(metal, 0.3))
    H.gem(BODY, "front", 4, 3.5, 0.9)                                 # petite gemme
    L.gem(BODY, "front", 4, 9, 1.0)                                   # boucle de ceinture
    H.energy(BODY, "front", [(1.2, 6.3), (6.8, 6.3)])                 # première ligne d'énergie

    if stage >= 1:   # niv. 5 : rivets
        for face in ("front", "back"):
            for (x, y) in ((0.6, 0.6), (6.6, 0.6), (0.6, 5.2), (6.6, 5.2)):
                H.rivet(BODY, face, x, y)
        for face in SIDES:
            H.rivet(ARM, face, 0.5, 9.6)
            H.rivet(ARM, face, 2.8, 9.6)
    if stage >= 2:   # niv. 10 : jointures lumineuses entre les lames
        for face in SIDES:
            W = 8 if face in ("front", "back") else 4
            H.energy(BODY, face, [(0.3, 9.4), (W - 0.3, 9.4)])
    if stage >= 3:   # niv. 15 : grosse gemme au centre du plastron
        H.gem(BODY, "front", 4, 3.2, 1.6)
    if stage >= 4:   # niv. 20 : bordure lumineuse des épaulières
        for face in SIDES:
            H.energy(ARM, face, [(0.2, 4.4), (3.8, 4.4)])
    if stage >= 5:   # niv. 25 : gemmes sur les genouillères
        L.gem(LEG, "front", 2, 6, 1.0)
    if stage >= 6:   # niv. 30 : épaulières à pointes
        for face in ("right", "front", "back"):
            H.fill(ARM, face, 1, 0, 2, 1.2, lambda hx, hy, W, Hh: shade(metal, 1.6 - 0.6 * hy / Hh) if abs(hx - W / 2) < (Hh - hy) else None)
    if stage >= 7:   # niv. 35 : filigranes gravés
        H.filigree(BODY, "front", 0.5, 1.5, 2.6, 1.2)
        H.filigree(BODY, "front", 4.9, 1.5, 2.6, 1.2)
        for face in SIDES:
            H.filigree(ARM, face, 0.3, 0.6, 3.4, 1.0)
    if stage >= 8:   # niv. 40 : manchettes lumineuses des gantelets
        for face in SIDES:
            H.energy(ARM, face, [(0.2, 11.4), (3.8, 11.4)])
    if stage >= 9:   # niv. 45 : circuits d'énergie sur le plastron
        H.energy(BODY, "front", [(4, 4.8), (4, 6.3)])
        H.energy(BODY, "front", [(1.2, 6.3), (0.6, 7.0)])
        H.energy(BODY, "front", [(6.8, 6.3), (7.4, 7.0)])
    if stage >= 10:  # niv. 50 : éperons et bouts de bottes sertis
        H.gem(LEG, "front", 2, 9.8, 0.9)
        H.energy(LEG, "back", [(1, 9.5), (3, 9.5)])
    if stage >= 11:  # niv. 55 : clous lumineux sur la ceinture
        for face in ("front", "back"):
            for x in (1.2, 6.8):
                L.node(BODY, face, x, 9.4, 0.7)
    if stage >= 12:  # niv. 60 : runes lumineuses
        for x in (1.3, 6.7):
            H.energy(BODY, "front", [(x, 1.4), (x, 4.6)])
            H.node(BODY, "front", x, 1.4, 0.6)
        L.energy(LEG, "front", [(2, 0.6), (2, 2.6)])
    if stage >= 13:  # niv. 65 : cape dans le dos
        cape = st["grip"]
        H.fill(BODY, "back", 0.5, 0.5, 7, 11.5,
               lambda hx, hy, W, Hh: shade(cape, 1.1 - 0.35 * hy / Hh + (0.08 if (hx // 3) % 2 else 0)))
        H.gem(BODY, "back", 4, 1.2, 0.9)
    if stage >= 14:  # niv. 70 : lignes lumineuses sur les bras
        for face in ("right", "left"):
            H.energy(ARM, face, [(2, 0.8), (2, 4.0)])
            H.energy(ARM, face, [(2, 9.2), (2, 11.2)])
    if stage >= 15:  # niv. 75 : ailes brodées en lumière sur la cape
        H.energy(BODY, "back", [(4, 2.5), (1.0, 4.5), (0.8, 8.5)])
        H.energy(BODY, "back", [(4, 2.5), (7.0, 4.5), (7.2, 8.5)])
        H.energy(BODY, "back", [(4, 2.5), (2.2, 6.0)])
        H.energy(BODY, "back", [(4, 2.5), (5.8, 6.0)])
    if stage >= 16:  # niv. 80 : ailettes lumineuses sur les bottes
        for face in ("right", "left"):
            H.energy(LEG, face, [(0.6, 8.5), (2.0, 7.2), (3.4, 6.9)])
    if stage >= 17:  # niv. 85 : bandes lumineuses sur les cuissards
        for face in ("right", "left"):
            L.energy(LEG, face, [(2, 0.4), (2, 8.6)])
    if stage >= 18:  # niv. 90 : bordures lumineuses du plastron
        H.energy(BODY, "front", [(0.25, 0.3), (0.25, 6.6)])
        H.energy(BODY, "front", [(7.75, 0.3), (7.75, 6.6)])
    if stage >= 19:  # niv. 95 : filigrane doré et noeuds d'énergie
        for x in (1, 3, 5, 7):
            H.node(BODY, "front", x, 0.4, 0.45)
            L.node(BODY, "front", x, 7.5, 0.45)
        for face in SIDES:
            H.node(ARM, face, 0.4, 0.4, 0.4)
            H.node(ARM, face, 3.6, 0.4, 0.4)
    if stage >= 20:  # niv. 100 : armure ultime, toutes les jointures brillent
        for face in SIDES:
            W = 8 if face in ("front", "back") else 4
            H.energy(BODY, face, [(0.3, 7.4), (W - 0.3, 7.4)])
            H.energy(BODY, face, [(0.3, 11.5), (W - 0.3, 11.5)])
            L.energy(LEG, face, [(0.3, 3.4), (3.7, 3.4)])
            H.energy(ARM, face, [(0.2, 2.4), (3.8, 2.4)])
        r = random.Random(100)
        for _ in range(6):
            H.node(BODY, "front", r.uniform(1, 7), r.uniform(1, 6), 0.35)
    return H.img, L.img, H.glow, L.glow


ARMOR_ITEMS = ["evolving_helmet", "evolving_chestplate", "evolving_leggings", "evolving_boots"]


def gen_armor():
    save_png(visor_texture(), "item", "armor", "visor")
    for i, st in enumerate(STAGES):
        save_png(horn_texture(st, i), "item", "armor", f"stage{i}_horn")
        humanoid, leggings, humanoid_glow, leggings_glow = armor_textures(i)
        save_png(humanoid, "entity", "equipment", "humanoid", f"evolium_stage{i}")
        save_png(leggings, "entity", "equipment", "humanoid_leggings", f"evolium_stage{i}")
        save_png(humanoid_glow, "entity", "evolium_glow", "humanoid", f"stage{i}")
        save_png(leggings_glow, "entity", "evolium_glow", "humanoid_leggings", f"stage{i}")
        write_json(os.path.join(ASSETS, "equipment", f"evolium_stage{i}.json"), {
            "layers": {
                "humanoid": [{"texture": f"{MOD}:evolium_stage{i}"}],
                "humanoid_leggings": [{"texture": f"{MOD}:evolium_stage{i}"}],
            }
        })
        write_json(os.path.join(ASSETS, "models", "item", f"evolving_helmet_stage{i}.json"), helmet_model(i))
        for item in ARMOR_ITEMS[1:]:
            write_json(os.path.join(ASSETS, "models", "item", f"{item}_stage{i}.json"), armor_icon_model(item, i))
    for item in ARMOR_ITEMS:
        write_json(os.path.join(ASSETS, "items", f"{item}.json"), stage_dispatch(item))


def mod_icon():
    """Icône 128x128 : sprite de pioche dessiné en pixel art, agrandi."""
    st = STAGES[10]
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    wood, metal, gem = st["wood"], hexc("8f86a8"), GEMS["evolium"]
    for i in range(4, 22):                                   # manche diagonal
        x, y = i, 31 - i
        img.putpixel((x, y), shade(wood, 1.1))
        img.putpixel((x + 1, y), shade(wood, 0.8))
    for t in range(-11, 12):                                 # tête perpendiculaire
        x, y = 20 + t, 11 + t
        if 0 <= x < 32 and 0 <= y < 32:
            h = 1.0 - abs(t) / 14.0
            curve = int(abs(t) ** 2 / 30)
            for k in range(3):
                px, py = x - curve + k, y + curve - k
                if 0 <= px < 32 and 0 <= py < 32:
                    img.putpixel((px, py), shade(metal, 0.7 + 0.3 * h + 0.1 * k))
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dx) + abs(dy) <= 2:
                img.putpixel((21 + dx, 10 + dy), gem[2] if dx + dy < 0 else gem[1])
    img.putpixel((20, 9), gem[4])
    return img.resize((128, 128), Image.NEAREST)


def gem_light(stage):
    """Les gemmes brillent un peu dès le début, puis de plus en plus fort."""
    return min(15, 5 + stage // 2)


def energy_light(stage):
    """Les lignes d'énergie brillent toujours, au maximum dès le niveau 90."""
    return min(15, 9 + stage // 3)


def finalize(elements, stage):
    """Rend lumineux (light_emission) les éléments en gemme et les lignes d'énergie."""
    for el in elements:
        tex = next(iter(el["faces"].values()))["texture"]
        if tex == "#gem":
            el["light_emission"] = gem_light(stage)
        elif tex == "#energy":
            el["light_emission"] = energy_light(stage)
            el["shade"] = False
    return elements


def stage_textures(stage, extra=None):
    """Jeu de textures d'une forme (partagé par tous les outils, armes et pièces d'armure 3D)."""
    st = f"{MOD}:item/pickaxe/stage{stage}_"
    tex = {"particle": st + "metal"}
    for name in ("handle", "grip", "metal", "gem", "energy", "edge", "dark", "trim"):
        tex[name] = st + name
    tex.update(extra or {})
    return tex


def sym8(add):
    """Ajoute un élément et son symétrique par rapport au plan x = 8 (angles z et y inversés)."""
    def both(frm, to, tex, rot=None):
        add(frm, to, tex, rot)
        mfrm = [16 - to[0], frm[1], frm[2]]
        mto = [16 - frm[0], to[1], to[2]]
        mrot = None
        if rot:
            axis, angle, origin = rot
            mrot = [axis, -angle if axis in ("z", "y") else angle, [16 - origin[0], origin[1], origin[2]]]
        add(mfrm, mto, tex, mrot)
    return both


def halo(add, cx, cy, r, tex="energy", t=0.25, z=8.0, depth=0.25):
    """Anneau octogonal dans le plan x-y (4 barres droites + 4 barres à 45°)."""
    s = 2 * r * math.tan(math.pi / 8)
    h = s / 2
    z0, z1 = z - depth, z + depth
    add([cx - h, cy + r - t, z0], [cx + h, cy + r + t, z1], tex)
    add([cx - h, cy - r - t, z0], [cx + h, cy - r + t, z1], tex)
    add([cx - r - t, cy - h, z0], [cx - r + t, cy + h, z1], tex)
    add([cx + r - t, cy - h, z0], [cx + r + t, cy + h, z1], tex)
    d = r / math.sqrt(2)
    for sx, sy in ((1, 1), (-1, -1), (-1, 1), (1, -1)):
        px, py = cx + sx * d, cy + sy * d
        angle = -45 if sx == sy else 45
        add([px - h, py - t, z0], [px + h, py + t, z1], tex, ["z", angle, [px, py, z]])


def halo_flat(add, cx, cz, y, r, tex="energy", t=0.25, depth=0.25):
    """Anneau octogonal horizontal (plan x-z), autour d'une tête de masse par exemple."""
    s = 2 * r * math.tan(math.pi / 8)
    h = s / 2
    y0, y1 = y - depth, y + depth
    add([cx - h, y0, cz + r - t], [cx + h, y1, cz + r + t], tex)
    add([cx - h, y0, cz - r - t], [cx + h, y1, cz - r + t], tex)
    add([cx - r - t, y0, cz - h], [cx - r + t, y1, cz + h], tex)
    add([cx + r - t, y0, cz - h], [cx + r + t, y1, cz + h], tex)
    d = r / math.sqrt(2)
    for sx, sz in ((1, 1), (-1, -1), (-1, 1), (1, -1)):
        px, pz = cx + sx * d, cz + sz * d
        angle = 45 if sx == sz else -45
        add([px - h, y0, pz - t], [px + h, y1, pz + t], tex, ["y", angle, [px, y, pz]])


def energy_frames(st, frames=8):
    """Texture animée des lignes lumineuses : de l'énergie qui coule le long des lignes."""
    dark, mid, light, bright, spark = st["gem"]
    img = Image.new("RGBA", (16, 16 * frames))
    for f in range(frames):
        for y in range(16):
            for x in range(16):
                phase = (y / 16.0) * 2 - f / frames
                wave = 0.5 + 0.5 * math.sin(2 * math.pi * phase)
                ripple = 0.5 + 0.5 * math.sin(2 * math.pi * ((x + y) / 8.0 + f / frames))
                v = 0.55 * wave + 0.25 * ripple + 0.2
                c = lerp(mid, light, min(1.0, v * 1.15))
                if v > 0.72:
                    c = lerp(c, bright, (v - 0.72) / 0.28)
                if v > 0.93:
                    c = lerp(c, spark, (v - 0.93) / 0.07 * 0.6)
                if st.get("rainbow"):
                    h = ((x + y) / 32.0 + f / frames) % 1.0
                    rr, gg, bb = colorsys.hsv_to_rgb(h, 0.55, 1.0)
                    c = lerp(c, (int(rr * 255), int(gg * 255), int(bb * 255)), 0.5)
                img.putpixel((x, y + 16 * f), c)
    return img


def save_animated(img, frametime, *parts):
    save_png(img, *parts)
    path = os.path.join(ASSETS, "textures", *parts) + ".png.mcmeta"
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}}, f, indent=2)
        f.write("\n")


def edge_texture(st, seed):
    """Tranchant poli : métal très clair avec un reflet net."""
    base = shade(st["metal"], 1.45)
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            f = 1.0 + 0.12 * math.sin((x + y * 0.4) * 0.9) + r.uniform(-0.03, 0.03)
            if (x + y) % 9 == 0:
                f = 1.25
            img.putpixel((x, y), shade(base, f))
    return img


def dark_texture(st, seed):
    """Métal sombre gravé de filigranes (gorges, talons, plaques intérieures)."""
    base = shade(st["metal"], 0.5)
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            f = 1.0 + r.uniform(-0.05, 0.05)
            scroll = math.sin(x * 0.8 + math.sin(y * 0.6) * 2.0)
            if abs(scroll) < 0.18:
                f = 0.55
            elif 0.18 <= scroll < 0.32:
                f = 1.45
            img.putpixel((x, y), shade(base, f))
    for p in ((3, 3), (12, 12)):
        img.putpixel(p, st["gem"][2])
    return img


def trim_color(st):
    return lerp(st["gem"][2], hexc("ffe39a"), 0.45)


def trim_texture(st, seed):
    """Dorures : liserés et ornements, teintés par la gemme de la forme."""
    base = trim_color(st)
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            band = 1.18 if y % 4 == 0 else 0.8 if y % 4 == 3 else 1.0
            f = band + 0.08 * math.sin(x * 1.3) + r.uniform(-0.03, 0.03)
            img.putpixel((x, y), shade(base, f))
    return img


def armor_display(gui_scale):
    return {
        # Porté (casque) : rendu sur la tête comme une citrouille sculptée (face avant = nord).
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "gui": {"rotation": [20, 200, 0], "translation": [0, 0, 0], "scale": [gui_scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
        "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
        "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
    }


def chestplate_elements(stage):
    """Plastron 3D (icône) : face avant = nord, comme le heaume."""
    E, add = builder()
    sym = mirror_x(add)
    add([3.5, 1.5, 6], [12.5, 12.5, 10], "metal")                    # torse
    add([4, 6, 5.5], [12, 12.25, 6], "metal")                        # pectoral
    for y in (1.75, 3.25, 4.75):                                     # lames de la braconnière
        add([4, y, 5.6], [12, y + 1.1, 6], "dark")
    add([7.6, 6, 5.25], [8.4, 12.25, 5.5], "edge")                   # arête centrale
    add([7.1, 8.6, 5.0], [8.9, 10.4, 5.5], "gem")                    # gemme
    sym([0.5, 9.5, 5.25], [3.75, 13.25, 10.75], "metal")             # épaulières
    sym([0.25, 9.25, 5.0], [3.75, 9.75, 11.0], "trim")
    add([5, 12.5, 6.5], [11, 13.5, 9.5], "trim")                     # col
    add([4, 2, 10], [12, 12, 10.4], "dark")                          # dos
    add([4, 1.25, 5.45], [12, 1.75, 10.55], "trim")                  # ceinture
    if stage >= 1:   # rivets
        for (x, y) in ((4.6, 11.3), (10.9, 11.3), (4.6, 7), (10.9, 7)):
            add([x, y, 5.35], [x + 0.5, y + 0.5, 5.5], "trim")
    if stage >= 2:   # jointures lumineuses
        add([4.1, 2.95, 5.52], [11.9, 3.2, 5.6], "energy")
        add([4.1, 4.45, 5.52], [11.9, 4.7, 5.6], "energy")
    if stage >= 3:   # grosse gemme
        add([6.8, 8.3, 4.8], [9.2, 10.7, 5.1], "gem")
    if stage >= 4:   # bordure colorée des épaulières
        sym([0.25, 13.25, 5.0], [3.75, 13.6, 11.0], "gem")
    if stage >= 6:   # épaulières à pointes
        sym([0.6, 13.2, 7.6], [1.6, 15.2, 8.4], "edge", ["z", 22.5, [1.1, 13.2, 8]])
    if stage >= 7:   # blason en croix
        add([5, 9.25, 5.3], [11, 9.75, 5.5], "trim")
    if stage >= 10:  # lignes d'énergie du pectoral
        sym([4.5, 10.8, 5.4], [7.3, 11.1, 5.5], "energy")
        sym([4.5, 6.4, 5.4], [7.3, 6.7, 5.5], "energy")
    if stage >= 12:  # runes lumineuses sur les épaulières
        sym([0.35, 10.5, 6.5], [0.5, 12.5, 9.5], "energy")
    if stage >= 13:  # cape
        add([4, 0, 10.4], [12, 12, 10.9], "grip")
    if stage >= 15:  # ailes dans le dos
        sym([-1, 5, 10.6], [4.5, 13, 11.1], "metal", ["z", -22.5, [4.5, 9, 10.85]])
        sym([-0.4, 6, 10.55], [3.8, 12.2, 11.15], "energy", ["z", -22.5, [4.5, 9, 10.85]])
    if stage >= 18:  # bordures en gemme
        sym([3.85, 6, 5.4], [4.15, 12.25, 5.6], "gem")
    if stage >= 20:  # halo
        halo(add, 8, 8, 7.5, z=11.5, depth=0.2)
    return E


def leggings_elements(stage):
    E, add = builder()
    sym = mirror_x(add)
    add([3.5, 11.5, 6], [12.5, 13.5, 10], "grip")                    # ceinture
    add([7, 11.75, 5.6], [9, 13.25, 6], "trim")                      # boucle
    add([7.5, 12.1, 5.4], [8.5, 12.9, 5.7], "gem")
    sym([3.75, 1, 6.5], [7.75, 11.5, 9.5], "metal")                  # jambes
    sym([3.6, 8, 6.2], [7.9, 11, 6.5], "dark")                       # cuissards
    sym([3.5, 5, 6.0], [8.0, 7.25, 6.6], "edge")                     # genouillères
    sym([3.6, 1, 6.2], [7.9, 4.5, 6.5], "dark")                      # jambières
    if stage >= 5:   # gemmes sur les genouillères
        sym([5.2, 5.6, 5.75], [6.3, 6.7, 6.05], "gem")
    if stage >= 11:  # clous sur la ceinture
        for x in (4.25, 11.25):
            add([x, 12.25, 5.8], [x + 0.5, 12.75, 6.05], "gem")
    if stage >= 12:  # runes lumineuses
        sym([5.5, 8.6, 6.1], [6.0, 10.4, 6.25], "energy")
    if stage >= 17:  # bandes lumineuses sur les côtés
        sym([3.6, 1.5, 7.5], [3.8, 11, 8.5], "energy")
    if stage >= 20:
        sym([3.6, 2.2, 6.05], [7.9, 2.5, 6.25], "energy")
        halo(add, 8, 7, 6.5, z=11.0, depth=0.2)
    return E


def boots_elements(stage):
    E, add = builder()
    sym = mirror_x(add)
    sym([3, 1, 7], [7.25, 7, 10], "metal")                           # tiges
    sym([3, 0.75, 4.25], [7.25, 3, 7], "metal")                      # pieds
    sym([2.85, 0, 4], [7.4, 0.75, 10.15], "dark")                    # semelles
    sym([2.75, 6.5, 6.75], [7.5, 7.75, 10.25], "trim")               # revers
    sym([3.2, 1.0, 4.0], [7.05, 2.8, 4.3], "edge")                   # bouts ferrés
    if stage >= 2:
        sym([2.95, 3.4, 6.9], [7.3, 3.7, 10.1], "energy")             # ligne lumineuse
    if stage >= 10:  # bouts sertis
        sym([4.5, 1.5, 3.85], [5.75, 2.4, 4.1], "gem")
    if stage >= 16:  # ailettes
        sym([0.5, 4, 8], [3, 7.5, 8.5], "metal", ["z", 22.5, [3, 4, 8.25]])
        sym([1.0, 4.4, 7.95], [2.3, 7.1, 8.55], "energy", ["z", 22.5, [3, 4, 8.25]])
    if stage >= 20:
        halo_flat(add, 8, 7, 9, 6.5, depth=0.2, t=0.2)
    return E


ARMOR_ICON_MODELS = {
    "evolving_chestplate": (chestplate_elements, 0.62),
    "evolving_leggings": (leggings_elements, 0.62),
    "evolving_boots": (boots_elements, 0.66),
}


def armor_icon_model(item, stage):
    elements, scale = ARMOR_ICON_MODELS[item]
    return {
        "credit": "Evo Tool - pièce d'armure 3D générée par tools/generate_assets.py",
        "gui_light": "side",
        "textures": stage_textures(stage),
        "elements": finalize(elements(stage), stage),
        "display": armor_display(scale),
    }


# --------------------------------------------------------------------------- armure portée (textures HD)


HD = 4  # les textures d'armure sont peintes en 256x128 (4 fois la résolution vanilla), même disposition


class ArmorPainter:
    """Peint une texture d'armure 64x32 en haute définition, avec un masque des lignes lumineuses."""

    def __init__(self, st, stage, seed):
        self.st, self.stage = st, stage
        self.img = Image.new("RGBA", (64 * HD, 32 * HD), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (64 * HD, 32 * HD), (0, 0, 0, 0))
        self.r = random.Random(seed)

    def rect(self, box, face):
        x0, y0, w, h = box_faces(box)[face]
        return x0 * HD, y0 * HD, w * HD, h * HD

    def set(self, box, face, hx, hy, color, glow=False):
        x0, y0, w, h = self.rect(box, face)
        hx, hy = int(round(hx)), int(round(hy))
        if 0 <= hx < w and 0 <= hy < h:
            self.img.putpixel((x0 + hx, y0 + hy), color)
            if glow:
                self.glow.putpixel((x0 + hx, y0 + hy), color)

    def get(self, box, face, hx, hy):
        x0, y0, w, h = self.rect(box, face)
        return self.img.getpixel((x0 + min(w - 1, max(0, hx)), y0 + min(h - 1, max(0, hy))))

    def block(self, box, face, x, y, color, glow=False):
        """Un pixel « vanilla » (4x4 en HD)."""
        for dy in range(HD):
            for dx in range(HD):
                self.set(box, face, x * HD + dx, y * HD + dy, color, glow)

    def plate(self, box, face, rows, light=1.0):
        """Plaques de métal brossé : dégradé, reflets en haut à gauche, ombres en bas."""
        st = self.st
        x0, y0, w, h = self.rect(box, face)
        for hy in range(h):
            if hy // HD not in rows:
                continue
            for hx in range(w):
                f = light * (1.16 - 0.3 * (hy / max(1, h - 1)))
                f += 0.05 * math.sin(hy * 2.1 + hx * 0.15) + self.r.uniform(-0.035, 0.035)
                if hx == 0 or hy % (3 * HD) == 0:
                    f *= 1.2
                if hx == w - 1:
                    f *= 0.7
                self.img.putpixel((x0 + hx, y0 + hy), shade(st["metal"], f))

    def fill(self, box, face, x, y, w, h, fn):
        """Remplit une zone en pixels vanilla (x, y, w, h) avec fn(hx, hy, W, H) -> couleur."""
        for hy in range(int(h * HD)):
            for hx in range(int(w * HD)):
                c = fn(hx, hy, int(w * HD), int(h * HD))
                if c:
                    self.set(box, face, x * HD + hx, y * HD + hy, c)

    def seam(self, box, face, y, x0=0, x1=None, color=None):
        """Jointure entre deux plaques : trait sombre et reflet juste en dessous."""
        w = self.rect(box, face)[2]
        x1 = w if x1 is None else x1 * HD
        dark = color or shade(self.st["metal"], 0.35)
        for hx in range(x0 * HD, x1):
            self.set(box, face, hx, y * HD + 1, dark)
            self.set(box, face, hx, y * HD + 2, dark)
            self.set(box, face, hx, y * HD + 3, shade(self.st["metal"], 1.45))

    def trim(self, box, face, y, x0=0, x1=None):
        """Liseré doré sur une rangée vanilla."""
        w = self.rect(box, face)[2]
        x1 = w if x1 is None else x1 * HD
        c = trim_color(self.st)
        for hx in range(x0 * HD, x1):
            for k, f in ((0, 1.35), (1, 1.1), (2, 0.95), (3, 0.6)):
                self.set(box, face, hx, y * HD + k, shade(c, f if hx % 6 else f * 1.15))

    def energy(self, box, face, pts, width=1):
        """Ligne lumineuse (polyligne en pixels vanilla, flottants) : coeur éclatant et halo coloré."""
        dark, mid, light, bright, spark = self.st["gem"]
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            n = int(max(abs(bx - ax), abs(by - ay)) * HD * 2) + 1
            for i in range(n + 1):
                t = i / n
                hx = (ax + (bx - ax) * t) * HD
                hy = (ay + (by - ay) * t) * HD
                for dx in range(-width - 1, width + 2):
                    for dy in range(-width - 1, width + 2):
                        d = max(abs(dx), abs(dy))
                        if d < width:
                            self.set(box, face, hx + dx, hy + dy, spark if self.stage >= 16 else bright, True)
                        elif d == width:
                            self.set(box, face, hx + dx, hy + dy, light, True)
                        else:
                            base = self.get(box, face, int(hx + dx), int(hy + dy))
                            if base[3] and base not in (bright, spark, light):
                                self.set(box, face, hx + dx, hy + dy, lerp(base, mid, 0.35))

    def node(self, box, face, x, y, r=1.2):
        """Noeud lumineux (point d'énergie) au bout des lignes."""
        dark, mid, light, bright, spark = self.st["gem"]
        for dy in range(-int(r * HD), int(r * HD) + 1):
            for dx in range(-int(r * HD), int(r * HD) + 1):
                d = math.hypot(dx, dy) / (r * HD)
                if d <= 1:
                    c = spark if d < 0.35 else bright if d < 0.7 else light
                    self.set(box, face, x * HD + dx, y * HD + dy, c, True)

    def gem(self, box, face, x, y, r=1.5):
        """Gemme sertie : losange à facettes avec monture dorée."""
        dark, mid, light, bright, spark = self.st["gem"]
        rim = trim_color(self.st)
        R = r * HD
        for dy in range(-int(R) - 1, int(R) + 2):
            for dx in range(-int(R) - 1, int(R) + 2):
                d = (abs(dx) + abs(dy)) / R
                if d <= 1:
                    c = (bright if dx + dy < 0 else light) if d < 0.55 else (mid if dx < 0 else dark)
                    if d < 0.2 and dx < 0 and dy < 0:
                        c = spark
                    self.set(box, face, x * HD + dx, y * HD + dy, c, d < 0.55)
                elif d <= 1.0 + 1.5 / R:
                    self.set(box, face, x * HD + dx, y * HD + dy, shade(rim, 1.2 if dy < 0 else 0.8))

    def rivet(self, box, face, x, y):
        m = self.st["metal"]
        for (dx, dy, f) in ((0, 0, 1.6), (1, 0, 1.3), (0, 1, 1.3), (1, 1, 0.9), (2, 1, 0.5), (1, 2, 0.5), (2, 2, 0.4)):
            self.set(box, face, x * HD + dx, y * HD + dy, shade(m, f))

    def chain(self, box, face, rows):
        """Cotte de mailles."""
        cloth = self.st["grip"]
        m = shade(self.st["metal"], 0.8)
        x0, y0, w, h = self.rect(box, face)
        for hy in range(h):
            if hy // HD not in rows:
                continue
            for hx in range(w):
                ring = (hx % 3 == 1) != (hy % 3 == 1)
                ofs = (hy // 3) % 2
                c = shade(m, 1.25 if (hx + ofs) % 3 == 0 else 0.95) if ring else shade(cloth, 0.55)
                self.img.putpixel((x0 + hx, y0 + hy), c)

    def cloth(self, box, face, rows, light=1.0):
        c = self.st["grip"]
        x0, y0, w, h = self.rect(box, face)
        for hy in range(h):
            if hy // HD not in rows:
                continue
            for hx in range(w):
                weave = 1.08 if (hx // 2 + hy // 2) % 2 else 0.9
                self.img.putpixel((x0 + hx, y0 + hy), shade(c, light * weave + self.r.uniform(-0.03, 0.03)))

    def filigree(self, box, face, x, y, w, h):
        """Filigrane gravé (volutes)."""
        m = self.st["metal"]
        for hx in range(int(w * HD)):
            t = hx / (w * HD)
            for k in (0, 1):
                hy = (h * HD / 2) + math.sin(t * math.pi * 2 * 2 + k * math.pi) * (h * HD / 2 - 1)
                self.set(box, face, x * HD + hx, y * HD + hy, shade(m, 0.45))
                self.set(box, face, x * HD + hx, y * HD + hy + 1, shade(m, 1.5))


# --------------------------------------------------------------------------- dimension évolutive

EVOLUTIONS = 6   # niveaux d'évolution des blocs (0 à 5), une évolution toutes les 10 minutes passées dans la dimension
EVO_COLORS = [None, hexc("a64dff"), hexc("2fe6e8"), hexc("3cf07a"), hexc("ffd23a"), hexc("ffffff")]
EVO_BLOCKS = ["evolving_stone", "evolving_dirt", "evolving_grass", "evolving_log", "evolving_leaves",
              "evolving_crystal", "evolving_ore"]


def evo_color(e, x=0, y=0):
    """Couleur des veines d'énergie d'une évolution (arc-en-ciel à la dernière)."""
    if e >= 5:
        h = ((x + y) / 20.0) % 1.0
        rr, gg, bb = colorsys.hsv_to_rgb(h, 0.45, 1.0)
        return (int(rr * 255), int(gg * 255), int(bb * 255), 255)
    return EVO_COLORS[e]


def evo_stone(e, seed=7):
    base = hexc("4a3f5c")
    n = value_noise(16, 16, 4, seed)
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            f = 0.85 + (n[y][x] - 0.5) * 0.5 + r.uniform(-0.05, 0.05)
            img.putpixel((x, y), shade(base, f))
    if e >= 1:   # veines d'énergie
        r = random.Random(seed + 1)
        for v in range(1 + e):
            x, y = r.randrange(16), 0
            while y < 16:
                img.putpixel((x % 16, y), evo_color(e, x, y))
                if e >= 2 and r.random() < 0.35:
                    img.putpixel(((x + 1) % 16, y), lerp(evo_color(e, x, y), base, 0.45))
                x += r.choice((-1, 0, 0, 1))
                y += 1
    if e >= 3:   # éclats de cristal
        r = random.Random(seed + 2)
        for _ in range(e * 2):
            x, y = r.randrange(1, 15), r.randrange(1, 15)
            img.putpixel((x, y), shade(evo_color(e, x, y), 1.4))
    if e >= 4:   # filigrane doré
        for x in range(16):
            if x % 4 != 3:
                img.putpixel((x, 0), shade(hexc("ffd23a"), 1.0 - 0.2 * (x % 2)))
                img.putpixel((x, 15), shade(hexc("ffd23a"), 0.8))
    return img


def evo_dirt(e, seed=8):
    base = hexc("4b2f45")
    n = value_noise(16, 16, 3, seed)
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            f = 0.9 + (n[y][x] - 0.5) * 0.45 + r.uniform(-0.08, 0.08)
            img.putpixel((x, y), shade(base, f))
    for _ in range(e * 3):
        x, y = r.randrange(16), r.randrange(16)
        img.putpixel((x, y), evo_color(max(1, e), x, y))
    return img


def evo_grass_top(e, seed=9):
    base = lerp(hexc("6b3fa8"), hexc("2fbfa0"), e / 5.0)
    n = value_noise(16, 16, 3, seed)
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            f = 0.9 + (n[y][x] - 0.5) * 0.5 + r.uniform(-0.1, 0.1)
            img.putpixel((x, y), shade(base, f))
    for _ in range(2 + e * 3):   # petites fleurs / étincelles
        x, y = r.randrange(16), r.randrange(16)
        img.putpixel((x, y), evo_color(max(1, e), x, y) if e else hexc("d9a8ff"))
    return img


def evo_grass_side(e):
    img = evo_dirt(e, 10)
    top = evo_grass_top(e, 11)
    r = random.Random(12)
    for x in range(16):
        depth = 3 + r.randrange(3)
        for y in range(depth):
            img.putpixel((x, y), top.getpixel((x, y)))
    return img


def evo_log_side(e):
    base = hexc("3a2448")
    img = Image.new("RGBA", (16, 16))
    r = random.Random(13)
    for x in range(16):
        stripe = 0.8 + 0.3 * math.sin(x * 1.9)
        for y in range(16):
            f = stripe + r.uniform(-0.06, 0.06)
            img.putpixel((x, y), shade(base, f))
    if e >= 1:   # sillons lumineux dans l'écorce
        for x in (3, 8, 12)[: min(3, e)]:
            for y in range(16):
                if (y + x) % 7 != 0:
                    img.putpixel((x, y), lerp(evo_color(e, x, y), base, 0.25))
    return img


def evo_log_top(e):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c = shade(hexc("6a4a7a"), 0.9 + 0.15 * math.sin(d * 1.8))
            if d > 6.5:
                c = shade(hexc("3a2448"), 1.0)
            elif e >= 1 and int(d) in (2, 5):
                c = lerp(evo_color(e, x, y), c, 0.3)
            img.putpixel((x, y), c)
    return img


def evo_leaves(e):
    base = lerp(hexc("8a3fd0"), hexc("ff7ad8"), e / 5.0)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    r = random.Random(14)
    for y in range(16):
        for x in range(16):
            if r.random() < 0.18:
                continue   # trous (rendu en découpe)
            f = 0.75 + r.uniform(0, 0.5)
            img.putpixel((x, y), shade(base, f))
    for _ in range(e * 3):
        x, y = r.randrange(16), r.randrange(16)
        img.putpixel((x, y), evo_color(max(1, e), x, y))
    return img


def evo_crystal(e):
    c = evo_color(e) if e else hexc("c8a8ff")
    pal = [shade(c, 0.35), shade(c, 0.7), c, shade(c, 1.4), hexc("ffffff")]
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            u, v = (x % 8) - 3.5, (y % 8) - 3.5
            facet = (u > 0) * 1 + (v > 0) * 2
            col = (pal[3], pal[2], pal[1], pal[0])[facet]
            if e >= 5:
                col = lerp(col, evo_color(5, x, y), 0.5)
            img.putpixel((x, y), col)
    for p in ((2, 2), (10, 10), (10, 2), (2, 10)):
        img.putpixel(p, pal[4])
    return img


def evo_ore(e):
    img = evo_stone(e, 15)
    return ore_texture(img, GEMS["evolium"], glow=e >= 2)


def portal_frames():
    """Portail évolutif : tourbillon rouge rubis et violet, animé."""
    frames = 16
    img = Image.new("RGBA", (16, 16 * frames))
    for f in range(frames):
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                a = math.atan2(dy, dx)
                d = math.hypot(dx, dy)
                v = 0.5 + 0.5 * math.sin(a * 3 + d * 0.9 - f * 2 * math.pi / frames)
                c = lerp(hexc("6a0f2a"), hexc("ff3a6a"), v)
                c = lerp(c, hexc("b04dff"), 0.35 + 0.25 * math.sin(d * 0.7 + f * 0.4))
                if v > 0.92:
                    c = lerp(c, hexc("ffffff"), 0.6)
                img.putpixel((x, y + 16 * f), c[:3] + (200,))
    return img


def lighter_sprite():
    """Briquet d'évolium : corps en évolium serti, silex sur le dessus."""
    gem = GEMS["evolium"]
    rows = [
        "................",
        "................",
        "........ff......",
        ".......ffff.....",
        "......ffffs.....",
        ".......ff.......",
        "......oooo......",
        ".....ollllo.....",
        ".....olgglo.....",
        ".....olgmlo.....",
        ".....omggmo.....",
        ".....ommmmo.....",
        ".....odmmdo.....",
        ".....odddd o....",
        "......oooo......",
        "................",
    ]
    colors = {"o": shade(gem[0], 0.7), "l": gem[2], "m": gem[1], "d": gem[0], "g": gem[3],
              "f": hexc("3a3a40"), "s": hexc("ffffff")}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:16]):
            if ch in colors:
                img.putpixel((x, y), colors[ch])
    img.putpixel((9, 3), hexc("ff9a3a"))
    return img


def gen_dimension_assets():
    makers = {
        "evolving_stone": lambda e: {"all": evo_stone(e)},
        "evolving_dirt": lambda e: {"all": evo_dirt(e)},
        "evolving_grass": lambda e: {"top": evo_grass_top(e), "side": evo_grass_side(e), "bottom": evo_dirt(e)},
        "evolving_log": lambda e: {"side": evo_log_side(e), "end": evo_log_top(e)},
        "evolving_leaves": lambda e: {"all": evo_leaves(e)},
        "evolving_crystal": lambda e: {"all": evo_crystal(e)},
        "evolving_ore": lambda e: {"all": evo_ore(e)},
    }
    for block, make in makers.items():
        variants = {}
        for e in range(EVOLUTIONS):
            tex = make(e)
            refs = {}
            for k, img in tex.items():
                save_png(img, "block", "evolving", f"{block}_{k}_{e}")
                refs[k] = f"{MOD}:block/evolving/{block}_{k}_{e}"
            model = f"{MOD}:block/evolving/{block}_{e}"
            if block == "evolving_grass":
                body = {"parent": "minecraft:block/cube_bottom_top", "textures": refs}
            elif block == "evolving_log":
                body = {"parent": "minecraft:block/cube_column", "textures": refs}
            elif block == "evolving_leaves":
                body = {"parent": "minecraft:block/leaves", "textures": refs}
            else:
                body = {"parent": "minecraft:block/cube_all", "textures": refs}
            write_json(os.path.join(ASSETS, "models", "block", "evolving", f"{block}_{e}.json"), body)
            if block == "evolving_log":
                variants[f"axis=y,evolution={e}"] = {"model": model}
                variants[f"axis=z,evolution={e}"] = {"model": model, "x": 90}
                variants[f"axis=x,evolution={e}"] = {"model": model, "x": 90, "y": 90}
            else:
                variants[f"evolution={e}"] = {"model": model}
        write_json(os.path.join(ASSETS, "blockstates", f"{block}.json"), {"variants": variants})
        write_json(os.path.join(ASSETS, "items", f"{block}.json"),
                   {"model": {"type": "minecraft:model", "model": f"{MOD}:block/evolving/{block}_0"}})

    # portail
    save_animated(portal_frames(), 2, "block", "evolution_portal")
    for name, frm, to in (("ns", [0, 0, 6], [16, 16, 10]), ("ew", [6, 0, 0], [10, 16, 16])):
        faces = ({"north": {"uv": [0, 0, 16, 16], "texture": "#portal"}, "south": {"uv": [0, 0, 16, 16], "texture": "#portal"}}
                 if name == "ns" else
                 {"east": {"uv": [0, 0, 16, 16], "texture": "#portal"}, "west": {"uv": [0, 0, 16, 16], "texture": "#portal"}})
        write_json(os.path.join(ASSETS, "models", "block", f"evolution_portal_{name}.json"), {
            "textures": {"particle": f"{MOD}:block/evolution_portal", "portal": f"{MOD}:block/evolution_portal"},
            "elements": [{"from": frm, "to": to, "faces": faces, "light_emission": 15, "shade": False}],
        })
    write_json(os.path.join(ASSETS, "blockstates", "evolution_portal.json"), {"variants": {
        "axis=x": {"model": f"{MOD}:block/evolution_portal_ns"},
        "axis=z": {"model": f"{MOD}:block/evolution_portal_ew"},
    }})

    # briquet
    save_png(lighter_sprite(), "item", "evolium_lighter")
    write_json(os.path.join(ASSETS, "models", "item", "evolium_lighter.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/evolium_lighter"}})
    write_json(os.path.join(ASSETS, "items", "evolium_lighter.json"),
               {"model": {"type": "minecraft:model", "model": f"{MOD}:item/evolium_lighter"}})
    gen_boss_assets()


def evo_state(block, e=0, **props):
    p = {"evolution": str(e)}
    p.update({k: str(v) for k, v in props.items()})
    return {"Name": f"{MOD}:{block}", "Properties": p}


def gen_dimension_data():
    # --- butin des blocs évolutifs
    silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    shears = {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}

    def self_drop(b):
        return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]}

    def at_evolution(e):
        return {"condition": "minecraft:block_state_property", "block": f"{MOD}:evolving_stone",
                "properties": {"evolution": str(e)}}

    tables = {
        "evolving_stone": [self_drop("evolving_stone"),
                           {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:evolium"}],
                            "conditions": [at_evolution(5), {"condition": "minecraft:random_chance", "chance": 0.04},
                                           {"condition": "minecraft:inverted", "term": silk}]}],
        "evolving_dirt": [self_drop("evolving_dirt")],
        "evolving_grass": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{MOD}:evolving_grass", "conditions": [silk]},
            {"type": "minecraft:item", "name": f"{MOD}:evolving_dirt"}]}]}],
        "evolving_log": [self_drop("evolving_log")],
        "evolving_leaves": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:evolving_leaves"}],
                             "conditions": [{"condition": "minecraft:any_of", "terms": [silk, shears]}]}],
        "evolving_crystal": [self_drop("evolving_crystal")],
    }
    # minerai évolutif : 1 évolium par niveau d'évolution en plus (+ Fortune)
    ore_children = [{"type": "minecraft:item", "name": f"{MOD}:evolving_ore", "conditions": [silk]}]
    for e in range(EVOLUTIONS - 1, -1, -1):
        ore_children.append({
            "type": "minecraft:item", "name": f"{MOD}:evolium",
            "conditions": [{"condition": "minecraft:block_state_property", "block": f"{MOD}:evolving_ore",
                            "properties": {"evolution": str(e)}}],
            "functions": [{"function": "minecraft:set_count", "count": 1 + e},
                          {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                           "formula": "minecraft:ore_drops"},
                          {"function": "minecraft:explosion_decay"}],
        })
    tables["evolving_ore"] = [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": ore_children}]}]
    for b, pools in tables.items():
        write_json(os.path.join(DATA, "loot_table", "blocks", f"{b}.json"),
                   {"type": "minecraft:block", "pools": [dict(p, bonus_rolls=0) for p in pools],
                    "random_sequence": f"{MOD}:blocks/{b}"})

    # --- tags
    tags = os.path.join(ROOT, "data")
    ids = lambda names: [f"{MOD}:{n}" for n in names]
    write_json(os.path.join(tags, "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"replace": False, "values": [f"{MOD}:{o}" for o in ORES] + [f"{MOD}:{g}_block" for g in GEMS]
                + [f"{MOD}:evolite_ore", f"{MOD}:evolite_block"] + ids(["evolving_stone", "evolving_crystal", "evolving_ore"])})
    write_json(os.path.join(tags, "minecraft", "tags", "block", "mineable", "shovel.json"),
               {"replace": False, "values": ids(["evolving_dirt", "evolving_grass"])})
    write_json(os.path.join(tags, "minecraft", "tags", "block", "mineable", "axe.json"),
               {"replace": False, "values": ids(["evolving_log"])})
    write_json(os.path.join(tags, "minecraft", "tags", "block", "mineable", "hoe.json"),
               {"replace": False, "values": ids(["evolving_leaves"])})
    write_json(os.path.join(tags, "minecraft", "tags", "block", "dirt.json"),
               {"replace": False, "values": ids(["evolving_dirt", "evolving_grass"])})
    write_json(os.path.join(tags, "minecraft", "tags", "block", "logs.json"),
               {"replace": False, "values": ids(["evolving_log"])})
    write_json(os.path.join(tags, "minecraft", "tags", "block", "overworld_carver_replaceables.json"),
               {"replace": False, "values": ids(["evolving_stone", "evolving_dirt", "evolving_grass", "evolving_ore"])})
    write_json(os.path.join(tags, "minecraft", "tags", "block", "base_stone_overworld.json"),
               {"replace": False, "values": ids(["evolving_stone"])})

    # --- briquet d'évolium
    write_json(os.path.join(DATA, "recipe", "evolium_lighter.json"), {
        "type": "minecraft:crafting_shapeless", "category": "equipment",
        "ingredients": [f"{MOD}:evolium", "minecraft:flint"],
        "result": {"id": f"{MOD}:evolium_lighter", "count": 1},
    })

    # --- type de dimension : ciel violet, cycle jour / nuit normal
    sky = {
        "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 6000,
                                                          "sound": "minecraft:music.overworld.cherry_grove"}},
        "minecraft:gameplay/bed_rule": {"can_set_spawn": "always", "can_sleep": "when_dark",
                                        "error_message": {"translate": "block.minecraft.bed.no_sleep"}},
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:visual/cloud_color": "#ccf0c8ff",
        "minecraft:visual/cloud_height": 210.33,
        "minecraft:visual/fog_color": "#b48cff",
        "minecraft:visual/sky_color": "#7a4ad8",
    }
    write_json(os.path.join(DATA, "dimension_type", "evolutive.json"), {
        "ambient_light": 0.08,
        "attributes": sky,
        "coordinate_scale": 1.0,
        "has_ceiling": False,
        "has_skylight": True,
        "height": 384,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 384,
        "min_y": -64,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0},
        "timelines": "#minecraft:in_overworld",
    })

    # --- relief : grandes collines arrondies avec surplombs (bruit 3D), grottes vanilla
    wg = os.path.join(DATA, "worldgen")
    write_json(os.path.join(wg, "noise", "evolutive_terrain.json"),
               {"firstOctave": -7, "amplitudes": [1.0, 1.0, 0.5, 0.5, 0.25]})
    write_json(os.path.join(wg, "noise", "evolutive_detail.json"),
               {"firstOctave": -4, "amplitudes": [1.0, 0.5]})
    final_density = {
        "type": "minecraft:interpolated",
        "argument": {
            "type": "minecraft:add",
            "argument1": {"type": "minecraft:y_clamped_gradient", "from_y": -64, "to_y": 220,
                          "from_value": 2.2, "to_value": -2.2},
            "argument2": {"type": "minecraft:add",
                          "argument1": {"type": "minecraft:mul", "argument1": 1.1,
                                        "argument2": {"type": "minecraft:noise", "noise": f"{MOD}:evolutive_terrain",
                                                      "xz_scale": 1.0, "y_scale": 0.45}},
                          "argument2": {"type": "minecraft:mul", "argument1": 0.12,
                                        "argument2": {"type": "minecraft:noise", "noise": f"{MOD}:evolutive_detail",
                                                      "xz_scale": 1.0, "y_scale": 1.0}}},
        },
    }
    shifted = lambda noise: {"type": "minecraft:shifted_noise", "noise": noise, "shift_x": "minecraft:shift_x",
                             "shift_y": 0.0, "shift_z": "minecraft:shift_z", "xz_scale": 0.25, "y_scale": 0.0}
    floor = lambda depth: {"type": "minecraft:stone_depth", "offset": depth, "surface_type": "floor",
                           "add_surface_depth": depth > 0, "secondary_depth_range": 0}
    write_json(os.path.join(wg, "noise_settings", "evolutive.json"), {
        "aquifers_enabled": False,
        "default_block": evo_state("evolving_stone"),
        "default_fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
        "disable_mob_generation": False,
        "legacy_random_source": False,
        "noise": {"height": 384, "min_y": -64, "size_horizontal": 1, "size_vertical": 2},
        "noise_router": {
            "barrier": 0.0, "continents": 0.0, "depth": 0.0, "erosion": 0.0,
            "final_density": final_density,
            "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0, "lava": 0.0,
            "preliminary_surface_level": 0.0, "ridges": 0.0,
            "temperature": shifted("minecraft:temperature"),
            "vegetation": shifted("minecraft:vegetation"),
            "vein_gap": 0.0, "vein_ridged": 0.0, "vein_toggle": 0.0,
        },
        "ore_veins_enabled": False,
        "sea_level": 40,
        "spawn_target": [],
        "surface_rule": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition",
             "if_true": {"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
                         "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
             "then_run": {"type": "minecraft:block", "result_state": {"Name": "minecraft:bedrock"}}},
            {"type": "minecraft:condition", "if_true": floor(0),
             "then_run": {"type": "minecraft:block", "result_state": evo_state("evolving_grass")}},
            {"type": "minecraft:condition", "if_true": floor(0) | {"add_surface_depth": True},
             "then_run": {"type": "minecraft:block", "result_state": evo_state("evolving_dirt")}},
        ]},
    })

    # --- éléments du décor
    cf, pf = os.path.join(wg, "configured_feature"), os.path.join(wg, "placed_feature")
    simple = lambda block, e=0, **p: {"type": "minecraft:simple_state_provider", "state": evo_state(block, e, **p)}
    write_json(os.path.join(cf, "evolving_tree.json"), {"type": "minecraft:tree", "config": {
        "decorators": [],
        "dirt_provider": simple("evolving_dirt"),
        "foliage_placer": {"type": "minecraft:cherry_foliage_placer", "corner_hole_chance": 0.25,
                           "hanging_leaves_chance": 0.2, "hanging_leaves_extension_chance": 0.33,
                           "height": 5, "offset": 0, "radius": 4, "wide_bottom_layer_hole_chance": 0.25},
        "foliage_provider": simple("evolving_leaves"),
        "force_dirt": False, "ignore_vines": True,
        "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 2},
        "trunk_placer": {"type": "minecraft:cherry_trunk_placer", "base_height": 8,
                         "branch_count": {"type": "minecraft:weighted_list", "distribution": [
                             {"data": 1, "weight": 1}, {"data": 2, "weight": 1}, {"data": 3, "weight": 1}]},
                         "branch_end_offset_from_top": {"type": "minecraft:uniform", "max_inclusive": 0, "min_inclusive": -1},
                         "branch_horizontal_length": {"type": "minecraft:uniform", "max_inclusive": 4, "min_inclusive": 2},
                         "branch_start_offset_from_top": {"max_inclusive": -3, "min_inclusive": -4},
                         "height_rand_a": 2, "height_rand_b": 1},
        "trunk_provider": simple("evolving_log", axis="y"),
    }})
    write_json(os.path.join(cf, "evolving_crystal_spike.json"), {"type": "minecraft:block_column", "config": {
        "allowed_placement": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
        "direction": "up", "prioritize_tip": False,
        "layers": [{"height": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 4},
                    "provider": simple("evolving_crystal")}],
    }})
    write_json(os.path.join(cf, "evolving_geode.json"), {"type": "minecraft:geode", "config": {
        "blocks": {
            "alternate_inner_layer_provider": simple("evolving_ore"),
            "cannot_replace": "#minecraft:features_cannot_replace",
            "filling_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:air"}},
            "inner_layer_provider": simple("evolving_crystal"),
            "inner_placements": [evo_state("evolving_crystal")],
            "invalid_blocks": "#minecraft:geode_invalid_blocks",
            "middle_layer_provider": simple("evolving_dirt"),
            "outer_layer_provider": simple("evolving_stone"),
        },
        "crack": {"base_crack_size": 2.0, "crack_point_offset": 2, "generate_crack_chance": 0.9},
        "distribution_points": {"type": "minecraft:uniform", "max_inclusive": 4, "min_inclusive": 3},
        "invalid_blocks_threshold": 1,
        "layers": {"filling": 1.7, "inner_layer": 2.2, "middle_layer": 3.2, "outer_layer": 4.2},
        "max_gen_offset": 16, "min_gen_offset": -16, "noise_multiplier": 0.05,
        "outer_wall_distance": {"type": "minecraft:uniform", "max_inclusive": 6, "min_inclusive": 4},
        "placements_require_layer0_alternate": True,
        "point_offset": {"type": "minecraft:uniform", "max_inclusive": 2, "min_inclusive": 1},
        "use_alternate_layer0_chance": 0.15, "use_potential_placements_chance": 0.35,
    }})
    write_json(os.path.join(cf, "ore_evolving.json"), {"type": "minecraft:ore", "config": {
        "discard_chance_on_air_exposure": 0.0, "size": 6,
        "targets": [{"target": {"predicate_type": "minecraft:block_match", "block": f"{MOD}:evolving_stone"},
                     "state": evo_state("evolving_ore")}],
    }})
    sapling_ok = {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive",
                  "state": {"Name": "minecraft:cherry_sapling", "Properties": {"stage": "0"}}}}
    for name, count in (("evolving_trees_sparse", 1), ("evolving_trees_dense", 6)):
        write_json(os.path.join(pf, f"{name}.json"), {"feature": f"{MOD}:evolving_tree", "placement": [
            {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
            {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}, {"type": "minecraft:biome"}, sapling_ok]})
    for name, count in (("evolving_crystal_spikes", 3), ("evolving_crystal_fields", 14)):
        write_json(os.path.join(pf, f"{name}.json"), {"feature": f"{MOD}:evolving_crystal_spike", "placement": [
            {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}]})
    write_json(os.path.join(pf, "evolving_geode.json"), {"feature": f"{MOD}:evolving_geode", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 10}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                      "min_inclusive": {"absolute": -40}, "max_inclusive": {"absolute": 40}}},
        {"type": "minecraft:biome"}]})
    write_json(os.path.join(pf, "ore_evolving.json"), {"feature": f"{MOD}:ore_evolving", "placement": [
        {"type": "minecraft:count", "count": 10}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                      "min_inclusive": {"absolute": -60}, "max_inclusive": {"absolute": 120}}},
        {"type": "minecraft:biome"}]})

    # --- biomes
    def biome(trees, crystals, particle, prob, sky_color, fog_color):
        features = [[] for _ in range(11)]
        features[2] = [f"{MOD}:evolving_geode"]
        features[6] = [f"{MOD}:ore_evolving"]
        features[9] = [f"{MOD}:{trees}", f"{MOD}:{crystals}"]
        return {
            "attributes": {
                "minecraft:visual/sky_color": sky_color,
                "minecraft:visual/fog_color": fog_color,
                "minecraft:visual/water_fog_color": "#4a2a8f",
                "minecraft:visual/ambient_particles": [{"particle": {"type": particle}, "probability": prob}],
            },
            "carvers": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"],
            "downfall": 0.4,
            "effects": {"water_color": "#8a5aff", "grass_color": "#9a6aff", "foliage_color": "#c07aff"},
            "features": features,
            "has_precipitation": False,
            "spawn_costs": {},
            "spawners": {
                "ambient": [], "axolotls": [], "misc": [], "underground_water_creature": [],
                "water_ambient": [], "water_creature": [],
                "creature": [{"type": "minecraft:rabbit", "maxCount": 3, "minCount": 2, "weight": 6},
                             {"type": "minecraft:sheep", "maxCount": 4, "minCount": 2, "weight": 8}],
                "monster": [{"type": "minecraft:zombie", "maxCount": 4, "minCount": 2, "weight": 60},
                            {"type": "minecraft:skeleton", "maxCount": 4, "minCount": 2, "weight": 60},
                            {"type": "minecraft:spider", "maxCount": 3, "minCount": 1, "weight": 40},
                            {"type": "minecraft:enderman", "maxCount": 2, "minCount": 1, "weight": 15}],
            },
            "temperature": 0.7,
        }
    biomes = {
        "evolutive_plains": biome("evolving_trees_sparse", "evolving_crystal_spikes", "minecraft:end_rod", 0.0015,
                                  "#7a4ad8", "#b48cff"),
        "crystal_forest": biome("evolving_trees_dense", "evolving_crystal_spikes", "minecraft:glow", 0.004,
                                "#5a3ab8", "#9a7ae8"),
        "prism_fields": biome("evolving_trees_sparse", "evolving_crystal_fields", "minecraft:electric_spark", 0.003,
                              "#8a5af0", "#d0a0ff"),
    }
    for name, body in biomes.items():
        write_json(os.path.join(wg, "biome", f"{name}.json"), body)

    def params(t, h):
        return {"temperature": t, "humidity": h, "continentalness": 0.0, "erosion": 0.0,
                "weirdness": 0.0, "depth": 0.0, "offset": 0.0}
    write_json(os.path.join(DATA, "dimension", "evolutive.json"), {
        "type": f"{MOD}:evolutive",
        "generator": {
            "type": "minecraft:noise",
            "settings": f"{MOD}:evolutive",
            "biome_source": {"type": "minecraft:multi_noise", "biomes": [
                {"biome": f"{MOD}:evolutive_plains", "parameters": params([-1.0, 0.0], [-1.0, 0.1])},
                {"biome": f"{MOD}:crystal_forest", "parameters": params([-1.0, 0.0], [0.1, 1.0])},
                {"biome": f"{MOD}:prism_fields", "parameters": params([0.0, 1.0], [-1.0, 1.0])},
            ]},
        },
    })


# --------------------------------------------------------------------------- boss : le Slime Cloche

BOSS_PARTS = ["slime_body", "slime_bell", "war_bell", "colossus_bell", "colossus_arm", "colossus_leg",
              "titan_bell", "titan_crown", "divine_bell", "divine_halo", "divine_wings",
              "titan_arm", "titan_leg", "divine_arm", "divine_leg"]


def gel_texture(base, alpha, seed):
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            f = 1.0 + 0.18 * math.sin((x + y) * 0.7) + r.uniform(-0.05, 0.05)
            c = shade(base, f)
            img.putpixel((x, y), c[:3] + (alpha,))
    for p in ((2, 2), (3, 2), (2, 3), (11, 4)):
        img.putpixel(p, (255, 255, 255, min(255, alpha + 60)))
    return img


def bell_texture(base, seed):
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            f = 1.15 - 0.35 * (x / 15.0) + 0.06 * math.sin(y * 1.3) + r.uniform(-0.03, 0.03)
            if y % 5 == 0:
                f *= 0.75
            img.putpixel((x, y), shade(base, f))
    return img


def muscle_texture(base, seed):
    """Peau musclée : reliefs (clair / sombre) en bandes arrondies."""
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            bulge = math.sin(x / 15.0 * math.pi) * (0.8 + 0.2 * math.sin(y * 0.8))
            f = 0.75 + 0.45 * bulge + r.uniform(-0.03, 0.03)
            if y % 6 == 5:
                f *= 0.7
            img.putpixel((x, y), shade(base, f))
    return img


def eye_texture():
    img = Image.new("RGBA", (16, 16), (20, 8, 24, 255))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 4:
                img.putpixel((x, y), (255, 240, 120, 255) if d < 2 else (255, 120, 40, 255))
    return img


def boss_elements(part):
    E, add = builder()
    sym = sym8(add)
    face_z = None
    if part == "slime_body":
        add([1, 0, 1], [15, 14, 15], "gel")                       # gelée
        add([5, 3, 5], [11, 9, 11], "core")                       # coeur d'évolium
        sym([3.5, 8, 15], [6, 10.5, 15.4], "eye")                 # yeux
        add([6, 4.5, 15], [10, 5.5, 15.3], "mouth")
        add([2, 13.5, 2], [14, 14.5, 14], "gel")
    elif part in ("slime_bell", "war_bell", "colossus_bell", "titan_bell", "divine_bell"):
        skin = {"slime_bell": "gel", "war_bell": "bronze", "colossus_bell": "gel", "titan_bell": "bronze",
                "divine_bell": "gold"}[part]
        add([6, 15, 7], [10, 17.5, 9], "trim")                    # anse
        add([7, 17.5, 7], [9, 18.5, 9], "trim")
        add([5, 13, 5], [11, 15, 11], skin)                       # dôme
        add([4, 9, 4], [12, 13, 12], skin)                        # épaules de la cloche
        add([3, 3, 3], [13, 9, 13], skin)                         # robe
        add([2, 0, 2], [14, 3, 14], skin)                         # lèvre
        add([1.8, 2.6, 1.8], [14.2, 3.2, 14.2], "trim")           # bord doré
        add([6.5, -1.5, 6.5], [9.5, 1.5, 9.5], "core")            # battant lumineux
        sym([4.5, 9.5, 12], [6.75, 11.5, 12.4], "eye")            # yeux
        add([6, 5.5, 13], [10, 6.5, 13.3], "mouth")
        add([3.8, 8.6, 3.8], [12.2, 9.0, 12.2], "energy")         # ceinture d'énergie
        if part != "slime_bell":
            for (x, z) in ((3, 8), (13, 8), (8, 3)):              # pointes
                add([x - 0.6, 4, z - 0.6], [x + 0.6, 7.5, z + 0.6], "trim", ["y", 45, [x, 5.75, z]])
            add([3.9, 11, 3.9], [12.1, 11.4, 12.1], "energy")     # runes
            sym([3.2, 10.8, 11.8], [6.9, 11.3, 12.5], "trim")     # sourcils en colère
        if part == "titan_bell":
            add([2.8, 5.5, 2.8], [13.2, 6.0, 13.2], "energy")
        if part == "divine_bell":
            add([2.8, 5.5, 2.8], [13.2, 6.0, 13.2], "energy")
            add([3.9, 13.6, 3.9], [12.1, 14.0, 12.1], "energy")
    elif part.endswith("_arm"):
        skin = {"colossus_arm": "muscle", "titan_arm": "titan", "divine_arm": "divine"}[part]
        add([4, 12, 4], [12, 16, 12], skin)                       # épaule
        add([3.5, 6.5, 3.5], [12.5, 12, 12.5], skin)              # biceps énorme
        add([5, 2.5, 5], [11, 6.5, 11], skin)                     # avant-bras
        add([4, -2, 4], [12, 2.5, 12], skin)                      # poing
        add([3.8, 1.8, 3.8], [12.2, 2.6, 12.2], "trim")           # bracelet
        add([4.8, 2.6, 4.8], [11.2, 3.0, 11.2], "energy")
        add([4, 15, 4], [12, 16.5, 12], "trim")                   # épaulière
    elif part.endswith("_leg"):
        skin = {"colossus_leg": "muscle", "titan_leg": "titan", "divine_leg": "divine"}[part]
        add([4.5, 9, 4.5], [11.5, 16, 11.5], skin)                # cuisse
        add([5, 4, 5], [11, 9, 11], skin)                         # mollet
        add([4.8, 7.5, 3.8], [11.2, 10, 5], "trim")               # genouillère
        add([4, 0, 2], [12, 4, 12], skin)                         # pied
        add([3.8, 0, 1.8], [12.2, 1, 12.2], "trim")               # semelle
        add([4.6, 3.8, 4.6], [11.4, 4.2, 11.4], "energy")
    elif part == "titan_crown":
        add([3, 0, 3], [13, 3, 13], "trim")
        for (x, z) in ((3, 3), (12, 3), (3, 12), (12, 12), (7.5, 3), (7.5, 12), (3, 7.5), (12, 7.5)):
            add([x, 3, z], [x + 1, 7, z + 1], "trim")
            add([x + 0.2, 7, z + 0.2], [x + 0.8, 8.2, z + 0.8], "core")
        add([2.8, 1.2, 2.8], [13.2, 1.8, 13.2], "energy")
    elif part == "divine_halo":
        halo_flat(add, 8, 8, 8, 7.0, tex="core", t=0.45, depth=0.45)
        halo_flat(add, 8, 8, 8, 6.2, tex="energy", t=0.2, depth=0.3)
    elif part == "divine_wings":
        for side in (1, -1):
            for i in range(5):
                x0 = 8 + side * (2 + i * 2.5)
                lo, hi = sorted((x0, x0 + side * 2.4))
                add([lo, 4 + i * 1.2, 7.6], [hi, 14 - i * 0.3, 8.4], "gold" if i % 2 else "trim")
                add([lo + 0.6, 5 + i * 1.2, 7.5], [hi - 0.6, 13 - i * 0.3, 8.5], "energy")
    return E


def gen_boss_assets():

    save_png(gel_texture(hexc("8a4adf"), 200, 1), "item", "boss", "gel")
    save_png(bell_texture(hexc("b87333"), 2), "item", "boss", "bronze")
    save_png(bell_texture(hexc("ffd23a"), 3), "item", "boss", "gold")
    save_png(muscle_texture(hexc("9a5ae8"), 4), "item", "boss", "muscle")
    save_png(muscle_texture(hexc("6a4a3a"), 5), "item", "boss", "titan")
    save_png(muscle_texture(hexc("ffe08a"), 6), "item", "boss", "divine")
    save_png(eye_texture(), "item", "boss", "eye")
    save_png(gel_texture(hexc("1a0a24"), 255, 7), "item", "boss", "mouth")
    tex = {"particle": f"{MOD}:item/boss/gel"}
    for name in ("gel", "bronze", "gold", "muscle", "titan", "divine", "eye", "mouth"):
        tex[name] = f"{MOD}:item/boss/{name}"
    tex["core"] = f"{MOD}:item/pickaxe/stage10_gem"
    tex["energy"] = f"{MOD}:item/pickaxe/stage10_energy"
    tex["trim"] = f"{MOD}:item/pickaxe/stage19_trim"
    for part in BOSS_PARTS:
        elements = boss_elements(part)
        for el in elements:
            t = next(iter(el["faces"].values()))["texture"]
            if t in ("#core", "#eye"):
                el["light_emission"] = 15
            elif t == "#energy":
                el["light_emission"] = 15
                el["shade"] = False
        write_json(os.path.join(ASSETS, "models", "item", "boss", f"{part}.json"),
                   {"textures": tex, "elements": elements})


    def ref(i):
        return {"type": "minecraft:model", "model": f"{MOD}:item/boss/{BOSS_PARTS[i]}"}
    write_json(os.path.join(ASSETS, "items", "bell_boss_part.json"), {"model": {
        "type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
        "fallback": ref(0), "entries": [{"threshold": i, "model": ref(i)} for i in range(len(BOSS_PARTS))]}})


# --------------------------------------------------------------------------- génération

def gen_textures():
    stone = stone_base(11)
    deep = deepslate_base(22)
    save_png(ore_texture(deep, GEMS["evolium"], glow=True), "block", "deepslate_evolium_ore")
    save_png(ore_texture(stone, GEMS["ruby"]), "block", "ruby_ore")
    save_png(ore_texture(deep, GEMS["ruby"]), "block", "deepslate_ruby_ore")
    save_png(ore_texture(stone_base(33), GEMS["sapphire"]), "block", "sapphire_ore")
    save_png(ore_texture(deepslate_base(44), GEMS["sapphire"]), "block", "deepslate_sapphire_ore")
    save_png(ore_texture(end_stone_base(55), EVOLITE, glow=True), "block", "evolite_ore")
    save_png(storage_block(EVOLITE), "block", "evolite_block")
    save_png(ingot_sprite(EVOLITE), "item", "evolite_ingot")
    for g in GEMS:
        save_png(storage_block(GEMS[g]), "block", f"{g}_block")
        save_png(gem_sprite(GEMS[g]), "item", g)
    for i, st in enumerate(STAGES):
        save_png(wood_texture(st["wood"], i), "item", "pickaxe", f"stage{i}_handle")
        save_png(grip_texture(st["grip"], i), "item", "pickaxe", f"stage{i}_grip")
        save_png(metal_texture(st, i), "item", "pickaxe", f"stage{i}_metal")
        save_png(gem_texture(st, i), "item", "pickaxe", f"stage{i}_gem")
        save_animated(energy_frames(st), 2, "item", "pickaxe", f"stage{i}_energy")
        save_png(edge_texture(st, i), "item", "pickaxe", f"stage{i}_edge")
        save_png(dark_texture(st, i), "item", "pickaxe", f"stage{i}_dark")
        save_png(trim_texture(st, i), "item", "pickaxe", f"stage{i}_trim")
    mod_icon().save(os.path.join(ASSETS, "icon.png"))


BLOCKS = ["deepslate_evolium_ore", "evolium_block", "ruby_ore", "deepslate_ruby_ore", "ruby_block",
          "sapphire_ore", "deepslate_sapphire_ore", "sapphire_block", "evolite_ore", "evolite_block"]
ORES = {
    "deepslate_evolium_ore": "evolium",
    "ruby_ore": "ruby", "deepslate_ruby_ore": "ruby",
    "sapphire_ore": "sapphire", "deepslate_sapphire_ore": "sapphire",
}


def stage_dispatch(item):
    """L'apparence change selon la forme (custom_model_data floats[0], mis à jour par le mod)."""
    def ref(i):
        return {"type": "minecraft:model", "model": f"{MOD}:item/{item}_stage{i}"}

    return {
        "model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "fallback": ref(0),
            "entries": [{"threshold": i, "model": ref(i)} for i in range(len(STAGES))],
        }
    }


def gen_models():
    for b in BLOCKS:
        write_json(os.path.join(ASSETS, "blockstates", f"{b}.json"),
                   {"variants": {"": {"model": f"{MOD}:block/{b}"}}})
        write_json(os.path.join(ASSETS, "models", "block", f"{b}.json"),
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{b}"}})
        write_json(os.path.join(ASSETS, "items", f"{b}.json"),
                   {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{b}"}})
    for flat in ["evolite_ingot"]:
        write_json(os.path.join(ASSETS, "models", "item", f"{flat}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{flat}"}})
        write_json(os.path.join(ASSETS, "items", f"{flat}.json"),
                   {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{flat}"}})
    for g in GEMS:
        write_json(os.path.join(ASSETS, "models", "item", f"{g}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{g}"}})
        write_json(os.path.join(ASSETS, "items", f"{g}.json"),
                   {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{g}"}})
    for i in range(len(STAGES)):
        write_json(os.path.join(ASSETS, "models", "item", f"evolving_pickaxe_stage{i}.json"), pickaxe_model(i))

    for i in range(len(STAGES)):
        write_json(os.path.join(ASSETS, "models", "item", f"evolium_mace_stage{i}.json"), mace_model(i))
    write_json(os.path.join(ASSETS, "items", "evolium_mace.json"), stage_dispatch("evolium_mace"))
    for item in TOOL_MODELS:
        for i in range(len(STAGES)):
            write_json(os.path.join(ASSETS, "models", "item", f"{item}_stage{i}.json"), tool_model(item, i))
        definition = stage_dispatch(item)
        if item == "evolving_spear":
            definition["swap_animation_scale"] = 1.95                # comme les lances vanilla
        write_json(os.path.join(ASSETS, "items", f"{item}.json"), definition)

    write_json(os.path.join(ASSETS, "items", "evolving_pickaxe.json"), stage_dispatch("evolving_pickaxe"))


def gen_data():
    # --- tables de butin
    for b in BLOCKS:
        if b in ORES:
            gem = ORES[b]
            pool = {
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{
                    "type": "minecraft:alternatives",
                    "children": [
                        {
                            "type": "minecraft:item",
                            "name": f"{MOD}:{b}",
                            "conditions": [{
                                "condition": "minecraft:match_tool",
                                "predicate": {"predicates": {"minecraft:enchantments": [
                                    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}},
                            }],
                        },
                        {
                            "type": "minecraft:item",
                            "name": f"{MOD}:{gem}",
                            "functions": [
                                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                                 "formula": "minecraft:ore_drops"},
                                {"function": "minecraft:explosion_decay"},
                            ],
                        },
                    ],
                }],
            }
        else:
            pool = {"rolls": 1, "bonus_rolls": 0,
                    "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b}"}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}]}
        write_json(os.path.join(DATA, "loot_table", "blocks", f"{b}.json"),
                   {"type": "minecraft:block", "pools": [pool], "random_sequence": f"{MOD}:blocks/{b}"})

    # --- recettes
    rec = os.path.join(DATA, "recipe")
    write_json(os.path.join(rec, "evolving_pickaxe.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": ["EEE", " | ", " | "],
        "key": {"E": f"{MOD}:evolium", "|": "minecraft:stick"},
        "result": {"id": f"{MOD}:evolving_pickaxe", "count": 1},
    })
    for item, pattern in (("evolving_sword", ["E", "E", "|"]),
                          ("evolving_axe", ["EE", "E|", " |"]),
                          ("evolving_shovel", ["E", "|", "|"]),
                          ("evolving_hoe", ["EE", " |", " |"]),
                          ("evolving_spear", ["  E", " | ", "|  "])):
        write_json(os.path.join(rec, f"{item}.json"), {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": pattern,
            "key": {"E": f"{MOD}:evolium", "|": "minecraft:stick"},
            "result": {"id": f"{MOD}:{item}", "count": 1},
        })
    for item, pattern in (("evolving_helmet", ["EEE", "E E"]),
                          ("evolving_chestplate", ["E E", "EEE", "EEE"]),
                          ("evolving_leggings", ["EEE", "E E", "E E"]),
                          ("evolving_boots", ["E E", "E E"])):
        write_json(os.path.join(rec, f"{item}.json"), {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": pattern,
            "key": {"E": f"{MOD}:evolium"},
            "result": {"id": f"{MOD}:{item}", "count": 1},
        })
    write_json(os.path.join(rec, "evolving_bow.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": [" EX", "E X", " EX"],
        "key": {"E": f"{MOD}:evolium", "X": "minecraft:string"},
        "result": {"id": f"{MOD}:evolving_bow", "count": 1},
    })
    write_json(os.path.join(rec, "evolium_mace.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "pattern": ["B", "|"],
        "key": {"B": f"{MOD}:evolium_block", "|": "minecraft:stick"},
        "result": {"id": f"{MOD}:evolium_mace", "count": 1},
    })
    for g in GEMS:
        write_json(os.path.join(rec, f"{g}_block.json"), {
            "type": "minecraft:crafting_shaped",
            "category": "building",
            "pattern": ["###", "###", "###"],
            "key": {"#": f"{MOD}:{g}"},
            "result": {"id": f"{MOD}:{g}_block", "count": 1},
        })
        write_json(os.path.join(rec, f"{g}_from_block.json"), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [f"{MOD}:{g}_block"],
            "result": {"id": f"{MOD}:{g}", "count": 9},
        })
    write_json(os.path.join(rec, "evolite_block.json"), {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["###", "###", "###"], "key": {"#": f"{MOD}:evolite_ingot"},
        "result": {"id": f"{MOD}:evolite_block", "count": 1},
    })
    write_json(os.path.join(rec, "evolite_ingot_from_block.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [f"{MOD}:evolite_block"], "result": {"id": f"{MOD}:evolite_ingot", "count": 9},
    })
    for kind, time in (("smelting", 200), ("blasting", 100)):
        write_json(os.path.join(rec, f"evolite_ingot_from_{kind}.json"), {
            "type": f"minecraft:{kind}", "category": "misc", "ingredient": f"{MOD}:evolite_ore",
            "result": {"id": f"{MOD}:evolite_ingot"}, "experience": 2.0, "cookingtime": time,
        })
    for ore, gem in ORES.items():
        for kind, time in (("smelting", 200), ("blasting", 100)):
            write_json(os.path.join(rec, f"{gem}_from_{kind}_{ore}.json"), {
                "type": f"minecraft:{kind}",
                "category": "misc",
                "group": gem,
                "ingredient": f"{MOD}:{ore}",
                "result": {"id": f"{MOD}:{gem}"},
                "experience": 1.0 if gem != "evolium" else 2.0,
                "cookingtime": time,
            })

    # --- tags
    ore_ids = [f"{MOD}:{o}" for o in ORES]
    gem_blocks = [f"{MOD}:{g}_block" for g in GEMS]
    tags = os.path.join(ROOT, "data")
    evolite = [f"{MOD}:evolite_ore", f"{MOD}:evolite_block"]
    write_json(os.path.join(tags, "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"replace": False, "values": ore_ids + gem_blocks + evolite})
    # Évolium : pioche en diamant obligatoire. Rubis / saphir : pioche en fer.
    write_json(os.path.join(tags, "minecraft", "tags", "block", "needs_diamond_tool.json"),
               {"replace": False, "values": [f"{MOD}:deepslate_evolium_ore", f"{MOD}:evolium_block"] + evolite})
    write_json(os.path.join(tags, "minecraft", "tags", "block", "needs_iron_tool.json"),
               {"replace": False, "values": [o for o in ore_ids if "evolium" not in o]
                + [f"{MOD}:ruby_block", f"{MOD}:sapphire_block"]})
    write_json(os.path.join(tags, "minecraft", "tags", "block", "beacon_base_blocks.json"),
               {"replace": False, "values": gem_blocks})
    write_json(os.path.join(tags, "c", "tags", "block", "ores.json"),
               {"replace": False, "values": ore_ids + [f"{MOD}:evolite_ore"]})
    write_json(os.path.join(tags, "c", "tags", "item", "ingots.json"),
               {"replace": False, "values": [f"{MOD}:evolite_ingot"]})
    write_json(os.path.join(tags, "c", "tags", "item", "gems.json"),
               {"replace": False, "values": [f"{MOD}:{g}" for g in GEMS]})
    write_json(os.path.join(DATA, "tags", "block", "gem_ores.json"),
               {"replace": False, "values": [o for o in ore_ids if "evolium" not in o]})
    write_json(os.path.join(DATA, "tags", "item", "evolium_tool_materials.json"),
               {"replace": False, "values": [f"{MOD}:evolium"]})
    # l'arc accepte les enchantements d'arc (Puissance, Frappe, Flamme, Infinité…)
    write_json(os.path.join(tags, "minecraft", "tags", "item", "enchantable", "bow.json"),
               {"replace": False, "values": [f"{MOD}:evolving_bow"]})
    # la mace accepte Densité, Brèche et Charge de vent comme la mace vanilla
    write_json(os.path.join(tags, "minecraft", "tags", "item", "enchantable", "mace.json"),
               {"replace": False, "values": [f"{MOD}:evolium_mace"]})
    for tag, item in (("pickaxes", "evolving_pickaxe"), ("swords", "evolving_sword"), ("axes", "evolving_axe"),
                      ("shovels", "evolving_shovel"), ("hoes", "evolving_hoe"), ("spears", "evolving_spear"),
                      ("head_armor", "evolving_helmet"), ("chest_armor", "evolving_chestplate"),
                      ("leg_armor", "evolving_leggings"), ("foot_armor", "evolving_boots")):
        write_json(os.path.join(tags, "minecraft", "tags", "item", f"{tag}.json"),
                   {"replace": False, "values": [f"{MOD}:{item}"]})

    # --- Élan évolutif : Élan vanilla sans faim (apply_exhaustion) ni usure (change_item_damage)
    on_foot = {
        "condition": "minecraft:all_of",
        "terms": [
            {"condition": "minecraft:inverted",
             "term": {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"vehicle": {}}}},
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"flags": {"is_fall_flying": False}}},
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"flags": {"is_in_water": False}}},
        ],
    }
    write_json(os.path.join(DATA, "enchantment", "evolving_lunge.json"), {
        "anvil_cost": 2,
        "description": {"translate": "enchantment.evotool.evolving_lunge"},
        "effects": {"minecraft:post_piercing_attack": [{
            "effect": {"type": "minecraft:all_of", "effects": [
                {"type": "minecraft:apply_impulse", "coordinate_scale": [1.0, 0.0, 1.0], "direction": [0.0, 0.0, 1.0],
                 "magnitude": {"type": "minecraft:linear", "base": 0.458, "per_level_above_first": 0.458}},
                {"type": "minecraft:play_sound", "pitch": 1.0, "volume": 1.0,
                 "sound": ["minecraft:item.spear.lunge_1", "minecraft:item.spear.lunge_2", "minecraft:item.spear.lunge_3"]},
            ]},
            "requirements": on_foot,
        }]},
        "exclusive_set": ["minecraft:lunge"],
        "max_cost": {"base": 25, "per_level_above_first": 8},
        "max_level": 3,
        "min_cost": {"base": 5, "per_level_above_first": 8},
        "slots": ["hand"],
        "supported_items": f"#{MOD}:evolving_spears",
        "weight": 1,
    })
    write_json(os.path.join(DATA, "tags", "item", "evolving_spears.json"),
               {"replace": False, "values": [f"{MOD}:evolving_spear"]})

    # --- génération du monde
    wg = os.path.join(DATA, "worldgen")

    def target(rule, block):
        return {"target": {"predicate_type": "minecraft:tag_match", "tag": rule},
                "state": {"Name": f"{MOD}:{block}"}}

    ores_cfg = {
        # Évolium : uniquement dans l'ardoise des abîmes, petits filons, souvent détruits à l'air libre.
        "ore_evolium": {"targets": [target("minecraft:deepslate_ore_replaceables", "deepslate_evolium_ore")],
                        "size": 4, "discard_chance_on_air_exposure": 0.5},
        "ore_ruby": {"targets": [target("minecraft:stone_ore_replaceables", "ruby_ore"),
                                 target("minecraft:deepslate_ore_replaceables", "deepslate_ruby_ore")],
                     "size": 6, "discard_chance_on_air_exposure": 0.3},
        "ore_sapphire": {"targets": [target("minecraft:stone_ore_replaceables", "sapphire_ore"),
                                     target("minecraft:deepslate_ore_replaceables", "deepslate_sapphire_ore")],
                         "size": 6, "discard_chance_on_air_exposure": 0.3},
    }
    ores_cfg["ore_evolite"] = {
        "targets": [{"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:end_stone"},
                     "state": {"Name": f"{MOD}:evolite_ore"}}],
        "size": 3, "discard_chance_on_air_exposure": 0.5,
    }
    placements = {
        # Débris antiques : ~2 petits filons par chunk dans un Nether plein de roche.
        # L'End est surtout du vide : on tente 10 filons par chunk, seuls ceux qui tombent dans la pierre de l'End restent.
        "ore_evolite": [{"type": "minecraft:count", "count": 10},
                        {"type": "minecraft:in_square"},
                        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                                      "min_inclusive": {"absolute": 10},
                                                                      "max_inclusive": {"absolute": 90}}},
                        {"type": "minecraft:biome"}],
        # Diamant vanilla : ~7 filons/chunk de taille 4 (+ gros filons). Évolium : 3 filons/chunk de taille 4,
        # dans l'ardoise des abîmes uniquement (Y -64 à -16) : nettement plus rare que le diamant, mais trouvable.
        "ore_evolium": [{"type": "minecraft:count", "count": 5},
                        {"type": "minecraft:in_square"},
                        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid",
                                                                      "min_inclusive": {"absolute": -64},
                                                                      "max_inclusive": {"absolute": -16}}},
                        {"type": "minecraft:biome"}],
        "ore_ruby": [{"type": "minecraft:count", "count": 4},
                     {"type": "minecraft:in_square"},
                     {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid",
                                                                   "min_inclusive": {"absolute": -48},
                                                                   "max_inclusive": {"absolute": 48}}},
                     {"type": "minecraft:biome"}],
        "ore_sapphire": [{"type": "minecraft:count", "count": 4},
                         {"type": "minecraft:in_square"},
                         {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                                       "min_inclusive": {"absolute": -32},
                                                                       "max_inclusive": {"absolute": 64}}},
                         {"type": "minecraft:biome"}],
    }
    for name, cfg in ores_cfg.items():
        write_json(os.path.join(wg, "configured_feature", f"{name}.json"), {"type": "minecraft:ore", "config": cfg})
        write_json(os.path.join(wg, "placed_feature", f"{name}.json"),
                   {"feature": f"{MOD}:{name}", "placement": placements[name]})


if __name__ == "__main__":
    gen_textures()
    gen_models()
    gen_armor()
    gen_bow()
    gen_data()
    gen_dimension_assets()
    gen_dimension_data()
    print("Ressources générées dans", os.path.normpath(ROOT))
