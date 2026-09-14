#!/usr/bin/env python3
"""Read every book once and store it as ordered, sectioned text.

Every PDF in the set carries real bookmarks, so the chapter tree does not have
to be guessed: the bookmark gives the title, the page and (in most books) the
height on that page where the section starts. Blocks are handed out to the
section they fall under, which turns 16.500 pages into a few thousand sections
that later steps can work with.

Writes build/docs/<doc_id>.json.gz and build/extract_report.json
"""
import gzip
import json
import os
import re
import sys
from concurrent.futures import ProcessPoolExecutor

import pymupdf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import BUILD, ROOT, read_json, write_json
from layout import blocks_of, drop_key, render, running_text, xy_cut

DOCS = os.path.join(BUILD, "docs")
# Each worker holds a whole book in memory; four of them is plenty on a laptop
# that is also running an emulator.
WORKERS = int(os.environ.get("KB_WORKERS", "4"))
NUMBERED = re.compile(r"^\s*(\d+(?:\.\d+)*)\.?\s+(.*)$")
SPM_SECTION = re.compile(r"^\s*(\d{4,5})\s+(.*)$")
WS = re.compile(r"\s+")


def norm(text):
    return WS.sub(" ", (text or "")).strip().lower()


def anchors_from_toc(doc):
    """Bookmark entries as (page index, y, level, number, title)."""
    out = []
    for entry in doc.get_toc(simple=False):
        level, title, page = entry[0], entry[1].strip(), entry[2]
        dest = entry[3] if len(entry) > 3 else {}
        y = None
        point = dest.get("to")
        if point is not None:
            try:
                y = float(point.y)
                if y != y:            # NaN
                    y = None
            except Exception:
                y = None
        page_idx = max(0, (dest.get("page", page - 1)))
        out.append(dict(level=level, title=title, page=page_idx, y=y))
    return out


def split_number(title):
    m = NUMBERED.match(title)
    if m and not re.match(r"^\d{4,5}$", m.group(1)):
        return m.group(1), m.group(2).strip()
    m = SPM_SECTION.match(title)
    if m:
        return m.group(1), m.group(2).strip()
    return None, title.strip()


def locate(page, title, y_hint):
    """Where on the page the heading actually sits."""
    want = norm(title)
    if want:
        for b in page.get_text("dict", flags=7)["blocks"]:
            if b.get("type") == 1:
                continue
            text = norm(" ".join(s["text"] for l in b["lines"] for s in l["spans"]))
            if text.startswith(want[:60]) or want.startswith(text[:60]) and len(text) > 8:
                return b["bbox"][1] - 2
    return y_hint if y_hint is not None else 0.0


def headings_by_font(doc, drop):
    """Fallback for the few books without bookmarks: big or bold stand-alone lines."""
    sizes = {}
    for i in range(doc.page_count):
        for b in blocks_of(doc[i], drop):
            if b["kind"] != "text":
                continue
            for ln in b["lines"]:
                sizes[ln["size"]] = sizes.get(ln["size"], 0) + len(ln["text"])
    if not sizes:
        return []
    body = max(sizes, key=sizes.get)
    out = []
    for i in range(doc.page_count):
        for b in blocks_of(doc[i], drop):
            if b["kind"] != "text":
                continue
            ln = b["lines"][0]
            text = ln["text"].strip()
            if not text or len(text) > 90:
                continue
            big = ln["size"] >= body + 1.5
            code = SPM_SECTION.match(text)
            if big or (code and ln["size"] >= body):
                out.append(dict(level=1 if big else 2, title=text, page=i,
                                y=b["bbox"][1] - 2))
    return out


def columns_of(blocks, page_width):
    """Group blocks into the columns they visually sit in."""
    xs = sorted({round(b["bbox"][0]) for b in blocks})
    if not xs:
        return []
    edges, prev = [xs[0]], xs[0]
    for x in xs[1:]:
        if x - prev > page_width * 0.12:
            edges.append(x)
        prev = x
    def col_of(b):
        return max(i for i, e in enumerate(edges) if b["bbox"][0] >= e - 1)
    groups = {}
    for b in blocks:
        groups.setdefault(col_of(b), []).append(b)
    return [sorted(groups[k], key=lambda b: (round(b["bbox"][1] / 6), b["bbox"][0]))
            for k in sorted(groups)]


STEP_NUMBER = re.compile(r"^\s*(\d{1,2})\s*$")


