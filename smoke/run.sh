#!/usr/bin/env bash
set -uo pipefail
main() {
MC=$1
MODE=${2:-planted}
HERE="$(cd "$(dirname "$0")" && pwd)"
NODE="$HERE/../nodes/$MC"
[ -d "$NODE" ] || { echo "SMOKE FAIL $MC $MODE: no node"; exit 2; }
prop() { sed -n "s/^$1=//p" "$NODE/gradle.properties"; }
case "$(prop java)" in 16|17) RT=gamma ;; 21) RT=delta ;; *) RT=epsilon ;; esac
JAVA=~/.local/share/PrismLauncher/java/java-runtime-$RT/bin/java
SERVER_JAR=~/.local/share/gradle/caches/fabric-loom/$MC/minecraft-server.jar
[ -f "$SERVER_JAR" ] || { echo "SMOKE FAIL $MC $MODE: no server jar (build the node first)"; exit 2; }
WORK="$HERE/work/$MC-$MODE"
rm -rf "$WORK" && mkdir -p "$WORK/server" "$HERE/out"
RUN="$NODE/smoke-run"
rm -rf "$RUN/screenshots" "$RUN/config/dripleaf" "$RUN/logs/latest.log" "$RUN/config/seedcracker.json" \
  "$RUN/config/SeedCrackerX saved structures" && mkdir -p "$RUN"
if [ "${7:-}" = scx ]; then
  SCX="$(awk -F'\t' -v v="$MC" '$1 == v {print $2}' "$HERE/seedcracker/versions.tsv")"
  [ -n "$SCX" ] || { echo "SMOKE FAIL $MC $MODE: no SeedcrackerX release for $MC"; exit 2; }
  SMOKE_EXTRA="$HERE/seedcracker/$SCX${SMOKE_EXTRA:+:$SMOKE_EXTRA}"
fi
PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
NEGATIVE=""
python3 -c 'import sys; v = [int(x) for x in sys.argv[1].split(".")] + [0]; sys.exit(0 if v >= [1, 19, 3] else 1)' "$MC" && NEGATIVE=4,101,40
pids=()
cleanup() { for p in "${pids[@]}"; do kill "$p" 2>/dev/null; done; }
trap cleanup EXIT

cd "$WORK/server"
echo eula=true > eula.txt
COMMON="online-mode=false
enforce-secure-profile=false
server-ip=127.0.0.1
server-port=$PORT
spawn-protection=0
view-distance=4
simulation-distance=4
max-tick-time=-1
sync-chunk-writes=false"
if [ "$MODE" = realworld ]; then
  printf '%s\nlevel-seed=%s\ngamemode=spectator\nforce-gamemode=true\nspawn-monsters=false\n' "$COMMON" "$3" > server.properties
else
  printf '%s\nlevel-type=flat\ngenerate-structures=false\nspawn-monsters=false\nspawn-animals=false\ngamemode=creative\n%s\n' "$COMMON" \
    'generator-settings={"structures":{"structures":{}},"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":3}],"biome":"minecraft:lush_caves"}' \
    > server.properties
fi
if [ "$MODE" != realworld ] && [[ "$MC" == 1.17* || "$MC" == 1.18* ]]; then
    sed -i '/^generator-settings=/d' server.properties
    echo stop | timeout 300 "$JAVA" -Xmx2G -jar "$SERVER_JAR" nogui > first.log 2>&1
    python3 - world/level.dat <<'PATCH' || { echo "SMOKE FAIL $MC $MODE: level.dat patch failed"; exit 1; }
import gzip, sys
data = gzip.decompress(open(sys.argv[1], 'rb').read())
old, new = b'minecraft:plains', b'minecraft:lush_caves'
if data.count(len(old).to_bytes(2, 'big') + old) == 0: sys.exit('no plains biome in level.dat')
data = data.replace(len(old).to_bytes(2, 'big') + old, len(new).to_bytes(2, 'big') + new)
open(sys.argv[1], 'wb').write(gzip.compress(data))
PATCH
    rm -rf world/region world/entities world/poi
  fi
  mkfifo cmd
