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
    el = {"from": frm, "to": to, "faces": faces}
    if rot:
        axis, angle, origin = rot
        el["rotation"] = {"angle": angle, "axis": axis, "origin": origin}
    return el


def pickaxe_elements(stage):
    E = []
    s = 0

    def add(frm, to, tex, rot=None):
        nonlocal s
        s += 1
        E.append(element(frm, to, tex, rot, s))

    # --- Manche
    add([7.25, -3, 7.25], [8.75, 10.5, 8.75], "handle")
    add([6.75, -4.75, 6.75], [9.25, -3, 9.25], "metal")              # pommeau
    for y in (-2.25, -0.25, 1.75, 3.75):                               # bandes de cuir
        add([6.9, y, 6.9], [9.1, y + 1.25, 9.1], "grip")
    add([7.1, -2.5, 7.1], [8.9, 5.5, 8.9], "grip")                     # manchon de prise
    add([6.75, 9.5, 6.75], [9.25, 11, 9.25], "metal")                  # virole
    # langues de métal le long du manche
    add([6.9, 6.5, 7.6], [7.25, 10, 8.4], "metal")
    add([8.75, 6.5, 7.6], [9.1, 10, 8.4], "metal")

    # --- Tête
    add([6, 11, 6.5], [10, 15.5, 9.5], "metal")                        # douille
    if stage >= 20:
        add([6.25, 11.5, 5.75], [9.75, 15, 10.25], "gem")              # coeur surchargé
    else:
        add([6.5, 11.75, 6], [9.5, 14.75, 10], "gem")                  # coeur d'évolium
    add([2, 12.25, 7], [6, 14.75, 9], "metal")                         # bras gauche
    add([10, 12.25, 7], [14, 14.75, 9], "metal")                       # bras droit
    # lames recourbées : segment puis pointe
    add([-1.5, 11.75, 7.2], [2.5, 13.75, 8.8], "metal", ["z", 22.5, [2, 12.75, 8]])
    add([13.5, 11.75, 7.2], [17.5, 13.75, 8.8], "metal", ["z", -22.5, [14, 12.75, 8]])
    tip = 4.0 if stage < 8 else 5.5
    add([-0.5 - tip, 10.6, 7.55], [-0.5, 11.9, 8.45], "metal", ["z", 45, [-0.5, 11.3, 8]])
    add([16.5, 10.6, 7.55], [16.5 + tip, 11.9, 8.45], "metal", ["z", -45, [16.5, 11.3, 8]])

    # Chaque forme (tous les 5 niveaux) ajoute de nouvelles pièces aux précédentes.
    if stage >= 1:   # niv. 5 : arêtes de renfort sur les bras
        add([3, 14.75, 7.5], [6, 15.25, 8.5], "metal")
        add([10, 14.75, 7.5], [13, 15.25, 8.5], "metal")
    if stage >= 2:   # niv. 10 : anneau de braise sur la virole (auto-smelt)
        add([6.6, 9.75, 6.6], [9.4, 10.5, 9.4], "gem")
    if stage >= 3:   # niv. 15 : incrustations de gemmes (Fortune)
        add([3, 12.75, 6.75], [5, 14.25, 9.25], "gem")
        add([11, 12.75, 6.75], [13, 14.25, 9.25], "gem")
    if stage >= 4:   # niv. 20 : couronne
        add([7, 15.5, 7], [9, 17, 9], "gem")
    if stage >= 5:   # niv. 25 : clous de gemme devant / derrière la douille
        add([7.25, 12.5, 5.5], [8.75, 14, 6], "gem")
        add([7.25, 12.5, 10], [8.75, 14, 10.5], "gem")
    if stage >= 6:   # niv. 30 : ailerons
        add([4.75, 10.5, 7.5], [6, 13, 8.5], "metal", ["z", -22.5, [6, 11, 8]])
        add([10, 10.5, 7.5], [11.25, 13, 8.5], "metal", ["z", 22.5, [10, 11, 8]])
    if stage >= 7:   # niv. 35 : pommeau en gemme
        add([7.25, -5.75, 7.25], [8.75, -4.75, 8.75], "gem")
    # niv. 40 : lames plus longues (voir "tip" plus haut)
    if stage >= 9:   # niv. 45 : garde au milieu du manche
        add([6.5, 0.75, 6.5], [9.5, 1.5, 9.5], "metal")
    if stage >= 10:  # niv. 50 : pointe en losange au sommet
        add([7.25, 16.5, 7.25], [8.75, 19.5, 8.75], "gem", ["y", 45, [8, 18, 8]])
    if stage >= 11:  # niv. 55 : barbes sous les bras
        add([3.5, 10.75, 7.6], [4.5, 12.25, 8.4], "metal", ["z", 45, [4, 12.25, 8]])
        add([11.5, 10.75, 7.6], [12.5, 12.25, 8.4], "metal", ["z", -45, [12, 12.25, 8]])
    if stage >= 12:  # niv. 60 : runes lumineuses sur le manche
        for y in (6.75, 8.25):
            add([7.75, y, 7.0], [8.25, y + 0.75, 7.25], "gem")
            add([7.75, y, 8.75], [8.25, y + 0.75, 9.0], "gem")
    if stage >= 13:  # niv. 65 : épines sur le dessus des bras
        add([1.75, 14.25, 7.6], [2.75, 16.25, 8.4], "metal", ["z", 22.5, [2.25, 14.25, 8]])
        add([13.25, 14.25, 7.6], [14.25, 16.25, 8.4], "metal", ["z", -22.5, [13.75, 14.25, 8]])
    if stage >= 14:  # niv. 70 : deux premiers cristaux flottants
        crystal(add, 1.5, 17.5)
        crystal(add, 14.5, 17.5)
    if stage >= 15:  # niv. 75 : ailes au-dessus de la tête
        add([4, 15, 7.7], [6, 18.5, 8.3], "metal", ["z", 22.5, [6, 15, 8]])
        add([10, 15, 7.7], [12, 18.5, 8.3], "metal", ["z", -22.5, [10, 15, 8]])
    if stage >= 16:  # niv. 80 : cristal au zénith
        crystal(add, 8, 21.5)
    if stage >= 17:  # niv. 85 : cristaux latéraux
        crystal(add, -2.5, 15)
        crystal(add, 18.5, 15)
    if stage >= 18:  # niv. 90 : cristal sous le pommeau
        add([7.6, -7.5, 7.6], [8.4, -6.25, 8.4], "gem", ["y", 45, [8, -7, 8]])
    if stage >= 19:  # niv. 95 : diadème et gemmes sur la virole
        add([5.5, 15.25, 7.75], [10.5, 15.75, 8.25], "metal")
        add([6.4, 10, 7.5], [6.75, 10.75, 8.5], "gem")
        add([9.25, 10, 7.5], [9.6, 10.75, 8.5], "gem")
    if stage >= 20:  # niv. 100 : halo complet
        crystal(add, -3.5, 9.5)
        crystal(add, 19.5, 9.5)
    return E


