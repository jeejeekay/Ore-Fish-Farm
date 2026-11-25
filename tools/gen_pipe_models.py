"""Pipe Tank models, blockstate, item model and glass textures (Ore Fish Farm 2.26.0).

An 8x8 glass tube (4..12) round a 7x7 tube of tinted, animated water (4.5..11.5), with a 10x10 black rim collar:
where it meets an inventory the collar reaches 2 px out of the block (14..18) into the inventory's; a capped dead
end keeps it inside (14..16).

How it stays glitch-free
------------------------
The glass is one surface, the 8 px tube's outside (2.26.1 tried inner faces on the water tube like the aquarium
walls; it looked the same for half again the faces, so it was dropped). The glass is drawn on Minecraft's "tripwire" layer, which always draws after the translucent layer the water is
on, so glass is always over the water whatever the camera angle (Minecraft only re-sorts see-through faces now and
then, which is what made water vanish at sharp angles in 2.25). Glass faces only sort against other glass.

Faces
-----
  core face, side d   (when side d has no connection): a 6x6 square at the core's wall
  arm face, arm a, s  (when side a is connected, s one of a's 4 side walls): 6 across, 5 long, core wall to block edge
No end faces: connected arms run to the block edge and carry on into the next pipe's arm; rims cover the ends.

Edges
-----
The glass fades in at an edge only where the tube's surface folds there (a corner of the tube, outer or inner); an
edge that carries straight on into the next face (core to arm, arm to the next pipe) is plain glass, so straight
runs, bends, T and cross junctions and closed loops read as one continuous tube with no seams.
  core face d, edge toward e: fades if side e has no connection (outer corner of the core); else the face carries
      on into arm e's face on side d (same plane).
  arm face a/s: both long edges always fade (the arm's own corners); the edge at the core fades if side s is
      connected too (an inner corner, where arm a meets arm s); the far end never fades.
Each face's 4 edges make a mask (u-min, u-max, v-min, v-max in the face's texture space); pipe_glass_<mask>.png
draws that face, 1 texel per pixel, in texels 0..5.
Run from the repo root: python tools/gen_pipe_models.py
"""
import json, os, glob
from PIL import Image

ROOT = 'src/main/resources/assets/orefishfarm'
MD = ROOT + '/models/block/pipe'
TX = ROOT + '/textures/block'
os.makedirs(MD, exist_ok=True)

G0, G1 = 4, 12     # the tube's glass: 8 px across, on whole pixels
W0, W1 = 4.5, 11.5 # the water inside: 7 px
R0, R1 = 3, 13     # the rim collar: 10 px across, 14..16 in the block (+2 px out at an inventory)
SPAN = G1 - G0     # 8
GLASS_LAYER = 'minecraft:tripwire'
CONNECTED = 'pipe|rim|cap'

DIRS = ['north', 'south', 'west', 'east', 'up', 'down']
VEC = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
AXIS = {d: 'xyz'[[abs(c) for c in VEC[d]].index(1)] for d in DIRS}
SIGN = {d: sum(VEC[d]) for d in DIRS}
OPP = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west', 'up': 'down', 'down': 'up'}

# Minecraft's own texture axes per face (FaceBakery's default uv): (axis, +1 if u grows with it, -1 if it shrinks)
UV_AXES = {
    'up':    (('x', 1), ('z', 1)),
    'down':  (('x', 1), ('z', -1)),
    'north': (('x', -1), ('y', -1)),
    'south': (('x', 1), ('y', -1)),
    'west':  (('z', 1), ('y', -1)),
    'east':  (('z', -1), ('y', -1)),
}


def toward(axis, sign):
    """The direction along `axis` with this sign."""
    for d in DIRS:
        if AXIS[d] == axis and SIGN[d] == sign:
            return d


def perp(d):
    return [e for e in DIRS if AXIS[e] != AXIS[d]]


# ------------------------------------------------------------------ glass textures

BODY = (222, 243, 252, 30)                       # the glass itself: a faint cool tint
FADE = [(236, 250, 255, 135), (226, 246, 254, 72)]  # in from a folding edge: still see-through, never solid


INNER = 7  # the inner faces' span (the 7 px water tube)


