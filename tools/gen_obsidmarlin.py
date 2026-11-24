"""The 'marlin' shared shape (Ore Fish Farm's Obsidmarlin, formerly the Obsidian Fish): a marlin. A long spear bill,
a tall pointed sail of a dorsal, long narrow pectorals, a slim tail stalk and a tall crescent tail. Obsidian: glassy
black-purple with lilac glints, pale purple vertical bars down the sides, a lighter belly.
Articulated: marlin_front holds still, marlin swings, marlin_tail wags.
Run: python tools/gen_obsidmarlin.py <orefishfarm assets> <preview dir>"""
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
FRONT = {'bill', 'head', 'body_front', 'sail', 'pectoral_n', 'pectoral_s'}
body = [
    box('bill', 'bill', (13.6, 7.0, 7.8), (15.9, 7.4, 8.2)),
    box('head', 'head', (11.8, 6.0, 7.0), (13.6, 8.2, 9.0)),
    box('body_front', 'body', (SPLIT, 5.6, 6.8), (11.8, 8.8, 9.2)),
    box('sail', 'sail', (8.2, 8.8, 8), (11.4, 11.4, 8), SIDES),
    box('pectoral_n', 'fin', (9.4, 5.4, 6.4), (11.4, 5.8, 6.8)),
    box('pectoral_s', 'fin', (9.4, 5.4, 9.2), (11.4, 5.8, 9.6)),
    box('body_rear', 'body', (4.4, 6.0, 7.1), (SPLIT, 8.4, 8.9)),
    box('stalk', 'body', (2.4, 6.6, 7.6), (4.4, 7.6, 8.4)),
    box('dorsal_2', 'fin_side', (3.8, 7.6, 8), (4.8, 8.4, 8), SIDES),
    box('anal', 'fin_side', (5.0, 5.3, 8), (7.2, 6.0, 8), SIDES),
]
tail = [box('tail', 'tail', (0.0, 4.4, 8), (2.4, 9.8, 8), SIDES)]
TAIL = [(2.4, 7.6), (1.2, 8.8), (0.0, 9.8), (0.6, 8.4), (1.0, 7.1), (0.6, 5.8), (0.0, 4.4), (1.2, 5.4), (2.4, 6.6)]   # crescent
SAIL = [(8.2, 8.8), (8.6, 10.6), (9.0, 11.4), (9.6, 10.6), (10.4, 9.6), (11.4, 8.8)]       # tall at the front, sweeping back

BLACK, GLASS, LILAC, BELLY = (18, 12, 30), (52, 30, 88), (150, 104, 214), (74, 60, 100)
BAR = (104, 72, 156)
random.seed(41)
GLINTS = [(random.uniform(2.5, 13.5), random.uniform(5.8, 8.8)) for _ in range(26)]

def paint(role, f, x, y, z):
    if role == 'tail':
        if not inside(TAIL, x, y): return None
        return GLASS if (x > 1.6 or abs(y - 7.1) < 0.5) else BLACK
    if role == 'sail':
        if not inside(SAIL, x, y): return None
        if (int(x * 2.5) + int(y * 2.5)) % 5 == 0: return LILAC                    # lilac spots on the sail
        return mix(GLASS, BLACK, (y - 8.8) / 2.6)
    if role in ('fin', 'fin_side'): return mix(GLASS, BLACK, 0.4)
    if role == 'bill': return BLACK if f != 'up' else GLASS
    if f == 'up': return BLACK
    if f == 'down': return BELLY
    top, bot = (8.2, 6.0) if role == 'head' else ((8.8, 5.6) if x >= SPLIT else (8.4, 6.0))
    t = (y - bot) / max(0.1, top - bot)
    c = mix(BELLY, GLASS, t * 2.2) if t < 0.45 else mix(GLASS, BLACK, (t - 0.45) * 1.8)
    if role == 'head' and f in SIDES:
        d = (x - 12.9) ** 2 + (y - 7.5) ** 2
        if d < 0.05: return (8, 6, 12)
        if d < 0.16: return (196, 160, 236)
        if x > 13.0 and abs(y - 6.7) < 0.1: return BLACK                           # mouth line under the bill
    if f in SIDES and role == 'body' and 0.25 < t < 0.85 and int(x * 1.4) % 3 == 0:
        c = mix(c, BAR, 0.6)                                                     # pale vertical bars
    if f in SIDES and any((x - gx) ** 2 + (y - gy) ** 2 < 0.02 for gx, gy in GLINTS): c = LILAC  # obsidian glints
    return c

cred = ("Jake's Ore Fish Farm: shared 'marlin' fish model (spear bill, tall sail, crescent tail); side view, head toward "
        "+x. Articulated: marlin_front holds still, marlin swings, marlin_tail wags.")
D = f'{OFF}/models/fish_category'
tex = build('marlin', body, tail, paint, cred, D, f'{OFF}/textures/item/fish/obsidmarlin.png', 'orefishfarm:item/fish/obsidmarlin')
m = json.load(open(f'{D}/marlin.json'))
front = {k: v for k, v in m.items() if k != 'elements'}
front['elements'] = [e for e in m['elements'] if e['name'] in FRONT]
m['elements'] = [e for e in m['elements'] if e['name'] not in FRONT]
json.dump(front, open(f'{D}/marlin_front.json', 'w'), indent=1)
json.dump(m, open(f'{D}/marlin.json', 'w'), indent=1)
side = side_view(body, tail, paint)
side.save(f'{PREV}/obsidmarlin_side.png')
sp = sprite(side); sp.save(f'{OFF}/textures/item/obsidmarlin.png')
print('marlin ok')