def crystal(add, cx, cy):
    add([cx - 0.6, cy - 1.1, 7.4], [cx + 0.6, cy + 1.1, 8.6], "gem", ["y", 45, [cx, cy, 8]])


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
    st = f"{MOD}:item/pickaxe/stage{stage}_"
    return {
        "credit": "Evo Tool - modèle 3D généré par tools/generate_assets.py",
        "gui_light": "side",
        "textures": {
            "particle": st + "metal",
            "handle": st + "handle",
            "grip": st + "grip",
            "metal": st + "metal",
            "gem": st + "gem",
        },
        "elements": pickaxe_elements(stage),
        "display": display(27),
    }


def mace_elements(stage):
    E = []
    n = 0

    def add(frm, to, tex, rot=None):
        nonlocal n
        n += 1
        E.append(element(frm, to, tex, rot, n))

    # --- Forme de base : manche, tête lourde et coeur d'évolium
    add([7.25, -3, 7.25], [8.75, 10, 8.75], "handle")
    add([6.75, -4.75, 6.75], [9.25, -3, 9.25], "metal")
    for y in (-2.25, -0.25, 1.75, 3.75):
        add([6.9, y, 6.9], [9.1, y + 1.25, 9.1], "grip")
    add([7.1, -2.5, 7.1], [8.9, 5.5, 8.9], "grip")
    add([6.5, 9, 6.5], [9.5, 10.5, 9.5], "metal")
    add([4.5, 10.5, 4.5], [11.5, 17.5, 11.5], "metal")
    if stage >= 20:
        add([5, 11, 3.75], [11, 17, 12.25], "gem")                    # coeur surchargé
        add([3.75, 11, 5], [12.25, 17, 11], "gem")
    else:
        add([5.5, 11.5, 4], [10.5, 16.5, 12], "gem")
        add([4, 11.5, 5.5], [12, 16.5, 10.5], "gem")
    add([4, 10, 4], [12, 11, 12], "metal")
    add([4, 17, 4], [12, 18, 12], "metal")

    # --- Chaque forme (tous les 5 niveaux) ajoute des pièces
    if stage >= 1:   # niv. 5 : garde sur le manche
        add([6.5, 0.75, 6.5], [9.5, 1.5, 9.5], "metal")
    if stage >= 2:   # niv. 10 : 4 pointes en losange
        for (x, z) in ((2.5, 8), (13.5, 8), (8, 2.5), (8, 13.5)):
            add([x - 1, 13, z - 1], [x + 1, 15, z + 1], "metal", ["y", 45, [x, 14, z]])
    if stage >= 3:   # niv. 15 : pommeau en gemme
        add([7.25, -5.75, 7.25], [8.75, -4.75, 8.75], "gem")
    if stage >= 4:   # niv. 20 : cristal au sommet
        add([7, 18, 7], [9, 21, 9], "gem", ["y", 45, [8, 19.5, 8]])
    if stage >= 5:   # niv. 25 : clous aux 4 coins de la tête
        for (x, z) in ((4, 4), (11, 4), (4, 11), (11, 11)):
            add([x, 13.5, z], [x + 1, 14.5, z + 1], "gem")
    if stage >= 6:   # niv. 30 : pointes diagonales gauche / droite
        add([3.5, 17, 7.5], [5, 20, 8.5], "metal", ["z", 22.5, [5, 17, 8]])
        add([11, 17, 7.5], [12.5, 20, 8.5], "metal", ["z", -22.5, [11, 17, 8]])
    if stage >= 7:   # niv. 35 : pointes diagonales avant / arrière
        add([7.5, 17, 3.5], [8.5, 20, 5], "metal", ["x", -22.5, [8, 17, 5]])
        add([7.5, 17, 11], [8.5, 20, 12.5], "metal", ["x", 22.5, [8, 17, 11]])
    if stage >= 8:   # niv. 40 : pointes sous la tête
        add([3.5, 8, 7.5], [5, 11, 8.5], "metal", ["z", -22.5, [5, 11, 8]])
        add([11, 8, 7.5], [12.5, 11, 8.5], "metal", ["z", 22.5, [11, 11, 8]])
    if stage >= 9:   # niv. 45 : runes sur le manche
        for y in (6.25, 7.75):
            add([7.75, y, 7.0], [8.25, y + 0.75, 7.25], "gem")
            add([7.75, y, 8.75], [8.25, y + 0.75, 9.0], "gem")
    if stage >= 10:  # niv. 50 : pointes latérales doublées
        for (x, z) in ((0.5, 8), (15.5, 8), (8, 0.5), (8, 15.5)):
            add([x - 0.6, 13.4, z - 0.6], [x + 0.6, 14.6, z + 0.6], "gem", ["y", 45, [x, 14, z]])
    if stage >= 11:  # niv. 55 : lames verticales sur les 4 faces
        add([3.5, 11, 7.75], [4, 17, 8.25], "metal")
        add([12, 11, 7.75], [12.5, 17, 8.25], "metal")
        add([7.75, 11, 3.5], [8.25, 17, 4], "metal")
        add([7.75, 11, 12], [8.25, 17, 12.5], "metal")
    if stage >= 12:  # niv. 60 : couronne
        add([5, 18, 5], [11, 18.5, 11], "metal")
        for (x, z) in ((5, 5), (10.25, 5), (5, 10.25), (10.25, 10.25)):
            add([x, 18.5, z], [x + 0.75, 19.75, z + 0.75], "gem")
    if stage >= 13:  # niv. 65 : cristal sous le pommeau
        add([7.6, -7.5, 7.6], [8.4, -6.25, 8.4], "gem", ["y", 45, [8, -7, 8]])
    if stage >= 14:  # niv. 70 : deux cristaux flottants
        crystal(add, 0, 19)
        crystal(add, 16, 19)
    if stage >= 15:  # niv. 75 : ailes
        add([2, 16, 7.7], [4, 20, 8.3], "metal", ["z", 22.5, [4, 16, 8]])
        add([12, 16, 7.7], [14, 20, 8.3], "metal", ["z", -22.5, [12, 16, 8]])
    if stage >= 16:  # niv. 80 : cristal au zénith
        crystal(add, 8, 24)
    if stage >= 17:  # niv. 85 : cristaux latéraux
        crystal(add, -2.5, 11)
        crystal(add, 18.5, 11)
    if stage >= 18:  # niv. 90 : deuxième garde sertie
        add([6.4, 5.75, 6.4], [9.6, 6.25, 9.6], "metal")
        add([6.1, 5.6, 7.6], [6.4, 6.4, 8.4], "gem")
        add([9.6, 5.6, 7.6], [9.9, 6.4, 8.4], "gem")
    if stage >= 19:  # niv. 95 : anneau doré autour de la tête
        add([3.25, 13.75, 3.25], [12.75, 14.25, 4], "metal")
        add([3.25, 13.75, 12], [12.75, 14.25, 12.75], "metal")
    if stage >= 20:  # niv. 100 : halo complet
        crystal(add, -1, 4)
        crystal(add, 17, 4)
    return E