def poster_sections(doc, drop):
    """Short maintenance instructions: a fold-out with numbered steps.

    The bookmarks of these sheets are unreliable, but the layout is not: a big
    numeral sits in the left margin of the step it belongs to, and the columns
    read one after the other.
    """
    sections = []
    group = None
    step = None
    for i in range(doc.page_count):
        page = doc[i]
        blocks = blocks_of(page, drop)
        weight = {}
        for b in blocks:
            for ln in b.get("lines", ()):
                weight[ln["size"]] = weight.get(ln["size"], 0) + len(ln["text"])
        body = max(weight, key=weight.get) if weight else 10.0
        def is_marker(b):
            if b["kind"] != "text":
                return False
            text = "\n".join(l["text"] for l in b["lines"]).strip()
            return bool(STEP_NUMBER.match(text)) and max(
                l["size"] for l in b["lines"]) >= body + 4

        for column in columns_of(blocks, page.rect.width):
            # the numeral stands beside the first line of its step: read it first
            column.sort(key=lambda b: (b["bbox"][1], b["bbox"][0]))
            rows = []
            for b in column:
                if rows and b["bbox"][1] - rows[-1][0] < 8:
                    rows[-1][1].append(b)
                else:
                    rows.append((b["bbox"][1], [b]))
            column = [b for _y, items in rows
                      for b in sorted(items, key=lambda b: (0 if is_marker(b) else 1,
                                                            b["bbox"][0]))]
            for b in column:
                if b["kind"] == "text":
                    text = "\n".join(l["text"] for l in b["lines"]).strip()
                    size = max(l["size"] for l in b["lines"])
                    m = STEP_NUMBER.match(text)
                    if m and size >= body + 4:
                        step = dict(number=m.group(1), title=(group or "") ,
                                    level=2, page=i + 1, y=b["bbox"][1],
                                    lines=[], images=[], page_to=i + 1)
                        sections.append(step)
                        continue
                    if size >= body + 6 and len(text) < 60:
                        group = text.replace("\n", " ")
                        step = dict(number=None, title=group, level=1, page=i + 1,
                                    y=b["bbox"][1], lines=[], images=[],
                                    page_to=i + 1)
                        sections.append(step)
                        continue
                if step is None:
                    step = dict(number=None, title=group or "(intro)", level=1,
                                page=i + 1, y=b["bbox"][1], lines=[], images=[],
                                page_to=i + 1)
                    sections.append(step)
                target = step
                # a step number set beside its first line: fold that line in
                # a number set beside the first line of its step reads as if it
                # came after that line; only that case folds back
                if (len(sections) > 1 and target["number"] and not target["lines"]
                        and target["page"] == i + 1
                        and target["y"] - 30 < b["bbox"][1] < target["y"] - 4):
                    target = sections[-2]
                if b["kind"] == "image":
                    target["images"].append(dict(page=i + 1, bbox=b["bbox"],
                                                 xref=b.get("xref"),
                                                 width=b.get("width"),
                                                 height=b.get("height")))
                else:
                    for ln in b["lines"]:
                        ln = dict(ln)
                        ln["page"] = i + 1
                        target["lines"].append(ln)
                target["page_to"] = i + 1
    return sections


PAGE_LABEL = re.compile(r"^(page\s*\d+|\d+)$", re.I)
DATE_LABEL = re.compile(r"^(january|february|march|april|may|june|july|august|"
                        r"september|october|november|december)\s+\d", re.I)


def parts_sections(doc, drop):
    """Parts books: every page names its drawing in the header.

    More dependable than the bookmarks, which a few of the books simply do not
    have, and it keeps the drawing page and its table together.
    """
    sections = []
    for i in range(doc.page_count):
        page = doc[i]
        blocks = blocks_of(page, drop)
        header = None
        for b in blocks:
            if b["kind"] != "text" or b["bbox"][1] > page.rect.height * 0.13:
                continue
            text = " ".join(l["text"] for l in b["lines"]).strip()
            low = text.lower()
            if (not text or low.startswith("spare parts manual") or PAGE_LABEL.match(low)
                    or DATE_LABEL.match(low) or len(text) > 70):
                continue
            header = re.sub(r"\s+", " ", text)
            break
        if header is None:
            header = sections[-1]["title"] if sections else "(front matter)"
        number, title = split_number(header)
        same = (sections and sections[-1]["title"] == title
                and number in (None, sections[-1]["number"]))
        if not same:
            sections.append(dict(number=number, title=title, level=1, page=i + 1,
                                 y=0, lines=[], images=[], page_to=i + 1,
                                 raw=header))
        sec = sections[-1]
        sec["page_to"] = i + 1
        for b in xy_cut(blocks):
            if b["kind"] == "image":
                sec["images"].append(dict(page=i + 1, bbox=b["bbox"], xref=b.get("xref"),
                                          width=b.get("width"), height=b.get("height")))
            else:
                for ln in b["lines"]:
                    ln = dict(ln)
                    ln["page"] = i + 1
                    sec["lines"].append(ln)
    return sections


def page_sections(doc, drop):
    """Brochures: no structure worth guessing, so one section per page."""
    sections = []
    for i in range(doc.page_count):
        page = doc[i]
        sec = dict(number=str(i + 1), title=f"Pagina {i + 1}", level=1, page=i + 1,
                   y=0, lines=[], images=[], page_to=i + 1)
        for b in xy_cut(blocks_of(page, drop)):
            if b["kind"] == "image":
                sec["images"].append(dict(page=i + 1, bbox=b["bbox"], xref=b.get("xref"),
                                          width=b.get("width"), height=b.get("height")))
            else:
                for ln in b["lines"]:
                    ln = dict(ln)
                    ln["page"] = i + 1
                    sec["lines"].append(ln)
        sections.append(sec)
    return sections


