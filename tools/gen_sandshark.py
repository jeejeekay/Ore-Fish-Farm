"""The 'shark' shared shape (replaces the old 'ray'; Quicreate's Sandshark): a nurse shark. Broad flat head with
barbels and small eyes, stocky body, big flat pectorals, two dorsal fins set far back, a long upper tail lobe.
Articulated: shark_front (head, front body, pectorals) holds still, shark (rear body and fins) swings.
Sandy tan with pale speckles, cream belly.
Run: python tools/gen_sandshark.py <orefishfarm assets> <quicreate assets> <preview dir>"""
import json, random, sys, os
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

FRONT = {'head', 'snout', 'body_front', 'pectoral_n', 'pectoral_s', 'barbel_n', 'barbel_s'}
body = [
    box('head', 'head', (12.2, 5.6, 6.6), (15.0, 7.7, 9.4)),
    box('snout', 'head', (15.0, 5.8, 7.0), (15.7, 7.1, 9.0)),
    box('body_front', 'body', (8.5, 5.4, 6.5), (12.2, 8.4, 9.5)),
    box('pectoral_n', 'fin', (9.4, 5.1, 5.1), (11.8, 5.6, 6.5)),
    box('pectoral_s', 'fin', (9.4, 5.1, 9.5), (11.8, 5.6, 10.9)),
    box('barbel_n', 'barbel', (15.0, 5.2, 7.5), (15.5, 5.8, 7.8)),
    box('barbel_s', 'barbel', (15.0, 5.2, 8.2), (15.5, 5.8, 8.5)),
    box('body_rear', 'body', (4.6, 5.8, 7.0), (8.5, 8.0, 9.0)),
    box('stalk', 'body', (2.6, 6.3, 7.5), (4.6, 7.6, 8.5)),
    box('dorsal_1', 'dorsal', (6.2, 8.0, 8), (8.4, 10.0, 8), SIDES),
    box('dorsal_2', 'dorsal', (3.8, 7.6, 8), (5.6, 9.2, 8), SIDES),
    box('pelvic', 'fin_side', (6.4, 5.1, 8), (7.6, 5.8, 8), SIDES),
    box('anal', 'fin_side', (3.6, 5.6, 8), (4.6, 6.3, 8), SIDES),
]
tail = [box('tail', 'tail', (0.0, 5.6, 8), (2.6, 8.8, 8), SIDES)]
# heterocercal: a long upper lobe sweeping back and up, a short lower lobe
TAIL = [(2.6, 7.6), (1.8, 8.0), (0.6, 8.6), (0.0, 8.7), (0.4, 8.1), (1.0, 7.1), (1.4, 6.4), (1.2, 5.7), (2.0, 5.9), (2.6, 6.3)]
D1 = [(6.2, 8.0), (6.9, 9.3), (7.4, 10.0), (7.9, 9.9), (8.1, 9.2), (8.4, 8.0)]
D2 = [(3.8, 7.6), (4.3, 8.6), (4.8, 9.2), (5.2, 9.1), (5.4, 8.5), (5.6, 7.6)]

BACK, SIDE, BELLY = (168, 138, 92), (196, 168, 118), (230, 218, 188)
SPECK, FIN, FIN_EDGE = (222, 204, 160), (172, 140, 94), (138, 108, 70)
random.seed(21)
SPECKS = [(random.uniform(3.0, 14.6), random.uniform(6.2, 8.2)) for _ in range(30)]

def paint(role, f, x, y, z):
    if role == 'tail':
        if not inside(TAIL, x, y): return None
        return FIN_EDGE if (x < 0.5 or y > 8.4) else FIN
    if role == 'dorsal':
        poly = D1 if x > 6 else D2
        if not inside(poly, x, y): return None
        return FIN_EDGE if y > (9.6 if x > 6 else 8.9) else FIN
    if role == 'fin_side': return FIN
    if role == 'fin':  # flat pectorals: tan top, pale underneath, darker trailing edge
        if f == 'down': return BELLY
        return FIN_EDGE if x < 9.8 else FIN
    if role == 'barbel': return (150, 120, 82)
    if f == 'up': return SPECK if any((x - sx) ** 2 + (z - 8 - (sy - 7.2)) ** 2 < 0.05 for sx, sy in SPECKS[:12]) else BACK
    if f == 'down': return BELLY
    top, bot = (7.7, 5.6) if role == 'head' else (8.4, 5.4)
    t = (y - bot) / max(0.1, top - bot)
    c = mix(BELLY, SIDE, t * 2.2) if t < 0.45 else mix(SIDE, BACK, (t - 0.45) * 1.8)
    if role == 'head' and f in SIDES:
        if (x - 13.9) ** 2 + (y - 7.15) ** 2 < 0.05: return (24, 20, 18)            # small eye
        if x > 14.2 and abs(y - 6.0) < 0.14: return (92, 70, 50)                       # mouth under the snout
        if 12.3 < x < 12.9 and 6.2 < y < 7.4 and int(x * 6) % 2 == 0: return (150, 122, 84)   # gill slits
    if role == 'head' and f == 'east' and abs(y - 6.1) < 0.14: return (92, 70, 50)
    if f in SIDES and t > 0.35 and any((x - sx) ** 2 + (y - sy) ** 2 < 0.035 for sx, sy in SPECKS): c = SPECK
    return c

cred = ("Jake's Ore Fish Farm: shared 'shark' fish model (nurse shark: broad flat head, barbels, two rear dorsals, long "
        "upper tail lobe); side view, head toward +x. Articulated: shark_front holds still, shark swings. A fish uses "
        "it with its own texture via fish/<fish>.json (parent shark), <fish>_front.json (parent shark_front) and "
        "<fish>_tail.json (parent shark_tail).")
os.makedirs(f'{QC}/textures/item/fish', exist_ok=True)
D = f'{OFF}/models/fish_category'
tex = build('shark', body, tail, paint, cred, D, f'{QC}/textures/item/fish/sandshark.png', 'quicreate:item/fish/sandshark')
m = json.load(open(f'{D}/shark.json'))
front = {k: v for k, v in m.items() if k != 'elements'}
front['elements'] = [e for e in m['elements'] if e['name'] in FRONT]
m['elements'] = [e for e in m['elements'] if e['name'] not in FRONT]
json.dump(front, open(f'{D}/shark_front.json', 'w'), indent=1)
json.dump(m, open(f'{D}/shark.json', 'w'), indent=1)
side = side_view(body, tail, paint)
side.save(f'{PREV}/sandshark_side.png')
sp = sprite(side); sp.save(f'{QC}/textures/item/sandshark.png')
sp.resize((256, 256), Image.NEAREST).save(f'{PREV}/sandshark_sprite.png')
tex.resize((256, 256), Image.NEAREST).save(f'{PREV}/sandshark_tex.png')
print('shark ok')