def mace_model(stage):
    model = pickaxe_model(stage)
    model["credit"] = "Evo Tool - masse 3D générée par tools/generate_assets.py"
    model["elements"] = mace_elements(stage)
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
    add([7.25, -3, 7.25], [8.75, top, 8.75], "handle")
    add([6.75, -4.75, 6.75], [9.25, -3, 9.25], "metal")
    for y in (-2.25, -0.25, 1.75, 3.75):
        if y + 1.25 <= grip_top:
            add([6.9, y, 6.9], [9.1, y + 1.25, 9.1], "grip")
    add([7.1, -2.5, 7.1], [8.9, grip_top, 8.9], "grip")


def common_decor(add, stage, runes, crystals, second_guard_y=5.75):
    """Décorations communes à tous les outils, qui s'ajoutent forme après forme."""
    if stage >= 3:   # niv. 15 : pommeau en gemme
        add([7.25, -5.75, 7.25], [8.75, -4.75, 8.75], "gem")
    if stage >= 9:   # niv. 45 : garde sur le manche
        add([6.5, 0.75, 6.5], [9.5, 1.5, 9.5], "metal")
    if stage >= 12:  # niv. 60 : runes lumineuses
        for (y, z0, z1) in runes:
            add([7.75, y, z0], [8.25, y + 0.75, z1], "gem")
    if stage >= 13:  # niv. 65 : cristal sous le pommeau
        add([7.6, -7.5, 7.6], [8.4, -6.25, 8.4], "gem", ["y", 45, [8, -7, 8]])
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
        add([6.4, y, 6.4], [9.6, y + 0.5, 9.6], "metal")
        add([6.1, y - 0.15, 7.6], [6.4, y + 0.65, 8.4], "gem")
        add([9.6, y - 0.15, 7.6], [9.9, y + 0.65, 8.4], "gem")
    if stage >= 20:  # niv. 100 : halo complet
        for c in crystals[5:7]:
            crystal(add, *c)


def handle_runes(*ys):
    return [r for y in ys for r in ((y, 7.0, 7.25), (y, 8.75, 9.0))]


def sword_elements(stage):
    E, add = builder()
    bt = 24 if stage < 8 else 26                                     # haut de la lame
    handle(add, 5.5, 5)
    add([3, 5.5, 7], [13, 7, 9], "metal")                            # garde
    add([6.5, 7, 7.6], [9.5, bt, 8.4], "metal")                      # lame
    add([6.25, 7, 7.75], [6.5, bt, 8.25], "metal")                   # tranchants
    add([9.5, 7, 7.75], [9.75, bt, 8.25], "metal")
    add([6.94, bt - 1.06, 7.6], [9.06, bt + 1.06, 8.4], "metal", ["z", 45, [8, bt, 8]])  # pointe
    if stage >= 1:   # embouts de garde
        add([2.5, 5.25, 6.75], [3.5, 7.25, 9.25], "metal")
        add([12.5, 5.25, 6.75], [13.5, 7.25, 9.25], "metal")
    if stage >= 2:   # gouttière en gemme
        if stage >= 20:
            add([7.5, 8, 7.45], [8.5, bt - 2, 8.55], "gem")
        else:
            add([7.75, 8, 7.5], [8.25, bt - 2, 8.5], "gem")
    if stage >= 4:   # gemme au centre de la garde
        add([7, 5.25, 6.75], [9, 7.25, 9.25], "gem")
    if stage >= 5:   # gemmes sur la garde
        add([4.5, 5.75, 6.9], [5.5, 6.75, 9.1], "gem")
        add([10.5, 5.75, 6.9], [11.5, 6.75, 9.1], "gem")
    if stage >= 6:   # quillons relevés
        add([0.5, 6, 7.6], [3, 7.5, 8.4], "metal", ["z", -22.5, [3, 6.75, 8]])
        add([13, 6, 7.6], [15.5, 7.5, 8.4], "metal", ["z", 22.5, [13, 6.75, 8]])
    if stage >= 7:   # talon de lame renforcé
        add([6.25, 7, 7.4], [9.75, 9, 8.6], "metal")
    if stage >= 10:  # losange de gemme à la base de la lame
        add([7.25, 10, 7.35], [8.75, 11.5, 8.65], "gem", ["z", 45, [8, 10.75, 8]])
    if stage >= 11:  # dents sur les tranchants
        for y in (13, 16, 19):
            add([5.7, y, 7.7], [6.5, y + 0.8, 8.3], "metal", ["z", 45, [6.1, y + 0.4, 8]])
            add([9.5, y, 7.7], [10.3, y + 0.8, 8.3], "metal", ["z", 45, [9.9, y + 0.4, 8]])
    if stage >= 15:  # ailes sur la garde
        add([2, 7, 7.7], [4, 11, 8.3], "metal", ["z", 22.5, [4, 7, 8]])
        add([12, 7, 7.7], [14, 11, 8.3], "metal", ["z", -22.5, [12, 7, 8]])
    if stage >= 19:  # diadème
        add([5.5, 7, 7.7], [10.5, 7.5, 8.3], "metal")
    runes = [r for y in (12, 15, 18, 21) for r in ((y, 7.35, 7.6), (y, 8.4, 8.65))]
    common_decor(add, stage, runes,
                 [(3, 15), (13, 15), (8, bt + 4), (1, 21), (15, 21), (2, 2), (14, 2)], second_guard_y=-3.6)
    return E


