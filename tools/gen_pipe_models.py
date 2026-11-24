"""Pipe Tank models and blockstate (Ore Fish Farm 2.23.0).

A 10x10 glass tube (3..13) with water inside (3.5..12.5). Per side of the block:
  none -> the core's glass and water faces on that side
  pipe -> an arm: glass and water out to the block edge, no end faces, so the water runs on into the next pipe
          with no seam
  rim  -> the same arm plus a black rim collar (2..14 across, last 2 pixels): where it meets an inventory,
          or the capped far end of a dead-end pipe
Looks: the glass is the placed tanks' clear glass (tank_glass_cutout, cutout), the water the tanks' tinted vanilla
water (translucent); the rim texture is the tank frame's pattern made black (tools/gen_pipe_rim.py).
Run from the repo root: python tools/gen_pipe_models.py
"""
import json, os

ROOT = 'src/main/resources/assets/orefishfarm'
MD = ROOT + '/models/block/pipe'
os.makedirs(MD, exist_ok=True)

G0, G1 = 3, 13          # glass
W0, W1 = 3.5, 12.5      # water
R0, R1 = 2, 14          # rim collar
DIRS = ['north', 'south', 'west', 'east', 'up', 'down']
AXIS = {'north': ('z', -1), 'south': ('z', 1), 'west': ('x', -1), 'east': ('x', 1), 'up': ('y', 1), 'down': ('y', -1)}
SIDES = {'x': ['north', 'south', 'up', 'down'], 'y': ['north', 'south', 'east', 'west'], 'z': ['east', 'west', 'up', 'down']}


def box(d, lo, hi, a, b):
    """Box on side d: lo..hi across, a..b along (a < b measured from the centre outward, in 0..16 of the + side)."""
    ax, sign = AXIS[d]
    if sign < 0:
        a, b = 16 - b, 16 - a
    f, t = [lo, lo, lo], [hi, hi, hi]
    i = 'xyz'.index(ax)
    f[i], t[i] = a, b
    return f, t


def model(textures, elements, render='minecraft:translucent'):
    return {"render_type": render, "ambientocclusion": False, "textures": textures, "elements": elements}


GLASS = {"glass": "orefishfarm:block/tank_glass_cutout", "particle": "orefishfarm:block/pipe_rim"}
WATER = {"fill": "minecraft:block/water_still", "particle": "orefishfarm:block/pipe_rim"}


def write(name, data):
    with open(f'{MD}/{name}.json', 'w') as fh:
        json.dump(data, fh, indent=1)


for d in DIRS:
    ax, sign = AXIS[d]
    # core faces on side d (glass + water)
    gf, gt = [G0] * 3, [G1] * 3
    wf, wt = [W0] * 3, [W1] * 3
    i = 'xyz'.index(ax)
    if sign > 0:
        gf[i] = gt[i] = G1
        wf[i] = wt[i] = W1
    else:
        gf[i] = gt[i] = G0
        wf[i] = wt[i] = W0
    write(f'core_glass_{d}', model(GLASS, [{"from": gf, "to": gt, "faces": {d: {"texture": "#glass"}}}], 'minecraft:cutout'))
    write(f'core_{d}', model(WATER, [{"from": wf, "to": wt, "faces": {d: {"texture": "#fill", "tintindex": 0}}}]))
    # arm: four side faces of glass and of water, from the core to the block edge
    f, t = box(d, G0, G1, G1, 16)
    fw, tw = box(d, W0, W1, W1, 16)
    write(f'arm_glass_{d}', model(GLASS, [{"from": f, "to": t, "faces": {s: {"texture": "#glass"} for s in SIDES[ax]}}], 'minecraft:cutout'))
    write(f'arm_{d}', model(WATER, [{"from": fw, "to": tw, "faces": {s: {"texture": "#fill", "tintindex": 0} for s in SIDES[ax]}}]))
    # rim collar
    f, t = box(d, R0, R1, R1, 16)
    write(f'rim_{d}', model({"rim": "orefishfarm:block/pipe_rim", "particle": "orefishfarm:block/pipe_rim"}, [
        {"from": f, "to": t, "faces": {s: {"texture": "#rim"} for s in DIRS}},
    ], 'minecraft:cutout'))

parts = []
for d in DIRS:
    parts.append({"when": {d: "none"}, "apply": {"model": f"orefishfarm:block/pipe/core_glass_{d}"}})
    parts.append({"when": {d: "none"}, "apply": {"model": f"orefishfarm:block/pipe/core_{d}"}})
    parts.append({"when": {d: "pipe|rim"}, "apply": {"model": f"orefishfarm:block/pipe/arm_glass_{d}"}})
    parts.append({"when": {d: "pipe|rim"}, "apply": {"model": f"orefishfarm:block/pipe/arm_{d}"}})
    parts.append({"when": {d: "rim"}, "apply": {"model": f"orefishfarm:block/pipe/rim_{d}"}})
with open(ROOT + '/blockstates/pipe_tank.json', 'w') as fh:
    json.dump({"multipart": parts}, fh, indent=1)

# Item: a short east-west tube capped at both ends (what a lone pipe looks like).
els = []
for d in ['north', 'south', 'up', 'down']:
    els += json.load(open(f'{MD}/core_glass_{d}.json'))['elements'] + json.load(open(f'{MD}/core_{d}.json'))['elements']
for d in ['east', 'west']:
    els += json.load(open(f'{MD}/arm_glass_{d}.json'))['elements'] + json.load(open(f'{MD}/arm_{d}.json'))['elements']
for d in ['east', 'west']:
    for e in json.load(open(f'{MD}/rim_{d}.json'))['elements']:
        e = json.loads(json.dumps(e).replace('#rim', '#rim'))
        els.append(e)
item = {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
        "textures": {**GLASS, **WATER, "rim": "orefishfarm:block/pipe_rim"}, "elements": els}
os.makedirs(ROOT + '/models/item', exist_ok=True)
with open(ROOT + '/models/item/pipe_tank.json', 'w') as fh:
    json.dump(item, fh, indent=1)
print('pipe models written')
