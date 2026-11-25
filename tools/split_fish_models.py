"""Python copy of build.gradle's splitFishModels (for checking without Gradle): splits src/main/fishmodels/<folder>/*.json
into <out>/assets/orefishfarm/models/<folder>/<shape>[_<group>].json and fish_joints.json.
Usage: python tools/split_fish_models.py <out dir>"""
import json, os, sys
out = sys.argv[1]
SRC = 'src/main/fishmodels'
joints = {}
def collect(children, acc):
    for c in children:
        if isinstance(c, int): acc.append(c)
        elif isinstance(c, dict): collect(c.get('children', []), acc)
for folder in sorted(os.listdir(SRC)):
    for fn in sorted(os.listdir(f'{SRC}/{folder}')):
        if not fn.endswith('.json'): continue
        m = json.load(open(f'{SRC}/{folder}/{fn}')); shape = fn[:-5]
        els = m.get('elements', []); used = set(); pieces = {}
        for g in m.get('groups', []):
            if not isinstance(g, dict): continue
            idx = []; collect(g.get('children', []), idx); used |= set(idx)
            pieces[g['name']] = {'idx': idx, 'origin': g.get('origin')}
        loose = [i for i in range(len(els)) if i not in used]
        if loose:
            b = pieces.setdefault('body', {'idx': [], 'origin': None}); b['idx'] = sorted(b['idx'] + loose)
        for name, p in pieces.items():
            model = {}
            if m.get('texture_size'): model['texture_size'] = m['texture_size']
            model['textures'] = m.get('textures', {})
            model['elements'] = [els[i] for i in sorted(p['idx'])]
            on = shape if name == 'body' else f'{shape}_{name}'
            d = f'{out}/assets/orefishfarm/models/{folder}'; os.makedirs(d, exist_ok=True)
            json.dump(model, open(f'{d}/{on}.json', 'w'), indent=1)
        j = {}
        if pieces.get('body', {}).get('origin'): j['body'] = pieces['body']['origin']
        if pieces.get('tail', {}).get('origin'): j['tail'] = pieces['tail']['origin']
        for name in pieces:
            if name not in ('front', 'tail'): joints[f'orefishfarm:{folder}/{shape if name == "body" else shape + "_" + name}'] = j
json.dump(joints, open(f'{out}/assets/orefishfarm/fish_joints.json', 'w'), indent=1)
