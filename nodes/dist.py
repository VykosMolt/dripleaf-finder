#!/usr/bin/env python3
import hashlib, json, os, shutil, sys, zipfile
HERE = os.path.dirname(os.path.abspath(__file__))
DIST = os.path.join(HERE, '..', 'dist')
releases = [l.split('\t')[0].strip() for l in open(os.path.join(HERE, 'nodes.tsv')) if l.strip() and not l.startswith('#')]

def props(mc):
    return dict(l.strip().split('=', 1) for l in open(os.path.join(HERE, mc, 'gradle.properties')) if '=' in l)

def has_autism(mc):
    return props(mc).get('autism') == 'true'

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

for mc in releases:
    for path in [jar(mc)] + ([jar(mc, 'dripleaf-finder-autism')] if has_autism(mc) else []):
        if not os.path.exists(path):
            sys.exit(f'{mc}: no jar ({path}); build it first (nodes/build.sh {mc})')
shutil.rmtree(DIST, ignore_errors=True)
os.makedirs(DIST)

groups = []
for mc in releases:
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
    ranges += [(mc, name) for mc in versions]
    print(f'{name}: {", ".join(versions)}')
open(os.path.join(DIST, 'ranges.tsv'), 'w').write(''.join(f'{mc}\t{name}\n' for mc, name in ranges))

plain = {name: open(os.path.join(DIST, name), 'rb').read() for name in sorted({name for _, name in ranges})}
for mc in releases:
    if has_autism(mc):
        shutil.copy(jar(mc, 'dripleaf-finder-autism'), DIST)
        print(os.path.basename(jar(mc, 'dripleaf-finder-autism')) + ': ' + mc + ' (AUTISM addon)')

table = ''.join(f'{mc}\t{name}\t{hashlib.sha1(plain[name]).hexdigest()}\n' for mc, name in ranges)
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
