"""ALTERNATIVE look, not in use (Jake kept the first texture and only had its head restyled): the Ironpaima painted
in the same style as the Sturginc and Dorada (shading from a dark back to a pale belly,
sheen, a white-ringed eye), on the 'paima' shape (boxes unchanged from tools/gen_paima_model.py). Iron greys, with
the arapaima's red creeping in toward the tail as rust, and red-edged scales on the rear half.
Run: python tools/gen_ironpaima.py <assets/orefishfarm> <preview dir>"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from gen_fish_shape import build, side_view, sprite
from PIL import Image

A, PREV = sys.argv[1], sys.argv[2]
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

body = [
    dict(name='body', role='body', **{'from': [1.5, 6, 6.8], 'to': [9.8, 8.9, 9.2]}, faces=ALL),
    dict(name='head', role='head', **{'from': [9.8, 6.2, 7.1], 'to': [14.5, 7.75, 8.9]}, faces=['north', 'south', 'up', 'down', 'east']),
    dict(name='dorsal', role='dorsal', **{'from': [1.9, 8.9, 7.6], 'to': [6.4, 9.7, 8.4]}, faces=['north', 'south', 'up', 'east', 'west']),
    # lower fins in pairs, one each side of the belly
    dict(name='rear_fin_n', role='fin', **{'from': [2.8, 5.6, 6.5], 'to': [3.7, 6.2, 7.1]}, faces=ALL),
    dict(name='rear_fin_s', role='fin', **{'from': [2.8, 5.6, 8.9], 'to': [3.7, 6.2, 9.5]}, faces=ALL),
    dict(name='front_fin_n', role='fin', **{'from': [8.2, 5.5, 6.4], 'to': [9.4, 6.25, 7.0]}, faces=ALL),
    dict(name='front_fin_s', role='fin', **{'from': [8.2, 5.5, 9.0], 'to': [9.4, 6.25, 9.6]}, faces=ALL),
]
tail = [dict(name='tail', role='tail', **{'from': [0, 6.1, 8], 'to': [1.6, 8.8, 8]}, faces=SIDES)]
TAIL = [(1.6, 8.2), (0.9, 8.8), (0.2, 8.4), (0.0, 7.45), (0.2, 6.5), (0.9, 6.1), (1.6, 6.7)]  # rounded paddle

BACK, SIDE, BELLY = (84, 90, 98), (158, 162, 168), (218, 220, 222)
RUST, RUST_D = (182, 96, 58), (128, 62, 40)

def rust(x):
    """How rusty the fish is at x: none at the front, building toward the tail."""
    return max(0.0, min(1.0, (6.8 - x) / 5.0))

def paint(role, f, x, y, z):
    r = rust(x)
    if role == 'tail':
        if not inside(TAIL, x, y): return None
        c = mix((150, 84, 56), RUST_D, 0.4 if abs(y - 7.45) > 0.9 else 0.0)
        return RUST_D if x < 0.35 else c
    if role == 'dorsal':
        c = mix((96, 100, 108), RUST_D, r * 0.9)
        return mix(c, (60, 40, 32), 0.3) if f == 'up' else c
    if role == 'fin':
        return mix((112, 116, 124), RUST, r * 0.6)
    if role == 'head':
        if f in SIDES and abs(x - 13.3) < 0.28 and abs(y - 7.25) < 0.26: return (16, 16, 20)            # pupil
        if f in SIDES and abs(x - 13.3) < 0.52 and abs(y - 7.25) < 0.42: return (226, 224, 210)         # eye ring
        if f in SIDES and x > 13.7 and abs(y - 6.65) < 0.14: return (44, 44, 50)                        # mouth line
        if f == 'down': return BELLY
        if f == 'up': return (96, 102, 110)
        t = (y - 6.2) / 1.55
        return mix((176, 180, 186), (108, 114, 122), t)
    # body: dark back to pale belly like the Sturginc, iron sheen, rust and red-edged scales toward the tail
    if f == 'up': return mix(BACK, RUST_D, r * 0.7)
    if f == 'down': return mix(BELLY, (214, 176, 156), r * 0.6)
    if f in ('east', 'west'): return mix(SIDE, RUST, r * 0.5)
    t = (y - 6.0) / 2.9
    c = mix(BELLY, SIDE, t * 2.2) if t < 0.5 else mix(SIDE, BACK, (t - 0.5) * 2)
    # scales as flecks on the texture's own pixels (like the Sturginc): iron sheen at the front, rusty scale
    # edges building toward the tail
    ix, iy = int(x * 2), int(y * 2)
    c = mix(c, RUST, r * 0.55)
    if f in SIDES and 0.15 < t < 0.9 and r > 0.15 and (ix + iy) % 3 == 0: c = mix(c, RUST_D, 0.25 + 0.4 * r)
    elif f in SIDES and 0.3 < t < 0.85 and (ix + 2 * iy) % 5 == 0: c = mix(c, (236, 238, 242), 0.5 * (1 - r))
    return c

cred = ("Jake's Ore Fish Farm: shared 'paima_painted' fish model (the paima shape with the shape tool's UV layout), an arapaima (long rectangular body, low dorsal ridge on the rear back, "
        "small head, two belly fins, rounded tail; shape from Jake's drawing); side view, head toward +x. A fish uses it with its "
        "own texture: {\"parent\": \"orefishfarm:fish_category/paima\", \"textures\": {\"t\": ..., \"particle\": ...}}")
tex = build('paima_painted', body, tail, paint, cred, f'{A}/models/fish_category', f'{A}/textures/item/fish/ironpaima_painted.png', 'orefishfarm:item/fish/ironpaima_painted')
side = side_view(body, tail, paint)
side.save(f'{PREV}/ironpaima_side.png')
sp = sprite(side)  # preview only: the item sprite in use is the first one
sp.resize((256, 256), Image.NEAREST).save(f'{PREV}/ironpaima_sprite.png')
tex.resize((256, 256), Image.NEAREST).save(f'{PREV}/ironpaima_tex.png')
print('ironpaima ok')
