#!/usr/bin/env python3
"""Génère toutes les ressources du mod Evo Tool (textures, modèles 3D, données JSON).

Usage : python3 tools/generate_assets.py
Les fichiers sont écrits dans src/main/resources/. Le script est déterministe (graine fixe).
"""
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

def write_json(path, obj):
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

STAGES = [
    # handle, grip, metal, gem   (4 paliers : niv. 0, 20, 50, 100)
    {"wood": hexc("6b4423"), "grip": hexc("5a2e17"), "metal": hexc("a9b2bd"), "gem": GEMS["evolium"]},
    {"wood": hexc("3b2417"), "grip": hexc("7a1426"), "metal": hexc("6fd6d0"), "gem": GEMS["evolium"]},
    {"wood": hexc("241b33"), "grip": hexc("1b2a6b"), "metal": hexc("4a3f5c"), "gem": GEMS["evolium"]},
    {"wood": hexc("efe6d0"), "grip": hexc("f5f0ff"), "metal": hexc("f2c14e"),
     "gem": [hexc("6a0f8a"), hexc("d94dff"), hexc("ff9cf5"), hexc("ffffff"), hexc("ffffff")]},
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


def metal_texture(base, seed):
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
    img.putpixel((2, 2), shade(base, 1.6))
    img.putpixel((3, 2), shade(base, 1.4))
    return img


def gem_texture(palette, seed):
    dark, mid, light, bright, spark = palette
    img = Image.new("RGBA", (16, 16))
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            u, v = (x % 8) - 3.5, (y % 8) - 3.5
            facet = (u > 0) * 1 + (v > 0) * 2
            c = (bright, light, mid, dark)[facet]
            if abs(abs(u) - abs(v)) < 0.6:
                c = lerp(c, light, 0.5)
            img.putpixel((x, y), lerp(c, mid, r.uniform(0, 0.15)))
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
    add([6.5, 11.75, 6], [9.5, 14.75, 10], "gem")                      # coeur d'évolium
    add([2, 12.25, 7], [6, 14.75, 9], "metal")                         # bras gauche
    add([10, 12.25, 7], [14, 14.75, 9], "metal")                       # bras droit
    # lames recourbées : segment puis pointe
    add([-1.5, 11.75, 7.2], [2.5, 13.75, 8.8], "metal", ["z", 22.5, [2, 12.75, 8]])
    add([13.5, 11.75, 7.2], [17.5, 13.75, 8.8], "metal", ["z", -22.5, [14, 12.75, 8]])
    tip = 4.0 if stage < 2 else 5.5
    add([-0.5 - tip, 10.6, 7.55], [-0.5, 11.9, 8.45], "metal", ["z", 45, [-0.5, 11.3, 8]])
    add([16.5, 10.6, 7.55], [16.5 + tip, 11.9, 8.45], "metal", ["z", -45, [16.5, 11.3, 8]])
    # arêtes de renfort sur le dessus des bras
    add([3, 14.75, 7.5], [6, 15.25, 8.5], "metal")
    add([10, 14.75, 7.5], [13, 15.25, 8.5], "metal")

    if stage >= 1:
        # incrustations de gemmes + couronne
        add([3, 12.75, 6.75], [5, 14.25, 9.25], "gem")
        add([11, 12.75, 6.75], [13, 14.25, 9.25], "gem")
        add([7, 15.5, 7], [9, 17, 9], "gem")
        add([6.6, 9.75, 6.6], [9.4, 10.5, 9.4], "gem")                 # anneau sur la virole

    if stage >= 2:
        # pointe arrière en losange, ailerons et pommeau en gemme
        add([7.25, 16.5, 7.25], [8.75, 19.5, 8.75], "gem", ["y", 45, [8, 18, 8]])
        add([4.75, 10.5, 7.5], [6, 13, 8.5], "metal", ["z", -22.5, [6, 11, 8]])
        add([10, 10.5, 7.5], [11.25, 13, 8.5], "metal", ["z", 22.5, [10, 11, 8]])
        add([7.25, -5.75, 7.25], [8.75, -4.75, 8.75], "gem")
        add([6.5, 0.75, 6.5], [9.5, 1.5, 9.5], "metal")

    if stage >= 3:
        # cristaux flottants en halo autour de la tête (forme finale, niveau 100)
        for (cx, cy) in ((1.5, 17.5), (14.5, 17.5), (8, 21.5), (-2.5, 15), (18.5, 15)):
            add([cx - 0.6, cy - 1.1, 7.4], [cx + 0.6, cy + 1.1, 8.6], "gem", ["y", 45, [cx, cy, 8]])
        add([7.6, -7.5, 7.6], [8.4, -6.25, 8.4], "gem", ["y", 45, [8, -7, 8]])
        add([5.5, 15.25, 7.75], [10.5, 15.75, 8.25], "metal")
    return E


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
        # Le modèle est construit vertical (manche en bas, tête en haut) ;
        # les transformations le remettent en diagonale comme une pioche vanilla.
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 3.5, 0.5], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [0, 90, -10], "translation": [0, 3.5, 0.5], "scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.6, 1.13], "scale": [0.5, 0.5, 0.5]},
            "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 3.6, 1.13], "scale": [0.5, 0.5, 0.5]},
            "gui": {"rotation": [15, -25, -45], "translation": [0, 0, 0], "scale": [0.52, 0.52, 0.52]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.35, 0.35, 0.35]},
            "fixed": {"rotation": [0, 180, 45], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [0.8, 0.8, 0.8]},
        },
    }


