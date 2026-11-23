"""The 'mudskipper' shared shape (Quicreate's Mudskipper): a long blunt-headed fish with bulging eyes on top of the
head, a tall spotted front dorsal fin, a long low rear dorsal, and stout side fins it walks on. Mud browns with dark
spots, pale belly, small blue speckles.
Run: python tools/gen_mudskipper.py <orefishfarm assets> <quicreate assets> <preview dir>"""
import math, random, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from gen_fish_shape import build, side_view, sprite
from PIL import Image

OFF, QC, PREV = sys.argv[1], sys.argv[2], sys.argv[3]
SIDES = ['north', 'south']
ALL = ['north', 'south', 'east', 'west', 'up', 'down']

def mix(a, b, t):
    t = max(0.0, min(1.0, t)); return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

def inside(poly, x, y):
    n, c = len(poly), False
    for i in range(n):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1): c = not c
    return c

def box(name, role, a, b, faces=ALL):
    return dict(name=name, role=role, **{'from': list(a), 'to': list(b)}, faces=faces)

body = [
    box('head', 'head', (10.4, 5.4, 6.8), (13.8, 7.8, 9.2)),
    box('body_front', 'body', (6.6, 5.5, 7.0), (10.4, 7.6, 9.0), ['north', 'south', 'up', 'down', 'west']),
    box('body_rear', 'body', (3.0, 5.8, 7.3), (6.6, 7.2, 8.7), ['north', 'south', 'up', 'down', 'west']),
    # bulging eyes, close together on top of the head
    box('eye_n', 'eye', (11.6, 7.8, 7.0), (12.8, 9.0, 7.9)),
    box('eye_s', 'eye', (11.6, 7.8, 8.1), (12.8, 9.0, 9.0)),
    # a tall front dorsal (a spotted sail) and a long low rear dorsal
    box('dorsal_front', 'sail', (7.4, 7.6, 8), (9.8, 10.0, 8), SIDES),
    box('dorsal_rear', 'dorsal', (3.4, 7.2, 8), (7.0, 8.3, 8), SIDES),
    # stout side fins, the "arms", out and down from the front of the body
    box('arm_n', 'arm', (9.4, 4.6, 6.2), (10.8, 5.8, 7.0)),
    box('arm_s', 'arm', (9.4, 4.6, 9.0), (10.8, 5.8, 9.8)),
    box('pelvic', 'fin', (7.6, 4.9, 8), (8.8, 5.5, 8), SIDES),
]
tail = [box('tail', 'tail', (0.4, 5.0, 8), (3.0, 8.0, 8), SIDES)]
TAIL = [(3.0, 7.2), (1.6, 7.9), (0.6, 7.4), (0.4, 6.5), (0.6, 5.6), (1.6, 5.1), (3.0, 5.8)]   # rounded, a little pointed
SAIL = [(7.4, 7.6), (7.6, 9.4), (8.3, 10.0), (9.2, 9.7), (9.8, 8.6), (9.8, 7.6)]

BACK, SIDE, BELLY = (82, 70, 54), (126, 108, 82), (198, 188, 160)
SPOT, BLUE = (58, 48, 38), (110, 178, 212)
random.seed(9)
SPOTS = [(random.uniform(3.2, 13.4), random.uniform(6.0, 7.8)) for _ in range(22)]
TSPOTS = [(random.uniform(0.9, 2.8), random.uniform(5.4, 7.6)) for _ in range(5)]
BLUES = [(random.uniform(4, 12.5), random.uniform(6.2, 7.4)) for _ in range(9)]

def paint(role, f, x, y, z):
    if role == 'tail':
        if not inside(TAIL, x, y): return None
        if x < 0.7: return SPOT
        return SPOT if any((x - sx) ** 2 + (y - sy) ** 2 < 0.06 for sx, sy in TSPOTS) else (104, 88, 66)
    if role == 'sail':
        if not inside(SAIL, x, y): return None
        if y > 9.4: return (232, 226, 206)                       # pale edge
        if (int(x * 2) + int(y * 2)) % 3 == 0: return BLUE         # blue spots on the sail
        return mix((92, 80, 64), (70, 60, 48), (y - 7.6) / 2.4)
    if role == 'dorsal':
        if y > 8.0: return (214, 206, 184)
        return SPOT if int(x * 2) % 3 == 0 else (96, 82, 64)
    if role == 'eye':
        if f == 'up': return (70, 60, 48)
        if f in SIDES or f == 'east':
            d = (x - 12.2) ** 2 + (y - 8.45) ** 2 if f in SIDES else (z - (7.45 if z < 8 else 8.55)) ** 2 + (y - 8.45) ** 2
            if d < 0.09: return (14, 14, 16)            # pupil
            if d < 0.30: return (186, 176, 120)         # pale gold ring
        return mix(SIDE, BACK, (y - 7.8) / 1.2)         # the rest of the bulge, skin-coloured
    if role == 'arm':
        if f == 'down' or y < 4.9: return (170, 158, 128)
        return mix(SIDE, SPOT, 0.4 if int(x * 2) % 2 else 0.1)
    if role == 'fin': return (150, 136, 108)
    # head and body: dark back to pale belly, dark spots, a few blue speckles, a wide mouth
    if role == 'head':
        if f == 'east' and abs(y - 6.2) < 0.18: return (40, 32, 26)
        if f in SIDES and x > 12.6 and abs(y - 6.2) < 0.15: return (40, 32, 26)     # mouth line
    if f == 'up': return BACK
    if f == 'down': return BELLY
    top, bot = (7.8, 5.4) if role == 'head' else ((7.6, 5.5) if x >= 6.6 else (7.2, 5.8))
    t = (y - bot) / (top - bot)
    c = mix(BELLY, SIDE, t * 2.4) if t < 0.45 else mix(SIDE, BACK, (t - 0.45) * 1.8)
    if f in SIDES or f in ('east', 'west'):
        if t > 0.3 and any((x - sx) ** 2 + (y - sy) ** 2 < 0.07 for sx, sy in SPOTS): c = SPOT
        elif 0.25 < t < 0.75 and any((x - sx) ** 2 + (y - sy) ** 2 < 0.03 for sx, sy in BLUES): c = BLUE
    return c

cred = ("Jake's Ore Fish Farm: shared 'mudskipper' fish model (long blunt head, bulging eyes on top, tall spotted front "
        "dorsal, long low rear dorsal, stout side fins it walks on); side view, head toward +x. A fish uses it with its own "
        "texture: {\"parent\": \"orefishfarm:fish_category/mudskipper\", \"textures\": {\"t\": ..., \"particle\": ...}}")
os.makedirs(f'{QC}/textures/item/fish', exist_ok=True)
tex = build('mudskipper', body, tail, paint, cred, f'{OFF}/models/fish_category', f'{QC}/textures/item/fish/mudskipper.png', 'quicreate:item/fish/mudskipper')
side = side_view(body, tail, paint)
side.save(f'{PREV}/mudskipper_side.png')
sp = sprite(side); sp.save(f'{QC}/textures/item/mudskipper.png')
sp.resize((256, 256), Image.NEAREST).save(f'{PREV}/mudskipper_sprite.png')
tex.resize((256, 256), Image.NEAREST).save(f'{PREV}/mudskipper_tex.png')
print('mudskipper ok')
