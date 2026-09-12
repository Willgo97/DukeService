#!/usr/bin/env python3
"""Pull the exploded drawings out of the spare parts books.

Every parts table sits opposite a drawing with numbered balloons. This finds
the drawing page for each section, crops it and writes a WebP, so the app can
show the picture next to the list the way the paper manual does.

Drawings are shared between books (a drip tray sensor is a drip tray sensor),
so identical images are stored once.

Run from the project root:  python3 tools/parse_drawings.py
"""
import hashlib, json, os, re, shutil, subprocess, sys

import cv2
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "tek")
BOOKS = {
    "virtu": "VirtuParts.pdf",
    "lua": "Luaparts.pdf",
    "zia": "Ziaparts.pdf",
    "nio": "Nioparts.pdf",
    "avy": "Avyparts.pdf",
    "rosa": "Spare_Parts_Manual_ROSA_Filterfresh_Small_9FND_EN_2025jan29.pdf",
}
SECTION = re.compile(r'^\s*(\d{4}\s+[A-Z][^\n]{3,60})\s*$', re.M)


def drawing_pages(pdf):
    """Pages carrying a full-size illustration, largest first per page."""
    listing = subprocess.run(["pdfimages", "-list", pdf], capture_output=True, text=True).stdout
    pages = {}
    for line in listing.split("\n")[2:]:
        f = line.split()
        if len(f) < 6:
            continue
        page, w, h = int(f[0]), int(f[3]), int(f[4])
        if w >= 700 and h >= 500:
            pages[page] = max(pages.get(page, 0), w * h)
    return pages


def trim(img, margin=10):
    """Crop to the drawing itself.

    Cutting at the first stray dark pixel keeps the page's thin border frame and
    leaves the drawing floating in white, so rows and columns only count when a
    real share of them carries ink.
    """
    grey = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    dark = (grey < 235).astype(np.uint8)
    if dark.sum() == 0:
        return img
    rows = dark.sum(axis=1)
    cols = dark.sum(axis=0)
    row_min = max(3, int(img.shape[1] * 0.012))
    col_min = max(3, int(img.shape[0] * 0.012))
    ys = np.where(rows > row_min)[0]
    xs = np.where(cols > col_min)[0]
    if len(ys) == 0 or len(xs) == 0:
        ys, xs = np.where(rows > 0)[0], np.where(cols > 0)[0]
        if len(ys) == 0 or len(xs) == 0:
            return img
    y0, y1 = max(0, ys.min() - margin), min(img.shape[0], ys.max() + margin)
    x0, x1 = max(0, xs.min() - margin), min(img.shape[1], xs.max() + margin)
    return img[y0:y1, x0:x1]


def main():
    # start clean, otherwise images from an earlier run pile up
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(OUT, exist_ok=True)
    tmp = os.path.join(ROOT, "txt", "tektmp")
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp, exist_ok=True)

    by_hash = {}           # image hash -> filename, so a shared drawing is stored once
    mapping = {}           # "machine|section" -> "tek/xxx.webp"
    sources = {}           # image hash -> where it came from, for re-rendering
    for machine, filename in BOOKS.items():
        pdf = os.path.join(ROOT, "manuals", filename)
        if not os.path.exists(pdf):
            print(f"  overslaan: {filename}", file=sys.stderr)
            continue
        text = subprocess.run(["pdftotext", "-layout", pdf, "-"], capture_output=True, text=True).stdout
        pages = text.split("\x0c")
        found = 0
        for page in sorted(drawing_pages(pdf)):
            body = pages[page - 1] if page - 1 < len(pages) else ""
            secs = SECTION.findall(body)
            if not secs:
                continue
            section = re.sub(r'\s+', ' ', secs[0]).strip()
            key = f"{machine}|{section}"
            if key in mapping:
                continue
            subprocess.run(["pdftoppm", "-png", "-r", "100", "-f", str(page), "-l", str(page),
                            pdf, os.path.join(tmp, "pg")], check=True)
            hits = [f for f in os.listdir(tmp) if f.startswith("pg")]
            if not hits:
                continue
            path = os.path.join(tmp, hits[0])
            img = cv2.imread(path)
            os.remove(path)
            if img is None:
                continue
            # drop the header band, then trim to the drawing itself
            img = trim(img[int(img.shape[0] * 0.115):int(img.shape[0] * 0.94)])
            h, w = img.shape[:2]
            if w > 720:
                img = cv2.resize(img, (720, int(h * 720 / w)), interpolation=cv2.INTER_AREA)
            ok, buf = cv2.imencode(".webp", img, [cv2.IMWRITE_WEBP_QUALITY, 62])
            if not ok:
                continue
            digest = hashlib.sha1(buf.tobytes()).hexdigest()[:12]
            name = by_hash.get(digest)
            if name is None:
                name = f"{digest}.webp"
                open(os.path.join(OUT, name), "wb").write(buf.tobytes())
                by_hash[digest] = name
                sources[digest] = {"pdf": filename, "page": page}
            mapping[key] = "tek/" + name
            found += 1
        print(f"  {machine:6} {found} tekeningen", file=sys.stderr)

    shutil.rmtree(tmp, ignore_errors=True)
    json.dump(mapping, open(os.path.join(ROOT, "data", "drawings.json"), "w"),
              ensure_ascii=False, indent=1, sort_keys=True)
    json.dump(mapping, open(os.path.join(ROOT, "app/src/main/assets/drawings.json"), "w"),
              ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    json.dump(sources, open(os.path.join(ROOT, "data", "drawing_sources.json"), "w"),
              ensure_ascii=False, indent=1, sort_keys=True)
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f"{len(mapping)} secties, {len(by_hash)} unieke tekeningen, {size // 1024} kB")


if __name__ == "__main__":
    main()
