"""The 'stonefish' shared shape (Quicreate's Stonefish): a squat, wide bottom-dweller with a flat head, upturned mouth,
eyes on top, big fan pectoral fins spread sideways, a ridge of dorsal spines made of cubes turned 45 degrees (diamonds
from the side) and cobblestone lumps on the body, painted as cobblestone.
Run: python tools/gen_stonefish.py <orefishfarm assets> <quicreate assets> <preview dir>"""
import math, random, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from gen_fish_shape import build, side_view, sprite
from PIL import Image

OFF, QC, PREV = sys.argv[1], sys.argv[2], sys.argv[3]
SIDES = ['north', 'south']
ALL = ['north', 'south', 'east', 'west', 'up', 'down']

def mix(a, b, t):
    t = max(0.0, min(1.0, t)); return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

def box(name, role, a, b, faces=ALL, **kw):
    return dict(name=name, role=role, **{'from': list(a), 'to': list(b)}, faces=faces, **kw)

body = [
    box('head', 'head', (10.2, 4.8, 5.8), (13.4, 7.8, 10.2)),
    box('jaw', 'jaw', (13.4, 6.3, 6.6), (14.2, 7.7, 9.4), ['north', 'south', 'up', 'down', 'east']),
    box('body', 'body', (5.2, 4.8, 6.2), (10.2, 8.6, 9.8)),
    box('rear', 'body', (2.8, 5.2, 6.8), (5.2, 7.6, 9.2), ['north', 'south', 'up', 'down', 'west']),
    # big fan pectorals, flat and spread out sideways near the floor
    box('pectoral_n', 'fin', (7.4, 5.0, 3.8), (10.6, 5.4, 6.2)),
    box('pectoral_s', 'fin', (7.4, 5.0, 9.8), (10.6, 5.4, 12.2)),
    # eyes on top of the head
    box('eye_n', 'eye', (12.0, 7.8, 6.5), (12.8, 8.4, 7.3), ['north', 'south', 'up', 'east', 'west']),
    box('eye_s', 'eye', (12.0, 7.8, 8.7), (12.8, 8.4, 9.5), ['north', 'south', 'up', 'east', 'west']),
]
# dorsal spines: 0.9 cubes on the back ridge, turned 45 degrees about z (diamonds from the side)
for i, x in enumerate((5.7, 6.6, 7.5, 8.4, 9.3)):
    c = (x, 8.6, 8.0)
    body.append(box(f'spine{i}', 'spine', (x - 0.45, 8.15, 7.55), (x + 0.45, 9.05, 8.45),
                    rotation={'angle': 45, 'axis': 'z', 'origin': list(c)}))
# cobblestone lumps standing proud of the body and head
LUMPS = [  # (name, from, to, face left off: the one against the body)
    ('lump_s1', (5.8, 6.0, 9.8), (7.0, 7.2, 10.15), 'north'),
    ('lump_s2', (7.8, 5.2, 9.8), (8.9, 6.2, 10.15), 'north'),
    ('lump_s3', (8.4, 7.0, 9.8), (9.7, 8.1, 10.15), 'north'),
    ('lump_n1', (6.3, 6.9, 5.85), (7.5, 8.0, 6.2), 'south'),
    ('lump_n2', (8.2, 5.3, 5.85), (9.4, 6.4, 6.2), 'south'),
    ('lump_t1', (6.0, 8.6, 8.6), (7.2, 8.95, 9.5), 'down'),
    ('lump_t2', (7.9, 8.6, 6.5), (9.0, 8.95, 7.4), 'down'),
    ('lump_h1', (10.8, 5.6, 10.2), (11.9, 6.8, 10.5), 'north'),
    ('lump_h2', (10.6, 6.0, 5.5), (11.7, 7.1, 5.8), 'south'),
]
for name, a, b, off in LUMPS:
    body.append(box(name, 'lump', a, b, [f for f in ALL if f != off]))
