#!/usr/bin/env python3
"""Cut every picture out of the books once.

The same exploded drawing appears in a dozen parts books and the same
schematic in every translation of a technical manual, so pictures are stored
by what they contain: identical pixels land on the same file. Regions are
rendered rather than pulled out as embedded images, which keeps masks,
clipping and the numbers drawn on top intact.

Writes kb/media/<hash>.webp and build/media.json
"""
import gzip
import hashlib
import io
import json
import math
import os
import sys
from concurrent.futures import ProcessPoolExecutor

import pymupdf
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import BUILD, KB, ROOT, read_json, write_json

OUT = os.path.join(KB, "media")
DOCS = os.path.join(BUILD, "docs")
MIN_SIDE = 48
WORKERS = int(os.environ.get("KB_WORKERS", "3"))
# A pixmap is four bytes a pixel and PIL keeps a second copy, so cap the area
# rather than trust the scale: a full page still comes out near 1500 px.
MAX_PIXELS = 4_000_000
TARGET = {"drawing": 1500, "figure": 1100, "photo": 1100}


def kind_of(meta, bbox, page_rect, biggest):
    """An exploded view is the picture the page was made for."""
    area = (bbox[2] - bbox[0]) * (bbox[3] - bbox[1])
    page_area = page_rect.width * page_rect.height
    if meta.get("doctype") == "BR":
        return "photo"
    if meta.get("doctype") == "SPM" and area >= biggest and area > page_area * 0.18:
        return "drawing"
    return "figure"


def cluster(boxes, gap=6.0):
    """Join picture blocks that touch: one drawing is often cut into tiles."""
    groups = []
    for box in sorted(boxes, key=lambda b: (b[1], b[0])):
        placed = False
        for group in groups:
            gx0, gy0, gx1, gy1 = group["bbox"]
            if (box[0] <= gx1 + gap and box[2] >= gx0 - gap
                    and box[1] <= gy1 + gap and box[3] >= gy0 - gap):
                group["bbox"] = (min(gx0, box[0]), min(gy0, box[1]),
                                 max(gx1, box[2]), max(gy1, box[3]))
                group["members"].append(box)
                placed = True
                break
        if not placed:
            groups.append(dict(bbox=tuple(box), members=[box]))
    # a join can bring two groups within reach of each other
    changed = True
    while changed and len(groups) > 1:
        changed = False
        for i in range(len(groups)):
            for j in range(i + 1, len(groups)):
                a, b = groups[i]["bbox"], groups[j]["bbox"]
                if (a[0] <= b[2] + gap and a[2] >= b[0] - gap
                        and a[1] <= b[3] + gap and a[3] >= b[1] - gap):
                    groups[i]["bbox"] = (min(a[0], b[0]), min(a[1], b[1]),
                                         max(a[2], b[2]), max(a[3], b[3]))
                    groups[i]["members"] += groups[j]["members"]
                    del groups[j]
                    changed = True
                    break
            if changed:
                break
    return groups


def render(page, bbox, target):
    rect = pymupdf.Rect(bbox) & page.rect
    if rect.is_empty or rect.width < 4 or rect.height < 4:
        return None
    scale = min(6.0, max(1.0, target / max(rect.width, rect.height)))
    scale = min(scale, math.sqrt(MAX_PIXELS / max(1.0, rect.width * rect.height)))
    pix = page.get_pixmap(matrix=pymupdf.Matrix(scale, scale), clip=rect, alpha=False)
    if pix.width < MIN_SIDE or pix.height < MIN_SIDE:
        return None
    return pix


def one_doc(meta):
    path = os.path.join(DOCS, meta["doc_id"] + ".json.gz")
    if not os.path.exists(path):
        return []
    data = json.load(gzip.open(path, "rt", encoding="utf-8"))
    doc = pymupdf.open(os.path.join(ROOT, meta["path"]))
    out = []
    per_page = {}
    for sec in data["sections"]:
        for img in sec["images"]:
            per_page.setdefault(img["page"], {})[tuple(img["bbox"])] = sec
    for page_no, blocks in sorted(per_page.items()):
        page = doc[page_no - 1]
        groups = cluster(list(blocks))
        biggest = max(((g["bbox"][2] - g["bbox"][0]) * (g["bbox"][3] - g["bbox"][1]))
                      for g in groups)
        for group in groups:
            bbox = group["bbox"]
            kind = kind_of(meta, bbox, page.rect, biggest)
            pix = render(page, bbox, TARGET[kind])
            if pix is None:
                continue
            raw = pix.samples
            digest = hashlib.sha1(raw + bytes(str(pix.width), "ascii")).hexdigest()[:16]
            dst = os.path.join(OUT, digest + ".webp")
            if not os.path.exists(dst):
                image = Image.frombytes("RGB", (pix.width, pix.height), raw)
                if image.getextrema() == ((255, 255), (255, 255), (255, 255)):
                    continue                      # blank
                buf = io.BytesIO()
                image.save(buf, "WEBP", quality=82, method=4)
                with open(dst, "wb") as fh:
                    fh.write(buf.getvalue())
            for member in group["members"]:
                sec = blocks[member]
                out.append(dict(hash=digest, kind=kind, doc_id=meta["doc_id"],
                                page=page_no, section=sec["number"],
                                section_title=sec["title"], lang=meta.get("lang"),
                                width=pix.width, height=pix.height,
                                bbox=[round(v, 1) for v in member],
                                group_bbox=[round(v, 1) for v in bbox]))
    doc.close()
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    corpus = read_json(os.path.join(BUILD, "corpus.json"))
    docs = [d for d in corpus["documents"] if not d.get("duplicate_of")]
    only = [a for a in sys.argv[1:] if not a.startswith("-")]
    if only:
        docs = [d for d in docs if any(o in d["doc_id"] for o in only)]
    rows = []
    with ProcessPoolExecutor(max_workers=WORKERS) as pool:
        for res in pool.map(one_doc, docs, chunksize=1):
            rows += res
            if res:
                print(f"  {res[0]['doc_id']:52} {len(res):4} pictures", flush=True)
    uniq = {r["hash"] for r in rows}
    write_json(os.path.join(BUILD, "media.json"),
               dict(count=len(rows), unique=len(uniq), items=rows))
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f"{len(rows):,} placements, {len(uniq):,} distinct pictures, "
          f"{size/1e6:.1f} MB")


if __name__ == "__main__":
    main()