def axe_elements(stage):
    E, add = builder()
    handle(add, 11)
    add([6.75, 9.5, 6.75], [9.25, 11, 9.25], "metal")                # virole
    add([6.5, 11, 6.5], [9.5, 16, 9.5], "metal")                     # douille
    if stage >= 20:
        add([6.75, 11.5, 6], [9.25, 15.5, 10], "gem")
    else:
        add([7, 12, 6.25], [9, 15, 9.75], "gem")
    add([2.5, 10.5, 7.4], [6.5, 16.5, 8.6], "metal")                 # corps de la lame
    add([1, 9.5, 7.6], [2.5, 17.5, 8.4], "metal")                    # tranchant
    add([0.5, 16.5, 7.65], [2.5, 18, 8.35], "metal", ["z", -22.5, [2.5, 17.25, 8]])
    add([0.5, 8, 7.65], [2.5, 9.5, 8.35], "metal", ["z", 22.5, [2.5, 8.75, 8]])
    if stage < 10:
        add([9.5, 12.5, 7.25], [11, 14.5, 8.75], "metal")            # talon
    if stage >= 1:   # arête sur la lame
        add([2.5, 16.5, 7.5], [6.5, 17, 8.5], "metal")
    if stage >= 2:   # fil en gemme
        add([0.5, 10, 7.7], [1, 17, 8.3], "gem")
    if stage >= 4:   # cristal sur la douille
        add([7, 16, 7], [9, 17.5, 9], "gem")
    if stage >= 5:   # clous sur la lame
        add([3.5, 12, 7.2], [4.5, 13, 8.8], "gem")
        add([3.5, 14, 7.2], [4.5, 15, 8.8], "gem")
    if 6 <= stage < 10:  # bec à l'arrière
        add([11, 12.75, 7.5], [14, 14.25, 8.5], "metal", ["z", -22.5, [11, 13.5, 8]])
    if stage >= 7:   # incrustation
        add([5, 12.5, 7.3], [6.25, 14.5, 8.7], "gem")
    if stage >= 8:   # corne au-dessus du tranchant
        add([1, 17.5, 7.7], [2, 20, 8.3], "metal", ["z", 22.5, [1.5, 17.5, 8]])
    if stage >= 10:  # hache double
        add([9.5, 11, 7.45], [13, 16, 8.55], "metal")
        add([13, 10, 7.6], [14.5, 17, 8.4], "metal")
        add([14.5, 10.5, 7.7], [15, 16.5, 8.3], "gem")
    if stage >= 11:  # barbes sous la lame
        add([3, 9.25, 7.6], [4, 10.5, 8.4], "metal", ["z", 45, [3.5, 10.5, 8]])
    if stage >= 15:  # ailes
        add([5.5, 16, 7.7], [7, 19.5, 8.3], "metal", ["z", 22.5, [7, 16, 8]])
        add([9, 16, 7.7], [10.5, 19.5, 8.3], "metal", ["z", -22.5, [9, 16, 8]])
    if stage >= 19:  # diadème
        add([6, 16, 7.75], [10, 16.5, 8.25], "metal")
    common_decor(add, stage, handle_runes(6.75, 8.25),
                 [(4, 21), (12, 21), (8, 24), (-2, 13), (18, 13), (-1, 4), (17, 4)])
    return E


def shovel_elements(stage):
    E, add = builder()
    top = 19 if stage < 8 else 20                                    # haut de la lame
    handle(add, 9)
    add([6.75, 9, 6.75], [9.25, 10.5, 9.25], "metal")                # virole
    add([7.25, 10.5, 7.5], [8.75, 12, 8.5], "metal")                 # col
    add([5, 12, 7.5], [11, top, 8.5], "metal")                       # lame
    add([4.75, 12, 7.6], [5, top, 8.4], "metal")
    add([11, 12, 7.6], [11.25, top, 8.4], "metal")
    add([5.5, top, 7.55], [10.5, top + 1, 8.45], "metal")            # bout arrondi
    add([6.5, top + 1, 7.6], [9.5, top + 2, 8.4], "metal")
    add([7.25, top + 2, 7.65], [8.75, top + 2.75, 8.35], "metal")
    if stage >= 20:
        add([6.25, 13.25, 7.2], [9.75, 16.75, 8.8], "gem")
    else:
        add([6.5, 13.5, 7.25], [9.5, 16.5, 8.75], "gem")
    if stage >= 1:   # nervure au dos
        add([7.75, 12, 8.5], [8.25, top, 8.75], "metal")
    if stage >= 2:   # bord en gemme
        add([5, 12, 7.4], [11, 12.75, 8.6], "gem")
    if stage >= 4:   # pointe en gemme
        add([7.5, top + 2.5, 7.7], [8.5, top + 3.5, 8.3], "gem", ["z", 45, [8, top + 3, 8]])
    if stage >= 5:   # clous
        add([5.5, 17, 7.3], [6.25, 17.75, 8.7], "gem")
        add([9.75, 17, 7.3], [10.5, 17.75, 8.7], "gem")
    if stage >= 6:   # épaulettes
        add([3.5, 11.5, 7.7], [5, 14, 8.3], "metal", ["z", -22.5, [5, 12, 8]])
        add([11, 11.5, 7.7], [12.5, 14, 8.3], "metal", ["z", 22.5, [11, 12, 8]])
    if stage >= 7:   # collier
        add([6, 11.75, 7.25], [10, 12.25, 8.75], "metal")
    if stage >= 10:  # grand losange de gemme
        add([6.9, 15, 7.15], [9.1, 17.2, 8.85], "gem", ["z", 45, [8, 16.1, 8]])
    if stage >= 11:  # pointes latérales
        add([3.75, 15, 7.7], [4.75, 16, 8.3], "metal", ["z", 45, [4.25, 15.5, 8]])
        add([11.25, 15, 7.7], [12.25, 16, 8.3], "metal", ["z", 45, [11.75, 15.5, 8]])
    if stage >= 15:  # ailes
        add([4, 8.5, 7.7], [6, 12, 8.3], "metal", ["z", 22.5, [6, 9, 8]])
        add([10, 8.5, 7.7], [12, 12, 8.3], "metal", ["z", -22.5, [10, 9, 8]])
    if stage >= 19:  # diadème
        add([5, top - 0.5, 7.4], [11, top, 8.6], "metal")
    common_decor(add, stage, handle_runes(6, 7.5),
                 [(2, 20), (14, 20), (8, top + 6), (-1, 14), (17, 14), (0, 4), (16, 4)])
    return E


