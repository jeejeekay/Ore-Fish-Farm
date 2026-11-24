"""The 'barracuda' shared shape (Ore Fish Farm's Barkacuda): a long, slim barracuda. A pointed head with a jutting
lower jaw, a small spiny front dorsal and a matching rear dorsal and anal fin set far back, a slim stalk and a forked
tail. Bark: a patchwork of dark tree barks (dark oak, spruce, mangrove, jungle, cherry) with darker grain lines,
a paler belly and a yellow eye. Also writes the eight bark scale sprites.
Articulated: barracuda_front holds still, barracuda swings, barracuda_tail wags.
Run: python tools/gen_barkacuda.py <orefishfarm assets> <preview dir>"""
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
def inside(poly, x, y):
    n, c = len(poly), False
    for i in range(n):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1): c = not c
    return c

SPLIT = 8.0
FRONT = {'head', 'jaw', 'body_front', 'dorsal_1', 'pectoral_n', 'pectoral_s'}
body = [
    box('head', 'head', (12.2, 6.7, 7.25), (15.4, 8.0, 8.75)),
    box('jaw', 'jaw', (12.6, 6.3, 7.4), (15.9, 6.7, 8.6)),
    box('body_front', 'body', (SPLIT, 6.0, 6.9), (12.2, 8.4, 9.1)),
    box('dorsal_1', 'fin_side', (9.0, 8.4, 8), (10.6, 9.6, 8), SIDES),
    box('pectoral_n', 'fin', (10.6, 6.1, 6.6), (11.8, 6.4, 6.9)),
    box('pectoral_s', 'fin', (10.6, 6.1, 9.1), (11.8, 6.4, 9.4)),
    box('body_rear', 'body', (3.6, 6.2, 7.0), (SPLIT, 8.2, 9.0)),
    box('stalk', 'body', (2.0, 6.7, 7.5), (3.6, 7.7, 8.5)),
    box('dorsal_2', 'fin_side', (4.4, 8.2, 8), (5.6, 9.2, 8), SIDES),
    box('anal', 'fin_side', (4.4, 5.4, 8), (5.6, 6.2, 8), SIDES),
]
tail = [box('tail', 'tail', (0.0, 4.8, 8), (2.0, 9.6, 8), SIDES)]
TAIL = [(2.0, 7.7), (0.8, 8.7), (0.0, 9.6), (0.5, 8.3), (1.1, 7.2), (0.5, 6.1), (0.0, 4.8), (0.8, 5.7), (2.0, 6.7)]
DORSAL_1 = [(9.0, 8.4), (9.2, 9.6), (9.8, 9.3), (10.6, 8.4)]
DORSAL_2 = [(4.4, 8.2), (4.7, 9.2), (5.6, 8.2)]
ANAL = [(4.4, 6.2), (4.7, 5.4), (5.6, 6.2)]

BARKS = [(60, 46, 26), (58, 37, 16), (84, 62, 38), (86, 68, 28), (64, 38, 48), (72, 54, 32)]   # dark oak, spruce, mangrove, jungle, cherry, oak-ish
GRAIN, BELLY, EYE = (34, 24, 14), (150, 130, 98), (226, 196, 60)
random.seed(77)
CELL = {}
def bark(x, y):
    k = (int(x * 2.2), int(y * 2.2))
    if k not in CELL: CELL[k] = random.choice(BARKS)
    return CELL[k]

def paint(role, f, x, y, z):
    if role == 'tail':
        if not inside(TAIL, x, y): return None
        return mix(BARKS[0], GRAIN, 0.4) if abs(y - 7.2) > 1.2 else BARKS[1]
    if role == 'fin_side':
        if not any(inside(P, x, y) for P in (DORSAL_1, DORSAL_2, ANAL)): return None
        return mix(BARKS[1], GRAIN, 0.3)
    if role == 'fin': return mix(BARKS[2], GRAIN, 0.3)
    if role == 'jaw': return BELLY if f != 'up' else mix(BELLY, GRAIN, 0.4)
    if f == 'down': return BELLY
    c = bark(x, y if f in SIDES else z + 3)
    top, bot = (8.0, 6.7) if role == 'head' else ((8.4, 6.0) if x >= SPLIT else (8.2, 6.2))
    t = (y - bot) / max(0.1, top - bot)
    if f in SIDES and t < 0.3: c = mix(BELLY, c, t / 0.3)               # pale belly
    if f in SIDES and role != 'head' and abs((x * 3.1 + (y * 1.7) % 0.9) % 2.0 - 1.0) < 0.12: c = mix(c, GRAIN, 0.7)  # bark grain
    if role == 'head' and f in SIDES:
        d = (x - 14.0) ** 2 + (y - 7.5) ** 2
        if d < 0.04: return (12, 10, 6)
        if d < 0.13: return EYE
        if x > 13.0 and abs(y - 6.75) < 0.08: return GRAIN
    return c

cred = ("Jake's Ore Fish Farm: shared 'barracuda' fish model (long slim body, jutting jaw, two small dorsals, forked "
        "tail); side view, head toward +x. Articulated: barracuda_front holds still, barracuda swings, barracuda_tail wags.")
D = f'{OFF}/models/fish_category'
build('barracuda', body, tail, paint, cred, D, f'{OFF}/textures/item/fish/barkacuda.png', 'orefishfarm:item/fish/barkacuda')
m = json.load(open(f'{D}/barracuda.json'))
front = {k: v for k, v in m.items() if k != 'elements'}
front['elements'] = [e for e in m['elements'] if e['name'] in FRONT]
m['elements'] = [e for e in m['elements'] if e['name'] not in FRONT]
json.dump(front, open(f'{D}/barracuda_front.json', 'w'), indent=1)
json.dump(m, open(f'{D}/barracuda.json', 'w'), indent=1)
side = side_view(body, tail, paint)
side.save(f'{PREV}/barkacuda_side.png')
sprite(side).save(f'{OFF}/textures/item/barkacuda.png')

# Bark scales: the iron scale's shape, recoloured along each bark's dark -> light ramp.
RAMPS = {'oak': ((70, 54, 32), (152, 120, 74)), 'spruce': ((38, 24, 10), (104, 74, 40)), 'birch': ((70, 68, 62), (236, 234, 226)),
         'jungle': ((60, 46, 16), (136, 112, 52)), 'acacia': ((68, 62, 56), (148, 140, 128)), 'dark_oak': ((36, 26, 14), (100, 78, 46)),
         'mangrove': ((54, 40, 24), (128, 96, 62)), 'cherry': ((40, 22, 32), (110, 70, 90))}
tmpl = Image.open(f'{OFF}/textures/item/iron_scale.png').convert('RGBA')
px = tmpl.load()
lums = [sum(px[x, y][:3]) for y in range(tmpl.height) for x in range(tmpl.width) if px[x, y][3] > 0]
lo, hi = min(lums), max(lums)
for wood, (dark, light) in RAMPS.items():
    out = Image.new('RGBA', tmpl.size); o = out.load()
    for y in range(tmpl.height):
        for x in range(tmpl.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            t = (r + g + b - lo) / max(1, hi - lo)
            c = mix(dark, light, t)
            if wood == 'birch' and (x * 5 + y * 3) % 7 == 0 and 0.2 < t < 0.8: c = (40, 38, 34)   # birch's black flecks
            o[x, y] = c + (a,)
    out.save(f'{OFF}/textures/item/{wood}_bark_scale.png')
print('barracuda ok')
