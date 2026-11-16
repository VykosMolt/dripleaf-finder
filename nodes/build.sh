#!/usr/bin/env bash
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/gradle/jdks/eclipse_adoptium-25-amd64-linux.2}"
mkdir -p "$HERE/logs"
versions=("$@")
[ ${#versions[@]} -gt 0 ] || mapfile -t versions < <(grep -v '^#' "$HERE/nodes.tsv" | cut -f1)
fails=0
for v in "${versions[@]}"; do
  log="$HERE/logs/$v.log"
  if timeout 1800 "$HERE/../gradlew" -p "$HERE/$v" build --console=plain > "$log" 2>&1; then
    echo "PASS $v $(grep -c 'PASSED' "$log") checks"
  else
    fails=$((fails + 1))
    echo "FAIL $v: $(grep -m1 -E 'error:|What went wrong|FAILED:|Exception' "$log" | sed "s|$HERE/../||" | cut -c1-160)"
  fi
done
exit $fails
