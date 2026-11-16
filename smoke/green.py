import sys
from PIL import Image
img = Image.open(sys.argv[1]).convert('RGB')
w, h = img.size
pts = [(x, y) for y in range(h) for x in range(w) for r, g, b in [img.getpixel((x, y))] if g >= 200 and r <= 90 and b <= 90]
if len(pts) < 20:
    print(f'{len(pts)} green pixels: no box')
    sys.exit()
cx, cy = sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts)
centred = abs(cx - w / 2) <= 20 and abs(cy - h / 2) <= 20
print(f'{len(pts)} green pixels around ({cx:.0f},{cy:.0f}) of {w}x{h}: ' + ('box centred' if centred else 'box off centre'))
