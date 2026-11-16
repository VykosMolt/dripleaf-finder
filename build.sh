#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
python3 "$HERE/nodes/make-nodes.py"
"$HERE/nodes/build.sh" "$@"
python3 "$HERE/nodes/dist.py"
