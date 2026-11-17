#!/usr/bin/env bash
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DIST=${1:-}
[ -f "$DIST" ] || { echo "usage: engines-all.sh <addon jar> [version ...]"; exit 2; }
DIST=$(realpath "$DIST")
shift
SLOT=${SLOT:-}
[ -x "$SLOT" ] || { echo "ALL ENGINES FAIL: set SLOT to the game-launch limiter"; exit 2; }
JAVA=${JAVA:-$HOME/.local/share/PrismLauncher/java/java-runtime-epsilon/bin/java}
FEATURE=$("$JAVA" -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.specification.version = //p')
[ "$FEATURE" = 25 ] || { echo "ALL ENGINES FAIL: Java 25 required, found ${FEATURE:-unknown}"; exit 2; }
JOBS=${JOBS:-4}
[[ "$JOBS" =~ ^[1-4]$ ]] || { echo "ALL ENGINES FAIL: JOBS must be 1 to 4"; exit 2; }
SEED=6608149111735331168
G=$HOME/.local/share/gradle/caches/modules-2/files-2.1
L=$HOME/.local/share/gradle/caches/fabric-loom
LOADER=$(ls "$G"/net.fabricmc/fabric-loader/0.20.1/*/fabric-loader-0.20.1.jar \
  "$G"/net.fabricmc/sponge-mixin/0.17.5+mixin.0.8.7/*/sponge-mixin-0.17.5+mixin.0.8.7.jar \
  "$G"/org.ow2.asm/asm*/9.10.1/*/asm*-9.10.1.jar \
  "$G"/io.github.llamalad7/mixinextras-fabric/0.5.5/*/mixinextras-fabric-0.5.5.jar 2>/dev/null | grep -v sources | paste -sd:)
[ -n "$LOADER" ] || { echo "ALL ENGINES FAIL: Fabric loader 0.20.1 jars missing"; exit 2; }
mkdir -p "$HERE/out"
W=${OUT:-$(mktemp -d "$HERE/out/all-engines-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX")}
mkdir -p "$W"
W=$(realpath "$W")
[ ! -e "$W/targets.tsv" ] || { echo "ALL ENGINES FAIL: output directory already contains a run: $W"; exit 2; }
export ALSOFT_DRIVERS=null ALSA_CONFIG_PATH=/dev/null

python3 -I - "$DIST" "$HERE/../nodes/nodes.tsv" "$W" "$@" <<'PY'
import hashlib, io, json, pathlib, re, sys, zipfile
addon, matrix, output, *requested = sys.argv[1:]
out = pathlib.Path(output)
expected = {line.split('\t')[0] for line in pathlib.Path(matrix).read_text().splitlines() if line.strip() and not line.startswith('#')}
try:
    with zipfile.ZipFile(addon) as jar:
        meta = json.loads(jar.read('fabric.mod.json'))
        client = meta['depends']['minecraft']
        if client != '26.2' or meta['id'] != 'dripleaf-finder' or 'autism' not in meta['entrypoints']:
            raise ValueError('expected the single 26.2 AUTISM addon')
        targets = {client: pathlib.Path(addon)}
        for line in jar.read('searchers/versions.tsv').decode().splitlines():
            version, name, wanted = line.split('\t')
            if version in targets or not re.fullmatch(r'[A-Za-z0-9_.+\-]+\.jar', name):
                raise ValueError(f'invalid or duplicate engine entry {version}')
            data = jar.read('searchers/' + name)
            if hashlib.sha1(data).hexdigest() != wanted:
                raise ValueError(f'{version}: bundled SHA-1 mismatch')
            with zipfile.ZipFile(io.BytesIO(data)) as engine:
                m = json.loads(engine.read('fabric.mod.json'))
                if m['id'] != 'dripleaf-finder' or m['depends']['minecraft'] != version:
                    raise ValueError(f'{version}: engine Minecraft metadata mismatch')
                if not m['version'].endswith('+mc' + version) or m['entrypoints'] != {'main': ['dripleaffinder.Searcher']}:
                    raise ValueError(f'{version}: wrong engine version or client entrypoint')
            target = out / 'engines' / name
            target.parent.mkdir(exist_ok=True)
            target.write_bytes(data)
            targets[version] = target
        if set(targets) != expected:
            raise ValueError(f'engine matrix mismatch: missing={sorted(expected-set(targets))}, extra={sorted(set(targets)-expected)}')
        versions = requested or [line.split('\t')[0] for line in pathlib.Path(matrix).read_text().splitlines() if line.strip() and not line.startswith('#')]
        if len(versions) != len(set(versions)) or not set(versions) <= expected:
            raise ValueError('requested versions contain an unknown or duplicate release')
        with (out / 'targets.tsv').open('w') as table:
            for version in versions:
                table.write(f'{version}\t{targets[version]}\n')
        with zipfile.ZipFile(out / 'fabric-api-base-standin.jar', 'w') as standin:
            standin.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'fabric-api-base', 'version': '0.0.0+dripleaf.search', 'name': 'Fabric API base stand-in (Dripleaf Finder seed search server)', 'environment': '*'}))
        (out / 'artifact.sha256').write_text(hashlib.sha256(pathlib.Path(addon).read_bytes()).hexdigest() + '\n')