PROFILE = {"SMI": "poster", "SPM": "parts", "BR": "page"}


def extract_one(meta):
    path = os.path.join(ROOT, meta["path"])
    doc = pymupdf.open(path)
    drop = running_text(doc)
    profile = PROFILE.get(meta.get("doctype"), "flow")
    if profile != "flow":
        builder = {"poster": poster_sections, "parts": parts_sections,
                   "page": page_sections}[profile]
        ordered = builder(doc, drop)
        return finish(doc, meta, ordered, profile, drop)
    anchors = anchors_from_toc(doc)
    if not anchors:
        anchors = headings_by_font(doc, drop)

    # resolve the y of every anchor on its page
    by_page = {}
    for a in anchors:
        by_page.setdefault(a["page"], []).append(a)
    for page_idx, items in by_page.items():
        if page_idx >= doc.page_count:
            continue
        page = doc[page_idx]
        for a in items:
            a["y"] = locate(page, a["title"], a["y"])
        items.sort(key=lambda a: a["y"])

    sections = []
    for a in sorted(anchors, key=lambda a: (a["page"], a["y"] or 0)):
        number, title = split_number(a["title"])
        sections.append(dict(number=number, title=title, level=a["level"],
                             page=a["page"] + 1, y=a["y"], lines=[], images=[],
                             page_to=a["page"] + 1))
    front = dict(number=None, title="(front matter)", level=0, page=1, y=-1,
                 lines=[], images=[], page_to=1)
    ordered = [front] + sections

    cursor = 0
    for i in range(doc.page_count):
        page = doc[i]
        blocks = xy_cut(blocks_of(page, drop))
        for b in blocks:
            while (cursor + 1 < len(ordered)
                   and (ordered[cursor + 1]["page"] - 1, ordered[cursor + 1]["y"])
                   <= (i, b["bbox"][1] + 1.5)):
                cursor += 1
            sec = ordered[cursor]
            sec["page_to"] = i + 1
            if b["kind"] == "image":
                sec["images"].append(dict(page=i + 1, bbox=b["bbox"], xref=b.get("xref"),
                                          width=b.get("width"), height=b.get("height")))
            else:
                for ln in b["lines"]:
                    ln = dict(ln)
                    ln["page"] = i + 1
                    sec["lines"].append(ln)

    return finish(doc, meta, ordered, "flow", drop)


def finish(doc, meta, ordered, profile, drop=()):
    out_sections = []
    for idx, sec in enumerate(ordered):
        # A running footer that shares a block with body text survives the
        # block filter; catch it again per line.
        sec["lines"] = [l for l in sec["lines"] if drop_key(l["text"]) not in drop]
        text = render(sec["lines"])
        if not text and not sec["images"] and sec["title"] == "(front matter)":
            continue
        out_sections.append(dict(
            id=f"{meta['doc_id']}#{sec['number'] or 's%d' % idx}",
            number=sec["number"], title=sec["title"], level=sec["level"],
            page=sec["page"], page_to=sec["page_to"], text=text,
            lines=[dict(t=l["text"], x=l["bbox"][0], y=l["bbox"][1], p=l["page"],
                        s=l["size"], b=l["bold"], i=l["italic"]) for l in sec["lines"]],
            images=sec["images"]))

    doc.close()
    payload = dict(meta=meta, profile=profile, sections=out_sections)
    os.makedirs(DOCS, exist_ok=True)
    with gzip.open(os.path.join(DOCS, meta["doc_id"] + ".json.gz"), "wt",
                   encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False)
    chars = sum(len(s["text"]) for s in out_sections)
    return dict(doc_id=meta["doc_id"], sections=len(out_sections), chars=chars,
                images=sum(len(s["images"]) for s in out_sections),
                pages=meta.get("pages"))


def main():
    corpus = read_json(os.path.join(BUILD, "corpus.json"))
    docs = [d for d in corpus["documents"]
            if not d.get("duplicate_of")]
    only = [a for a in sys.argv[1:] if not a.startswith("-")]
    if only:
        docs = [d for d in docs if d["doc_id"] in only or
                any(o in d["doc_id"] for o in only)]
    print(f"extracting {len(docs)} books")
    results = []
    with ProcessPoolExecutor(max_workers=WORKERS) as pool:
        for r in pool.map(extract_one, docs, chunksize=1):
            results.append(r)
            print(f"  {r['doc_id']:52} {r['sections']:4} sections "
                  f"{r['chars']:8} chars {r['images']:4} images", flush=True)
    write_json(os.path.join(BUILD, "extract_report.json"),
               dict(count=len(results), docs=results))
    print(f"total {sum(r['chars'] for r in results):,} characters, "
          f"{sum(r['sections'] for r in results):,} sections")


if __name__ == "__main__":
    main()
