#!/usr/bin/env bash
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
EXPECTED="$HERE/engines.tsv"
DIST=${1:-$(ls "$HERE"/../dist/dripleaf-finder-[0-9]*+mc26.2.jar 2>/dev/null)}
[ -f "$DIST" ] || { echo "usage: engines.sh [dist jar]  (no single dist/dripleaf-finder-*+mc26.2.jar)"; exit 2; }
DIST=$(realpath "$DIST")
SEED=6608149111735331168
JAVA=${JAVA:-~/.local/share/PrismLauncher/java/java-runtime-epsilon/bin/java}
SLOT=${SLOT:-}
G=~/.local/share/gradle/caches/modules-2/files-2.1
L=~/.local/share/gradle/caches/fabric-loom
LOADER=$(ls $G/net.fabricmc/fabric-loader/0.20.1/*/fabric-loader-0.20.1.jar $G/net.fabricmc/sponge-mixin/0.17.5+mixin.0.8.7/*/sponge-mixin-0.17.5+mixin.0.8.7.jar \
  $G/org.ow2.asm/asm*/9.10.1/*/asm*-9.10.1.jar $G/io.github.llamalad7/mixinextras-fabric/0.5.5/*/mixinextras-fabric-0.5.5.jar 2>/dev/null | grep -v sources | paste -sd:)
[ -n "$LOADER" ] || { echo "ENGINES FAIL: no Fabric loader 0.20.1 jars in the Gradle cache"; exit 2; }
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT

sha1() { sha1sum "$1" | cut -d' ' -f1; }

server_jar() {
  local v=$1 jar=$L/$1/minecraft-server.jar info=$L/$1/mojang_minecraft_info.json url want
  if [ -f "$jar" ] && [ -f "$info" ] && [ "$(sha1 "$jar")" = "$(jq -r .downloads.server.sha1 "$info")" ]; then echo "$jar"; return; fi
  url=$(curl -fsS https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | jq -r --arg v "$v" '.versions[] | select(.id == $v) | .url')
  info=$(curl -fsS "$url") || return 1
  mkdir -p "$W/$v" && curl -fsSo "$W/$v/server.jar" "$(jq -r .downloads.server.url <<< "$info")" || return 1
  want=$(jq -r .downloads.server.sha1 <<< "$info")
  [ "$(sha1 "$W/$v/server.jar")" = "$want" ] && echo "$W/$v/server.jar"
}