except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
    print('ALL ENGINES FAIL:', error, file=sys.stderr)
    sys.exit(2)
print(f'PACKAGING PASS: {len(expected)} exact versions; {len(versions)} selected')
PY
[ $? = 0 ] || exit 2
printf 'Java %s; output %s\n' "$FEATURE" "$W"
if [ "${INSPECT_ONLY:-0}" = 1 ]; then cat "$W/targets.tsv"; exit 0; fi

sha1() { sha1sum "$1" | cut -d' ' -f1; }

server_jar() {
  local v=$1 jar=$L/$1/minecraft-server.jar info=$L/$1/mojang_minecraft_info.json url want
  if [ -f "$jar" ] && [ -f "$info" ] && [ "$(sha1 "$jar")" = "$(jq -r .downloads.server.sha1 "$info")" ]; then echo "$jar"; return; fi
  url=$(curl -fsS --connect-timeout 10 --max-time 60 https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | jq -r --arg v "$v" '.versions[] | select(.id == $v) | .url')
  [ -n "$url" ] && [ "$url" != null ] || return 1
  info=$(curl -fsS --connect-timeout 10 --max-time 60 "$url") || return 1
  mkdir -p "$W/cache/$v"
  curl -fsS --connect-timeout 10 --max-time 300 -o "$W/cache/$v/server.jar" "$(jq -r .downloads.server.url <<< "$info")" || return 1
  want=$(jq -r .downloads.server.sha1 <<< "$info")
  [ "$(sha1 "$W/cache/$v/server.jar")" = "$want" ] && echo "$W/cache/$v/server.jar"
}

