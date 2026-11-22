import sys, os
sys.path.insert(0, '/home/claude/ore-fish-farm/tools')
from gen_fish_buckets import side, shrink, bucket
from PIL import Image
OFF = '/home/claude/ore-fish-farm/src/main/resources/assets/orefishfarm'
REF = '/mnt/user-data/uploads/Quicreate/_vanilla_ref'
QTEX = {'quicreate:item/fish/furry_trout': '/tmp/bk/qm/furry_trout.png', 'quicreate:item/fish/glass_eel': f'{REF}/quicreate_glass_eel_fish.png'}
QMOD = {'quicreate:fish/furry_trout': '/tmp/bk/qm/furry_trout.json', 'quicreate:fish/furry_trout_tail': '/tmp/bk/qm/furry_trout_tail.json'}
def resolve(kind, rid):
    ns, path = rid.split(':')
    if kind == 'model':
        if rid in QMOD: return QMOD[rid]
        return f'{OFF}/models/{path}.json'
    if rid in QTEX: return QTEX[rid]
    return f'{OFF}/textures/{path}.png'
FISH = [('orefishfarm', 'obsidian_fish', 'water'), ('orefishfarm', 'ferrofin', 'water'), ('orefishfarm', 'gilded_carp', 'water'),
        ('orefishfarm', 'zincray', 'water'), ('orefishfarm', 'cuprum_eel', 'water'), ('orefishfarm', 'brass_bream', 'water'),
        ('quicreate', 'furry_trout', 'water'), ('quicreate', 'glass_eel', 'water')]
os.makedirs('/tmp/bk/out', exist_ok=True)
sheet = Image.new('RGBA', (len(FISH) * 136, 136), (58, 86, 128, 255))
for i, (ns, name, hab) in enumerate(FISH):
    if ns == 'quicreate' and name == 'glass_eel':
        paths = [f'{OFF}/models/fish_category/power.json', f'{OFF}/models/fish_category/power_tail.json']
        import json
        # glass eel = power shape with its own texture
        tmp = '/tmp/bk/qm/glass_eel.json'; tmpt = '/tmp/bk/qm/glass_eel_tail.json'
        json.dump({'parent': 'orefishfarm:fish_category/power', 'textures': {'t': 'quicreate:item/fish/glass_eel'}}, open(tmp, 'w'))
        json.dump({'parent': 'orefishfarm:fish_category/power_tail', 'textures': {'t': 'quicreate:item/fish/glass_eel'}}, open(tmpt, 'w'))
        paths = [tmp, tmpt]
    elif ns == 'quicreate':
        paths = [QMOD[f'quicreate:fish/{name}'], QMOD.get(f'quicreate:fish/{name}_tail')]
    else:
        paths = [f'{OFF}/models/fish/{name}.json', f'{OFF}/models/fish/{name}_tail.json']
    sv = side(paths, resolve)
    b = bucket(REF, shrink(sv), hab)
    b.save(f'/tmp/bk/out/{name}.png')
    sheet.paste(b.resize((128, 128), Image.NEAREST), (i * 136 + 4, 4))
sheet.save('/tmp/bk/sheet.png')
