#!/usr/bin/env python3
import re, sys, zipfile, pathlib
jar, out = zipfile.ZipFile(sys.argv[1]), pathlib.Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
DIST = r'(vertexDistance|sphericalVertexDistance|cylindricalVertexDistance)'
for ext in ('vsh', 'fsh'):
    src = jar.read(f'assets/minecraft/shaders/core/rendertype_lines.{ext}').decode()
    lines = []
    for line in src.splitlines():
        if re.match(r'\s*#moj_import <(minecraft:)?fog\.glsl>', line): continue
        if re.match(rf'\s*(in|out) float {DIST};', line): continue
        if re.match(rf'\s*{DIST} = .*;', line): continue
        fog = re.match(r'(\s*fragColor = )(linear_fog|apply_fog)\((\w+(?: \* \w+)?),.*\);', line)
        if fog: line = fog.group(1) + fog.group(3) + ';'
        lines.append(line)
    text = '\n'.join(lines) + '\n'
    if re.search(r'fog|Distance', '\n'.join(l for l in lines if not l.lstrip().startswith('uniform')), re.I):
        sys.exit(f'{ext}: fog left over:\n{text}')
    (out / f'lines_fogless.{ext}').write_text(text)
print('ok', out)
