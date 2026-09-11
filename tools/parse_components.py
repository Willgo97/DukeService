#!/usr/bin/env python3
"""Build the component guide (chapters 4-5 of the Dutch technical manual).

Writes data/components.json plus page renders into app/src/main/assets/img/.
Sections are split out of one continuous text, because several of them share a
page; illustrations are whole page renders, because the embedded images are
layered fragments that mean nothing on their own.

Run from the project root:  python3 tools/parse_components.py
"""
import json, os, re, subprocess, sys, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PDF = os.path.join(ROOT, "manuals", "tm_avy_coex_medium_cec_nl_v10_5dtcet10m.pdf")
IMG_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "img")
BRON = "TM Avy CoEx Medium NL V1.0 (5DTCET10M)"
FIRST, LAST = 28, 99

HEAD = re.compile(r'^\s*(4(?:\.\d+){1,3}|5(?:\.\d+){0,2})\s+([A-Z][^.\n]{3,70})\s*$')
FOOT = re.compile(r'^\s*(\d+\s+)?Avy CEC Medium.*$|^.*Technische handleiding 5DT\w+ NL V[\d.]+\s*\d*\s*$')


def page_text(page):
    return subprocess.run(["pdftotext", "-layout", "-f", str(page), "-l", str(page), PDF, "-"],
                          capture_output=True, text=True).stdout.replace("\x0c", "")


def collect():
    """All lines of the chapter, each tagged with the page it came from."""
    lines = []
    for page in range(FIRST, LAST + 1):
        for line in page_text(page).split("\n"):
            if FOOT.match(line) or not line.strip():
                continue
            if re.match(r'^\s*\d{1,3}\s*$', line):
                continue
            lines.append((page, line))
    return lines


def split(lines):
    secs, cur = [], None
    for page, line in lines:
        m = HEAD.match(line)
        if m and not m.group(2).strip().endswith("."):
            cur = {"nr": m.group(1), "titel": m.group(2).strip(), "pagina": page,
                   "tot": page, "regels": []}
            secs.append(cur)
            continue
        if cur is not None:
            cur["regels"].append(line.strip())
            cur["tot"] = page
    for s in secs:
        text = re.sub(r'\s+', ' ', " ".join(s.pop("regels"))).strip()
        s["tekst"] = re.sub(r'\s+([.,;])', r'\1', text)
    return secs


def trim(img, margin=14):
    """Crop the white page margin; a phone screen has no room to waste."""
    import cv2, numpy as np
    grey = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    ink = np.where(grey < 235)
    if len(ink[0]) == 0:
        return img
    y0, y1 = max(0, ink[0].min() - margin), min(img.shape[0], ink[0].max() + margin)
    x0, x1 = max(0, ink[1].min() - margin), min(img.shape[1], ink[1].max() + margin)
    return img[y0:y1, x0:x1]


def render(secs):
    """One page render per section, at the page where it starts."""
    import cv2
    tmp = os.path.join(ROOT, "txt", "pagetmp")
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp, exist_ok=True)
    shutil.rmtree(IMG_DIR, ignore_errors=True)
    os.makedirs(IMG_DIR, exist_ok=True)

    pages = sorted({s["pagina"] for s in secs})
    saved = {}
    for page in pages:
        subprocess.run(["pdftoppm", "-png", "-r", "110", "-f", str(page), "-l", str(page),
                        PDF, os.path.join(tmp, "pg")], check=True)
        hits = [f for f in os.listdir(tmp) if f.startswith("pg")]
        if not hits:
            continue
        img = cv2.imread(os.path.join(tmp, hits[0]))
        os.remove(os.path.join(tmp, hits[0]))
        if img is None:
            continue
        img = trim(img)
        h, w = img.shape[:2]
        if w > 880:
            img = cv2.resize(img, (880, int(h * 880 / w)), interpolation=cv2.INTER_AREA)
        name = f"p{page}.webp"
        cv2.imwrite(os.path.join(IMG_DIR, name), img, [cv2.IMWRITE_WEBP_QUALITY, 68])
        saved[page] = "img/" + name
    shutil.rmtree(tmp, ignore_errors=True)
    for s in secs:
        if s["pagina"] in saved:
            s["afb"] = saved[s["pagina"]]
    print(f"  {len(saved)} pagina's gerenderd", file=sys.stderr)


def main():
    secs = split(collect())
    keep = [s for s in secs if len(s["tekst"]) > 100]
    render(keep)
    for s in keep:
        s["bron"] = BRON
        s["pagina_tot"] = s.pop("tot")
    json.dump(keep, open(os.path.join(ROOT, "data", "components.json"), "w"),
              ensure_ascii=False, indent=1)
    json.dump(keep, open(os.path.join(ROOT, "app/src/main/assets/components.json"), "w"),
              ensure_ascii=False, separators=(",", ":"))
    print(f"{len(keep)} componenten, {sum(1 for s in keep if s.get('afb'))} met paginabeeld")


if __name__ == "__main__":
    main()
