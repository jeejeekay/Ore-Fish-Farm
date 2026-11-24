"""The 'lampfish' shared shape (Quicreate's Red Lampfish, a hybrid): a lanternfish. Big blunt head with a huge eye and
a wide jaw, slim body, a small spiny dorsal and an adipose fin, a forked tail. The body is part see-through, and rows of
photophores (a belly row and a broken band along the side) glow: they are separate thin strips just outside the body,
marked emissive with Forge's element "forge_data" (block and sky light 15), so they light up in the dark.
Articulated: lampfish_front holds still, lampfish swings.
Run: python tools/gen_lampfish.py <orefishfarm assets> <quicreate assets> <preview dir>"""
import json, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from gen_fish_shape import build, side_view, sprite
from PIL import Image

OFF, QC, PREV = sys.argv[1], sys.argv[2], sys.argv[3]
SIDES = ['north', 'south']
ALL = ['north', 'south', 'east', 'west', 'up', 'down']
def box(name, role, a, b, faces=ALL): return dict(name=name, role=role, **{'from': list(a), 'to': list(b)}, faces=faces)
def inside(poly, x, y):
    n, c = len(poly), False
    for i in range(n):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1): c = not c
    return c
def mix(a, b, t):
    t = max(0.0, min(1.0, t)); return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))

SPLIT = 8.0
FRONT = {'head', 'jaw', 'body_front', 'pectoral_n', 'pectoral_s'}
body = [
    box('head', 'head', (11.4, 5.8, 7.0), (14.4, 8.6, 9.0)),
    box('jaw', 'jaw', (12.8, 5.5, 7.3), (14.9, 6.2, 8.7)),
    box('body_front', 'body', (SPLIT, 6.0, 7.15), (11.4, 8.4, 8.85)),
    box('pectoral_n', 'fin', (9.6, 6.0, 6.7), (10.8, 6.4, 7.15)),
    box('pectoral_s', 'fin', (9.6, 6.0, 8.85), (10.8, 6.4, 9.3)),
    box('body_rear', 'body', (4.4, 6.25, 7.35), (SPLIT, 8.15, 8.65)),
    box('stalk', 'body', (2.6, 6.7, 7.6), (4.4, 7.7, 8.4)),
    box('dorsal', 'dorsal', (5.6, 8.1, 8), (7.8, 9.6, 8), SIDES),
    box('adipose', 'fin', (3.4, 7.7, 7.9), (4.0, 8.1, 8.1)),
    box('anal', 'fin_side', (3.8, 5.6, 8), (6.2, 6.3, 8), SIDES),
]
# glowing strips just outside each side of the body (front and rear halves apart, so they swing with the body)
for half, x0, x1, zn, zs in (('f', SPLIT, 11.2, 7.13, 8.87), ('r', 4.4, SPLIT, 7.33, 8.67)):
    body.append(box(f'glow_low_{half}_n', 'glow_low', (max(x0, 4.6), 6.25, zn), (x1, 6.65, zn), SIDES))
    body.append(box(f'glow_low_{half}_s', 'glow_low', (max(x0, 4.6), 6.25, zs), (x1, 6.65, zs), SIDES))
    body.append(box(f'glow_mid_{half}_n', 'glow_mid', (x0, 7.05, zn), (x1, 7.45, zn), SIDES))
    body.append(box(f'glow_mid_{half}_s', 'glow_mid', (x0, 7.05, zs), (x1, 7.45, zs), SIDES))
    if half == 'f':
        FRONT |= {f'glow_low_{half}_n', f'glow_low_{half}_s', f'glow_mid_{half}_n', f'glow_mid_{half}_s'}
tail = [box('tail', 'tail', (0.2, 5.6, 8), (2.6, 8.8, 8), SIDES)]
TAIL = [(2.6, 7.6), (1.2, 8.8), (0.2, 8.7), (1.0, 7.3), (1.0, 7.0), (0.2, 5.7), (1.2, 5.6), (2.6, 6.8)]   # forked
DORSAL = [(5.6, 8.1), (6.0, 9.2), (6.6, 9.6), (7.2, 9.3), (7.8, 8.1)]

