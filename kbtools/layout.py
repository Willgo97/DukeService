"""Turn a PDF page into ordered text, whatever the page looks like.

The books use three very different layouts: flowing chapters with the heading
hanging in the left margin (technical manuals, quick start guides), a poster
with numbered steps spread over several columns (short maintenance
instructions), and a parts table with fixed columns. One ordering algorithm
covers the first two: cut the page into blank-space bands, preferring a
horizontal cut over a vertical one, because a page reads top to bottom before
it reads left to right.
"""
import re

BULLET = re.compile(r"^\s*(?:[•▪◦·‣–—-]|\d{1,2}[.)]|[a-z][.)]|\([0-9a-z]\))\s+")
ONLY_MARKER = re.compile(r"^\s*(?:[•▪◦·‣–—-]|\d{1,2}[.)]|[a-z][.)]|\([0-9a-z]\))\s*$")
DIGITS = re.compile(r"\d+")
SENTENCE_END = re.compile(r"[.:;!?]\s*$")


def drop_key(text):
    """Header and footer lines differ only in the page number."""
    return DIGITS.sub("#", re.sub(r"\s+", " ", text)).strip()


def blocks_of(page, drop=()):
    """Text and image blocks with the details the rest of the pipeline needs."""
    out = []
    top, bottom = page.rect.height * 0.08, page.rect.height * 0.92
    for b in page.get_text("dict", flags=7)["blocks"]:
        bbox = tuple(round(v, 1) for v in b["bbox"])
        if b.get("type") == 1:
            out.append(dict(kind="image", bbox=bbox, width=b.get("width"),
                            height=b.get("height"), xref=b.get("number")))
            continue
        lines = []
        for ln in b.get("lines", []):
            spans = [s for s in ln["spans"] if s["text"].strip()]
            if not spans:
                continue
            text = "".join(s["text"] for s in ln["spans"])
            text = text.replace(" ", " ").rstrip()
            if not text.strip():
                continue
            lines.append(dict(
                text=text,
                bbox=tuple(round(v, 1) for v in ln["bbox"]),
                size=round(max(s["size"] for s in spans), 1),
                bold=any("bold" in s["font"].lower() or s["flags"] & 16 for s in spans),
                italic=any("italic" in s["font"].lower() or s["flags"] & 2 for s in spans),
            ))
        if not lines:
            continue
        text = "\n".join(l["text"] for l in lines)
        margin = bbox[3] < top or bbox[1] > bottom
        if margin and drop_key(text) in drop:
            continue
        out.append(dict(kind="text", bbox=bbox, lines=lines))
    return out


def _gaps(intervals, min_gap):
    """Empty stretches, at least min_gap wide, that no interval covers."""
    if not intervals:
        return []
    intervals = sorted(intervals)
    gaps, edge = [], intervals[0][1]
    for a, b in intervals[1:]:
        if a - edge >= min_gap:
            gaps.append((edge, a))
        edge = max(edge, b)
    return gaps


def xy_cut(blocks, min_hgap=6.0, min_vgap=14.0, depth=0):
    """Reading order by recursive whitespace cuts, horizontal first."""
    if len(blocks) <= 1 or depth > 12:
        return list(blocks)
    rows = _gaps([(b["bbox"][1], b["bbox"][3]) for b in blocks], min_hgap)
    if rows:
        edge = rows[0][0]
        top = [b for b in blocks if b["bbox"][1] < edge]
        rest = [b for b in blocks if b["bbox"][1] >= edge]
        if top and rest:
            return (xy_cut(top, min_hgap, min_vgap, depth + 1) +
                    xy_cut(rest, min_hgap, min_vgap, depth + 1))
    cols = _gaps([(b["bbox"][0], b["bbox"][2]) for b in blocks], min_vgap)
    if cols:
        edge = cols[0][0]
        left = [b for b in blocks if b["bbox"][0] < edge]
        rest = [b for b in blocks if b["bbox"][0] >= edge]
        if left and rest:
            return (xy_cut(left, min_hgap, min_vgap, depth + 1) +
                    xy_cut(rest, min_hgap, min_vgap, depth + 1))
    return sorted(blocks, key=lambda b: (b["bbox"][1], b["bbox"][0]))


def drop_marker_column(lines):
    """Some books set the bullet or step number in a column of its own.

    The glyph then arrives as a line with nothing but "3." or a bullet in it,
    next to the line it belongs with. Fold it back in, or drop it when the
    text already carries the same marker.
    """
    keep = []
    for idx, ln in enumerate(lines):
        if not ONLY_MARKER.match(ln["text"]):
            keep.append(ln)
            continue
        mark = ln["text"].strip()
        partner = None
        for other in (lines[idx + 1] if idx + 1 < len(lines) else None,
                      lines[idx - 1] if idx else None):
            if other is None or other.get("page") != ln.get("page"):
                continue
            if abs(other["bbox"][1] - ln["bbox"][1]) < 4.5:
                partner = other
                break
        if partner is None:
            continue
        if partner["text"].lstrip().startswith(mark) or BULLET.match(partner["text"]):
            continue                      # already carries a marker
        partner["text"] = mark + " " + partner["text"].lstrip()
    return keep


def render(lines):
    """Join wrapped lines back into paragraphs, keep list items on their own line."""
    lines = drop_marker_column([dict(l) for l in lines])
    out = []
    prev = None
    for ln in lines:
        text = ln["text"].strip()
        if not text:
            continue
        start_new = True
        if prev is not None:
            same_indent = abs(ln["bbox"][0] - prev["bbox"][0]) < 4
            gap = ln["bbox"][1] - prev["bbox"][3]
            height = max(6.0, prev["bbox"][3] - prev["bbox"][1])
            wrapped = (same_indent and gap < 0.8 * height
                       and not BULLET.match(ln["text"])
                       and not SENTENCE_END.search(prev["text"])
                       and prev["size"] == ln["size"])
            hanging = (ln["bbox"][0] > prev["bbox"][0] + 3 and gap < 0.8 * height
                       and BULLET.match(prev["text"]) is not None)
            start_new = not (wrapped or hanging)
        if start_new:
            out.append(text)
        else:
            out[-1] = out[-1] + " " + text
        prev = ln
    return "\n".join(out)


def running_text(doc, sample=24):
    """Header and footer lines that repeat across the book, so they can go."""
    from collections import Counter
    seen = Counter()
    pages = doc.page_count
    step = max(1, pages // sample)
    checked = 0
    for i in range(0, pages, step):
        page = doc[i]
        top, bottom = page.rect.height * 0.08, page.rect.height * 0.92
        for b in page.get_text("dict", flags=7)["blocks"]:
            if b.get("type") == 1:
                continue
            y0, y1 = b["bbox"][1], b["bbox"][3]
            if y1 > top and y0 < bottom:
                continue
            lines = ["".join(s["text"] for s in l["spans"]) for l in b["lines"]]
            text = "\n".join(lines)
            if not text.strip():
                continue
            seen[drop_key(text)] += 1
            # The footer block usually carries the page number as a second
            # line. Index the lines separately too, because a footer that ends
            # up inside a body block has to be recognised line by line.
            if len(lines) > 1:
                for line in lines:
                    if line.strip():
                        seen[drop_key(line)] += 1
        checked += 1
    return {t for t, n in seen.items() if n > max(2, checked * 0.4)}