def hoe_elements(stage):
    E, add = builder()
    bottom = 9.5 if stage < 8 else 8.5                               # bas de la lame
    handle(add, 13)
    add([6.75, 11.5, 6.75], [9.25, 13, 9.25], "metal")               # virole
    add([6.5, 13, 6.5], [9.5, 16, 9.5], "metal")                     # douille
    if stage >= 20:
        add([6.75, 13.25, 6], [9.25, 15.75, 10], "gem")
    else:
        add([7, 13.5, 6.25], [9, 15.5, 9.75], "gem")
    add([1.5, 13.75, 7.25], [6.5, 15.75, 8.75], "metal")             # bras
    add([0, bottom, 6.5], [2.5, 15.75, 9.5], "metal")                # lame
    add([0.25, bottom - 1, 6.75], [2.25, bottom, 9.25], "metal")     # tranchant
    add([9.5, 14, 7.25], [11.5, 15.5, 8.75], "metal")                # contrepoids
    if stage >= 1:   # arête du bras
        add([2, 15.75, 7.5], [6.5, 16.25, 8.5], "metal")
    if stage >= 2:   # fil en gemme
        add([-0.25, bottom - 0.5, 6.6], [0.25, 15, 9.4], "gem")
    if stage >= 4:   # cristal sur la douille
        add([7, 16, 7], [9, 17.5, 9], "gem")
    if stage >= 5:   # clous sur le bras
        add([3.5, 13.5, 7], [4.5, 14.5, 9], "gem")
    if stage >= 6:   # pointe du contrepoids
        add([11.5, 14.25, 7.5], [14, 15.25, 8.5], "metal", ["z", 22.5, [11.5, 14.75, 8]])
    if stage >= 7:   # incrustation sur la lame
        add([0.6, 11, 6.35], [2, 13, 9.65], "gem")
    if stage >= 10:  # dents
        for (z0, z1) in ((6.75, 7.5), (7.6, 8.4), (8.5, 9.25)):
            add([0.5, bottom - 3, z0], [1.75, bottom - 1, z1], "metal")
    if stage >= 11:  # barbe sous le bras
        add([3, 12.5, 7.6], [4, 13.75, 8.4], "metal", ["z", 45, [3.5, 13.75, 8]])
    if stage >= 15:  # ailes
        add([5.5, 16, 7.7], [7, 19.5, 8.3], "metal", ["z", 22.5, [7, 16, 8]])
        add([9, 16, 7.7], [10.5, 19.5, 8.3], "metal", ["z", -22.5, [9, 16, 8]])
    if stage >= 19:  # diadème
        add([6, 16, 7.75], [10, 16.5, 8.25], "metal")
    common_decor(add, stage, handle_runes(7, 8.5),
                 [(4, 20), (13, 19.5), (8, 22.5), (-3, 12), (18, 12), (-2, 3), (17, 3)])
    return E