def mod_icon():
    """Icône 128x128 : sprite de pioche dessiné en pixel art, agrandi."""
    st = STAGES[2]
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
    for g in GEMS:
        save_png(storage_block(GEMS[g]), "block", f"{g}_block")
        save_png(gem_sprite(GEMS[g]), "item", g)
    for i, st in enumerate(STAGES):
        save_png(wood_texture(st["wood"], i), "item", "pickaxe", f"stage{i}_handle")
        save_png(grip_texture(st["grip"], i), "item", "pickaxe", f"stage{i}_grip")
        save_png(metal_texture(st["metal"], i), "item", "pickaxe", f"stage{i}_metal")
        save_png(gem_texture(st["gem"], i), "item", "pickaxe", f"stage{i}_gem")
    mod_icon().save(os.path.join(ASSETS, "icon.png"))


BLOCKS = ["deepslate_evolium_ore", "evolium_block", "ruby_ore", "deepslate_ruby_ore", "ruby_block",
          "sapphire_ore", "deepslate_sapphire_ore", "sapphire_block"]
ORES = {
    "deepslate_evolium_ore": "evolium",
    "ruby_ore": "ruby", "deepslate_ruby_ore": "ruby",
    "sapphire_ore": "sapphire", "deepslate_sapphire_ore": "sapphire",
}