"$JAVA" -Xmx1536M -jar "$SERVER_JAR" nogui < cmd > server.log 2>&1 &
pids+=($!)
exec 3> cmd
say() { printf '%s\n' "$@" >&3; }
for _ in $(seq 360); do grep -q 'Done (' server.log && break; sleep 0.5; done
grep -q 'Done (' server.log || { echo "SMOKE FAIL $MC $MODE: server did not start: $(tail -2 server.log | tr '\n' ' ')"; exit 1; }
if [ "$MODE" = planted ]; then
  say "forceload add 0 0 0 63" "fill 0 100 0 8 100 4 minecraft:glass" "fill 2 101 6 4 103 6 minecraft:stone" \
      "setblock 4 100 8 minecraft:smooth_basalt" "setblock 4 105 8 minecraft:glass" "setblock 4 101 8 minecraft:small_dripleaf[half=upper]"
  [ -n "$NEGATIVE" ] && say "fillbiome -12 92 28 19 111 51 minecraft:plains" "setblock 4 100 40 minecraft:smooth_basalt" \
      "setblock 4 105 40 minecraft:glass" "setblock 4 101 40 minecraft:small_dripleaf[half=upper]"
  say "time set noon" "gamerule doDaylightCycle false" "weather clear" "gamerule doWeatherCycle false"
fi

cat > "$RUN/options.txt" <<OPTS
pauseOnLostFocus:false
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
tutorialStep:none
renderDistance:4
soundCategory_master:0.0
OPTS
if [ -n "${SMOKE_PING:-}" ]; then
  mkdir -p "$WORK/ping" && cd "$WORK/ping" && echo eula=true > eula.txt
  PING_PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
  printf 'online-mode=false\nserver-ip=127.0.0.1\nserver-port=%s\nlevel-type=flat\nview-distance=2\n' "$PING_PORT" > server.properties
  case "$SMOKE_PING" in 1.17*|1.18*|1.19*|1.20|1.20.[1-4]) PRT=gamma ;; 1.*) PRT=delta ;; *) PRT=epsilon ;; esac
  ~/.local/share/PrismLauncher/java/java-runtime-$PRT/bin/java -Xmx1G -jar ~/.local/share/gradle/caches/fabric-loom/$SMOKE_PING/minecraft-server.jar nogui \
    < /dev/null > server.log 2>&1 &
  pids+=($!)
  for _ in $(seq 240); do grep -q 'Done (' server.log && break; sleep 0.5; done
  PING="-Ddripleaffinder.smoke.ping=127.0.0.1:$PING_PORT"
  cd "$WORK/server"
fi
case "$MC" in 1.17*|1.18*|1.19*) JOIN=""; CONNECT="-Ddripleaffinder.smoke.connect=127.0.0.1:$PORT" ;; *) JOIN="--quickPlayMultiplayer 127.0.0.1:$PORT"; CONNECT="" ;; esac
case "$MODE" in
  planted) SCENARIO="-Ddripleaffinder.smoke.pos=4,101,8 -Ddripleaffinder.smoke.camera=1.5,101,3.5 -Ddripleaffinder.smoke.negative=$NEGATIVE" ;;
  search) SCENARIO="-Ddripleaffinder.smoke.search=$3,$6,${7:-} -Ddripleaffinder.smoke.camera=$4.5,120,$5.5" ;;
  realworld) SCENARIO="-Ddripleaffinder.smoke.realworld=$3,$6,96,${7:-} -Ddripleaffinder.smoke.camera=$4.5,200,$5.5 -Ddripleaffinder.smoke.tp=$WORK/tp.txt" ;;
  *) echo "SMOKE FAIL $MC $MODE: unknown scenario"; exit 2 ;;