def spear_elements(stage):
    """Lance : long manche, fer en feuille, ailerons ; s'enrichit à chaque forme."""
    E, add = builder()
    top = 27.5 if stage < 8 else 29.5                                # pointe du fer
    # manche
    add([7.4, -10, 7.4], [8.6, 18, 8.6], "handle")
    add([7.1, -11.5, 7.1], [8.9, -10, 8.9], "metal")                 # talon
    for y in (-3, -1, 1):
        add([7.2, y, 7.2], [8.8, y + 1, 8.8], "grip")
    add([7.3, -3.5, 7.3], [8.7, 3, 8.7], "grip")
    add([7, 17, 7], [9, 19, 9], "metal")                             # douille
    # fer en forme de feuille
    add([6.5, 19, 7.6], [9.5, 22, 8.4], "metal")
    add([6, 22, 7.65], [10, top - 3.5, 8.35], "metal")
    add([6.75, top - 3.5, 7.7], [9.25, top - 1, 8.3], "metal")
    add([7.1, top - 1.9, 7.7], [8.9, top - 0.1, 8.3], "metal", ["z", 45, [8, top - 1, 8]])
    if stage >= 20:
        add([7, 20.5, 7.35], [9, 24, 8.65], "gem")
    else:
        add([7.25, 21, 7.4], [8.75, 23.5, 8.6], "gem")
    if stage >= 1:   # niv. 5 : anneaux de douille
        add([6.8, 16.5, 6.8], [9.2, 17, 9.2], "metal")
    if stage >= 2:   # niv. 10 : nervure lumineuse
        add([7.8, 23.5, 7.55], [8.2, top - 1.5, 8.45], "gem")
    if stage >= 3:   # niv. 15 : talon serti
        add([7.4, -12.5, 7.4], [8.6, -11.5, 8.6], "gem")
    if stage >= 4:   # niv. 20 : ailerons (lance à ailettes)
        add([4.5, 18.5, 7.6], [11.5, 19.5, 8.4], "metal")
    if stage >= 5:   # niv. 25 : rubans
        add([6.4, 13, 7.6], [6.9, 16.5, 8.4], "grip")
        add([9.1, 13.5, 7.6], [9.6, 16.5, 8.4], "grip")
    if stage >= 6:   # niv. 30 : ailerons relevés
        add([2.5, 18.5, 7.65], [4.5, 19.5, 8.35], "metal", ["z", -22.5, [4.5, 19, 8]])
        add([11.5, 18.5, 7.65], [13.5, 19.5, 8.35], "metal", ["z", 22.5, [11.5, 19, 8]])
    if stage >= 7:   # niv. 35 : gemmes sur les ailerons
        add([5, 18.25, 7.4], [6, 19.75, 8.6], "gem")
        add([10, 18.25, 7.4], [11, 19.75, 8.6], "gem")
    # niv. 40 : fer plus long (voir "top")
    if stage >= 9:   # niv. 45 : garde sur le manche
        add([7, 4, 7], [9, 4.75, 9], "metal")
    if stage >= 10:  # niv. 50 : lames latérales (pertuisane)
        add([4, 20, 7.7], [6.5, 21.25, 8.3], "metal", ["z", 45, [6.5, 20.5, 8]])
        add([9.5, 20, 7.7], [12, 21.25, 8.3], "metal", ["z", -45, [9.5, 20.5, 8]])
    if stage >= 11:  # niv. 55 : crochets
        add([5.25, 22.5, 7.7], [6, 24, 8.3], "metal", ["z", 22.5, [6, 22.5, 8]])
        add([10, 22.5, 7.7], [10.75, 24, 8.3], "metal", ["z", -22.5, [10, 22.5, 8]])
    if stage >= 12:  # niv. 60 : runes sur le manche
        for y in (7, 9.5, 12):
            add([7.8, y, 7.2], [8.2, y + 0.75, 7.4], "gem")
            add([7.8, y, 8.6], [8.2, y + 0.75, 8.8], "gem")
    if stage >= 13:  # niv. 65 : pointe de talon
        add([7.6, -14.5, 7.6], [8.4, -12.5, 8.4], "metal", ["y", 45, [8, -13.5, 8]])
    if stage >= 15:  # niv. 75 : ailes
        add([3, 19.5, 7.7], [5, 24, 8.3], "metal", ["z", 22.5, [5, 19.5, 8]])
        add([11, 19.5, 7.7], [13, 24, 8.3], "metal", ["z", -22.5, [11, 19.5, 8]])
    if stage >= 18:  # niv. 90 : deuxième garde sertie
        add([7, 5.5, 7], [9, 6, 9], "metal")
        add([6.7, 5.4, 7.6], [7, 6.1, 8.4], "gem")
        add([9, 5.4, 7.6], [9.3, 6.1, 8.4], "gem")
    if stage >= 19:  # niv. 95 : diadème
        add([6, 19, 7.5], [10, 19.4, 8.5], "gem")
    crystals = [(3, 27), (13, 27), (8, 30.8), (1, 15), (15, 15), (2, 5), (14, 5)]
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
    tex = {"L": "handle", "M": "metal", "G": "grip", "S": "gem" if stage >= 2 else "string",
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
    depth = {"fin": (7.6, 8.4, "metal"), "edge": (7.4, 8.6, "gem"), "frame": (7.45, 8.55, "metal"), "membrane": (7.8, 8.2, "gem")}
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
                "gem" if i % 4 == 0 else "metal")
    if stage >= 18:  # niv. 90 : anneau runique devant l'arc
        cx, cy = 7.5, 8.5
        for i in range(16):
            a = 2 * math.pi * i / 16 + 0.2
            x, y = cx + math.cos(a) * 6.5, cy + math.sin(a) * 6.5
            add([round(x - 0.35, 3), round(y - 0.35, 3), 10.8], [round(x + 0.35, 3), round(y + 0.35, 3), 11.4], "gem")
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
    st = f"{MOD}:item/pickaxe/stage{stage}_"
    return {
        "credit": "Evo Tool - arc 3D généré par tools/generate_assets.py",
        "gui_light": "front",
        "textures": {"particle": st + "handle", "handle": st + "handle", "grip": st + "grip",
                     "metal": st + "metal", "gem": st + "gem", "string": f"{MOD}:item/bow/string"},
        "elements": (elements := bow_elements(stage, pull)),
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
    "evolving_sword": (sword_elements, 34),
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
    model["elements"] = elements(stage)
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
    """Heaume de chevalier à cornes, face avant = nord. 16 unités du modèle ~ 1,25 tête une fois porté."""
    E, add = builder()
    sym = mirror_x(add)
    add([1, 0.5, 1], [15, 14.5, 15], "metal")                        # coque
    add([2, 14.5, 2], [14, 16, 14], "metal")                         # dôme
    add([1.5, 0.5, 0.5], [14.5, 13.5, 1], "metal")                   # plaque frontale
    add([2.5, 8, 0.25], [13.5, 9.25, 0.5], "gem" if stage >= 12 else "visor")   # fente des yeux
    for x in (9.5, 11, 12.5):                                        # trous d'aération
        for y in (3, 4.75):
            add([x, y, 0.25], [x + 0.75, y + 0.75, 0.5], "visor")
    add([7.5, 9.25, 0.25], [8.5, 13.5, 0.5], "metal")                # arête du nez
    add([7.5, 0.5, 0.25], [8.5, 8, 0.5], "metal")
    # cornes : base, segment montant, puis (plus tard) pointe recourbée et bout en gemme
    sym([-1.75, 9.75, 6.5], [1, 12.75, 9.5], "horn")
    sym([-4.75, 11, 6.9], [-1.5, 13.25, 9.1], "horn", ["z", -22.5, [-1.5, 12.1, 8]])
    if stage >= 1:   # bandeau
        add([0.75, 9.5, 0.75], [15.25, 10, 15.25], "metal")
    if stage >= 2:   # protège-joues
        sym([0.5, 1, 2], [1, 8, 10], "metal")
    if stage >= 3:   # gemme frontale
        add([7, 10.5, -0.25], [9, 12.5, 0.25], "gem")
    if stage >= 4:   # cornes plus longues
        sym([-6, 12.5, 7.2], [-3.75, 17.5, 8.8], "horn", ["z", 22.5, [-4.9, 12.5, 8]])
    if stage >= 5:   # rivets sertis
        for z in (4, 11):
            sym([0.75, 12, z], [1, 13, z + 1], "gem")
    if stage >= 6:   # crête
        add([7.25, 16, 3], [8.75, 17.5, 13], "metal")
    if stage >= 7:   # protège-nuque
        add([1.5, 0, 15], [14.5, 4, 15.75], "metal")
    if stage >= 8:   # pointes sur la crête
        for z in (5, 8, 11):
            add([7.5, 17.5, z], [8.5, 19 + (0.5 if z == 8 else 0), z + 1], "metal")
    if stage >= 9:   # mentonnière
        add([2.5, 0, 0], [13.5, 1.5, 0.5], "metal")
    if stage >= 10:  # bouts de cornes en gemme
        sym([-7.25, 17, 7.5], [-5.75, 20, 8.5], "gem", ["z", 45, [-6.5, 17, 8]])
    if stage >= 11:  # petites cornes frontales
        sym([3, 14, 1.5], [4, 16.5, 2.5], "horn", ["z", 22.5, [3.5, 14, 2]])
    if stage >= 15:  # ailes
        sym([-1, 13, 9.5], [1, 18, 10], "metal", ["z", 22.5, [1, 13, 9.75]])
    if stage >= 18:  # couronne de gemmes
        for (x, z) in ((3, 3), (12, 3), (3, 12), (12, 12)):
            add([x, 16, z], [x + 1, 17, z + 1], "gem")
    if stage >= 19:  # diadème
        add([2, 13.5, 0.25], [14, 14, 0.5], "gem")
    crystals = [(-1, 20), (17, 20), (8, 23), (-6, 9), (22, 9), (-4, 3), (20, 3)]
    if stage >= 13:  # pointes à l'arrière du casque
        for y in (6, 10):
            add([7.5, y, 15], [8.5, y + 1, 17], "metal")
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
    return E


def helmet_model(stage):
    st = f"{MOD}:item/pickaxe/stage{stage}_"
    return {
        "credit": "Evo Tool - heaume 3D généré par tools/generate_assets.py",
        "gui_light": "side",
        "textures": {
            "particle": st + "metal",
            "metal": st + "metal",
            "gem": st + "gem",
            "horn": f"{MOD}:item/armor/stage{stage}_horn",
            "visor": f"{MOD}:item/armor/visor",
        },
        "elements": helmet_elements(stage),
        "display": {
            # Porté : rendu sur la tête comme une citrouille sculptée (face avant = nord).
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "gui": {"rotation": [20, 200, 0], "translation": [0, 0, 0], "scale": [0.48, 0.48, 0.48]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
            "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
            "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
        },
    }


# Disposition de la texture d'armure (64x32) : (u, v, largeur, hauteur, profondeur) de chaque boîte.
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


def plate_pixel(st, x, y, w, h, r, light=1.0):
    """Plaque de métal : dégradé vertical, bords plus sombres, léger bruit."""
    f = light * (1.12 - 0.28 * (y / max(1, h - 1))) + r.uniform(-0.04, 0.04)
    if x == 0 or x == w - 1:
        f *= 0.82
    return shade(st["metal"], f)


def paint_side(img, rect, st, r, rows, kind, stage, face):
    """Peint une face (x0, y0, w, h) d'une boîte, uniquement sur les lignes « rows » (relatives)."""
    x0, y0, w, h = rect
    dark = shade(st["metal"], 0.45)
    trim = st["gem"][2] if stage >= 9 else shade(st["metal"], 1.35)
    cloth = st["grip"]
    for y in rows:
        for x in range(w):
            c = plate_pixel(st, x, y, w, h, r, 1.08 if face in ("top", "front") else 0.95)
            if kind == "chest":
                if y in (7, 9):                                  # lames de la braconnière
                    c = dark
                elif y == 11:
                    c = trim
                elif face == "front" and y < 7 and x in (3, 4):  # arête centrale du plastron
                    c = shade(st["metal"], 1.3)
            elif kind == "arm":
                if y == 4:                                       # bas de l'épaulière
                    c = dark
                elif 5 <= y <= 7:                                # cotte de mailles
                    c = shade(cloth, 0.9 if (x + y) % 2 else 0.6)
                elif y == 8:
                    c = trim
            elif kind == "belt":
                c = shade(cloth, 1.0 - 0.15 * ((x + y) % 2))
                if y == 0 or y == 4:
                    c = shade(cloth, 0.6)
            elif kind == "thigh":
                if y in (3, 9):
                    c = dark
                elif 5 <= y <= 7 and face == "front":            # genouillère
                    c = shade(st["metal"], 1.35 if y == 6 else 1.15)
            elif kind == "boot":
                if y == 6:
                    c = trim
                elif y == 11:
                    c = dark                                     # semelle
                elif face == "front" and y >= 9:
                    c = shade(st["metal"], 1.3)                  # bout ferré
            img.putpixel((x0 + x, y0 + y), c)


def paint_box(img, box, st, stage, kind, rows_main, seed):
    r = random.Random(seed)
    for face, (x0, y0, w, h) in box_faces(box).items():
        if face in ("top", "bottom"):
            continue
        paint_side(img, (x0, y0, w, h), st, r, [y for y in rows_main if y < h], kind, stage, face)


def put(img, box, face, x, y, color):
    """Pose un pixel sur une face d'une boîte (coordonnées relatives à la face)."""
    x0, y0, w, h = box_faces(box)[face]
    if 0 <= x < w and 0 <= y < h:
        img.putpixel((x0 + x, y0 + y), color)


SIDES = ("front", "back", "right", "left")


def armor_textures(stage):
    """Textures portées. Chaque forme (tous les 5 niveaux) ajoute un nouveau détail aux précédents."""
    st = STAGES[stage]
    gem = st["gem"]
    metal = st["metal"]
    light = shade(metal, 1.35)
    humanoid = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    leggings = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    # --- Base (niv. 0) : plastron + bras, bottes, ceinture + cuissardes
    paint_box(humanoid, BODY, st, stage, "chest", range(12), 1)
    paint_box(humanoid, ARM, st, stage, "arm", range(12), 2)
    for box, top_light in ((BODY, 1.2), (ARM, 1.3)):
        faces = box_faces(box)
        for face in ("top", "bottom"):
            x0, y0, w, h = faces[face]
            for y in range(h):
                for x in range(w):
                    humanoid.putpixel((x0 + x, y0 + y), shade(metal, top_light if face == "top" else 0.6))
    paint_box(humanoid, LEG, st, stage, "boot", range(6, 12), 3)
    x0, y0, w, h = box_faces(LEG)["bottom"]
    for y in range(h):
        for x in range(w):
            humanoid.putpixel((x0 + x, y0 + y), shade(metal, 0.4))
    paint_box(leggings, BODY, st, stage, "belt", range(7, 12), 4)
    paint_box(leggings, LEG, st, stage, "thigh", range(0, 10), 5)
    x0, y0, w, h = box_faces(LEG)["top"]
    for y in range(h):
        for x in range(w):
            leggings.putpixel((x0 + x, y0 + y), shade(metal, 1.1))
    put(humanoid, BODY, "front", 4, 3, gem[2])                       # petite gemme
    for (x, y) in ((3, 8), (4, 8), (3, 9), (4, 9)):                  # boucle de ceinture
        put(leggings, BODY, "front", x, y, gem[2] if y == 8 else gem[1])

    if stage >= 1:   # niv. 5 : rivets
        for face in ("front", "back"):
            for (x, y) in ((1, 1), (6, 1), (1, 5), (6, 5)):
                put(humanoid, BODY, face, x, y, light)
        for face in SIDES:
            put(humanoid, ARM, face, 1, 10, light)
            put(humanoid, ARM, face, 2, 10, light)
    if stage >= 2:   # niv. 10 : jointures lumineuses entre les lames
        for face in SIDES:
            for x in range(8 if face in ("front", "back") else 4):
                put(humanoid, BODY, face, x, 9, gem[1])
    if stage >= 3:   # niv. 15 : grosse gemme au centre du plastron
        for (x, y) in ((3, 2), (4, 2), (3, 3), (4, 3)):
            put(humanoid, BODY, "front", x, y, gem[2] if y == 2 else gem[1])
        put(humanoid, BODY, "front", 3, 2, gem[4])
    if stage >= 4:   # niv. 20 : bordure colorée des épaulières
        for face in SIDES:
            for x in range(4):
                put(humanoid, ARM, face, x, 4, gem[1])
    if stage >= 5:   # niv. 25 : gemmes sur les genouillères
        put(leggings, LEG, "front", 1, 6, gem[2])
        put(leggings, LEG, "front", 2, 6, gem[3])
    if stage >= 6:   # niv. 30 : épaulières à pointes
        for (x, y) in ((1, 1), (2, 2), (1, 2), (2, 1)):
            put(humanoid, ARM, "top", x, y, shade(metal, 1.6))
        for (x, y) in ((1, 1), (2, 0), (2, 1), (1, 2)):
            put(humanoid, ARM, "right", x, y, shade(metal, 1.55))
    if stage >= 7:   # niv. 35 : blason en croix sur le plastron
        for x in range(1, 7):
            if x not in (3, 4):
                put(humanoid, BODY, "front", x, 3, light)
        for y in (0, 1, 5, 6):
            put(humanoid, BODY, "front", 3, y, light)
            put(humanoid, BODY, "front", 4, y, light)
    if stage >= 8:   # niv. 40 : manchettes des gantelets
        for face in SIDES:
            for x in range(4):
                put(humanoid, ARM, face, x, 11, gem[1])
    # niv. 45 : les bordures passent à la couleur de la gemme (voir paint_side, stage >= 9)
    if stage >= 10:  # niv. 50 : éperons et bouts de bottes sertis
        put(humanoid, LEG, "back", 1, 9, gem[2])
        put(humanoid, LEG, "back", 2, 9, gem[2])
        put(humanoid, LEG, "front", 1, 10, gem[3])
        put(humanoid, LEG, "front", 2, 10, gem[3])
    if stage >= 11:  # niv. 55 : clous sur la ceinture
        for face in ("front", "back"):
            for x in (1, 6):
                put(leggings, BODY, face, x, 9, gem[3])
    if stage >= 12:  # niv. 60 : runes lumineuses
        for (x, y) in ((1, 4), (6, 4), (1, 5), (6, 5)):
            put(humanoid, BODY, "front", x, y, gem[3])
        put(leggings, LEG, "front", 1, 1, gem[3])
        put(leggings, LEG, "front", 2, 1, gem[3])
    if stage >= 13:  # niv. 65 : cape dans le dos
        for y in range(1, 12):
            for x in range(1, 7):
                put(humanoid, BODY, "back", x, y, shade(st["grip"], 1.05 - 0.04 * y + (0.06 if x % 2 else 0)))
        put(humanoid, BODY, "back", 3, 0, gem[2])
        put(humanoid, BODY, "back", 4, 0, gem[2])
    if stage >= 14:  # niv. 70 : runes sur les bras
        for face in ("right", "left"):
            put(humanoid, ARM, face, 1, 9, gem[3])
            put(humanoid, ARM, face, 2, 9, gem[3])
    if stage >= 15:  # niv. 75 : ailes brodées sur la cape
        for i in range(4):
            put(humanoid, BODY, "back", 3 - i, 2 + i, gem[2])
            put(humanoid, BODY, "back", 4 + i, 2 + i, gem[2])
    if stage >= 16:  # niv. 80 : ailettes sur les bottes
        for face in ("right", "left"):
            for (x, y) in ((1, 7), (2, 7), (2, 8)):
                put(humanoid, LEG, face, x, y, gem[3])
    if stage >= 17:  # niv. 85 : bandes lumineuses sur les cuissardes
        for face in ("right", "left"):
            for y in range(9):
                put(leggings, LEG, face, 2, y, gem[1])
    if stage >= 18:  # niv. 90 : bordures en gemme sur le plastron
        for y in range(7):
            put(humanoid, BODY, "front", 0, y, gem[2])
            put(humanoid, BODY, "front", 7, y, gem[2])
    if stage >= 19:  # niv. 95 : filigrane doré
        for x in range(0, 8, 2):
            put(humanoid, BODY, "front", x, 0, gem[3])
            put(leggings, BODY, "front", x, 7, gem[3])
        for face in SIDES:
            put(humanoid, ARM, face, 0, 0, gem[3])
            put(humanoid, ARM, face, 3, 0, gem[3])
    if stage >= 20:  # niv. 100 : armure ultime, toutes les jointures brillent
        for face in SIDES:
            for x in range(8 if face in ("front", "back") else 4):
                put(humanoid, BODY, face, x, 7, gem[2])
                put(humanoid, BODY, face, x, 11, gem[3])
        for face in SIDES:
            put(leggings, LEG, face, 0, 3, gem[2])
            put(leggings, LEG, face, 3, 3, gem[2])
        r = random.Random(100)
        for _ in range(6):
            put(humanoid, BODY, "front", r.randrange(8), r.randrange(7), gem[4])
    return humanoid, leggings


ICONS = {
    "evolving_chestplate": [
        "................",
        "..ooo......ooo..",
        ".olllo....olllo.",
        ".ollmmoooommllo.",
        ".olmmmmttmmmmlo.",
        ".ommmmmggmmmmmo.",
        ".odmmmmggmmmmdo.",
        "..oommmttmmmoo..",
        "....ommmmmmo....",
        "....ommmmmmo....",
        "....oddddddo....",
        "....ommmmmmo....",
        "....oddddddo....",
        "....otttttto....",
        ".....oooooo.....",
        "................",
    ],
    "evolving_leggings": [
        "................",
        "...oooooooooo...",
        "...otttggttto...",
        "...oooooooooo...",
        "...ommmmmmmmo...",
        "...omlmmmmlmo...",
        "...ommmoommmo...",
        "...ommo..ommo...",
        "...oddo..oddo...",
        "...ollo..ollo...",
        "...ommo..ommo...",
        "...ommo..ommo...",
        "...ommo..ommo...",
        "...oddo..oddo...",
        "...oooo..oooo...",
        "................",
    ],
    "evolving_boots": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "..oooo....oooo..",
        "..ommo....ommo..",
        "..olmo....olmo..",
        "..ttto....ttto..",
        "..ommo....ommo..",
        "..ommoo...ommoo.",
        "..ommllo..ommllo",
        "..ommmmo..ommmmo",
        "..oddddo..oddddo",
        "..oooooo..oooooo",
        "................",
    ],
}


def armor_icon(item, stage):
    st = STAGES[stage]
    rows = ICONS[item]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    trim = st["gem"][2] if stage >= 9 else st["grip"]
    colors = {"o": shade(st["metal"], 0.3), "m": st["metal"], "l": shade(st["metal"], 1.35),
              "d": shade(st["metal"], 0.65), "g": st["gem"][2], "t": trim}
    for y, row in enumerate(rows):
        assert len(row) == 16, (item, y, row)
        for x, ch in enumerate(row):
            if ch in colors:
                img.putpixel((x, y), colors[ch])
    if stage >= 12 and img.getpixel((7, 5))[3] > 0:
        img.putpixel((7, 5), st["gem"][4])
    return img


ARMOR_ITEMS = ["evolving_helmet", "evolving_chestplate", "evolving_leggings", "evolving_boots"]


def gen_armor():
    save_png(visor_texture(), "item", "armor", "visor")
    for i, st in enumerate(STAGES):
        save_png(horn_texture(st, i), "item", "armor", f"stage{i}_horn")
        humanoid, leggings = armor_textures(i)
        save_png(humanoid, "entity", "equipment", "humanoid", f"evolium_stage{i}")
        save_png(leggings, "entity", "equipment", "humanoid_leggings", f"evolium_stage{i}")
        write_json(os.path.join(ASSETS, "equipment", f"evolium_stage{i}.json"), {
            "layers": {
                "humanoid": [{"texture": f"{MOD}:evolium_stage{i}"}],
                "humanoid_leggings": [{"texture": f"{MOD}:evolium_stage{i}"}],
            }
        })
        write_json(os.path.join(ASSETS, "models", "item", f"evolving_helmet_stage{i}.json"), helmet_model(i))
        for item in ARMOR_ITEMS[1:]:
            save_png(armor_icon(item, i), "item", "armor", f"{item}_stage{i}")
            write_json(os.path.join(ASSETS, "models", "item", f"{item}_stage{i}.json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/armor/{item}_stage{i}"}})
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
        "ore_evolium": [{"type": "minecraft:count", "count": 3},
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
    print("Ressources générées dans", os.path.normpath(ROOT))
