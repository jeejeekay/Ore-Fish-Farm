from PIL import Image, ImageDraw
S = 128
L = Image.new('L', (S, S), 0); d = ImageDraw.Draw(L)
# labels: 1 back, 2 side, 3 belly, 4 head, 5 rust, 6 eye, 7 fin, 8 tail
cx = 0
K=1.22
def T(x,y): return (64+(x-66)*K, 64+(y-63)*K)
def R(x0, y0, x1, y1, v): d.rectangle([*T(x0,y0), *T(x1,y1)], fill=v)
# body (long rectangle), head small at the left, dorsal along the rear back, tail paddle at the right
R(34, 52, 102, 58, 1); R(34, 59, 102, 69, 2); R(34, 70, 102, 76, 3)
R(70, 59, 102, 69, 5)                       # rust-tipped scales toward the tail
R(10, 58, 34, 66, 4); R(10, 67, 34, 74, 3)  # head (upper darker, jaw pale)

R(46, 45, 92, 51, 7)                        # dorsal ridge
R(40, 77, 47, 81, 7); R(84, 77, 92, 81, 7)  # belly fins
d.ellipse([*T(100, 49), *T(122, 79)], fill=8)       # rounded tail
R(100, 52, 104, 76, 2)
lab = L.rotate(-32, resample=Image.NEAREST, center=(64, 64))
# majority per 8x8 block -> 16x16
small = [[0] * 16 for _ in range(16)]
for by in range(16):
    for bx in range(16):
        cnt = {}
        for y in range(by * 8, by * 8 + 8):
            for x in range(bx * 8, bx * 8 + 8):
                v = lab.getpixel((x, y)); cnt[v] = cnt.get(v, 0) + 1
        filled = sum(c for v, c in cnt.items() if v)
        if filled >= 24:
            best = max((c, v) for v, c in cnt.items() if v)[1]
            if cnt.get(6, 0) >= 6: best = 6
            small[by][bx] = best
PAL = {1: (88, 92, 100), 2: (150, 154, 160), 3: (205, 207, 210), 4: (120, 124, 132), 5: (176, 98, 62),
       6: (24, 24, 28), 7: (104, 74, 60), 8: (128, 84, 64)}
OUT = (38, 38, 44)
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for y in range(16):
    for x in range(16):
        if small[y][x]: img.putpixel((x, y), PAL[small[y][x]] + (255,))
for y in range(16):
    for x in range(16):
        if small[y][x]: continue
        if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and small[y + dy][x + dx] for dx, dy in ((1,0),(-1,0),(0,1),(0,-1))):
            img.putpixel((x, y), OUT + (255,))
# eye: the head's frontmost-top filled cell, one in from the outline
cands=[(x+y, x, y) for y in range(16) for x in range(16) if small[y][x]==4]
if cands:
    _, ex, ey = min(cands); ex += 1
    img.putpixel((ex, ey), (20, 20, 24, 255))
img.save('ironpaima.png'); img.resize((256, 256), Image.NEAREST).save('ironpaima_big.png')
for row in small: print(''.join(' .#=oR*fT'[v] if v else ' ' for v in row))
