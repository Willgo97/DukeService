#!/usr/bin/env bash
# Rebuild the knowledge base from the PDFs in manuals/.
#
# The order matters: each step reads what the one before it wrote.
set -euo pipefail
cd "$(dirname "$0")/.."
PY=${PY:-.venv/bin/python}

step() { printf '\n== %s\n' "$1"; $PY "kbtools/$1"; }

step inventory.py     # which books are there, and which are copies
[ "${1:-}" = "--organize" ] && $PY kbtools/organize.py --apply
step extract.py       # PDF -> ordered, sectioned text
step parts.py         # parts tables
step facts.py         # typed records
step topics.py        # machines + one record per subject
step media.py         # pictures
step emit.py          # kb/
step schemas.py       # kb/schema/
step appdata.py       # app/src/main/assets/

printf '\nkb: %s   app assets: %s\n' \
  "$(du -sh kb | cut -f1)" "$(du -sh app/src/main/assets | cut -f1)"
