"""pipe_rim.png: the Fish Tank frame (fish_tank_frame.png) with the same pixel pattern, shades and plank lines,
made black. Run from the repo root: python tools/gen_pipe_rim.py"""
from PIL import Image
T = 'src/main/resources/assets/orefishfarm/textures/block/'
src = Image.open(T + 'fish_tank_frame.png').convert('RGBA')
out = Image.new('RGBA', src.size)
lum = [0.3 * r + 0.59 * g + 0.11 * b for (r, g, b, a) in src.getdata()]
lo, hi = min(lum), max(lum)
for i, (px, l) in enumerate(zip(src.getdata(), lum)):
    v = int(10 + (l - lo) / max(1, hi - lo) * 46)   # 10..56: black, keeping the frame's light and dark rows
    out.putpixel((i % src.width, i // src.width), (v, v, v + 4, 255))
out.save(T + 'pipe_rim.png')
print('pipe_rim.png written')