esac
cd "$HERE/.."
ALSOFT_DRIVERS=null ALSA_CONFIG_PATH=/dev/null JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/gradle/jdks/eclipse_adoptium-25-amd64-linux.2}" \
  gamescope --backend headless -W 854 -H 480 -w 854 -h 480 -- ./gradlew -p "$NODE" smokeClient --console=plain \
    "-Psmoke_jvm=-Xmx1536M -Ddripleaffinder.smoke=$WORK/result.txt $SCENARIO $CONNECT ${PING:-}" "-Psmoke_args=--username DripTest $JOIN" \
    ${SMOKE_MOD:+"-Psmoke_mod=$SMOKE_MOD"} ${SMOKE_EXTRA:+"-Psmoke_extra=$SMOKE_EXTRA"} > "$WORK/client.log" 2>&1 &
pids+=($!)
client_gone() { ! kill -0 "${pids[1]}" 2>/dev/null || grep -qE 'Incompatible mods found|Could not execute entrypoint' "$WORK/client.log"; }
scx_tour() {
  local SL="$WORK/server/server.log" seen="" n at kind line x z
  say "execute in minecraft:the_end run tp DripTest 0 100 0"
  sleep 15
  say "execute in minecraft:overworld run tp DripTest $1 200 $2"
  sleep 5
  for at in "0 0" "3000 0" "-3000 0" "0 3000" "0 -3000" "3000 3000" "-3000 -3000" "3000 -3000" "-3000 3000"; do
    for kind in swamp_hut desert_pyramid igloo jungle_pyramid; do
      grep -qF 'SeedcrackerX found the world seed' "$RUN/logs/latest.log" 2>/dev/null && return
      client_gone && return
      n=$(grep -c 'is at \[' "$SL")
      case "$MC" in 1.18|1.18.1) say "execute positioned ${at% *} 0 ${at#* } run locate $kind" ;;
        1.18.2) say "execute positioned ${at% *} 0 ${at#* } run locate minecraft:$kind" ;;
        *) say "execute positioned ${at% *} 0 ${at#* } run locate structure minecraft:$kind" ;; esac
      for _ in $(seq 20); do [ "$(grep -c 'is at \[' "$SL")" -gt "$n" ] && break; sleep 0.5; done
      [ "$(grep -c 'is at \[' "$SL")" -gt "$n" ] || continue
      line="$(grep -o 'is at \[-\?[0-9]*, ~, -\?[0-9]*\]' "$SL" | tail -1)"
      x="$(echo "$line" | sed -E 's/.*\[(-?[0-9]+), ~, (-?[0-9]+)\]/\1/')"; z="$(echo "$line" | sed -E 's/.*\[(-?[0-9]+), ~, (-?[0-9]+)\]/\2/')"
      case " $seen " in *" $x,$z "*) continue ;; esac
      seen="$seen $x,$z"
      say "tp DripTest $x 150 $z"
      sleep 8
    done
  done
}
wait_for() { for _ in $(seq "$1"); do [ -f "$WORK/result.txt" ] && return 1; client_gone && return 1; eval "$2" && return 0; sleep 0.5; done; return 1; }
wait_for 600 "grep -q 'DripTest joined the game' '$WORK/server/server.log'"
sleep 2
case "$MODE" in
  planted) say "gamemode creative DripTest" "tp DripTest 1.5 101 3.5 -30.96 10.87" ;;
  search) say "gamemode creative DripTest" "tp DripTest $4 120 $5" ;;
  realworld)
    say "tp DripTest $4 200 $5"
    [ "${7:-}" = scx ] && scx_tour "$4" "$5"
    LAST=""
    while wait_for 7200 "[ -s '$WORK/tp.txt' ] && [ \"\$(cut -d' ' -f1 '$WORK/tp.txt')\" != '$LAST' ]"; do
      read -r N X Y Z AX AZ < "$WORK/tp.txt"
      LAST=$N
      at() { awk -v a="$1" -v b="$2" 'BEGIN { print a + b }'; }
      [ "${SMOKE_REVERSE:-}" = 1 ] && [ "$AX" != any ] && { AX=$(awk -v a="$AX" 'BEGIN { print -a }'); AZ=$(awk -v a="$AZ" 'BEGIN { print -a }'); }
      if [ "${SMOKE_WALK:-}" = 1 ] && [ "$AX" != any ]; then
        for d in $(seq 160 -4 4); do
          say "tp DripTest $(awk -v x="$X" -v a="$AX" -v d=$d 'BEGIN { print x + 0.5 + a * d }') $Y $(awk -v z="$Z" -v a="$AZ" -v d=$d 'BEGIN { print z + 0.5 + a * d }')"
          sleep 1
        done
      fi
      say "tp DripTest $(at "$X" 0.5) $Y $(at "$Z" -2.5) 0 20.47"
    done ;;
