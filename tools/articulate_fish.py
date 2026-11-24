"""Splits the long shared fish shapes into a static front (fish_category/<shape>_front) and a rear body that swings
from the front's back edge, salmon-style (see client/FishRender). Run from the repo root.

- paima: the old 'head' box is cut in half; its rear half becomes a mid piece between the head and the body in
  height and width. Front = mid + head; rear (paima.json) = body, dorsal, fins.
- sturgeon: split at x 8 (body_front | body_rear). mahi: split at x 7 (the dorsal plane is cut in two).
- power (eels): power.json is left whole (other mods' fish use it); power_front / power_rear are new.
Every box gets all six faces (joint faces show while the body swings)."""
import json, os
D = 'src/main/resources/assets/orefishfarm/models/fish_category/'
def load(n): return json.load(open(D + n + '.json'))
def save(n, m): json.dump(m, open(D + n + '.json', 'w'), indent=1)
def f(uv): return {'texture': '#t', 'uv': uv}
CREDIT = "Jake's Ore Fish Farm: shared fish shape, %s. Articulated: <shape>_front stays still, <shape> swings from its front edge (FishRender)."

# ---- paima
p = load('paima')
els = [e for e in p['elements'] if e.get('name') != 'head']
mid = {'name': 'mid', 'from': [9.8, 6.1, 6.95], 'to': [12.15, 8.3, 9.05], 'faces': {
    'north': f([2.5, 8, 0, 10]), 'south': f([0, 8, 2.5, 10]), 'up': f([5, 8, 7.5, 10]), 'down': f([10, 8, 12.5, 10]),
    'east': f([5, 8, 7, 10]), 'west': f([5, 8, 7, 10])}}
head = {'name': 'head', 'from': [12.15, 6.2, 7.1], 'to': [14.5, 7.75, 8.9], 'faces': {
    'north': f([5, 8, 2.5, 10]), 'south': f([2.5, 8, 5, 10]), 'up': f([7.5, 8, 10, 10]), 'down': f([12.5, 8, 15, 10]),
    'east': f([11, 0, 13, 2]), 'west': f([5, 8, 6.5, 9.5])}}
save('paima_front', {'credit': CREDIT % 'paima front (head and neck)', 'texture_size': [32, 32], 'elements': [mid, head]})
p['elements'] = els
save('paima', p)

# ---- sturgeon
s = load('sturgeon')
front_names = {'body_front', 'head', 'snout', 'pectoral', 'barbels', 'scute2', 'scute3', 'scute4'}
for e in s['elements']:
    if e.get('name') == 'body_rear': e['faces']['east'] = dict(e['faces']['west'])
    if e.get('name') == 'head' and 'west' not in e['faces']: e['faces']['west'] = dict(e['faces']['east'])
fr = [e for e in s['elements'] if e.get('name') in front_names]
s['elements'] = [e for e in s['elements'] if e.get('name') not in front_names]
save('sturgeon_front', {'credit': CREDIT % 'sturgeon front', 'texture_size': s.get('texture_size', [32, 32]), 'elements': fr})
save('sturgeon', s)

# ---- mahi
m = load('mahi')
rear, front = [], []
for e in m['elements']:
    n = e.get('name')
    if n == 'dorsal':
        x0, x1, split = e['from'][0], e['to'][0], 7.0
        t = (split - x0) / (x1 - x0)
        nu0, v0, nu1, v1 = e['faces']['north']['uv']   # north: u0 at x1 (east), u1 at x0
        su0, _, su1, _ = e['faces']['south']['uv']     # south: u0 at x0, u1 at x1
        nsplit = nu0 + (nu1 - nu0) * (1 - t)
        ssplit = su0 + (su1 - su0) * t
        r = json.loads(json.dumps(e)); r['name'] = 'dorsal_rear'; r['to'][0] = split
        r['faces']['north']['uv'] = [round(nsplit, 3), v0, nu1, v1]; r['faces']['south']['uv'] = [su0, v0, round(ssplit, 3), v1]
        fr_ = json.loads(json.dumps(e)); fr_['name'] = 'dorsal_front'; fr_['from'][0] = split
        fr_['faces']['north']['uv'] = [nu0, v0, round(nsplit, 3), v1]; fr_['faces']['south']['uv'] = [round(ssplit, 3), v0, su1, v1]
        rear.append(r); front.append(fr_)
        continue
    if n == 'body_mid': e['faces']['east'] = dict(e['faces']['west'])
    if n == 'head' and 'west' not in e['faces']: e['faces']['west'] = dict(e['faces']['east'])
    (front if n in ('head', 'body_front', 'pectoral') else rear).append(e)
save('mahi_front', {'credit': CREDIT % 'mahi front', 'texture_size': m.get('texture_size', [32, 32]), 'elements': front})
m['elements'] = rear
save('mahi', m)

# ---- power (eel): new split copies; power.json stays whole
w = load('power')
pf = [e for e in w['elements'] if e['from'][0] >= 9]
pr = [e for e in w['elements'] if e['from'][0] < 9]
save('power_front', {'credit': CREDIT % 'eel front', 'texture_size': w.get('texture_size', [32, 32]), 'elements': pf})
save('power_rear', {'credit': CREDIT % 'eel rear (fish using it need a <fish>_front with parent power_front)', 'texture_size': w.get('texture_size', [32, 32]), 'elements': pr})
print('done')
