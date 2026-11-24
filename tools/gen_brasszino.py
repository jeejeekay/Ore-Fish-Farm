"""The 'salminus' shared shape (Ore Fish Farm's Brasszino): a golden dorado. Long and deep-chested like a salmon,
a big bony head with a strong underslung jaw, gold with rows of small dark dots along the scale rows, an olive back,
and an orange-red forked tail with a black stripe through it. Articulated: salminus_front holds still, salminus swings.
Run: python tools/gen_brasszino.py <orefishfarm assets> <preview dir>"""
import json, sys, os
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

SPLIT = 8.4
FRONT = {'head', 'jaw', 'body_front', 'dorsal', 'pectoral_n', 'pectoral_s'}
body = [
    box('head', 'head', (11.8, 5.7, 6.9), (14.6, 8.4, 9.1)),
    box('jaw', 'jaw', (12.4, 5.2, 7.2), (15.2, 5.9, 8.8)),
    box('body_front', 'body', (SPLIT, 5.4, 6.8), (11.8, 8.8, 9.2)),
    box('pectoral_n', 'fin', (10.4, 5.4, 6.3), (11.6, 5.9, 6.8)),
    box('pectoral_s', 'fin', (10.4, 5.4, 9.2), (11.6, 5.9, 9.7)),
    box('dorsal', 'dorsal', (8.6, 8.8, 8), (10.6, 10.6, 8), SIDES),
    box('body_rear', 'body', (4.6, 5.8, 7.1), (SPLIT, 8.4, 8.9)),
    box('stalk', 'body', (2.8, 6.4, 7.5), (4.6, 7.6, 8.5)),
    box('adipose', 'fin', (3.8, 7.6, 7.85), (4.5, 8.1, 8.15)),
    box('pelvic', 'fin_side', (7.0, 5.0, 8), (8.2, 5.8, 8), SIDES),
    box('anal', 'fin_side', (4.4, 5.2, 8), (6.4, 6.0, 8), SIDES),
]
tail = [box('tail', 'tail', (0.0, 4.8, 8), (2.8, 9.2, 8), SIDES)]
TAIL = [(2.8, 7.6), (1.4, 8.6), (0.0, 9.2), (0.8, 7.0), (0.0, 4.8), (1.4, 5.4), (2.8, 6.4)]
DORSAL = [(8.6, 8.8), (9.0, 10.2), (9.6, 10.6), (10.2, 10.0), (10.6, 8.8)]

GOLD, GOLD_L, GOLD_D, OLIVE = (232, 184, 46), (248, 214, 96), (196, 140, 28), (122, 112, 40)
BELLY, DOT, RED, RED_D = (246, 232, 170), (44, 34, 18), (236, 96, 34), (186, 58, 22)

def paint(role, f, x, y, z):
    if role == 'tail':
        if not inside(TAIL, x, y): return None
        if abs(y - 7.0) < 0.3 and x < 2.4: return (30, 22, 14)        # black stripe through the middle
        return RED if x > 1.0 else RED_D
    if role == 'dorsal':
        if not inside(DORSAL, x, y): return None
        return RED_D if y > 10.0 else mix(GOLD_D, RED, 0.4)
    if role in ('fin', 'fin_side'): return mix(GOLD, RED, 0.55)
    if role == 'jaw':
        if f == 'down': return BELLY
        if f in SIDES and y > 5.75: return (70, 50, 24)                # mouth line
        return mix(GOLD_L, BELLY, 0.4)
    if f == 'up': return OLIVE
    if f == 'down': return BELLY
    top, bot = (8.4, 5.7) if role == 'head' else ((8.8, 5.4) if x >= SPLIT else (8.4, 5.8))
    t = (y - bot) / max(0.1, top - bot)
    c = mix(BELLY, GOLD_L, t * 2.5) if t < 0.4 else (mix(GOLD_L, GOLD, (t - 0.4) * 3) if t < 0.75 else mix(GOLD, OLIVE, (t - 0.75) * 4))
    if role == 'head' and f in SIDES:
        d = (x - 13.4) ** 2 + (y - 7.5) ** 2
        if d < 0.06: return (16, 12, 8)
        if d < 0.2: return (220, 200, 120)
        if 11.9 < x < 12.25 and 5.9 < y < 8.2: return GOLD_D       # gill cover edge
        return mix(c, GOLD_D, 0.25)
    if f in SIDES and role == 'body' and 0.18 < t < 0.85:
        # rows of small dark dots along the scale rows
        row = (y - bot) / 0.42
        if abs(row - round(row)) < 0.22 and int((x + round(row) * 0.3) * 2.2) % 2 == 0: return DOT
    return c

cred = ("Jake's Ore Fish Farm: shared 'salminus' fish model (golden dorado: deep body, big jaw, dotted scale rows, "
        "red forked tail); side view, head toward +x. Articulated: salminus_front holds still, salminus swings.")
D = f'{OFF}/models/fish_category'
tex = build('salminus', body, tail, paint, cred, D, f'{OFF}/textures/item/fish/brasszino.png', 'orefishfarm:item/fish/brasszino')
m = json.load(open(f'{D}/salminus.json'))
front = {k: v for k, v in m.items() if k != 'elements'}
front['elements'] = [e for e in m['elements'] if e['name'] in FRONT]
m['elements'] = [e for e in m['elements'] if e['name'] not in FRONT]
json.dump(front, open(f'{D}/salminus_front.json', 'w'), indent=1)
json.dump(m, open(f'{D}/salminus.json', 'w'), indent=1)
side = side_view(body, tail, paint)
side.save(f'{PREV}/brasszino_side.png')
sp = sprite(side); sp.save(f'{OFF}/textures/item/brasszino.png')
sp.resize((256, 256), Image.NEAREST).save(f'{PREV}/brasszino_sprite.png')
print('salminus ok')