esac
wait_for 7200 false
for _ in $(seq 60); do client_gone && break; sleep 0.5; done
say stop
for _ in $(seq 60); do kill -0 "${pids[0]}" 2>/dev/null || break; sleep 0.5; done

LOG="$RUN/logs/latest.log"
CRASH="$(ls -t "$RUN"/crash-reports/*.txt 2>/dev/null | head -1)"
[ -n "$CRASH" ] && [ "$CRASH" -nt "$WORK/server/eula.txt" ] || CRASH=""
RESULT="$(cat "$WORK/result.txt" 2>/dev/null || echo "SMOKE FAIL no result (${CRASH:+crash: $(grep -m1 -E 'Exception|Error' "$CRASH" | cut -c1-200); }client.log: $(grep -m1 -E 'requires|Incompatible|Exception|ERROR' "$WORK/client.log" | cut -c1-200))")"
PROBLEMS=""
if [ -n "${SMOKE_MOD:-}" ]; then EXPECT="$(unzip -p "$SMOKE_MOD" fabric.mod.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["version"])')"
else EXPECT="$(prop mod_version)+mc$(prop range_name)"; fi
grep -qF -- "- dripleaf-finder $EXPECT" "$LOG" 2>/dev/null || PROBLEMS="$PROBLEMS; loaded $(grep -m1 -oE -- '- dripleaf-finder [^ ]+' "$LOG" 2>/dev/null || echo nothing), not $EXPECT"
LOADED="$(grep -a -m1 -oP 'SMOKE mod jar: \K.*' "$LOG" 2>/dev/null)"
[ -z "${SMOKE_MOD:-}" ] || cmp -s "$LOADED" "$SMOKE_MOD" || PROBLEMS="$PROBLEMS; the loaded jar ${LOADED:-?} is not $SMOKE_MOD"
case "$MODE" in
  planted) grep -qF '[Dripleaf] Spliced small dripleaf top half at 4 101 8' "$LOG" || PROBLEMS="$PROBLEMS; no chat line" ;;
  search) case "$RESULT" in *"Lush caves only generate"*) ;; *) grep -qF '[Dripleaf] Spliced small dripleaf top half from the seed at' "$LOG" || PROBLEMS="$PROBLEMS; no announcement in chat" ;; esac ;;
  realworld) case "$RESULT" in *SPLICED*) grep -qF "[Dripleaf] Spliced small dripleaf top half at " "$LOG" || PROBLEMS="$PROBLEMS; no live chat line" ;; esac ;;
esac
SHOT="$(ls -t "$RUN"/screenshots/*.png 2>/dev/null | head -1)"
PIXELS=""
if [ -n "$SHOT" ]; then
  PNG="$HERE/out/$MC-$MODE-$(basename "${SMOKE_MOD:-node}" .jar).png"
  cp "$SHOT" "$PNG"
  if [ "$MODE" != search ]; then
    PIXELS="$(uv run --quiet --no-project --with pillow python3 "$HERE/green.py" "$PNG")"
    case "$PIXELS" in *"box centred"*) ;; *) PROBLEMS="$PROBLEMS; box: $PIXELS" ;; esac
  fi
fi
case "$RESULT" in
  "SMOKE PASS"*) [ -z "$PROBLEMS" ] && { echo "SMOKE PASS $MC $MODE: ${RESULT#SMOKE PASS }${PIXELS:+ | $PIXELS}"; exit 0; } ;;
esac
echo "SMOKE FAIL $MC $MODE: ${RESULT#SMOKE }${PROBLEMS}${PIXELS:+ | $PIXELS}"
exit 1
}
main "$@"; exit $?
