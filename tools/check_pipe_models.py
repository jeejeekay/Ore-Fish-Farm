"""Checks the generated pipe models on test layouts: every glass edge meets exactly one other glass face (or sits
under a rim collar), it fades exactly where the surface folds, no glass faces overlap, and each glass face has its
water twin. Run from the repo root after gen_pipe_models.py: python tools/check_pipe_models.py"""
import json, itertools
from fractions import Fraction as F
from PIL import Image

ROOT = 'src/main/resources/assets/orefishfarm'
BS = json.load(open(ROOT + '/blockstates/pipe_tank.json'))['multipart']
DIRS = ['north', 'south', 'west', 'east', 'up', 'down']
VEC = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
UV_AXES = {'up': (('x', 1), ('z', 1)), 'down': (('x', 1), ('z', -1)), 'north': (('x', -1), ('y', -1)),
           'south': (('x', 1), ('y', -1)), 'west': (('z', 1), ('y', -1)), 'east': (('z', -1), ('y', -1))}
MODELS, TEX = {}, {}


def model(name):
    if name not in MODELS:
        MODELS[name] = json.load(open(f"{ROOT}/models/block/pipe/{name.split('/')[-1]}.json"))
    return MODELS[name]


def tex(ref):
    if ref not in TEX:
        TEX[ref] = Image.open(f"{ROOT}/textures/block/{ref.split('/')[-1]}.png").convert('RGBA')
    return TEX[ref]


def shaped(pipes, inv, p):
    sides = {}
    for d in DIRS:
        n = tuple(a + b for a, b in zip(p, VEC[d]))
        sides[d] = 'pipe' if n in pipes else 'rim' if n in inv else 'none'
    joined = sum(s != 'none' for s in sides.values())
    if joined == 0:
        sides['east'] = sides['west'] = 'rim'
    elif joined == 1:
        d = next(d for d in DIRS if sides[d] != 'none')
        opp = tuple(-c for c in VEC[d])
        sides[next(e for e in DIRS if VEC[e] == opp)] = 'rim'
    return sides


def faces_of(state, pos):
    """World-space glass quads: (face, from, to, fades-at-edge function) and water quads."""
    glass, water, rims = [], [], []
    for part in BS:
        if not all(state[k] in v.split('|') for k, v in part['when'].items()):
            continue
        m = model(part['apply']['model'])
        for e in m['elements']:
            f = [F(pos[i] * 16) + F(str(e['from'][i])) for i in range(3)]
            t = [F(pos[i] * 16) + F(str(e['to'][i])) for i in range(3)]
            for face, spec in e['faces'].items():
                ref = m['textures'][spec['texture'][1:]]
                if 'rim' in ref:
                    rims.append((f, t))
                elif 'water' in ref:
                    water.append((face, f, t))
                else:
                    glass.append((face, f, t, tex(ref), spec['uv']))
    return glass, water, rims


def edges(face, f, t):
    """The 4 edges of a quad: (direction it points, segment endpoints)."""
    i = 'xyz'.index
    out = []
    n = [abs(c) for c in VEC[face]].index(1)
    for a in range(3):
        if a == n:
            continue
        for lo_hi, sign in ((f[a], -1), (t[a], 1)):
            b = 3 - n - a
            p0, p1 = list(f), list(f)
            p0[a] = p1[a] = lo_hi
            p0[n] = p1[n] = f[n]
            p0[b], p1[b] = f[b], t[b]
            d = next(dd for dd in DIRS if VEC[dd]['xyz'.index('xyz'[a])] == sign and abs(VEC[dd][a]) == 1)
            out.append((d, (tuple(p0), tuple(p1))))
    return out


def edge_fades(face, f, t, im, uv, d):
    """Does the texture fade along the edge pointing to d? (alpha of the texels on that edge vs the body)"""
    (ua, us), (va, vs) = UV_AXES[face]
    u0, v0, u1, v1 = uv
    if VEC[d]['xyz'.index(ua)] != 0:
        side_min = (VEC[d]['xyz'.index(ua)] * us) < 0
        u = u0 if side_min else u1 - 1
        texels = [im.getpixel((int(u), int(v)))[3] for v in range(int(v0), int(v1))]
    else:
        side_min = (VEC[d]['xyz'.index(va)] * vs) < 0
        v = v0 if side_min else v1 - 1
        texels = [im.getpixel((int(u), int(v)))[3] for u in range(int(u0), int(u1))]
    mid = im.getpixel((2, 2))[3] if False else 30
    # an edge fades if its middle texels are above the body alpha
    core = texels[1:-1] if len(texels) > 2 else texels
    return all(a > mid for a in core)


