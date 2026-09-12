#!/usr/bin/env python3
"""Work out where each balloon number sits on the exploded drawings.

The numbers are pixels, not text. Machine OCR reads the two-digit balloons well
but drops lone digits, so this does it in two passes:

  1. detect the balloon circles, which are drawn at a consistent size
  2. take the numbers OCR did read, use those circles as labelled examples, and
     match the rest against them — every balloon in a book comes from the same
     CAD export, so the glyphs are identical

The expected positions per section come from the parts tables, which makes the
result checkable: a drawing is only kept when what was read matches what the
table says should be there.

Run from the project root:  python3 tools/index_balloons.py
"""
import collections, json, os, sys

import cv2
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OCR_DIR = os.path.join(ROOT, "txt", "ocr")
GLYPH = (20, 28)


def all_glyphs(img):
    """Every digit-sized shape on the page, wherever it sits.

    Not every book draws balloons: the Rosa drawings put bare numbers at the end
    of a leader line, so relying on circles misses them entirely.
    """
    ink = (img < 128).astype(np.uint8)
    n, labels, stats, cent = cv2.connectedComponentsWithStats(ink, connectivity=8)
    heights = []
    for i in range(1, n):
        x, y, w, h, area = stats[i]
        if 18 <= h <= 70 and 4 <= w <= 60 and area >= 25 and area <= w * h * 0.9:
            heights.append(h)
    if not heights:
        return [], 0
    med = float(np.median(heights))
    out = []
    for i in range(1, n):
        x, y, w, h, area = stats[i]
        if not (med * 0.65 <= h <= med * 1.4):
            continue
        if not (med * 0.12 <= w <= med * 1.1):
            continue
        if area < 20 or area > w * h * 0.92:
            continue
        piece = (labels[y:y + h, x:x + w] == i).astype(np.uint8) * 255
        out.append({
            "x": x, "y": y, "w": w, "h": h,
            "cx": cent[i][0], "cy": cent[i][1],
            "img": cv2.resize(piece, GLYPH, interpolation=cv2.INTER_AREA),
        })
    return out, med


def group(glyph_list, med):
    """Chain glyphs that sit side by side into one number."""
    used = [False] * len(glyph_list)
    order = sorted(range(len(glyph_list)), key=lambda i: (glyph_list[i]["cy"], glyph_list[i]["x"]))
    groups = []
    for i in order:
        if used[i]:
            continue
        chain = [i]
        used[i] = True
        while True:
            last = glyph_list[chain[-1]]
            nxt = None
            for j in order:
                if used[j]:
                    continue
                g = glyph_list[j]
                if abs(g["cy"] - last["cy"]) > med * 0.35:
                    continue
                gap = g["x"] - (last["x"] + last["w"])
                if -2 <= gap <= med * 0.45:
                    if nxt is None or g["x"] < glyph_list[nxt]["x"]:
                        nxt = j
            if nxt is None or len(chain) >= 3:
                break
            used[nxt] = True
            chain.append(nxt)
        groups.append([glyph_list[k] for k in chain])
    return groups


def main():
    spots = json.load(open(os.path.join(ROOT, "txt", "hotspots.json")))
    tek = json.load(open(os.path.join(ROOT, "app/src/main/assets/drawings.json")))
    parts = json.load(open(os.path.join(ROOT, "app/src/main/assets/parts.json")))

    wanted = collections.defaultdict(set)      # digest -> positions the tables mention
    for p in parts:
        if not p["p"]:
            continue
        path = tek.get(f"{p['m']}|{p['s']}")
        if path:
            wanted[path.split("/")[-1][:-5]].add(p["p"].lstrip("0").lower() or "0")

    # --- pass one: circles, and the labels OCR already gave us --------------
    found = {}      # digest -> list of (cx, cy, r, label|None, glyphs)
    samples = collections.defaultdict(list)    # digit -> glyph images
    for name in sorted(os.listdir(OCR_DIR)):
        if not name.endswith(".png"):
            continue
        digest = name[:-4]
        img = cv2.imread(os.path.join(OCR_DIR, name), cv2.IMREAD_GRAYSCALE)
        if img is None:
            continue
        h, w = img.shape
        pieces, med = all_glyphs(img)
        numbers = group(pieces, med) if pieces else []
        read = spots.get(digest, [])
        balloons = []
        for chain in numbers:
            cx = sum(g["cx"] for g in chain) / len(chain)
            cy = sum(g["cy"] for g in chain) / len(chain)
            label = None
            for s in read:
                sx, sy = s["x"] * w, s["y"] * h
                if abs(sx - cx) < med * 1.6 and abs(sy - cy) < med * 0.9:
                    label = s["n"].lstrip("0").lower() or "0"
                    break
            if label and label.isdigit() and len(label) == len(chain):
                for digit, g in zip(label, chain):
                    samples[digit].append(g["img"])
            balloons.append((cx, cy, med, label, [g["img"] for g in chain]))
        found[digest] = (w, h, balloons)

    print(f"{len(found)} tekeningen, "
          f"{sum(len(b) for _, _, b in found.values())} ballonnen, "
          f"voorbeelden per cijfer: {{{', '.join(f'{k}:{len(v)}' for k, v in sorted(samples.items()))}}}",
          file=sys.stderr)

    # --- templates ---------------------------------------------------------
    templates = {}
    for digit, images in samples.items():
        if len(images) >= 2:
            templates[digit] = np.mean(np.stack(images).astype(np.float32), axis=0)
    if len(templates) < 8:
        print(f"te weinig cijfers geleerd ({sorted(templates)}), stoppen", file=sys.stderr)
        return

    def classify(g):
        scores = {d: float(np.mean(np.abs(t - g.astype(np.float32)))) for d, t in templates.items()}
        best = min(scores, key=scores.get)
        return best, scores[best]

    # --- pass two: read the balloons OCR skipped ---------------------------
    out = {}
    for digest, (w, h, balloons) in found.items():
        result = []
        for cx, cy, r, label, gs in balloons:
            rx, ry = cx / w, cy / h
            # the header strip and the drawing-number box hold digits that look
            # exactly like balloons and would otherwise steal their place
            if ry < 0.05 or (ry < 0.09 and rx > 0.70):
                continue
            score = 0.0
            if label is None and gs and len(gs) <= 3:
                guess = "".join(classify(g)[0] for g in gs)
                score = max(classify(g)[1] for g in gs)
                if score < 95:
                    label = guess.lstrip("0") or "0"
            if label:
                result.append({"n": label, "x": round(rx, 4), "y": round(ry, 4),
                               "r": round(r * 0.9 / w, 4), "_s": score})
        expect = wanted.get(digest)
        if expect:
            result = [s for s in result if s["n"] in expect]
        # one hotspot per number: the most confident reading wins
        best = {}
        for s in result:
            if s["n"] not in best or s["_s"] < best[s["n"]]["_s"]:
                best[s["n"]] = s
        out[digest] = [{k: v for k, v in s.items() if k != "_s"}
                       for s in sorted(best.values(), key=lambda s: (len(s["n"]), s["n"]))]

    json.dump(out, open(os.path.join(ROOT, "data", "hotspots.json"), "w"),
              ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    json.dump(out, open(os.path.join(ROOT, "app/src/main/assets/hotspots.json"), "w"),
              ensure_ascii=False, separators=(",", ":"), sort_keys=True)

    total = sum(len(v) for v in out.values())
    expect_total = sum(len(wanted.get(d, ())) for d in out)
    print(f"{total} ballonnen geplaatst van {expect_total} posities "
          f"({total * 100 // max(expect_total, 1)}%)")


if __name__ == "__main__":
    main()
