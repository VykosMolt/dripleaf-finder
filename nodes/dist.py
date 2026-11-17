#!/usr/bin/env python3
import hashlib, os, shutil, sys, zipfile
HERE = os.path.dirname(os.path.abspath(__file__))
DIST = os.path.join(HERE, '..', 'dist')
releases = [l.split('\t')[0].strip() for l in open(os.path.join(HERE, 'nodes.tsv')) if l.strip() and not l.startswith('#')]

def props(mc):
    return dict(l.strip().split('=', 1) for l in open(os.path.join(HERE, mc, 'gradle.properties')) if '=' in l)

def jar(mc):
    return os.path.join(HERE, mc, 'build', 'libs', f"dripleaf-finder-{props(mc)['mod_version']}+mc{mc}.jar")

for mc in releases:
    if not os.path.exists(jar(mc)):
        sys.exit(f'{mc}: no jar ({jar(mc)}); build it first (nodes/build.sh {mc})')
addons = [mc for mc in releases if props(mc).get('autism') == 'true']
if len(addons) != 1:
    sys.exit(f'nodes.tsv needs exactly one AUTISM addon node, has {addons}')
addon = addons[0]
engines = [mc for mc in releases if props(mc).get('search_only') == 'true']

shutil.rmtree(DIST, ignore_errors=True)
os.makedirs(DIST)
bundled = {mc: open(jar(mc), 'rb').read() for mc in engines}
table = addon + '\n' + ''.join(f'{mc}\t{os.path.basename(jar(mc))}\t{hashlib.sha1(data).hexdigest()}\n' for mc, data in bundled.items())
out = os.path.join(DIST, os.path.basename(jar(addon)))
with zipfile.ZipFile(jar(addon)) as z, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as w:
    for info in z.infolist():
        w.writestr(info, z.read(info.filename))
    w.writestr('searchers/versions.tsv', table)
    for mc, data in bundled.items():
        w.writestr('searchers/' + os.path.basename(jar(mc)), data)
print(f'{os.path.basename(out)}: AUTISM addon for {addon}, searches {addon} itself and {", ".join(engines)} with the bundled engines')
