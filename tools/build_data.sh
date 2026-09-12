#!/usr/bin/env bash
# Rebuild every JSON asset from the manuals in manuals/.
#
# Order matters: parse_components.py clears app/src/main/assets/img, so it has
# to run before the parsers that add page renders to that folder.
set -euo pipefail
cd "$(dirname "$0")/.."

run() { printf '\n== %s\n' "$1"; python3 "tools/$1"; }

run parse_parts.py
run parse_photos.py
run parse_drawings.py
run parse_components.py      # clears assets/img
run parse_servicemenu.py
run parse_procedures.py
run parse_faults.py
run merge_faults.py

printf '\nassets: %s\n' "$(du -sh app/src/main/assets | cut -f1)"