tail = [box('tail', 'tail', (0.4, 4.6, 8), (3.0, 8.4, 8), SIDES)]

# ---- cobblestone paint: stones (3D cells) with dark mortar between them
random.seed(23)
SEEDS = []
for gx in range(0, 17, 2):
    for gy in range(2, 13, 2):
        for gz in range(2, 15, 2):
            SEEDS.append((gx + random.uniform(-0.8, 0.8), gy + random.uniform(-0.8, 0.8), gz + random.uniform(-0.8, 0.8),
                          random.choice([(128, 126, 120), (112, 110, 104), (146, 142, 132), (98, 96, 92), (120, 118, 108), (136, 128, 112)])))
MORTAR = (66, 64, 62)

def cobble(x, y, z, lift=0):
    d = sorted(((x - sx) ** 2 + (y - sy) ** 2 + (z - sz) ** 2, c) for sx, sy, sz, c in SEEDS)
    (d1, c1), (d2, _) = d[0], d[1]
    if math.sqrt(d2) - math.sqrt(d1) < 0.22: return MORTAR
    return mix(c1, (200, 196, 184), lift) if lift > 0 else c1

def paint(role, f, x, y, z):
    if role == 'tail':
        # a short rounded fan: wider toward its end
        d, dy = 3.0 - x, abs(y - 6.5)
        if dy > 0.9 + d * 0.45 or (x < 0.9 and dy > 1.2): return None
        c = cobble(x, y, 8)
        return MORTAR if c == MORTAR else mix(c, (90, 86, 80), 0.25)
    if role == 'spine':
        return (178, 172, 156) if f in ('up', 'east', 'west') else (150, 144, 130)
    if role == 'eye':
        if f == 'up' or (f in SIDES and y > 8.15): return (226, 182, 84) if abs(x - 12.4) > 0.22 else (18, 18, 22)
        return cobble(x, y, z)
    if role == 'fin':
        edge = min(abs(z - 3.8), abs(z - 12.2), abs(x - 7.4)) < 0.35
        if edge: return (176, 112, 74)  # a rusty edge, like a stonefish's fin
        c = cobble(x, y, z)
        return mix(c, (96, 92, 86), 0.3) if c != MORTAR else c
    if role == 'jaw':
        if f in SIDES and abs(y - 6.9) < 0.18: return (40, 34, 32)  # the mouth line
        if f == 'east' and abs(y - 6.9) < 0.25: return (40, 34, 32)
        return mix(cobble(x, y, z), (180, 170, 150), 0.35)
    if f == 'down': return mix(cobble(x, y, z), (176, 170, 156), 0.45)
    if role == 'head' and f in SIDES and abs(y - 6.0) < 0.15 and x > 12.6: return (40, 34, 32)  # mouth corner
    return cobble(x, y, z, 0.15 if role == 'lump' else 0)

cred = ("Jake's Ore Fish Farm: shared 'stonefish' fish model, a squat stonefish (flat head, upturned mouth, eyes on top, fan "
        "pectorals, diamond dorsal spines turned 45 degrees about z, cobblestone lumps); side view, head toward +x. A fish uses "
        "it with its own texture: {\"parent\": \"orefishfarm:fish_category/stonefish\", \"textures\": {\"t\": ..., \"particle\": ...}}")
os.makedirs(f'{QC}/textures/item/fish', exist_ok=True)
tex = build('stonefish', body, tail, paint, cred, f'{OFF}/models/fish_category', f'{QC}/textures/item/fish/stone_fish.png', 'quicreate:item/fish/stone_fish')
side = side_view(body, tail, paint)
side.save(f'{PREV}/stone_fish_side.png')
sp = sprite(side); sp.save(f'{QC}/textures/item/stone_fish.png')
sp.resize((256, 256), Image.NEAREST).save(f'{PREV}/stone_fish_sprite.png')
tex.resize((256, 256), Image.NEAREST).save(f'{PREV}/stone_fish_tex.png')
print('stonefish ok')
