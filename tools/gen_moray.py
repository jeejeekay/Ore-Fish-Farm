"""The 'moray' shared shape (Ore Fish Farm's Cuprum Eel): a moray eel, long and thick, small head with an open
toothy mouth and a raised snout, one long fin running along the back into the tail and along the belly behind the
middle. Copper with a darker reticulated pattern and verdigris speckles, in the newer generated style.
Articulated: moray_front (head and fore body) holds still, moray (the rest) swings, moray_tail wags.
Run: python tools/gen_moray.py <orefishfarm assets> <preview dir>"""
import json, random, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from gen_fish_shape import build, side_view, sprite
from PIL import Image

OFF, PREV = sys.argv[1], sys.argv[2]
SIDES = ['north', 'south']
ALL = ['north', 'south', 'east', 'west', 'up', 'down']
def box(name, role, a, b, faces=ALL): return dict(name=name, role=role, **{'from': list(a), 'to': list(b)}, faces=faces)
def mix(a, b, t):
    t = max(0.0, min(1.0, t)); return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

SPLIT = 9.4
FRONT = {'head', 'snout', 'jaw', 'body_1', 'fin_1'}
body = [
    box('head', 'head', (12.6, 6.3, 7.15), (14.6, 8.5, 8.85)),
    box('snout', 'head', (14.6, 7.0, 7.4), (15.5, 8.1, 8.6)),
    box('jaw', 'jaw', (13.2, 5.8, 7.3), (15.1, 6.4, 8.7)),
    box('body_1', 'body', (SPLIT, 6.2, 7.1), (12.6, 8.6, 8.9)),
    box('body_2', 'body', (6.2, 6.25, 7.15), (SPLIT, 8.55, 8.85)),
    box('body_3', 'body', (3.4, 6.4, 7.25), (6.2, 8.4, 8.75)),
    box('fin_1', 'fin', (SPLIT, 8.6, 8), (12.0, 9.2, 8), SIDES),
    box('fin_2', 'fin', (6.2, 8.55, 8), (SPLIT, 9.3, 8), SIDES),
    box('fin_3', 'fin', (3.4, 8.4, 8), (6.2, 9.3, 8), SIDES),
    box('anal', 'fin', (3.4, 5.8, 8), (7.6, 6.4, 8), SIDES),
]
tail = [box('tail_body', 'body', (0.8, 6.6, 7.4), (3.4, 8.2, 8.6)),
        box('tail_fin', 'tailfin', (0.0, 5.9, 8), (3.4, 9.2, 8), SIDES)]

COPPER, COPPER_D, COPPER_L = (184, 104, 60), (120, 62, 36), (226, 152, 102)
BELLY, VERD, FIN = (214, 170, 130), (88, 170, 140), (150, 84, 50)
random.seed(33)
CELLS = [(random.uniform(0.5, 15), random.uniform(6.0, 8.8), random.uniform(0.25, 0.45)) for _ in range(70)]
VERDS = [(random.uniform(1, 14), random.uniform(6.4, 8.4)) for _ in range(14)]

def reticulate(x, y):
    # light copper blotches separated by a dark net
    best = min(((x - cx) ** 2 + (y - cy) ** 2) / (r * r) for cx, cy, r in CELLS)
    return best < 0.55

def paint(role, f, x, y, z):
    if role == 'tailfin':
        if y > 8.2 or y < 6.6 or x < 0.8:   # the fin around the tail tip, a pointed paddle
            top, bot = 9.2 - (3.4 - x) * 0.05, 5.9 + (3.4 - x) * 0.12
            if y > top or y < bot or (x < 0.4 and abs(y - 7.4) > 0.9): return None
            return FIN if int(x * 3) % 3 else COPPER_D
        return None
    if role == 'fin':
        return mix(FIN, COPPER_D, 0.5) if int(x * 3) % 4 == 0 else FIN
    if role == 'jaw':
        if f in SIDES and y > 6.2 and int(x * 4) % 2 == 0: return (236, 228, 206)   # little teeth along the jaw
        return mix(BELLY, COPPER, 0.3)
    if f == 'down': return BELLY
    if role == 'head' and f in SIDES:
        if (x - 14.0) ** 2 + (y - 7.9) ** 2 < 0.06: return (12, 10, 10)
        if (x - 14.0) ** 2 + (y - 7.9) ** 2 < 0.16: return (214, 196, 120)
        if x > 13.1 and abs(y - 6.55) < 0.12: return (60, 22, 18)                    # gape
    top, bot = 8.6, 6.2
    t = (y - bot) / (top - bot)
    c = COPPER_L if reticulate(x, y if f in SIDES else 6.2 + (z - 7.1)) else COPPER_D
    c = mix(mix(BELLY, c, 0.55), c, t * 1.6)
    if f in SIDES and any((x - vx) ** 2 + (y - vy) ** 2 < 0.03 for vx, vy in VERDS): c = VERD
    if f == 'up': c = mix(c, COPPER_D, 0.35)
    return c

cred = ("Jake's Ore Fish Farm: shared 'moray' fish model (moray eel: thick body, toothy jaw, one long fin along the "
        "back); side view, head toward +x. Articulated: moray_front holds still, moray swings, moray_tail wags. "
        "A fish uses it via fish/<fish>.json (parent moray), <fish>_front.json and <fish>_tail.json.")
D = f'{OFF}/models/fish_category'
tex = build('moray', body, tail, paint, cred, D, f'{OFF}/textures/item/fish/cuprum_eel_moray.png', 'orefishfarm:item/fish/cuprum_eel_moray')
m = json.load(open(f'{D}/moray.json'))
front = {k: v for k, v in m.items() if k != 'elements'}
front['elements'] = [e for e in m['elements'] if e['name'] in FRONT]
m['elements'] = [e for e in m['elements'] if e['name'] not in FRONT]
json.dump(front, open(f'{D}/moray_front.json', 'w'), indent=1)
json.dump(m, open(f'{D}/moray.json', 'w'), indent=1)
side = side_view(body, tail, paint)
side.save(f'{PREV}/moray_side.png')
sp = sprite(side); sp.save(f'{OFF}/textures/item/cuprum_eel_moray.png')
sp.resize((256, 256), Image.NEAREST).save(f'{PREV}/moray_sprite.png')
print('moray ok')