intermediary() {
  local v=$1 jar url want
  url=https://maven.fabricmc.net/net/fabricmc/intermediary/$v/intermediary-$v.jar
  want=$(curl -fsS --connect-timeout 10 --max-time 60 "$url.sha1" | awk '{print $1}') || return 1
  [[ "$want" =~ ^[0-9a-f]{40}$ ]] || return 1
  jar=$(ls "$G"/net.fabricmc/intermediary/"$v"/*/intermediary-"$v".jar 2>/dev/null | head -1)
  if [ -n "$jar" ] && [ "$(sha1 "$jar")" = "$want" ]; then echo "$jar"; return; fi
  mkdir -p "$W/cache/$v"
  curl -fsS --connect-timeout 10 --max-time 120 -o "$W/cache/$v/intermediary.jar" "$url" || return 1
  [ "$(sha1 "$W/cache/$v/intermediary.jar")" = "$want" ] && echo "$W/cache/$v/intermediary.jar"
}

validate() {
  python3 -I - "$1" "$2" "$3" <<'PY'
import json, pathlib, re, sys
version, file, output = sys.argv[1:]
try:
    lines = [line.split('\t') for line in pathlib.Path(file).read_text().splitlines() if line]
    if not lines or any(row[0] == 'error' for row in lines):
        raise ValueError('missing output or engine error record')
    known = {'status', 'spawn', 'hit', 'lush', 'done'}
    if any(row[0] not in known for row in lines):
        raise ValueError('unknown output record')
    for row in lines:
        kind = row[0]
        if kind == 'status' and (len(row) != 2 or not row[1]):
            raise ValueError('malformed status record')
        if kind in {'spawn', 'hit', 'lush', 'done'}:
            size = {'spawn': 4, 'hit': 7, 'lush': 5, 'done': 3}[kind]
            if len(row) != size:
                raise ValueError(f'malformed {kind} record')
            count = 2 if kind == 'done' else 4 if kind == 'lush' else 3
            if any(not re.fullmatch(r'-?\d+', value) for value in row[1:count + 1]):
                raise ValueError(f'non-numeric {kind} record')
            if kind == 'hit' and (not row[4] or any(not re.fullmatch(r'-?\d+,-?\d+|-', value) for value in row[5:])):
                raise ValueError('malformed hit provenance')
            if kind == 'lush' and int(row[4]) <= 0:
                raise ValueError('non-positive lush floor count')
    hits = [row for row in lines if row[0] == 'hit']
    lush = [row for row in lines if row[0] == 'lush']
    done = [row for row in lines if row[0] == 'done']
    status = [row[1] for row in lines if row[0] == 'status']
    if len(done) != 1 or lines[-1][0] != 'done' or int(done[0][1]) != len(hits) or int(done[0][2]) < 0:
        raise ValueError('missing/duplicate/incorrect done record')
    if len({tuple(row[1:4]) for row in hits}) != len(hits):
        raise ValueError('duplicate hit position')
    no_natural = version in {'1.17', '1.17.1'}
    plants = 0
    if no_natural:
        if hits or lush or f'no natural lush caves in {version}' not in status:
            raise ValueError('1.17 must explicitly report no natural lush caves with no candidates')
    else:
        if not {'finding lush caves', 'scanning spawn', 'measuring floors'} <= set(status):
            raise ValueError('search phases did not complete')
        plants_at = [re.match(r'\d+ candidates among (\d+) small dripleaf blocks', value) for value in status]
        plants_at = [match for match in plants_at if match]
        if len(plants_at) != 1 or not any('still there after ticking' in value for value in status):
            raise ValueError('decoration/checking phases did not complete')
        plants = int(plants_at[0].group(1))
    result = {'version': version, 'hits': len(hits), 'lush': len(lush), 'plants': plants, 'seconds': int(done[0][2]), 'natural': not no_natural, 'limited': not no_natural and not lush, 'phases': status}
    pathlib.Path(output).write_text(json.dumps(result, indent=2) + '\n')
    print(f'{len(hits)} hits, {len(lush)} lush, {plants} plants, {done[0][2]} s')
    sys.exit(3 if result['limited'] else 0)
except (ValueError, OSError, IndexError) as error:
    print(str(error), file=sys.stderr)
    sys.exit(1)
PY
}

attempt() {
  local v=$1 mod=$2 x=$3 z=$4 r=$5 d=$6 cp=$LOADER game mapping pid status
  mkdir -p "$d"
  game=$(server_jar "$v") || { echo "no verified $v server jar" > "$d/error"; return 1; }
  if [[ "$v" == 1.* ]]; then
    mapping=$(intermediary "$v") || { echo "no verified $v intermediary" > "$d/error"; return 1; }
    cp+=:$mapping
  fi
  printf 'level-seed=%s\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nenable-status=false\nenable-query=false\nenable-rcon=false\nmax-tick-time=-1\npause-when-empty-seconds=-1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nsync-chunk-writes=false\nregion-file-compression=lz4\nspawn-monsters=false\nspawn-animals=false\nwhite-list=true\n' "$SEED" > "$d/server.properties"
  echo eula=true > "$d/eula.txt"
  printf 'out=results.tsv\nx=%s\nz=%s\nradius=%s\norder=asc\nfloors=true\nsplices=true\n' "$x" "$z" "$r" > "$d/search.properties"
  : > "$d/results.tsv"
  (
    cd "$d" || exit 1
    setsid "$SLOT" timeout 900 "$JAVA" -Xmx$((1024 + r / 3))M -XX:+ExitOnOutOfMemoryError \
      -Dfabric.gameJarPath="$game" -Dfabric.addMods="$mod:$W/fabric-api-base-standin.jar" \
      -Ddripleaffinder.search=search.properties -cp "$cp" net.fabricmc.loader.impl.launch.knot.KnotServer nogui > server.log 2>&1 &
    pid=$!
    trap 'kill -- "-$pid" 2>/dev/null || :' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
    wait "$pid"
    status=$?
    trap - EXIT
    echo "$status" > exit-code
    exit "$status"
  )
  status=$?
  rm -rf "$d/world" "$d/libraries" "$d/versions" "$d/.fabric"
  [ "$status" = 0 ] || { echo "server exited $status" > "$d/error"; return 1; }
  validate "$v" "$d/results.tsv" "$d/summary.json" > "$d/summary.txt" 2> "$d/error"
  status=$?
  [ -s "$d/error" ] || rm -f "$d/error"
  return "$status"
}

run() {
  trap 'exit 130' INT
  trap 'exit 143' TERM
  local v=$1 mod=$2 x=5988 z=5144 r=128 d="$W/run/$1" status
  case "$v" in
    1.17|1.17.1) x=0; z=0; r=64 ;;
    1.18*) x=2589; z=665 ;;
    1.19*|1.20*) x=3584; z=-3422 ;;
    1.21.3) x=6300; z=5190 ;;
    26.2) x=1500; z=-160; r=200 ;;
  esac
  attempt "$v" "$mod" "$x" "$z" "$r" "$d/first"
  status=$?
  if [ "$status" = 3 ]; then
    attempt "$v" "$mod" "$x" "$z" 256 "$d/fallback"
    status=$?
    d="$d/fallback"
  else
    d="$d/first"
  fi
  if [ "$status" = 0 ]; then
    printf '%s\tPASS\t%s\n' "$v" "$(cat "$d/summary.txt")" > "$W/$v.result"
  elif [ "$status" = 3 ]; then
    printf '%s\tLIMITED\tno lush floor output after the wider-area fallback\n' "$v" > "$W/$v.result"
  else
    printf '%s\tFAIL\t%s (%s)\n' "$v" "$(cat "$d/error" 2>/dev/null || echo 'runner stopped')" "$d/server.log" > "$W/$v.result"
  fi
}

cleanup() {
  local pid
  for pid in $(jobs -pr); do kill "$pid" 2>/dev/null || :; done
  wait || :
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
start=$SECONDS
while IFS=$'\t' read -r version mod; do
  while [ "$(jobs -pr | wc -l)" -ge "$JOBS" ]; do wait -n || :; done
  run "$version" "$mod" &
done < "$W/targets.tsv"
wait
fail=0
: > "$W/results.tsv"
while IFS=$'\t' read -r version mod; do
  if [ -f "$W/$version.result" ]; then
    cat "$W/$version.result" | tee -a "$W/results.tsv"
    [ "$(cut -f2 "$W/$version.result")" = PASS ] || fail=1
  else
    printf '%s\tFAIL\tno runner result\n' "$version" | tee -a "$W/results.tsv"
    fail=1
  fi
done < "$W/targets.tsv"
printf 'ALL ENGINES %s: %s exact versions on Java %s in %s s; %s\n' "$([ "$fail" = 0 ] && echo PASS || echo FAIL)" "$(wc -l < "$W/targets.tsv")" "$FEATURE" "$((SECONDS - start))" "$W/results.tsv"
exit "$fail"
