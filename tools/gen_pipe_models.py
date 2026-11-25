"""Pipe Tank models, blockstate, item model and glass textures (Ore Fish Farm 2.25.0).

A 6x6 glass tube (5..11) of tinted, animated water, with an 8x8 black rim collar 2 px deep where it meets an
inventory or is capped.

How it stays glitch-free
------------------------
Minecraft sorts see-through faces back to front by each face's centre. Two see-through layers (glass and water)
only sort reliably when every glass face has a water face of exactly the same size and shape right behind it:
then, seen from outside, the glass's centre is always the nearer one. So every face here is a PAIR: the glass
face on the tube's surface and a water twin of the same extent 0.1 px further in (far enough apart not to
z-fight, close enough that nothing shows between them). Nothing else is see-through. Faces are at most 6 px
across, so faces of different pairs (a bend's two arms) sort correctly too.

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

G0, G1 = 5, 11     # the tube's glass: 6 px across, on whole pixels
TWIN = 0.1         # water twin: this far inside its glass face
R0, R1 = 4, 12     # the rim collar: 8 px across, 2 px deep (14..16 along)
SPAN = G1 - G0     # 6

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


def edge_texture(mask):
    """Texels 0..5 of a 16x16: body glass, fading in at each edge in the mask (u-min, u-max, v-min, v-max)."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for u in range(SPAN):
        for v in range(SPAN):
            best = BODY
            for bit, dist in ((1, u), (2, SPAN - 1 - u), (4, v), (8, SPAN - 1 - v)):
                if mask & bit and dist < len(FADE) and FADE[dist][3] > best[3]:
                    best = FADE[dist]
            im.putpixel((u, v), best)
    return im


for old in glob.glob(TX + '/pipe_glass*.png'):
    os.remove(old)
for m in range(16):
    edge_texture(m).save(f'{TX}/pipe_glass_{m}.png')


# ------------------------------------------------------------------ faces

def face_pair(face, lo, hi, fades):
    """
    A glass face on side `face` of the box lo..hi (only the face's plane is used), with its water twin.
    `fades` is the set of directions (in the face's plane) whose edge folds. Returns two elements.
    """
    (ua, us), (va, vs) = UV_AXES[face]
    i = 'xyz'.index
    lu, lv = hi[i(ua)] - lo[i(ua)], hi[i(va)] - lo[i(va)]
    # which model direction each texture edge points to
    edges = {1: toward(ua, -us), 2: toward(ua, us), 4: toward(va, -vs), 8: toward(va, vs)}
    mask = sum(b for b, d in edges.items() if d in fades)
    # texture window: SPAN texels hold the full pattern; a shorter face takes the end that has its fade
    u0 = SPAN - lu if (mask & 2 and not mask & 1) else 0
    v0 = SPAN - lv if (mask & 8 and not mask & 4) else 0
    uv = [u0, v0, u0 + lu, v0 + lv]
    # the face's plane
    n = i(AXIS[face])
    plane = hi[n] if SIGN[face] > 0 else lo[n]
    gf, gt = list(lo), list(hi)
    gf[n] = gt[n] = plane
    wf, wt = list(gf), list(gt)
    wf[n] = wt[n] = plane - SIGN[face] * TWIN
    glass = {"from": gf, "to": gt, "faces": {face: {"texture": f"#g{mask}", "uv": uv}}}
    water = {"from": wf, "to": wt, "faces": {face: {"texture": "#fill", "tintindex": 0}}}
    return [water, glass], mask


def write(name, elements, textures, render='minecraft:translucent'):
    with open(f'{MD}/{name}.json', 'w') as fh:
        json.dump({"render_type": render, "ambientocclusion": False, "textures": textures, "elements": elements}, fh, indent=1)


def textures_for(masks):
    t = {f"g{m}": f"orefishfarm:block/pipe_glass_{m}" for m in sorted(set(masks))}
    t["fill"] = "minecraft:block/water_still"
    t["particle"] = "orefishfarm:block/pipe_rim"
    return t


def arm_box(a):
    lo, hi = [G0] * 3, [G1] * 3
    n = 'xyz'.index(AXIS[a])
    if SIGN[a] > 0:
        lo[n], hi[n] = G1, 16
    else:
        lo[n], hi[n] = 0, G0
    return lo, hi


for old in glob.glob(MD + '/*.json'):
    os.remove(old)

parts = []
CORE = ([G0] * 3, [G1] * 3)

# core faces: side d unconnected; one model per combination of its four neighbours' connections
for d in DIRS:
    around = perp(d)  # four directions, in DIRS order
    for combo in range(16):
        open_sides = [e for k, e in enumerate(around) if combo >> k & 1]  # connected
        fades = {e for e in around if e not in open_sides}
        els, mask = face_pair(d, *CORE, fades)
        name = f'core_{d}_{combo}'
        write(name, els, textures_for([mask]))
        when = {d: "none"}
        for e in around:
            when[e] = "pipe|rim" if e in open_sides else "none"
        parts.append({"when": when, "apply": {"model": f"orefishfarm:block/pipe/{name}"}})

# arm faces: arm a, wall s; the edge at the core folds only if s is connected too
for a in DIRS:
    lo, hi = arm_box(a)
    for s in perp(a):
        long_edges = {e for e in perp(a) if AXIS[e] != AXIS[s]}
        for inner in (False, True):
            fades = set(long_edges) | ({OPP[a]} if inner else set())
            els, mask = face_pair(s, lo, hi, fades)
            name = f'arm_{a}_{s}_{"inner" if inner else "flat"}'
            write(name, els, textures_for([mask]))
            parts.append({"when": {a: "pipe|rim", s: "pipe|rim" if inner else "none"},
                          "apply": {"model": f"orefishfarm:block/pipe/{name}"}})

# rim collars (solid black, cutout)
for d in DIRS:
    lo, hi = [R0] * 3, [R1] * 3
    n = 'xyz'.index(AXIS[d])
    lo[n], hi[n] = (14, 16) if SIGN[d] > 0 else (0, 2)
    write(f'rim_{d}', [{"from": lo, "to": hi, "faces": {f: {"texture": "#rim"} for f in DIRS}}],
          {"rim": "orefishfarm:block/pipe_rim", "particle": "orefishfarm:block/pipe_rim"}, 'minecraft:cutout')
    parts.append({"when": {d: "rim"}, "apply": {"model": f"orefishfarm:block/pipe/rim_{d}"}})

with open(ROOT + '/blockstates/pipe_tank.json', 'w') as fh:
    json.dump({"multipart": parts}, fh, indent=1)

# Item: a lone pipe (east-west, rims both ends)
state = {d: 'none' for d in DIRS}
state['east'] = state['west'] = 'rim'


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
