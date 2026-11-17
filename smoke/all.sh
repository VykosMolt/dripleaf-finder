#!/usr/bin/env bash
HERE="$(cd "$(dirname "$0")" && pwd)"
versions=("$@")
[ ${#versions[@]} -gt 0 ] || mapfile -t versions < <(grep -v '^#' "$HERE/../nodes/nodes.tsv" | grep -v 'search_only=true' | cut -f1)
mkdir -p "$HERE/out"
export HERE SCENARIO="${SCENARIO:-planted}" SMOKE_DIST="${SMOKE_DIST:-}"
one() {
  v=$1
  SMOKE_MOD=""
  [ -n "$SMOKE_DIST" ] && SMOKE_MOD="$(ls "$HERE"/../dist/dripleaf-finder-*+mc"$v".jar)"
  line="$(SMOKE_MOD="$SMOKE_MOD" timeout 7200 "$HERE/run.sh" "$v" $SCENARIO 2>&1 | tail -1)"
  case "$line" in "SMOKE "*) ;; *) line="SMOKE FAIL $v: harness: $line" ;; esac
  [ -n "$SMOKE_MOD" ] && line="$line [$(basename "$SMOKE_MOD")]"
  echo "$(date +%T) $line" | tee -a "$HERE/out/results-${SCENARIO%% *}.txt"
}
export -f one
printf '%s\n' "${versions[@]}" | xargs -P "${JOBS:-1}" -I{} bash -c 'one {}'
