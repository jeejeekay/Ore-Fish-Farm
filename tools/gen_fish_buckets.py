"""Bucket-of-fish sprites in vanilla's style: the shared bucket of vanilla's cod/salmon/tropical fish buckets, the
water bucket's rim and water behind the fish (moved down a pixel, like vanilla's fish buckets), and the fish lying
across the top, facing left, rendered side-on from its own tank model and shrunk into rows 1-5, columns 3-12.
Lava / void / acid habitats recolour the water. Usage: see the bottom."""
import json, os, sys
from PIL import Image

def load_model(path, resolve):
    m = json.load(open(path))
    if 'parent' in m and not m['parent'].startswith('minecraft:'):
        base = load_model(resolve('model', m['parent']), resolve)
        base_tex = dict(base.get('textures', {})); base_tex.update(m.get('textures', {}))
        base['textures'] = base_tex
        return base
    return m

def side(model_paths, resolve, scale=8):
    """Side view (from +z, head to the right) of a fish's body and tail models, at `scale` px per unit."""
    img = Image.new('RGBA', (16 * scale, 16 * scale), (0, 0, 0, 0)); px = img.load()
    els = []
    for mp in model_paths:
        if not mp or not os.path.exists(mp): continue
        m = load_model(mp, resolve)
        texs = {}
        for e in m['elements']:
            f = 'south' if 'south' in e['faces'] else 'north' if 'north' in e['faces'] else None
            if not f: continue
            ref = e['faces'][f]['texture'].lstrip('#')
            tid = m['textures'].get(ref, ref)
            while tid.startswith('#'): tid = m['textures'].get(tid[1:], tid)
            if tid not in texs: texs[tid] = Image.open(resolve('texture', tid)).convert('RGBA')
            els.append((e, f, texs[tid]))
    els.sort(key=lambda t: t[0]['to'][2])
    for e, f, tex in els:
        (x1, y1, z1), (x2, y2, z2) = e['from'], e['to']
        u0, v0, u1, v1 = e['faces'][f].get('uv', [x1, 16 - y2, x2, 16 - y1])
        W, H = tex.size
        for Y in range(int(y1 * scale), int(-(-y2 * scale // 1))):
            for X in range(int(x1 * scale), int(-(-x2 * scale // 1))):
                wx, wy = (X + 0.5) / scale, (Y + 0.5) / scale
                if not (x1 <= wx <= x2 and y1 <= wy <= y2) or x2 == x1 or y2 == y1: continue
                fu = (wx - x1) / (x2 - x1) if f == 'south' else (x2 - wx) / (x2 - x1)
                fv = (y2 - wy) / (y2 - y1)
                tu, tv = u0 + fu * (u1 - u0), v0 + fv * (v1 - v0)
                c = tex.getpixel((min(W - 1, int(tu / 16 * W)), min(H - 1, int(tv / 16 * H))))
                if c[3] > 0: px[X, 16 * scale - 1 - Y] = c
    return img

def shrink(img, w=10, h=5, chunk=2.0):
    """Fits the side view into the w x h slot (head to the LEFT, like vanilla's bucket fish): always the full width,
    and `chunk` times its natural height (vanilla's bucket fish are drawn chunky), 3 to h rows; most common colour
    per cell, a darker top and side edge, and a black eye near the head."""
    img = img.transpose(Image.FLIP_LEFT_RIGHT)
    img = img.crop(img.getbbox())
    W, H = img.size
    ow = w
    oh = max(3, min(h, round(H / W * w * chunk)))
    kx, ky = W / ow, H / oh
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0)); on = [[False] * w for _ in range(h)]
    y0 = h - oh  # sits on the bottom of the slot, in the bucket
    for cy in range(oh):
        for cx in range(ow):
            cnt = {}
            ys = range(int(cy * ky), max(int(cy * ky) + 1, min(H, int((cy + 1) * ky))))
            xs = range(int(cx * kx), max(int(cx * kx) + 1, min(W, int((cx + 1) * kx))))
            for y in ys:
                for x in xs:
                    p = img.getpixel((x, y))
                    if p[3] > 0: cnt[p[:3]] = cnt.get(p[:3], 0) + 1
            if sum(cnt.values()) >= len(ys) * len(xs) * 0.35:
                out.putpixel((cx, y0 + cy), max(cnt, key=cnt.get) + (255,)); on[y0 + cy][cx] = True
    for y in range(h):
        for x in range(w):
            if not on[y][x]: continue
            if any(not (0 <= x + dx < w and 0 <= y + dy < h and on[y + dy][x + dx]) for dx, dy in ((0, -1), (-1, 0), (1, 0))):
                r, g, b, a = out.getpixel((x, y)); out.putpixel((x, y), (int(r * 0.62), int(g * 0.62), int(b * 0.62), a))
    col = next((x for x in range(w) if any(on[y][x] for y in range(h))), None)
    if col is not None:
        ex = min(w - 1, col + 1); ey = next((y for y in range(h) if on[y][ex]), 0) + 1
        if ey < h and on[ey][ex]: out.putpixel((ex, ey), (18, 18, 22, 255))
    return out

TINTS = {  # habitat -> what vanilla's water blues become
    'water': None,
    'lava': [(208, 72, 16), (240, 128, 24), (252, 186, 52)],
    'void': [(52, 24, 76), (88, 44, 120), (132, 84, 168)],
    'acid': [(64, 120, 24), (110, 176, 36), (168, 220, 70)],
}

def is_water(p):
    r, g, b, a = p; return a > 0 and b > r + 40 and b > 110

def retint(p, pal):
    if pal is None or not is_water(p): return p
    lum = (p[0] + p[1] + p[2]) / 3
    i = 0 if lum < 90 else 1 if lum < 140 else 2
    return pal[i] + (p[3],)

def bucket(ref, fish_img, habitat):
    ims = [Image.open(f'{ref}/{n}.png').convert('RGBA') for n in ('cod_bucket', 'salmon_bucket', 'tropical_fish_bucket')]
    water = Image.open(f'{ref}/water_bucket.png').convert('RGBA')
    out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            ps = [im.getpixel((x, y)) for im in ims]
            if all(p == ps[0] for p in ps): out.putpixel((x, y), ps[0])
            elif y >= 1: out.putpixel((x, y), water.getpixel((x, y - 1)))  # behind the fish: rim and water, a pixel lower
    out.alpha_composite(fish_img, (3, 1))
    pal = TINTS.get(habitat)
    if pal:
        for y in range(16):
            for x in range(16):
                out.putpixel((x, y), retint(out.getpixel((x, y)), pal))
    return out