def gen_models():
    for b in BLOCKS:
        write_json(os.path.join(ASSETS, "blockstates", f"{b}.json"),
                   {"variants": {"": {"model": f"{MOD}:block/{b}"}}})
        write_json(os.path.join(ASSETS, "models", "block", f"{b}.json"),
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{b}"}})
        write_json(os.path.join(ASSETS, "items", f"{b}.json"),
                   {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{b}"}})
    for g in GEMS:
        write_json(os.path.join(ASSETS, "models", "item", f"{g}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{g}"}})
        write_json(os.path.join(ASSETS, "items", f"{g}.json"),
                   {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{g}"}})
    for i in range(len(STAGES)):
        write_json(os.path.join(ASSETS, "models", "item", f"evolving_pickaxe_stage{i}.json"), pickaxe_model(i))

    # L'apparence change selon le palier (custom_model_data floats[0], mis à jour par le mod).
    def ref(i):
        return {"type": "minecraft:model", "model": f"{MOD}:item/evolving_pickaxe_stage{i}"}

    write_json(os.path.join(ASSETS, "items", "evolving_pickaxe.json"), {
        "model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "fallback": ref(0),
            "entries": [{"threshold": i, "model": ref(i)} for i in range(len(STAGES))],
        }
    })


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
        "pattern": ["B", "|"],
        "key": {"B": f"{MOD}:evolium_block", "|": "minecraft:stick"},
        "result": {"id": f"{MOD}:evolving_pickaxe", "count": 1},
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
    write_json(os.path.join(tags, "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"replace": False, "values": ore_ids + gem_blocks})
    # Évolium : pioche en diamant obligatoire. Rubis / saphir : pioche en fer.
    write_json(os.path.join(tags, "minecraft", "tags", "block", "needs_diamond_tool.json"),
               {"replace": False, "values": [f"{MOD}:deepslate_evolium_ore", f"{MOD}:evolium_block"]})
    write_json(os.path.join(tags, "minecraft", "tags", "block", "needs_iron_tool.json"),
               {"replace": False, "values": [o for o in ore_ids if "evolium" not in o]
                + [f"{MOD}:ruby_block", f"{MOD}:sapphire_block"]})
    write_json(os.path.join(tags, "minecraft", "tags", "block", "beacon_base_blocks.json"),
               {"replace": False, "values": gem_blocks})
    write_json(os.path.join(tags, "c", "tags", "block", "ores.json"), {"replace": False, "values": ore_ids})
    write_json(os.path.join(tags, "c", "tags", "item", "gems.json"),
               {"replace": False, "values": [f"{MOD}:{g}" for g in GEMS]})
    write_json(os.path.join(DATA, "tags", "block", "evolium_ores.json"),
               {"replace": False, "values": [f"{MOD}:deepslate_evolium_ore"]})
    write_json(os.path.join(DATA, "tags", "block", "gem_ores.json"),
               {"replace": False, "values": [o for o in ore_ids if "evolium" not in o]})
    write_json(os.path.join(DATA, "tags", "item", "evolium_tool_materials.json"),
               {"replace": False, "values": [f"{MOD}:evolium"]})
    write_json(os.path.join(tags, "minecraft", "tags", "item", "pickaxes.json"),
               {"replace": False, "values": [f"{MOD}:evolving_pickaxe"]})

    # --- génération du monde
    wg = os.path.join(DATA, "worldgen")

    def target(rule, block):
        return {"target": {"predicate_type": "minecraft:tag_match", "tag": rule},
                "state": {"Name": f"{MOD}:{block}"}}

    ores_cfg = {
        # Évolium : uniquement dans l'ardoise des abîmes, petits filons, souvent détruits à l'air libre.
        "ore_evolium": {"targets": [target("minecraft:deepslate_ore_replaceables", "deepslate_evolium_ore")],
                        "size": 3, "discard_chance_on_air_exposure": 0.7},
        "ore_ruby": {"targets": [target("minecraft:stone_ore_replaceables", "ruby_ore"),
                                 target("minecraft:deepslate_ore_replaceables", "deepslate_ruby_ore")],
                     "size": 6, "discard_chance_on_air_exposure": 0.3},
        "ore_sapphire": {"targets": [target("minecraft:stone_ore_replaceables", "sapphire_ore"),
                                     target("minecraft:deepslate_ore_replaceables", "deepslate_sapphire_ore")],
                         "size": 6, "discard_chance_on_air_exposure": 0.3},
    }
    placements = {
        # Diamant vanilla : 7 filons/chunk de taille 4. Évolium : 1 filon de taille 3, 1 chunk sur 2.
        "ore_evolium": [{"type": "minecraft:rarity_filter", "chance": 2},
                        {"type": "minecraft:in_square"},
                        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid",
                                                                      "min_inclusive": {"absolute": -64},
                                                                      "max_inclusive": {"absolute": -20}}},
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
    gen_data()
    print("Ressources générées dans", os.path.normpath(ROOT))
