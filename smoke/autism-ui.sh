#!/usr/bin/env bash
set -uo pipefail
[ $# -eq 1 ] && [ -f "$1" ] || { echo "usage: $0 <addon jar>"; exit 2; }
ADDON="$(realpath "$1")"
HERE="$(cd "$(dirname "$0")" && pwd)"
NODE="$HERE/../nodes/26.2"
RUN="$NODE/smoke-run"
WORK="$HERE/work/autism-ui"
OUT="$HERE/out/autism-ui"
prop() { sed -n "s/^$1=//p" "$NODE/gradle.properties"; }
AUTISM="${AUTISM_JAR:-$(prop autism_jar | sed "s|^~|$HOME|")}"
JDK="${JAVA_HOME:-$HOME/.local/share/gradle/jdks/eclipse_adoptium-25-amd64-linux.2}"
JAVA=~/.local/share/PrismLauncher/java/java-runtime-epsilon/bin/java
SERVER_JAR=~/.local/share/gradle/caches/fabric-loom/26.2/minecraft-server.jar
CACHE=~/.local/share/gradle/caches/modules-2/files-2.1
MCJAR="$(find "$NODE/.gradle/loom-cache/minecraftMaven" -name 'minecraft-*.jar' ! -name '*sources*' 2>/dev/null | head -1)"
FAPI="$(find "$CACHE/net.fabricmc.fabric-api/fabric-api" -name "fabric-api-$(prop fabric_api).jar" | head -1)"
LOADER="$(find "$CACHE/net.fabricmc/fabric-loader/$(prop loader)" -name "fabric-loader-$(prop loader).jar" | head -1)"
for f in "$AUTISM" "$SERVER_JAR" "$MCJAR" "$FAPI" "$LOADER"; do
  [ -f "$f" ] || { echo "AUTISM-UI FAIL: missing ${f:-a jar}; run ./build.sh 26.2 first"; exit 2; }
done

rm -rf "$WORK" "$OUT" && mkdir -p "$WORK/probe/classes" "$WORK/fapi" "$OUT"
unzip -qo "$FAPI" 'META-INF/jars/*' -d "$WORK/fapi"
"$JDK/bin/javac" -d "$WORK/probe/classes" -cp "$MCJAR:$(find "$WORK/fapi" -name '*.jar' | paste -sd:):$LOADER" \
  "$HERE/uiprobe/uiprobe/UiProbe.java" || { echo "AUTISM-UI FAIL: probe does not compile"; exit 2; }
printf '{"schemaVersion":1,"id":"uiprobe","version":"1","environment":"client","entrypoints":{"client":["uiprobe.UiProbe"]}}' \
  > "$WORK/probe/classes/fabric.mod.json"
"$JDK/bin/jar" cf "$WORK/probe/uiprobe.jar" -C "$WORK/probe/classes" .

pids=()
cleanup() {
  for p in "${pids[@]}"; do kill "$p" 2>/dev/null; done
  for p in "${pids[@]:0:2}"; do
    for _ in $(seq 60); do kill -0 "$p" 2>/dev/null || break; sleep 0.5; done
  done
  rm -rf "$WORK/a" "$WORK/b" "$WORK/fapi" "$RUN/dripleaf-finder/search"
}
trap cleanup EXIT
free_port() { python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])'; }
serve() {
  mkdir -p "$WORK/$1" && cd "$WORK/$1" && echo eula=true > eula.txt
  printf 'online-mode=false\nenforce-secure-profile=false\nserver-ip=127.0.0.1\nserver-port=%s\nlevel-type=flat\ngenerate-structures=false\nview-distance=4\nsimulation-distance=4\ngamemode=creative\nspawn-monsters=false\nspawn-animals=false\n' \
    "$2" > server.properties
  "$JAVA" -Xmx1G -jar "$SERVER_JAR" nogui < /dev/null > server.log 2>&1 &
  pids+=($!)
}
PORT_A=$(free_port)
serve a "$PORT_A"
PORT_B=$(free_port)
serve b "$PORT_B"
for s in a b; do
  for _ in $(seq 360); do grep -q 'Done (' "$WORK/$s/server.log" && break; sleep 0.5; done
  grep -q 'Done (' "$WORK/$s/server.log" || { echo "AUTISM-UI FAIL: server $s did not start"; exit 1; }
done

rm -rf "$RUN/uiprobe" "$RUN/screenshots" "$RUN/config/dripleaf" "$RUN/config/dripleaf-finder.json" "$RUN/dripleaf-finder/search"
mkdir -p "$RUN"
printf 'pauseOnLostFocus:false\nonboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\ntutorialStep:none\nrenderDistance:4\nguiScale:2\nsoundCategory_master:0.0\n' \
  > "$RUN/options.txt"
cd "$HERE/.."
ALSOFT_DRIVERS=null ALSA_CONFIG_PATH=/dev/null JAVA_HOME="$JDK" \
  gamescope --backend headless -W 854 -H 480 -w 854 -h 480 -- ./gradlew -p "$NODE" smokeClient --console=plain \
    "-Psmoke_jvm=-Xmx1536M" "-Psmoke_args=--username DripTest --quickPlayMultiplayer 127.0.0.1:$PORT_A" \
    "-Psmoke_mod=$ADDON" "-Psmoke_extra=$AUTISM:$WORK/probe/uiprobe.jar" > "$WORK/client.log" 2>&1 &
pids+=($!)
uv run --quiet --no-project --with pillow python3 "$HERE/autism_ui.py" "$RUN" "$OUT" "$PORT_A" "$PORT_B" "$ADDON" "${pids[2]}"
