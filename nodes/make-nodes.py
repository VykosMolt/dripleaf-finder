#!/usr/bin/env python3
import os
HERE = os.path.dirname(os.path.abspath(__file__))
SETTINGS = '''pluginManagement {{
    repositories {{
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }}
}}
rootProject.name = "dripleaf-finder-{mc}"
'''
for line in open(os.path.join(HERE, 'nodes.tsv')):
    if not line.strip() or line.startswith('#'):
        continue
    f = line.rstrip('\n').split('\t')
    mc, java, fapi, template, eras = f[:5]
    extra = dict(kv.split('=', 1) for kv in f[5].split(',')) if len(f) > 5 and f[5] else {}
    d = os.path.join(HERE, mc)
    os.makedirs(d, exist_ok=True)
    open(os.path.join(d, 'settings.gradle.kts'), 'w').write(SETTINGS.format(mc=mc))
    props = {'mc': mc, 'mc_range': extra.pop('mc_range', mc), 'range_name': extra.pop('range_name', mc), 'java': java,
             'fabric_api': fapi, 'loader': '0.20.1', 'eras': eras, 'autism': extra.pop('autism', 'false'),
             'mod_version': '1.2.0', 'org.gradle.jvmargs': '-Xmx3G',
             'org.gradle.java.installations.paths': ','.join(os.path.expanduser(f'~/.local/share/PrismLauncher/java/java-runtime-{r}')
                                                              for r in ('gamma', 'delta', 'epsilon')),
             'org.gradle.java.installations.auto-download': 'false'}
    props.update(extra)
    open(os.path.join(d, 'gradle.properties'), 'w').write(''.join(f'{k}={v}\n' for k, v in props.items()))
    link = os.path.join(d, 'build.gradle.kts')
    if os.path.lexists(link):
        os.remove(link)
    os.symlink(f'../../gradle/node-{template}.gradle.kts', link)
print('nodes written')
