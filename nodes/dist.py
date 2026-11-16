#!/usr/bin/env python3
import hashlib, io, json, os, shutil, sys, zipfile
HERE = os.path.dirname(os.path.abspath(__file__))
DIST = os.path.join(HERE, '..', 'dist')
rows = [l.rstrip('\n').split('\t') for l in open(os.path.join(HERE, 'nodes.tsv')) if l.strip() and not l.startswith('#')]
props = lambda mc: dict(l.strip().split('=', 1) for l in open(os.path.join(HERE, mc, 'gradle.properties')) if '=' in l)

def jar(mc, base='dripleaf-finder'):
    p = props(mc)
    return os.path.join(HERE, mc, 'build', 'libs', f"{base}-{p['mod_version']}+mc{p['range_name']}.jar")

def signature(path):
    with zipfile.ZipFile(path) as z:
        sig = []
        for name in sorted(z.namelist()):
            data = z.read(name)
            if name == 'fabric.mod.json':
                meta = json.loads(data)
                meta.pop('version')
                meta['depends'].pop('minecraft')
                data = json.dumps(meta, sort_keys=True).encode()
            elif name == 'META-INF/MANIFEST.MF':
                data = b''.join(l for l in data.splitlines(True) if not l.startswith(b'Fabric-Minecraft-Version'))
            sig.append((name, hashlib.sha256(data).hexdigest()))
        return tuple(sig)

def write_range(src, dst, versions):
    first, last = versions[0], versions[-1]
    version = props(first)['mod_version'] + '+mc' + (first if first == last else f'{first}-{last}')
    with zipfile.ZipFile(src) as z, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as out:
        for info in z.infolist():
            data = z.read(info.filename)
            if info.filename == 'fabric.mod.json':
                meta = json.loads(data)
                meta['version'] = version
                meta['depends']['minecraft'] = versions if len(versions) > 1 else versions[0]
                data = (json.dumps(meta, indent=2) + '\n').encode()
            out.writestr(info, data)
    return version

for r in rows:
    for path in [jar(r[0])] + ([jar(r[0], 'dripleaf-finder-autism')] if props(r[0]).get('autism') == 'true' else []):
        if not os.path.exists(path):
            sys.exit(f'{r[0]}: no jar ({path}); build it first (nodes/build.sh {r[0]})')
shutil.rmtree(DIST, ignore_errors=True)
os.makedirs(DIST)
groups = []
for r in rows:
    mc = r[0]
    sig = signature(jar(mc))
    if groups and groups[-1][1] == sig:
        groups[-1][0].append(mc)
    else:
        groups.append(([mc], sig))
ranges = []
for versions, _ in groups:
    first, last = versions[0], versions[-1]
    name = f"dripleaf-finder-{props(first)['mod_version']}+mc{first if first == last else first + '-' + last}.jar"
    write_range(jar(first), os.path.join(DIST, name), versions)
    ranges += [f'{v}\t{name}\n' for v in versions]
    print(f'{name}: {", ".join(versions)}')
open(os.path.join(DIST, 'ranges.tsv'), 'w').write(''.join(ranges))
plain = {name: open(os.path.join(DIST, name), 'rb').read() for name in sorted({line.split('\t')[1].strip() for line in ranges})}
for r in rows:
    p = props(r[0])
    if p.get('autism') == 'true':
        shutil.copy(jar(r[0], 'dripleaf-finder-autism'), DIST)
        print(os.path.basename(jar(r[0], 'dripleaf-finder-autism')) + ': ' + r[0] + ' (AUTISM addon)')

table = ''.join(f"{line.split(chr(9))[0]}\t{line.split(chr(9))[1].strip()}\t{hashlib.sha1(plain[line.split(chr(9))[1].strip()]).hexdigest()}\n" for line in ranges)
for name in sorted(os.listdir(DIST)):
    if not name.endswith('.jar'):
        continue
    path = os.path.join(DIST, name)
    with zipfile.ZipFile(path) as z:
        entries = [(info, z.read(info.filename)) for info in z.infolist()]
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as out:
        for info, data in entries:
            out.writestr(info, data)
        out.writestr('searchers/versions.tsv', table)
        for jar_name, data in plain.items():
            out.writestr('searchers/' + jar_name, data)
print(f'searchers bundled: {len(plain)} range jars in each of {len([n for n in os.listdir(DIST) if n.endswith(".jar")])} jars')