def check(name, pipes, inv=()):
    pipes, inv = set(pipes), set(inv)
    glass, water, rims = [], [], []
    for p in pipes:
        g, w, r = faces_of(shaped(pipes, inv, p), p)
        glass += g; water += w; rims += r
    problems = []
    # water twins
    for face, f, t, im, uv in glass:
        n = [abs(c) for c in VEC[face]].index(1)
        s = sum(VEC[face])
        twin = [list(f), list(t)]
        twin[0][n] -= s * F(1, 10); twin[1][n] -= s * F(1, 10)
        if not any(wf == face and wf2 == twin[0] and wt == twin[1] for wf, wf2, wt in water):
            problems.append(f'no water twin for {face} {f}')
    # edges
    edge_map = {}
    for gi, (face, f, t, im, uv) in enumerate(glass):
        for d, seg in edges(face, f, t):
            key = tuple(sorted(seg))
            edge_map.setdefault(key, []).append((gi, face, d))
    for key, users in edge_map.items():
        under_rim = any(all(rf[i] <= key[0][i] <= rt[i] and rf[i] <= key[1][i] <= rt[i] for i in range(3)) for rf, rt in rims)
        if len(users) == 1:
            if not under_rim:
                problems.append(f'open glass edge {[float(c) for c in key[0]]}->{[float(c) for c in key[1]]} ({users[0][1]})')
            continue
        if len(users) != 2:
            problems.append(f'{len(users)} faces on one edge {key}')
            continue
        (g1, f1, d1), (g2, f2, d2) = users
        fold = f1 != f2
        for gi, face, d in users:
            _, f, t, im, uv = glass[gi]
            if edge_fades(face, f, t, im, uv, d) != fold:
                problems.append(f'{"plain" if fold else "faded"} edge where the surface {"folds" if fold else "runs on"}: {face} toward {d} at {[float(c) for c in key[0]]}')
    # overlaps (coplanar, same facing, interiors intersect)
    for (a, b) in itertools.combinations(range(len(glass)), 2):
        fa, A0, A1 = glass[a][:3]
        fb, B0, B1 = glass[b][:3]
        if fa != fb:
            continue
        n = [abs(c) for c in VEC[fa]].index(1)
        if A0[n] != B0[n]:
            continue
        if all(max(A0[i], B0[i]) < min(A1[i], B1[i]) for i in range(3) if i != n):
            problems.append(f'overlapping {fa} glass at {A0}')
    print(f'{name:28s} {len(glass):4d} glass faces  ' + ('OK' if not problems else f'{len(problems)} PROBLEMS'))
    for pr in problems[:8]:
        print('    ', pr)
    return not problems


ok = True
L = lambda *ps: list(ps)
ok &= check('lone pipe', L((0, 0, 0)))
ok &= check('straight run of 4', [(x, 0, 0) for x in range(4)])
ok &= check('run into chests', [(x, 0, 0) for x in range(3)], [(-1, 0, 0), (3, 0, 0)])
ok &= check('L bend', L((0, 0, 0), (1, 0, 0), (1, 0, 1)))
ok &= check('T junction', L((0, 0, 0), (1, 0, 0), (2, 0, 0), (1, 0, 1)))
ok &= check('cross', L((1, 0, 1), (0, 0, 1), (2, 0, 1), (1, 0, 0), (1, 0, 2)))
ok &= check('6-way hub', L((0, 0, 0)) + [VEC[d] for d in DIRS])
ok &= check('3D corner', L((0, 0, 0), (1, 0, 0), (0, 1, 0), (0, 0, 1)))
ok &= check('2x2 loop', L((0, 0, 0), (1, 0, 0), (0, 0, 1), (1, 0, 1)))
ok &= check('2x2 loop, vertical', L((0, 0, 0), (1, 0, 0), (0, 1, 0), (1, 1, 0)))
ok &= check('3x3 ring', [(x, 0, z) for x in range(3) for z in range(3) if (x, z) != (1, 1)])
ok &= check('2x2x2 cube', [(x, y, z) for x in range(2) for y in range(2) for z in range(2)])
ok &= check('staircase', L((0, 0, 0), (1, 0, 0), (1, 1, 0), (2, 1, 0), (2, 2, 0)))
ok &= check('chest on a bend', L((0, 0, 0), (1, 0, 0)), L((1, 1, 0), (0, 0, 1)))
ok &= check('filled 3x3 slab', [(x, 0, z) for x in range(3) for z in range(3)])
print('ALL OK' if ok else 'FAILURES')
