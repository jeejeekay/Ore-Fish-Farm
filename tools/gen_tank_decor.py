import json, os, sys, itertools
root = sys.argv[1]  # .../assets/orefishfarm
mdir = os.path.join(root, 'models/block/tank')
ddir = os.path.join(mdir, 'decor'); os.makedirs(ddir, exist_ok=True)
FLOOR_Y = 3.01

LOOKS = {
    'water': dict(floor='minecraft:block/sand', rock=['minecraft:block/cobblestone', 'minecraft:block/mossy_cobblestone', 'minecraft:block/stone'],
                  pebble='minecraft:block/gravel', short='minecraft:block/seagrass', tall='minecraft:block/kelp_plant'),
    'lava': dict(floor='minecraft:block/soul_soil', rock=['minecraft:block/blackstone', 'minecraft:block/basalt_side', 'minecraft:block/polished_blackstone'],
                 pebble='minecraft:block/magma', short='minecraft:block/crimson_roots', tall='minecraft:block/weeping_vines_plant'),
    'void': dict(floor='minecraft:block/end_stone', rock=['minecraft:block/end_stone_bricks', 'minecraft:block/purpur_block', 'minecraft:block/obsidian'],
                 pebble='minecraft:block/purpur_pillar', short='minecraft:block/chorus_flower', tall='minecraft:block/twisting_vines_plant'),
}

def box(x, z, w, d, h, tex, y=FLOOR_Y - 0.01):
    faces = {f: {'texture': tex} for f in ('north', 'south', 'east', 'west', 'up')}
    return {'from': [x, y, z], 'to': [x + w, y + h, z + d], 'faces': faces}

def plant(x, z, size, h, tex):
    out = []
    for ang in (45, -45):
        out.append({'from': [x - size / 2, FLOOR_Y, z], 'to': [x + size / 2, FLOOR_Y + h, z],
                    'faces': {'north': {'texture': tex, 'uv': [0, 0, 16, 16]}, 'south': {'texture': tex, 'uv': [0, 0, 16, 16]}},
                    'rotation': {'origin': [x, FLOOR_Y, z], 'axis': 'y', 'angle': ang}, 'shade': False})
    return out

# Arrangements per size tier: lists of ('rock', x, z, w, d, h, rockIndex) / ('peb', x, z, s) / ('short'|'tall', x, z, size, h)
TIERS = {
    'small': [
        [('peb', 11, 4, 2), ('peb', 8.5, 10.5, 1.5), ('short', 4, 12, 4, 6)],
        [('rock', 9, 8, 3, 3, 2.5, 0), ('short', 5, 5, 4, 7)],
        [('short', 4, 11, 4, 6), ('short', 11, 5, 3, 4.5)],
        [('peb', 5, 5, 1.5), ('peb', 7, 6, 2), ('peb', 6, 8, 1.5), ('short', 11, 11, 3, 5)],
        [('rock', 4, 9, 4, 3, 2, 1), ('peb', 11, 4, 1.5)],
    ],
    'medium': [
        [('rock', 3, 4, 5, 4, 3.5, 0), ('peb', 9, 5, 2), ('short', 11, 11, 4, 8)],
        [('short', 4, 11, 5, 9), ('short', 6.5, 12.5, 4, 7), ('peb', 11, 4, 2)],
        [('rock', 8, 8, 5, 5, 3, 1), ('rock', 9, 9, 3, 3, 5, 0), ('short', 4, 4, 4, 6)],
        [('tall', 5, 5, 4, 10), ('short', 7, 6.5, 4, 7), ('tall', 11, 11, 4, 9)],
        [('rock', 4, 9, 6, 4, 3, 2), ('peb', 11, 4, 2), ('peb', 12, 6.5, 1.5)],
    ],
    'large': [
        [('rock', 4, 4, 8, 7, 5, 0), ('rock', 5, 5, 5, 4, 7, 1), ('peb', 12.5, 12, 2)],
        [('tall', 4, 5, 5, 13), ('tall', 6.5, 9, 5, 11), ('tall', 10, 6, 4, 12), ('short', 11, 11.5, 4, 7)],
        [('rock', 3, 8, 6, 5, 4, 2), ('rock', 8, 3, 5, 5, 6, 0), ('rock', 10, 10, 3, 3, 3, 1), ('short', 4, 4, 4, 7)],
        [('rock', 7, 7, 7, 6, 5, 1), ('tall', 4, 4, 5, 12), ('short', 5, 11.5, 4, 8)],
        [('tall', 8, 8, 6, 13), ('peb', 4, 4, 2), ('peb', 12, 4, 2), ('peb', 4, 12, 1.5)],
    ],
}

