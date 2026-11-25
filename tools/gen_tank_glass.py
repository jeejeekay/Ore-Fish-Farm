"""Aquarium glass textures (Ore Fish Farm 2.25.0): tank_glass_<mask>.png, 16 x 16, 1 texel per pixel.

The same glass as the pipes (tools/gen_pipe_models.py): a faint see-through body that fades in over 2 px at each edge
in the mask (u-min 1, u-max 2, v-min 4, v-max 8, in the face's own texture space), and is never solid.
TankExtrasModel picks the mask per face from TankGeometry's fades and lines the texture up: across a wall the texels
follow the block (so walls line up block to block), up a wall the fading end sits on the face's fading edge.
Run from the repo root: python tools/gen_tank_glass.py
"""
import glob, os
from PIL import Image

TX = 'src/main/resources/assets/orefishfarm/textures/block'
BODY = (222, 243, 252, 30)
FADE = [(236, 250, 255, 135), (226, 246, 254, 72)]

for old in glob.glob(TX + '/tank_glass_[0-9]*.png'):
    os.remove(old)
for mask in range(16):
    im = Image.new('RGBA', (16, 16))
    for u in range(16):
        for v in range(16):
            best = BODY
            for bit, dist in ((1, u), (2, 15 - u), (4, v), (8, 15 - v)):
                if mask & bit and dist < len(FADE) and FADE[dist][3] > best[3]:
                    best = FADE[dist]
            im.putpixel((u, v), best)
    im.save(f'{TX}/tank_glass_{mask}.png')
print('16 tank glass textures')
