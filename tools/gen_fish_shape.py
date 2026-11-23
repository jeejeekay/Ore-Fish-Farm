"""Builds a shared fish shape for Ore Fish Farm from a list of boxes and fins (side view, head toward +x):
models/fish_category/<shape>.json (+ _tail), a 32x32 texture painted per texel from where it sits on the fish,
a side-view preview, and an angled 16x16 item sprite made from the same model. See the shapes at the bottom."""
import json, math, sys
from PIL import Image

S = 2  # texture pixels per model unit

def face_dims(e, f):
    (x1, y1, z1), (x2, y2, z2) = e['from'], e['to']
    dx, dy, dz = x2 - x1, y2 - y1, z2 - z1
    return {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}[f]

def world(e, f, fu, fv):
    (x1, y1, z1), (x2, y2, z2) = e['from'], e['to']
    if f == 'south': return (x1 + fu * (x2 - x1), y2 - fv * (y2 - y1), z2)
    if f == 'north': return (x2 - fu * (x2 - x1), y2 - fv * (y2 - y1), z1)
    if f == 'east': return (x2, y2 - fv * (y2 - y1), z2 - fu * (z2 - z1))
    if f == 'west': return (x1, y2 - fv * (y2 - y1), z1 + fu * (z2 - z1))
    if f == 'up': return (x1 + fu * (x2 - x1), y2, z1 + fv * (z2 - z1))
    return (x1 + fu * (x2 - x1), y1, z2 - fv * (z2 - z1))  # down

def build(shape, body, tail, paint, credit, out_models, out_tex, tex_id):
    """Tries 2 texture pixels per unit, then less, until every face fits in 32x32."""
    global S
    for S in (2, 1.75, 1.5, 1.25):
        try:
            return _build(shape, body, tail, paint, credit, out_models, out_tex)
        except OverflowError:
            continue
    raise SystemExit(f'{shape}: does not fit in 32x32')

def _build(shape, body, tail, paint, credit, out_models, out_tex):
    faces = []
    for part, els in (('body', body), ('tail', tail)):
        for e in els:
            for f in e['faces']:
                w, h = face_dims(e, f)
                faces.append([part, e, f, max(1, round(w * S)), max(1, round(h * S))])
    # shelf packing, tallest first
    order = sorted(range(len(faces)), key=lambda i: -faces[i][4])
    x = y = shelf = 0
    for i in order:
        w, h = faces[i][3], faces[i][4]
        if w > 32: raise OverflowError
        if x + w > 32: x, y, shelf = 0, y + shelf, 0
        faces[i].append((x, y)); x += w; shelf = max(shelf, h)
    if y + shelf > 32: raise OverflowError
    tex = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); px = tex.load()
    for part, e, f, w, h, (u0, v0) in faces:
        for j in range(h):
            for i in range(w):
                c = paint(e.get('role', 'body'), f, *world(e, f, (i + 0.5) / w, (j + 0.5) / h))
                if c: px[u0 + i, v0 + j] = tuple(c) + ((255,) if len(c) == 3 else ())
        e.setdefault('_uv', {})[f] = [u0 / 2, v0 / 2, (u0 + w) / 2, (v0 + h) / 2]
    tex.save(out_tex)
    for part, els in (('body', body), ('tail', tail)):
        model = {'credit': credit, 'texture_size': [32, 32], 'elements': []}
        for e in els:
            el = {'name': e['name'], 'from': e['from'], 'to': e['to']}
            if 'rotation' in e: el['rotation'] = e['rotation']  # Minecraft: one axis, -45..45 in 22.5 steps
            el['faces'] = {f: {'texture': '#t', 'uv': e['_uv'][f]} for f in e['faces']}
            model['elements'].append(el)
        name = shape if part == 'body' else shape + '_tail'
        json.dump(model, open(f'{out_models}/{name}.json', 'w'), indent=1)
    return tex

def unrotate(e, wx, wy):
    """For a box turned about z (the axis toward the viewer), the point in the box's own unturned frame that lands
    on (wx, wy) in the side view. Boxes turned about other axes are drawn unturned (fine for small parts)."""
    r = e.get('rotation')
    if not r or r['axis'] != 'z': return wx, wy
    a = -math.radians(r['angle']); ox, oy = r['origin'][0], r['origin'][1]
    dx, dy = wx - ox, wy - oy
    return ox + dx * math.cos(a) - dy * math.sin(a), oy + dx * math.sin(a) + dy * math.cos(a)

def side_view(body, tail, paint, scale=24):
    """What the fish looks like from +z (its south side), drawn from the paint function."""
    img = Image.new('RGBA', (16 * scale, 16 * scale), (0, 0, 0, 0)); px = img.load()
    els = sorted([e for e in body + tail if 'south' in e['faces'] or 'north' in e['faces']], key=lambda e: e['to'][2])
    for e in els:
        f = 'south' if 'south' in e['faces'] else 'north'
        (x1, y1, z1), (x2, y2, z2) = e['from'], e['to']
        pad = 0.5 * max(x2 - x1, y2 - y1) if e.get('rotation') else 0
        for Y in range(int((y1 - pad) * scale), int(math.ceil((y2 + pad) * scale))):
            for X in range(int((x1 - pad) * scale), int(math.ceil((x2 + pad) * scale))):
                wx, wy = unrotate(e, (X + 0.5) / scale, (Y + 0.5) / scale)
                if not (x1 <= wx <= x2 and y1 <= wy <= y2): continue
                c = paint(e.get('role', 'body'), f, wx, wy, z2)
                if c: px[X, 16 * scale - 1 - Y] = tuple(c) + ((255,) if len(c) == 3 else ())
    return img

def sprite(side, angle=-32, outline=(38, 38, 44)):
    """An angled 16x16 item sprite (head up-left) from the side view: most common colour per cell, then an outline."""
    big = side.transpose(Image.FLIP_LEFT_RIGHT)  # head to the left
    bb = big.getbbox(); big = big.crop(bb)
    n = 128; w, h = big.size; k = (n * 0.92) / max(w, h * 1.0)
    big = big.resize((max(1, int(w * k)), max(1, int(h * k))), Image.NEAREST)
    canvas = Image.new('RGBA', (n, n), (0, 0, 0, 0)); canvas.paste(big, ((n - big.size[0]) // 2, (n - big.size[1]) // 2))
    rot = canvas.rotate(angle, resample=Image.NEAREST, center=(n / 2, n / 2))
    bb = rot.getbbox(); cx, cy = (bb[0] + bb[2]) / 2, (bb[1] + bb[3]) / 2
    rot = rot.transform(rot.size, Image.AFFINE, (1, 0, cx - n / 2, 0, 1, cy - n / 2), resample=Image.NEAREST)
    cell = n // 16; out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    filled = [[False] * 16 for _ in range(16)]
    for by in range(16):
        for bx in range(16):
            cnt = {}
            for y in range(by * cell, by * cell + cell):
                for x in range(bx * cell, bx * cell + cell):
                    p = rot.getpixel((x, y))
                    if p[3] > 0: cnt[p[:3]] = cnt.get(p[:3], 0) + 1
            if sum(cnt.values()) >= cell * cell * 0.4:
                out.putpixel((bx, by), max(cnt, key=cnt.get) + (255,)); filled[by][bx] = True
    for y in range(16):
        for x in range(16):
            if filled[y][x]: continue
            if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and filled[y + dy][x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), outline + (255,))
    return out