def edge_texture(mask, span=SPAN):
    """Texels 0..span-1 of a 16x16: body glass, fading in at each edge in the mask (u-min, u-max, v-min, v-max)."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for u in range(span):
        for v in range(span):
            best = BODY
            for bit, dist in ((1, u), (2, span - 1 - u), (4, v), (8, span - 1 - v)):
                if mask & bit and dist < len(FADE) and FADE[dist][3] > best[3]:
                    best = FADE[dist]
            im.putpixel((u, v), best)
    return im


for old in glob.glob(TX + '/pipe_glass*.png'):
    os.remove(old)
for m in range(16):
    edge_texture(m).save(f'{TX}/pipe_glass_{m}.png')


# ------------------------------------------------------------------ faces

def glass_face(face, lo, hi, fades, inner=False):
    """
    A glass face on side `face` of the box lo..hi; `fades`: the directions whose edge folds. -> (element, mask).
    inner: the face on that side of the (water) box facing back in, textured from pipe_glass_in_<mask>.
    """
    side = face
    if inner:
        face = OPP[face]
    span = INNER if inner else SPAN
    (ua, us), (va, vs) = UV_AXES[face]
    i = 'xyz'.index
    lu, lv = hi[i(ua)] - lo[i(ua)], hi[i(va)] - lo[i(va)]
    edges = {1: toward(ua, -us), 2: toward(ua, us), 4: toward(va, -vs), 8: toward(va, vs)}
    mask = sum(b for b, d in edges.items() if d in fades)
    # texture window: SPAN texels hold the full pattern; a shorter face takes the end that has its fade
    u0 = span - lu if (mask & 2 and not mask & 1) else 0
    v0 = span - lv if (mask & 8 and not mask & 4) else 0
    n = i(AXIS[side])
    f, t = list(lo), list(hi)
    f[n] = t[n] = hi[n] if SIGN[side] > 0 else lo[n]
    tex = f"#i{mask}" if inner else f"#g{mask}"
    return {"from": f, "to": t, "faces": {face: {"texture": tex, "uv": [u0, v0, u0 + lu, v0 + lv]}}}, mask


def water_face(face, lo, hi):
    n = 'xyz'.index(AXIS[face])
    f, t = list(lo), list(hi)
    f[n] = t[n] = hi[n] if SIGN[face] > 0 else lo[n]
    return {"from": f, "to": t, "faces": {face: {"texture": "#fill", "tintindex": 0}}}


def write(name, elements, textures, render='minecraft:translucent'):
    with open(f'{MD}/{name}.json', 'w') as fh:
        json.dump({"render_type": render, "ambientocclusion": False, "textures": textures, "elements": elements}, fh, indent=1)


def textures_for(masks, inner=()):
    t = {f"g{m}": f"orefishfarm:block/pipe_glass_{m}" for m in sorted(set(masks))}
    t.update({f"i{m}": f"orefishfarm:block/pipe_glass_in_{m}" for m in sorted(set(inner))})
    t["particle"] = "orefishfarm:block/pipe_rim"
    return t


WATER_TEX = {"fill": "minecraft:block/water_still", "particle": "orefishfarm:block/pipe_rim"}


def arm_box(a, a0=G0, a1=G1):
    lo, hi = [a0] * 3, [a1] * 3
    n = 'xyz'.index(AXIS[a])
    if SIGN[a] > 0:
        lo[n], hi[n] = a1, 16
    else:
        lo[n], hi[n] = 0, a0
    return lo, hi


for old in glob.glob(MD + '/*.json'):
    os.remove(old)

parts = []
CORE = ([G0] * 3, [G1] * 3)
WCORE = ([W0] * 3, [W1] * 3)

# core faces: side d unconnected; one model per combination of its four neighbours' connections
for d in DIRS:
    around = perp(d)  # four directions, in DIRS order
    for combo in range(16):
        open_sides = [e for k, e in enumerate(around) if combo >> k & 1]  # connected
        fades = {e for e in around if e not in open_sides}
        el, mask = glass_face(d, *CORE, fades)
        name = f'core_{d}_{combo}'
        write(name, [el], textures_for([mask]), GLASS_LAYER)
        when = {d: "none"}
        for e in around:
            when[e] = CONNECTED if e in open_sides else "none"
        parts.append({"when": when, "apply": {"model": f"orefishfarm:block/pipe/{name}"}})
    write(f'water_core_{d}', [water_face(d, *WCORE)], WATER_TEX)
    parts.append({"when": {d: "none"}, "apply": {"model": f"orefishfarm:block/pipe/water_core_{d}"}})

# arm faces: arm a, wall s; the edge at the core folds only if s is connected too
for a in DIRS:
    lo, hi = arm_box(a)
    for s in perp(a):
        long_edges = {e for e in perp(a) if AXIS[e] != AXIS[s]}
        for inner in (False, True):
            fades = set(long_edges) | ({OPP[a]} if inner else set())
            el, mask = glass_face(s, lo, hi, fades)
            name = f'arm_{a}_{s}_{"inner" if inner else "flat"}'
            write(name, [el], textures_for([mask]), GLASS_LAYER)
            parts.append({"when": {a: CONNECTED, s: CONNECTED if inner else "none"},
                          "apply": {"model": f"orefishfarm:block/pipe/{name}"}})
        write(f'water_arm_{a}_{s}', [water_face(s, *arm_box(a, W0, W1))], WATER_TEX)
        parts.append({"when": {a: CONNECTED}, "apply": {"model": f"orefishfarm:block/pipe/water_arm_{a}_{s}"}})

def rim_face(d, f, length):
    """
    A rim collar face with fixed texture coordinates, so rims and caps show the same frame pattern wherever they
    are: the ends show the middle 10x10 of pipe_rim.png; the sides show its bottom rows with the pipe's end at row
    16, a cap's 2 px being rows 14..16 and a rim's extra 2 px outside the block carrying on to rows 12..14.
    """
    if AXIS[f] == AXIS[d]:
        return {"texture": "#rim", "uv": [R0, R0, R1, R1]}
    (ua, us), (va, vs) = UV_AXES[f]
    along, across = (0, 1) if ua == AXIS[d] else (1, 0)
    axis_sign = us if along == 0 else vs
    # the face's low-texture edge along the collar points outward (to d)? then the outer end gets 16 - length
    low_points_out = toward(AXIS[d], -axis_sign) == d
    a0, a1 = (16 - length, 16) if low_points_out else (16, 16 - length)
    uv = [0, 0, 0, 0]
    uv[along], uv[along + 2] = a0, a1
    uv[across], uv[across + 2] = R0, R1
    return {"texture": "#rim", "uv": uv}


# rim collars (solid black, cutout): at an inventory 2 px out of the block, at a dead end inside it
for d in DIRS:
    for kind, (a, b) in (('rim', (14, 18)), ('cap', (14, 16))):
        lo, hi = [R0] * 3, [R1] * 3
        n = 'xyz'.index(AXIS[d])
        lo[n], hi[n] = (a, b) if SIGN[d] > 0 else (16 - b, 16 - a)
        write(f'{kind}_{d}', [{"from": lo, "to": hi, "faces": {f: rim_face(d, f, b - a) for f in DIRS}}],
              {"rim": "orefishfarm:block/pipe_rim", "particle": "orefishfarm:block/pipe_rim"}, 'minecraft:cutout')
        parts.append({"when": {d: kind}, "apply": {"model": f"orefishfarm:block/pipe/{kind}_{d}"}})

with open(ROOT + '/blockstates/pipe_tank.json', 'w') as fh:
    json.dump({"multipart": parts}, fh, indent=1)

# Item: a lone pipe (east-west, rims both ends)
state = {d: 'none' for d in DIRS}
state['east'] = state['west'] = 'cap'


def matches(when):
    return all(state[k] in v.split('|') for k, v in when.items())


els, tex = [], {}
for p in parts:
    if matches(p['when']):
        m = json.load(open(f"{MD}/{p['apply']['model'].split('/')[-1]}.json"))
        els += m['elements']
        tex.update(m['textures'])
item = {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
        "textures": {**tex, "rim": "orefishfarm:block/pipe_rim"}, "elements": els}
with open(ROOT + '/models/item/pipe_tank.json', 'w') as fh:
    json.dump(item, fh, indent=1)
print(len(glob.glob(MD + '/*.json')), 'pipe models,', len(parts), 'blockstate parts')