intermediary() {
  local v=$1 jar url
  jar=$(ls $G/net.fabricmc/intermediary/$v/*/intermediary-$v.jar 2>/dev/null | head -1)
  [ -n "$jar" ] && { echo "$jar"; return; }
  url=https://maven.fabricmc.net/net/fabricmc/intermediary/$v/intermediary-$v.jar
  mkdir -p "$W/$v" && curl -fsSo "$W/$v/intermediary.jar" "$url" || return 1
  [ "$(sha1 "$W/$v/intermediary.jar")" = "$(curl -fsS "$url.sha1" | cut -d' ' -f1)" ] && echo "$W/$v/intermediary.jar"
}

python3 -I - "$W/fabric-api-base-standin.jar" <<'EOF'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("fabric.mod.json", '{"schemaVersion": 1, "id": "fabric-api-base", "version": "0.0.0+dripleaf.search",'
        ' "name": "Fabric API base stand-in (Dripleaf Finder seed search server)", "environment": "*"}')
EOF

norm() {
  grep '^hit' "$1"
  grep '^lush' "$1" | LC_ALL=C sort
  sed -n -e '/^plants\t/p' -e 's/^status\t[0-9]* candidates among \([0-9]*\) small.*/plants\t\1/p' "$1"
}

run() {
  local v=$1 mod=$2 x=$3 z=$4 r=$5 order=$6 d="$W/run/$1-$6" cp=$LOADER game
  mkdir -p "$d"
  game=$(server_jar "$v") || { echo "no verified $v server jar" > "$d/error"; return; }
  if [[ $v == 1.* ]]; then cp+=:$(intermediary "$v") || { echo "no verified $v intermediary" > "$d/error"; return; }; fi
  printf 'level-seed=%s\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nenable-status=false\nenable-query=false\nenable-rcon=false\nmax-tick-time=-1\npause-when-empty-seconds=-1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nsync-chunk-writes=false\nregion-file-compression=lz4\nspawn-monsters=false\nspawn-animals=false\nwhite-list=true\n' "$SEED" > "$d/server.properties"
  echo eula=true > "$d/eula.txt"
  printf 'out=%s\nx=%s\nz=%s\nradius=%s\norder=%s\nfloors=true\nsplices=true\n' "$d/results.tsv" "$x" "$z" "$r" "$order" > "$d/search.properties"
  : > "$d/results.tsv"
  ( cd "$d" && $SLOT timeout 1200 "$JAVA" -Xmx$((1024 + r / 3))M -XX:+ExitOnOutOfMemoryError -Dfabric.gameJarPath="$game" \
      -Dfabric.addMods="$mod:$W/fabric-api-base-standin.jar" -Ddripleaffinder.search="$d/search.properties" -cp "$cp" \
      net.fabricmc.loader.impl.launch.knot.KnotServer nogui > server.log 2>&1 )
  rm -rf "$d/world" "$d/libraries" "$d/versions" "$d/.fabric"
  norm "$d/results.tsv" > "$d/got"
}

declare -A AREA=([1.21.11]="5988 5144 200" [1.21.3]="6300 5190 200" [26.2]="1500 -160 250")
declare -A MOD=([26.2]="$DIST")
fail=0
while IFS=$'\t' read -r v jar want; do
  [ -n "${jar:-}" ] || continue
  unzip -p "$DIST" "searchers/$jar" > "$W/$jar"
  got=$(sha1 "$W/$jar")
  [ "$got" = "$want" ] || { echo "$v: $jar SHA-1 $got, versions.tsv says $want"; fail=1; }
  MOD[$v]=$W/$jar
done < <(unzip -p "$DIST" searchers/versions.tsv)

start=$SECONDS
for v in 1.21.11 1.21.3 26.2; do
  for order in asc desc; do
    if [ -n "${MOD[$v]:-}" ]; then run "$v" "${MOD[$v]}" ${AREA[$v]} "$order" & else mkdir -p "$W/run/$v-$order"; echo "not in the dist jar" > "$W/run/$v-$order/error"; fi
  done
done
wait

: > "$W/all"
for v in 1.21.11 1.21.3 26.2; do
  for order in asc desc; do
    d="$W/run/$v-$order"
    [ -f "$d/got" ] && sed "s/^/$v\t$order\t/" "$d/got" >> "$W/all"
    if [ -f "$d/error" ]; then echo "$v $order FAIL $(cat "$d/error")"; fail=1; continue; fi
    awk -F'\t' -v v="$v" -v o="$order" '$1 == v && $2 == o' "$EXPECTED" | cut -f3- > "$d/expected"
    norm "$d/expected" > "$d/want"
    summary="$(grep -c '^hit' "$d/got") hits, $(grep -c '^lush' "$d/got") lush, $(sed -n 's/^plants\t//p' "$d/got") plants"
    if ! grep -q '^done' "$d/results.tsv"; then
      echo "$v $order FAIL search did not finish: $( (grep -m1 -E 'Incompatible|Exception|Error' "$d/server.log" || tail -1 "$d/server.log") | cut -c1-200)"; fail=1
    elif diff "$d/want" "$d/got" > "$d/diff"; then
      echo "$v $order PASS $summary"
    else
      echo "$v $order FAIL $summary"; sed 's/^/  /' "$d/diff"; fail=1
    fi
  done
done
[ -n "${RECORD:-}" ] && cp "$W/all" "$RECORD"
echo "ENGINES $([ $fail = 0 ] && echo PASS || echo FAIL) in $((SECONDS - start)) s"
exit $fail
