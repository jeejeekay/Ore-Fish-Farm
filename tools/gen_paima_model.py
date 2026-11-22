import json, random
from PIL import Image
random.seed(4)
T = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); px = T.load()
BACK, SIDE, SIDE2, BELLY = (82, 86, 94), (140, 145, 152), (118, 123, 130), (196, 198, 201)
RUST, RUST2, HEAD, HEAD2, FIN = (174, 96, 60), (136, 72, 48), (104, 108, 116), (86, 90, 98), (96, 70, 58)
def rect(x0, y0, w, h, fn):
    for y in range(h):
        for x in range(w): px[x0 + x, y0 + y] = fn(x, y, w, h) + (255,)
def scales(x, y, w, h, rear_left=True):
    # scale rows: alternating offset, each scale lighter in front, its rear edge rust toward the tail
    t = x / max(1, w - 1) if rear_left else 1 - x / max(1, w - 1)   # 0 at the tail end, 1 at the head end
    edge = ((x + (y % 2) * 2) % 4) == 0
    base = SIDE if (x + y) % 2 else SIDE2
    if y == 0: base = BACK
    if y == h - 1: base = BELLY
    if edge and 0 < y < h - 1:
        return RUST if t < 0.45 else (RUST2 if t < 0.65 else BACK)
    return base
# body side 17x6 (left = tail, right = head end)
rect(0, 0, 17, 6, lambda x, y, w, h: scales(x, y, w, h))
# body top 17x5: dark back with a faint centre line
rect(0, 6, 17, 5, lambda x, y, w, h: BACK if y in (0, 4) else ((70, 74, 82) if y == 2 else (92, 96, 104)))
# body bottom 17x5: belly
rect(0, 11, 17, 5, lambda x, y, w, h: BELLY if 0 < y < 4 else (176, 178, 182))
# body rear end 5x6
rect(17, 0, 5, 6, lambda x, y, w, h: RUST2 if 0 < y < 5 else BACK)
# head side 10x4 (left = neck, right = snout): smooth plates, eye near the snout, mouth line under it
def head(x, y, w, h):
    if (x, y) == (8, 1): return (18, 18, 22)
    if (x, y) == (7, 1): return (210, 200, 150)
    if y == 3 and x >= 6: return (40, 40, 46)
    return HEAD2 if y == 0 else (HEAD if (x // 3 + y) % 2 else (112, 116, 124))
rect(0, 16, 10, 4, head)
rect(10, 16, 10, 4, lambda x, y, w, h: HEAD2 if y % 3 == 0 else HEAD)            # head top
rect(20, 16, 10, 4, lambda x, y, w, h: BELLY)                                     # head bottom
rect(22, 0, 4, 4, lambda x, y, w, h: (40, 40, 46) if y == 3 else HEAD)            # snout
rect(0, 20, 9, 2, lambda x, y, w, h: FIN if (x + y) % 3 else (76, 54, 44))        # dorsal side
rect(9, 20, 9, 2, lambda x, y, w, h: (76, 54, 44))                                # dorsal top
rect(18, 20, 4, 2, lambda x, y, w, h: FIN)                                        # belly fins
# tail paddle 4x6, rounded corners
for y in range(6):
    for x in range(4):
        if (x, y) in ((0, 0), (0, 5)): continue
        px[0 + x, 22 + y] = (RUST2 if x < 2 else RUST) + (255,)
T.save('ironpaima_model.png'); T.resize((512, 512), Image.NEAREST).save('model_big.png')

def f(uv): return {'texture': '#t', 'uv': uv}
CREDIT = ("Jake's Ore Fish Farm: shared 'paima' fish model (arapaima style: long rectangular body, small head; side view, head "
          "toward +x). A fish uses it with its own texture: {\"parent\": \"orefishfarm:fish_category/paima\", \"textures\": {\"t\": ..., \"particle\": ...}}")
body = {'credit': CREDIT, 'texture_size': [32, 32], 'elements': [
  {'name': 'body', 'from': [1.5, 6, 6.8], 'to': [9.8, 8.9, 9.2], 'faces': {
      'north': f([8.5, 0, 0, 3]), 'south': f([0, 0, 8.5, 3]), 'up': f([0, 3, 8.5, 5.5]), 'down': f([0, 5.5, 8.5, 8]),
      'west': f([8.5, 0, 11, 3])}},
  {'name': 'head', 'from': [9.8, 6.2, 7.1], 'to': [14.5, 7.75, 8.9], 'faces': {
      'north': f([5, 8, 0, 10]), 'south': f([0, 8, 5, 10]), 'up': f([5, 8, 10, 10]), 'down': f([10, 8, 15, 10]),
      'east': f([11, 0, 13, 2])}},
  {'name': 'dorsal', 'from': [1.9, 8.9, 7.6], 'to': [6.4, 9.7, 8.4], 'faces': {
      'north': f([4.5, 10, 0, 11]), 'south': f([0, 10, 4.5, 11]), 'up': f([4.5, 10, 9, 11]),
      'east': f([4, 10, 4.5, 11]), 'west': f([0, 10, 0.5, 11])}},
  {'name': 'rear_fin', 'from': [2.8, 5.55, 7.6], 'to': [3.7, 6, 8.4], 'faces': {
      'north': f([9, 10, 11, 11]), 'south': f([9, 10, 11, 11]), 'down': f([9, 10, 11, 11]), 'east': f([9, 10, 10, 11]), 'west': f([9, 10, 10, 11])}},
  {'name': 'front_fin', 'from': [8.2, 5.55, 7.5], 'to': [9.4, 6.2, 8.5], 'faces': {
      'north': f([9, 10, 11, 11]), 'south': f([9, 10, 11, 11]), 'down': f([9, 10, 11, 11]), 'east': f([9, 10, 10, 11]), 'west': f([9, 10, 10, 11])}},
]}
tail = {'credit': CREDIT, 'texture_size': [32, 32], 'elements': [
  {'name': 'tail', 'from': [0, 6.1, 8], 'to': [1.6, 8.8, 8], 'faces': {'north': f([0, 11, 2, 14]), 'south': f([2, 11, 0, 14])}}]}
json.dump(body, open('paima.json', 'w'), indent=1); json.dump(tail, open('paima_tail.json', 'w'), indent=1)
