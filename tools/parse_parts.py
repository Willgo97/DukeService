#!/usr/bin/env python3
"""Turn the spare parts manuals into the app's parts.json.

Reads the PDFs in manuals/ (kept out of git) and writes data/parts.json.
Run from the project root:  python3 tools/parse_parts.py
"""
import json, os, re, subprocess, sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

BOOKS = {
    "virtu": ("VirtuParts.pdf", "Virtu 70/90 (9CECK, CoEx)"),
    "lua":   ("Luaparts.pdf",   "Lua 19000 (9XEAS, CoEx XL)"),
    "zia":   ("Ziaparts.pdf",   "Zia 7000/9000 (9CECP, CoEx)"),
    "nio":   ("Nioparts.pdf",   "Nio (9CKA, CoEx)"),
    "avy":   ("Avyparts.pdf",   "Avy 19000 (9XEAT, CoEx XL)"),
    "rosa":  ("Spare_Parts_Manual_ROSA_Filterfresh_Small_9FND_EN_2025jan29.pdf",
              "Rosa 2000 (9FNDV, Uni-Brewer)"),
}

HDR     = re.compile(r'^\s*Drawing\s+Pos\s+S\s+Part Nr\s+Qty\s+Spare Part Description\s*$')
SECTION = re.compile(r'^\s*(\d{4})\s+(.+?)\s*$')
NOISE   = re.compile(r'^\s*(Spare Parts Manual|Page \d+|[A-Z][a-z]+ \d{1,2}, \d{4}\s*(Page \d+)?|Table of Contents|SETS|PARTS)\s*$')
DRAW    = re.compile(r'^\d{4,5}(-[A-Z0-9]{1,3})?$')
PART    = re.compile(r'^[0-9]?[A-Z]{1,5}[0-9]{2,5}([-.][A-Z0-9]{1,10})*$')


def columns(header):
    return {name: header.index(label) for name, label in (
        ("drawing", "Drawing"), ("pos", "Pos"), ("s", "S"),
        ("part", "Part Nr"), ("qty", "Qty"), ("desc", "Spare Part Description"))}


def cut(line, start, end):
    return line[start:end].strip() if start < len(line) else ""


def parse(path, machine, label):
    text = subprocess.run(["pdftotext", "-layout", path, "-"],
                          capture_output=True, text=True, check=True).stdout
    lines = text.replace("\x0c", "").split("\n")
    rows, section, cur, i = [], "", None, 0
    while i < len(lines):
        if not HDR.match(lines[i]):
            m = SECTION.match(lines[i])
            if m and re.match(r'^\s{0,3}\d{4}\s+\S', lines[i]) and "....." not in lines[i]:
                section = f"{m.group(1)} {m.group(2)}"
            i += 1
            continue
        col = columns(lines[i])
        i += 1
        cur = None
        while i < len(lines):
            l = lines[i]
            if HDR.match(l):
                break
            if NOISE.match(l) or not l.strip():
                i += 1
                continue
            m = SECTION.match(l)
            if m and len(l) - len(l.lstrip()) < 4 and not re.match(r'^\s*\d{4}-', l):
                section, cur = f"{m.group(1)} {m.group(2)}", None
                i += 1
                continue
            drawing = cut(l, col["drawing"], col["pos"])
            pos     = cut(l, col["pos"], col["s"])
            sw      = cut(l, col["s"], col["part"])
            part    = cut(l, col["part"], col["qty"])
            qty     = cut(l, col["qty"], col["desc"])
            desc    = l[col["desc"]:].strip() if len(l) > col["desc"] else ""
            # A row is new when it carries its own drawing, or — as the books
            # often do — repeats neither but does give a position and a number.
            starts_row = bool(drawing) or (pos and part)
            if starts_row:
                cur = {"machine": machine, "model": label, "section": section,
                       "drawing": drawing or (cur or {}).get("drawing", ""),
                       "pos": pos, "service": sw, "part": part, "qty": qty, "desc": desc}
                rows.append(cur)
            elif cur is not None and (part or desc):
                if part:
                    cur["part"] = (cur["part"] + part) if cur["part"].endswith("-") \
                        else (cur["part"] + " " + part).strip()
                if desc:
                    cur["desc"] = (cur["desc"] + " " + desc).strip()
            i += 1
    return rows


def clean(rows):
    out = []
    for r in rows:
        p = re.sub(r'-{2,}', '-', re.sub(r'\s+', '', r["part"])).strip('-')
        if not DRAW.match(r["drawing"]):
            continue
        if p == "n/a":
            available = False
        elif PART.match(p):
            available = True
        else:
            continue
        desc = re.sub(r'\s+', ' ', r["desc"]).strip()
        if not desc:
            continue
        out.append({
            "m": r["machine"],
            "s": re.sub(r'^(\d{4}) Refer to drawing.*', r'\1', r["section"]).strip(),
            "d": r["drawing"],
            "p": r["pos"],
            "n": p if available else "",
            "q": r["qty"] if re.match(r'^\d{1,3}$', r["qty"]) else "",
            "v": r["service"] if r["service"] in ("SE", "SW", "n/a") else "",
            "t": desc,
        })
    return out


def main():
    manuals = os.path.join(ROOT, "manuals")
    allrows = []
    for machine, (filename, label) in BOOKS.items():
        path = os.path.join(manuals, filename)
        if not os.path.exists(path):
            print(f"  overslaan: {filename} ontbreekt", file=sys.stderr)
            continue
        rows = clean(parse(path, machine, label))
        print(f"  {machine:6} {len(rows):5} onderdelen", file=sys.stderr)
        allrows.extend(rows)

    dst = os.path.join(ROOT, "data", "parts.json")
    json.dump(allrows, open(dst, "w"), ensure_ascii=False, separators=(",", ":"))
    asset = os.path.join(ROOT, "app", "src", "main", "assets", "parts.json")
    json.dump(allrows, open(asset, "w"), ensure_ascii=False, separators=(",", ":"))
    print(f"totaal {len(allrows)} onderdelen, {len(set(r['n'] for r in allrows if r['n']))} unieke nummers")
    print(dict(Counter(r["m"] for r in allrows)))


if __name__ == "__main__":
    main()