def build(look, parts):
    t = LOOKS[look]
    textures = {'particle': t['floor'], 'pebble': t['pebble'], 'short': t['short'], 'tall': t['tall']}
    for i, r in enumerate(t['rock']): textures[f'rock{i}'] = r
    els = []
    for p in parts:
        k = p[0]
        if k == 'rock': els.append(box(p[1], p[2], p[3], p[4], p[5], f'#rock{p[6]}'))
        elif k == 'peb': els.append(box(p[1] - p[3] / 2, p[2] - p[3] / 2, p[3], p[3], p[3] * 0.7, '#pebble'))
        elif k in ('short', 'tall'): els += plant(p[1], p[2], p[3], p[4], '#' + k)
    return {'render_type': 'minecraft:cutout', 'ambientocclusion': False, 'textures': textures, 'elements': els}

written = []
for look in LOOKS:
    # The floor surface on its own (decor is separate now).
    t = LOOKS[look]
    floor = {'render_type': 'minecraft:cutout', 'ambientocclusion': False, 'textures': {'floor': t['floor'], 'particle': t['floor']},
             'elements': [{'from': [0, FLOOR_Y, 0], 'to': [16, FLOOR_Y, 16], 'faces': {'up': {'texture': '#floor'}}}]}
    json.dump(floor, open(os.path.join(mdir, f'floor_{look}.json'), 'w'), indent=2)
    for tier, sets in TIERS.items():
        for i, parts in enumerate(sets):
            name = f'{look}_{tier}_{i}'
            json.dump(build(look, parts), open(os.path.join(ddir, name + '.json'), 'w'), indent=2)
            written.append(name)
json.dump({'textures': {'particle': 'minecraft:block/sand'}, 'elements': []}, open(os.path.join(ddir, 'none.json'), 'w'), indent=2)

# Blockstate: decor entries for every look and tier, picked per position from a weighted list, in 4 rotations.
SIDES = ['north', 'east', 'south', 'west']
def tier_when(look, tier):
    base = {'role': 'fish', 'look': look, 'down': 'false'}
    if tier == 'small':
        return dict(base, **{s: 'false' for s in SIDES})
    if tier == 'large':
        return dict(base, **{s: 'true' for s in SIDES})
    # medium: at least one side joined and at least one side glass
    return {'OR': [dict(base, **{a: 'true', b: 'false'}) for a, b in itertools.permutations(SIDES, 2)]}

def choices(look, tier):
    out = []
    for i in range(len(TIERS[tier])):
        for y in (0, 90, 180, 270):
            e = {'model': f'orefishfarm:block/tank/decor/{look}_{tier}_{i}', 'weight': 2}
            if y: e['y'] = y
            out.append(e)
    # some bare floor too, more often in the small tier (a lone tank shouldn't always be busy)
    out.append({'model': 'orefishfarm:block/tank/decor/none', 'weight': {'small': 6, 'medium': 4, 'large': 2}[tier]})
    return out

bs_path = os.path.join(root, 'blockstates/fish_tank.json')
bs = json.load(open(bs_path))
mp = [p for p in bs['multipart'] if 'decor/' not in json.dumps(p.get('apply'))]
idx = max(i for i, p in enumerate(mp) if 'floor_' in json.dumps(p.get('apply'))) + 1
new = [{'when': tier_when(look, tier), 'apply': choices(look, tier)} for look in LOOKS for tier in TIERS]
mp[idx:idx] = new
bs['multipart'] = mp
json.dump(bs, open(bs_path, 'w'), indent=2)
print(len(written), 'decor models,', len(new), 'blockstate entries')