A_BODY, A_FIN = 196, 130
DARK, MID, BELLY = (34, 10, 14), (58, 16, 22), (96, 34, 40)
RED, RED_HI = (255, 52, 44), (255, 150, 120)

def paint(role, f, x, y, z):
    if role == 'glow_low':   # a row of round photophores, one every 0.8
        return RED + (255,) if (x * 1.25) % 1.0 < 0.45 else None
    if role == 'glow_mid':   # a broken lateral band: dashes, brightest in the middle of each
        p = (x * 1.6) % 1.0
        if p > 0.7: return None
        return (RED_HI if 0.2 < p < 0.5 else RED) + (255,)
    if role == 'tail':
        if not inside(TAIL, x, y): return None
        return mix(MID, DARK, abs(y - 7.2) / 1.6) + (A_FIN,)
    if role == 'dorsal':
        if not inside(DORSAL, x, y): return None
        return ((80, 24, 28) if int(x * 3) % 2 else MID) + (A_FIN,)
    if role in ('fin', 'fin_side'): return MID + (A_FIN,)
    if role == 'jaw':
        if f == 'down': return BELLY + (A_BODY,)
        return ((150, 40, 44) if y > 6.0 else MID) + (A_BODY,)
    if f == 'up': return DARK + (A_BODY,)
    if f == 'down': return BELLY + (A_BODY,)
    top, bot = (8.6, 5.8) if role == 'head' else (8.4, 6.0)
    t = (y - bot) / max(0.1, top - bot)
    c = mix(BELLY, MID, t * 2) if t < 0.5 else mix(MID, DARK, (t - 0.5) * 2)
    if role == 'head' and f in SIDES:
        d = (x - 13.3) ** 2 + (y - 7.5) ** 2
        if d < 0.12: return (6, 4, 6, 255)                  # pupil
        if d < 0.42: return (190, 170, 176, 235)            # big silvery eye
        if d < 0.55: return (120, 40, 44, 230)
        if 11.5 < x < 11.9 and 6.2 < y < 8.1: return (80, 22, 26, A_BODY)   # gill edge
    if role == 'head' and f == 'east' and y < 6.3: return (120, 30, 34, A_BODY)
    return c + (A_BODY,)

cred = ("Jake's Ore Fish Farm: shared 'lampfish' fish model (lanternfish: big eye, wide jaw, glowing photophore strips "
        "marked emissive with forge_data); side view, head toward +x. Articulated: lampfish_front holds still, lampfish swings. "
        "A fish uses it via fish/<fish>.json (parent lampfish), <fish>_front.json (parent lampfish_front) and <fish>_tail.json.")
os.makedirs(f'{QC}/textures/item/fish', exist_ok=True)
D = f'{OFF}/models/fish_category'
tex = build('lampfish', body, tail, paint, cred, D, f'{QC}/textures/item/fish/red_lampfish.png', 'quicreate:item/fish/red_lampfish')
m = json.load(open(f'{D}/lampfish.json'))
for e in m['elements']:
    if e['name'].startswith('glow'): e['forge_data'] = {'block_light': 15, 'sky_light': 15}
front = {k: v for k, v in m.items() if k != 'elements'}
front['elements'] = [e for e in m['elements'] if e['name'] in FRONT]
m['elements'] = [e for e in m['elements'] if e['name'] not in FRONT]
json.dump(front, open(f'{D}/lampfish_front.json', 'w'), indent=1)
json.dump(m, open(f'{D}/lampfish.json', 'w'), indent=1)
side = side_view(body, tail, paint)
side.save(f'{PREV}/lampfish_side.png')
sp = sprite(side); sp.save(f'{QC}/textures/item/red_lampfish.png')
sp.resize((256, 256), Image.NEAREST).save(f'{PREV}/lampfish_sprite.png')
tex.resize((256, 256), Image.NEAREST).save(f'{PREV}/lampfish_tex.png')
print('lampfish ok')
