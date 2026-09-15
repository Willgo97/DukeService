#!/usr/bin/env python3
"""Rebuild the parts tables out of the parts books.

Every drawing has a table beside it with six columns: the drawing it belongs
to, the balloon number, whether it is a stock item, the part number, how many
are fitted and what it is. The table is not a PDF table object, it is loose
text, so the columns are read back from the x position of the header cells.

Writes build/parts.json
"""
import gzip
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import BUILD, read_json, write_json

DOCS = os.path.join(BUILD, "docs")
HEADERS = ["Drawing", "Pos", "S", "Part Nr", "Qty", "Spare Part Description"]
FIELD = {"Drawing": "drawing", "Pos": "pos", "S": "stock", "Part Nr": "part_nr",
         "Qty": "qty", "Spare Part Description": "description"}
GROUP_ROW = re.compile(r"^(SETS?|PARTS?|KITS?|TOOLS?|OPTIONS?|ACCESSOIRES?)$", re.I)
REFER = re.compile(r"^refer to drawing\s+(\d{3,5})[:\s]*(.*)$", re.I)
PART_NR = re.compile(r"^[0-9][A-Z0-9]{5,}[A-Z0-9-]*$")
SUFFIX = re.compile(r"^[-–][A-Z0-9]{2,}$", re.I)      # colour code on its own line
STOCK = {"SW", "SE", "N/A"}
POS = re.compile(r"^\d{1,3}[A-Z]?$")
DRAWING_CELL = re.compile(r"^\d{3,5}(?:-[A-Z0-9]+)?$")


def rows_of(lines, tol=4.5):
    """Cluster lines into table rows by their y position on a page."""
    rows = []
    for ln in sorted(lines, key=lambda l: (l["p"], l["y"], l["x"])):
        if rows and rows[-1][0]["p"] == ln["p"] and abs(ln["y"] - rows[-1][0]["y"]) <= tol:
            rows[-1].append(ln)
        else:
            rows.append([ln])
    return rows


def header_columns(rows):
    """x of each column, taken from the header row wherever it appears."""
    for row in rows:
        texts = [l["t"].strip() for l in row]
        if "Drawing" in texts and "Part Nr" in texts:
            cols = []
            for l in row:
                name = l["t"].strip()
                if name in HEADERS:
                    cols.append((l["x"], FIELD[name]))
            if len(cols) >= 4:
                return sorted(cols)
    return None


def column_of(x, cols, slack=6.0):
    best = cols[0][1]
    for cx, name in cols:
        if x >= cx - slack:
            best = name
    return best


def parse_section(section, cols_hint=None):
    lines = [l for l in section["lines"] if l["t"].strip()]
    rows = rows_of(lines)
    cols = header_columns(rows) or cols_hint
    if not cols:
        return [], None
    # pages without a run of drawing cells carry no table, only notes
    table_pages = {}
    for ln in lines:
        if column_of(ln["x"], cols) == "drawing" and DRAWING_CELL.match(ln["t"].strip()):
            table_pages[ln["p"]] = table_pages.get(ln["p"], 0) + 1
    table_pages = {p for p, n in table_pages.items() if n >= 2}
    out = []
    for row in rows:
        if row[0]["p"] not in table_pages:
            continue
        cells = {}
        for l in sorted(row, key=lambda l: l["x"]):
            name = column_of(l["x"], cols)
            text = l["t"].strip()
            if not text:
                continue
            cells[name] = (cells.get(name, "") + " " + text).strip()
        if not cells:
            continue
        joined = " ".join(cells.values()).strip()
        if joined in HEADERS or (cells.get("drawing") == "Drawing"):
            continue
        if GROUP_ROW.match(joined):
            out.append(dict(kind="group", group=joined.upper()))
            continue
        # the copyright line and the legend under the table are not rows
        if joined.lower().startswith(("part numbers marked", "all rights", "no part of")):
            continue
        m = REFER.match(cells.get("description", ""))
        if m:
            out.append(dict(kind="ref", drawing=cells.get("drawing"),
                            ref_drawing=m.group(1), description=m.group(2).strip()))
            continue
        part = dict(kind="part", **{k: v for k, v in cells.items()})
        out.append(part)
    return out, cols


def tidy(rows, section, doc):
    """Fold continuation lines into the row above and keep the group heading."""
    parts, group = [], None
    for row in rows:
        if row.get("kind") == "group":
            group = row["group"]
            continue
        if row.get("kind") == "ref":
            parts.append(dict(kind="ref", ref_drawing=row["ref_drawing"],
                              description=row.get("description") or "",
                              group=group))
            continue
        pos = (row.get("pos") or "").strip()
        nr = (row.get("part_nr") or "").strip().replace(" ", "")
        desc = (row.get("description") or "").strip()
        qty = (row.get("qty") or "").strip()
        drawing = (row.get("drawing") or "").strip()
        stock = (row.get("stock") or "").strip()
        if stock.upper() not in STOCK:
            if stock and not desc:
                desc = stock
            stock = ""
        if pos and not POS.match(pos):
            desc = (pos + " " + desc).strip() if not desc.startswith(pos) else desc
            pos = ""
        # a row of its own always carries a drawing or a position number;
        # a wrapped part number or a second description line carries neither
        is_new = bool(nr) and not SUFFIX.match(nr) and (
            bool(drawing or pos) or bool(PART_NR.match(nr)))
        prev = parts[-1] if parts and parts[-1].get("kind") == "part" else None
        if not is_new and prev is not None and (desc or nr):
            if nr and prev.get("part_nr"):
                prev["part_nr"] = (prev["part_nr"] + nr).strip()
            if desc:
                prev["description"] = (prev["description"] + " " + desc).strip()
            if qty and not prev.get("qty"):
                prev["qty"] = qty
            continue
        if not nr or nr.replace(" ", "").lower() in ("partnr", "part"):
            continue
        available = nr.lower() not in ("n/a", "na", "-")
        parts.append(dict(kind="part", drawing=drawing or None, pos=pos or None,
                          stock=stock.upper() or None,
                          part_nr=nr if available else None,
                          available=available, qty=qty or None,
                          description=desc, group=group))
    for p in parts:
        p["section"] = section["number"]
        p["section_title"] = section["title"]
        p["page"] = section["page"]
        p["doc_id"] = doc
    return parts


def main():
    corpus = read_json(os.path.join(BUILD, "corpus.json"))
    books = [d for d in corpus["documents"]
             if d.get("doctype") == "SPM" and not d.get("duplicate_of")]
    out = []
    for meta in books:
        path = os.path.join(DOCS, meta["doc_id"] + ".json.gz")
        if not os.path.exists(path):
            continue
        data = json.load(gzip.open(path, "rt", encoding="utf-8"))
        cols_hint = None
        book_rows = []
        for sec in data["sections"]:
            rows, cols = parse_section(sec, cols_hint)
            if cols:
                cols_hint = cols
            book_rows += tidy(rows, sec, meta["doc_id"])
        print(f"  {meta['doc_id']:52} {len(book_rows):5} rows")
        out += book_rows
    write_json(os.path.join(BUILD, "parts.json"),
               dict(count=len(out),
                    stock_legend={"SW": "Service parts Warehouse stock",
                                  "SE": "Service parts Engineer stock",
                                  "n/a": "Not available as spare part"},
                    rows=out))
    uniq = {r["part_nr"] for r in out if r.get("part_nr")}
    print(f"{len(out):,} rows, {len(uniq):,} distinct part numbers, "
          f"{len(books)} books")


if __name__ == "__main__":
    main()
