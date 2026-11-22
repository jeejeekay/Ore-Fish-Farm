"""The Sturginc (sturgeon shape) and Dorada (mahi-mahi shape). Run: python tools/fish_shapes.py <assets/orefishfarm> <preview dir>"""
import math, random, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from gen_fish_shape import build, side_view, sprite
from PIL import Image

A, PREV = sys.argv[1], sys.argv[2]
SIDES = ['north', 'south']
ALL = ['north', 'south', 'east', 'west', 'up', 'down']

def mix(a, b, t):
    t = max(0.0, min(1.0, t)); return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

def inside(poly, x, y):
    c = False
    for i in range(len(poly)):
        (x1, y1), (x2, y2) = poly[i], poly[i - 1]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1: c = not c
    return c

# ---------------------------------------------------------------- Sturginc: a sturgeon in zinc
st_body = [
    dict(name='body_front', role='body', **{'from': [8, 6.2, 6.9], 'to': [12, 9.2, 9.1]}, faces=ALL),
    dict(name='body_rear', role='body', **{'from': [3.6, 6.6, 7.2], 'to': [8, 8.8, 8.8]}, faces=['north', 'south', 'up', 'down', 'west']),
    dict(name='head', role='head', **{'from': [12, 6.5, 7.2], 'to': [13.8, 8.7, 8.8]}, faces=['north', 'south', 'up', 'down', 'east']),
    dict(name='snout', role='snout', **{'from': [13.8, 6.7, 7.6], 'to': [15.4, 7.7, 8.4]}, faces=['north', 'south', 'up', 'down', 'east']),
    dict(name='dorsal', role='fin', **{'from': [3.8, 8.8, 8], 'to': [5.4, 10, 8]}, faces=SIDES),
    dict(name='pectoral', role='fin', **{'from': [10.6, 5.7, 6.6], 'to': [11.8, 6.2, 9.4]}, faces=ALL),
    dict(name='pelvic', role='fin', **{'from': [6, 6.0, 8], 'to': [7, 6.6, 8]}, faces=SIDES),
    dict(name='barbels', role='barbel', **{'from': [13.9, 6.1, 8], 'to': [14.5, 6.7, 8]}, faces=SIDES),
]
for i, x in enumerate((5.9, 7.2, 8.6, 10.0, 11.3)):
    y = 8.8 if x < 8 else 9.2
    st_body.append(dict(name=f'scute{i}', role='scute', **{'from': [x, y, 7.7], 'to': [x + 0.8, y + 0.5, 8.3]}, faces=['north', 'south', 'up', 'east', 'west']))
st_tail = [dict(name='tail', role='tail', **{'from': [0.4, 5.8, 8], 'to': [3.7, 10.7, 8]}, faces=SIDES)]
ST_TAIL = [(3.7, 8.9), (0.5, 10.7), (1.6, 8.6), (2.3, 7.4), (2.1, 5.9), (3.7, 7.0)]
BACK, SIDE, BELLY = (92, 110, 124), (148, 166, 178), (212, 220, 226)
def st_paint(role, f, x, y, z):
    if role == 'tail':
        if not inside(ST_TAIL, x, y): return None
        return (110, 128, 140) if y > 8.4 or y < 6.4 else (132, 150, 162)
    if role == 'scute': return (228, 234, 236) if f != 'up' else (240, 244, 246)
    if role == 'fin': return (104, 122, 134)
    if role == 'barbel': return (200, 196, 180)
    if role in ('head', 'snout'):
        if f in SIDES and abs(x - 13.2) < 0.3 and abs(y - 8.0) < 0.3: return (16, 16, 20)
        if f in SIDES and abs(x - 13.2) < 0.55 and abs(y - 8.0) < 0.45: return (224, 226, 210)
        if f == 'down': return BELLY
        return mix((120, 136, 146), (96, 110, 120), (y - 6.6) / 2)
    # body: dark back to pale belly, zinc sheen streaks, a row of side scutes
    if f == 'up': return BACK
    if f == 'down': return BELLY
    top = 9.2 if x >= 8 else 8.8; bot = 6.2 if x >= 8 else 6.6
    t = (y - bot) / (top - bot)
    c = mix(BELLY, SIDE, t * 2.2) if t < 0.5 else mix(SIDE, BACK, (t - 0.5) * 2)
    if f in SIDES and abs(t - 0.55) < 0.09 and int(x * 2) % 3 == 0: c = (220, 228, 232)  # side scutes
    if f in SIDES and (int(x * 2 + y * 2) % 7 == 0) and 0.3 < t < 0.8: c = mix(c, (190, 206, 214), 0.6)  # sheen
    return c

# ---------------------------------------------------------------- Dorada: a mahi-mahi in gold
dd_body = [
    dict(name='head', role='head', **{'from': [10.4, 5.4, 6.8], 'to': [14, 10.2, 9.2]}, faces=ALL),
    dict(name='body_front', role='body', **{'from': [7, 5.8, 7], 'to': [10.4, 9.8, 9]}, faces=['north', 'south', 'up', 'down', 'west']),
    dict(name='body_mid', role='body', **{'from': [4.6, 6.4, 7.25], 'to': [7, 9.2, 8.75]}, faces=['north', 'south', 'up', 'down', 'west']),
    dict(name='body_rear', role='body', **{'from': [2.8, 7, 7.45], 'to': [4.6, 8.6, 8.55]}, faces=['north', 'south', 'up', 'down', 'west']),
    dict(name='dorsal', role='dorsal', **{'from': [3.4, 8.6, 8], 'to': [12.5, 11.2, 8]}, faces=SIDES),
    dict(name='anal', role='anal', **{'from': [3.4, 5.6, 8], 'to': [7.5, 7, 8]}, faces=SIDES),
    dict(name='pectoral', role='fin', **{'from': [9.8, 6.2, 6.6], 'to': [11, 6.7, 9.4]}, faces=ALL),
]
dd_tail = [dict(name='tail', role='tail', **{'from': [0.2, 5.4, 8], 'to': [2.9, 10.4, 8]}, faces=SIDES)]
GOLD, GREEN, BLUE, PALE = (236, 190, 46), (60, 146, 112), (40, 108, 150), (248, 228, 126)
def body_top(x): return 10.2 if x > 10.4 else 9.8 if x > 7 else 9.2 if x > 4.6 else 8.6
def body_bot(x): return 5.4 if x > 10.4 else 5.8 if x > 7 else 6.4 if x > 4.6 else 7.0
random.seed(9)
SPOTS = [(random.uniform(3.2, 10), random.uniform(6.6, 9.2)) for _ in range(14)]
def dd_paint(role, f, x, y, z):
    if role == 'tail':
        d, dy = 2.9 - x, abs(y - 7.9)
        if dy > 0.95 * d + 0.55: return None             # outside the fan
        if d > 0.9 and dy < (d - 0.9) * 1.1: return None   # the fork's notch
        return BLUE if dy > 1.9 else GOLD
    if role == 'dorsal':
        topline = 8.9 + (x - 3.4) / (12.5 - 3.4) * 2.3
        if y > topline or y < body_top(x) - 0.25: return None
        return GOLD if y > topline - 0.25 else mix(BLUE, GREEN, (x - 3.4) / 9)
    if role == 'anal':
        if y < 7.0 - (x - 3.4) / 4.1 * 1.2 - 0.2 or y > body_bot(x) + 0.25: return None
        return mix(GOLD, BLUE, 0.35)
    if role == 'fin': return (214, 170, 40)
    if f == 'up': return GREEN
    if f == 'down': return PALE
    top, bot = body_top(x), body_bot(x)
    t = (y - bot) / max(0.1, top - bot)
    if role == 'head':
        if f in SIDES and abs(x - 13.0) < 0.3 and abs(y - 8.4) < 0.3: return (16, 16, 20)
        if f in SIDES and abs(x - 13.0) < 0.55 and abs(y - 8.4) < 0.5: return (250, 236, 170)
        if f in SIDES and abs(y - 6.6) < 0.15 and x > 12.4: return (90, 70, 30)   # mouth
        if f == 'east': return mix(PALE, GREEN, (y - 5.4) / 4.8)
    c = mix(PALE, GOLD, t * 2.5) if t < 0.4 else mix(GOLD, GREEN, (t - 0.4) / 0.6 * 1.2)
    if f in SIDES and any((x - sx) ** 2 + (y - sy) ** 2 < 0.05 for sx, sy in SPOTS): c = BLUE
    return c

for shape, fish, body, tail, paint, credit in (
        ('sturgeon', 'sturginc', st_body, st_tail, st_paint, 'sturgeon (long, bony scutes, shovel snout, upturned tail)'),
        ('mahi', 'dorada', dd_body, dd_tail, dd_paint, 'mahi-mahi (big square head, tapering body, full-length dorsal fin, forked tail)')):
    cred = (f"Jake's Ore Fish Farm: shared '{shape}' fish model, a {credit}; side view, head toward +x. A fish uses it with its own "
            f"texture: {{\"parent\": \"orefishfarm:fish_category/{shape}\", \"textures\": {{\"t\": ..., \"particle\": ...}}}}")
    tex = build(shape, body, tail, paint, cred, f'{A}/models/fish_category', f'{A}/textures/item/fish/{fish}.png', f'orefishfarm:item/fish/{fish}')
    side = side_view(body, tail, paint)
    side.save(f'{PREV}/{fish}_side.png')
    sp = sprite(side); sp.save(f'{A}/textures/item/{fish}.png')
    sp.resize((256, 256), Image.NEAREST).save(f'{PREV}/{fish}_sprite.png')
    tex.resize((256, 256), Image.NEAREST).save(f'{PREV}/{fish}_tex.png')
    print(shape, 'ok')
