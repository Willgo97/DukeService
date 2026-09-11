#!/usr/bin/env python3
"""Build the service-menu guide (chapters 6-7 of the Dutch technical manual).

Writes data/servicemenu.json and renders only the pages that actually carry an
illustration. Run from the project root.
"""
import json, os, re, subprocess, sys, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PDF = os.path.join(ROOT, "manuals", "tm_avy_coex_medium_cec_nl_v10_5dtcet10m.pdf")
IMG_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "img")
BRON = "TM Avy CoEx Medium NL V1.0 (5DTCET10M)"
FIRST, LAST = 100, 138

HEAD = re.compile(r'^\s*([67](?:\.\d+){1,2})\s+([A-Z][^.\n]{3,70})\s*$')
FOOT = re.compile(r'^\s*(\d+\s+)?Avy CEC Medium.*$|^.*Technische handleiding 5DT\w+ NL V[\d.]+\s*\d*\s*$')
PATH = re.compile(r'((?:Hoofdmenu|Main menu)\s*->[^.\n]+)')
LEVEL = re.compile(r'\(Niveau (\d)\)')
# Chapter 7 is written as procedures, with the same labels as the user manual.
LABEL = re.compile(r'^(\s+)(Doel|Vereist|Interval|Procedure|Resultaat)(?:\s{2,}(.*))?\s*$')
BULL = re.compile(r'^\s*[•▪]\s*(.*)$')
STEP = re.compile(r'^\s*(\d{1,2})\.\s+(.*)$')


def page_text(page):
    return subprocess.run(["pdftotext", "-layout", "-f", str(page), "-l", str(page), PDF, "-"],
                          capture_output=True, text=True).stdout.replace("\x0c", "")


def sections():
    secs, cur = [], None
    for page in range(FIRST, LAST + 1):
        for line in page_text(page).split("\n"):
            if FOOT.match(line) or not line.strip():
                continue
            if re.match(r'^\s*\d{1,3}\s*$', line):
                continue
            m = HEAD.match(line)
            if m and not m.group(2).strip().endswith("."):
                cur = {"nr": m.group(1), "titel": m.group(2).strip(), "pagina": page,
                       "tot": page, "regels": [], "ruw": [], "pad": ""}
                secs.append(cur)
                continue
            if cur is not None:
                stripped = line.strip()
                if PATH.fullmatch(stripped) and not cur.get("pad"):
                    cur["pad"] = re.sub(r'\s+', ' ', stripped)
                    continue
                cur["ruw"].append(line)
                cur["regels"].append(stripped)
                cur["tot"] = page
    for s in secs:
        text = re.sub(r'\s+', ' ', " ".join(s.pop("regels"))).strip()
        text = re.sub(r'\s+([.,;])', r'\1', text)
        lvl = LEVEL.search(s["titel"])
        s["niveau"] = lvl.group(1) if lvl else ""
        s["titel"] = LEVEL.sub("", s["titel"]).strip()
        s.setdefault("pad", "")
        s["tekst"] = text.lstrip('. ').strip()
        if s["nr"].startswith("7"):
            structure(s)
        s.pop("ruw", None)
    return secs


def structure(sec):
    """Split a chapter-7 job into purpose, needs, interval and numbered steps."""
    doel, nodig, interval, stappen = [], [], [], []
    field = None
    for line in sec["ruw"]:
        lm = LABEL.match(line)
        if lm:
            field = lm.group(2).lower()
            rest = (lm.group(3) or "").strip()
            if field == "doel" and rest:
                doel.append(rest)
            elif field == "interval" and rest and not rest.lower().startswith("voer deze procedure"):
                interval.append(rest)
            continue
        text = line.strip()
        if not text:
            continue
        bm, sm = BULL.match(line), STEP.match(line)
        if field == "vereist":
            if bm:
                nodig.append(bm.group(1).strip())
            elif nodig:
                nodig[-1] += " " + text
            continue
        if field == "interval":
            interval.append(bm.group(1).strip() if bm else text)
            continue
        if field in ("procedure", "resultaat"):
            if sm:
                stappen.append(sm.group(2).strip())
            elif bm and stappen:
                stappen[-1] += " — " + bm.group(1).strip()
            elif stappen:
                stappen[-1] += " " + text
            else:
                doel.append(text)
            continue
        if field == "doel":
            doel.append(text)

    def join(parts):
        return re.sub(r'\s+([.,;])', r'\1', re.sub(r'\s+', ' ', " ".join(parts)).strip())

    purpose = join(doel)
    # pdftotext flattens the phase table into noise; the page image shows it properly.
    purpose = re.split(r'\s*(?:De ontkalkprocedure is|#\s*ACTIE)', purpose)[0].strip()
    sec["doel"] = purpose
    sec["nodig"] = [join([n]) for n in nodig]
    sec["interval"] = join(interval)
    sec["stappen"] = [join([st]) for st in stappen if join([st])]
    if sec["stappen"] or sec["doel"]:
        sec["tekst"] = ""
    return sec


def pages_with_images():
    listing = subprocess.run(["pdfimages", "-list", PDF], capture_output=True, text=True).stdout
    pages = set()
    for line in listing.split("\n")[2:]:
        f = line.split()
        if len(f) < 6:
            continue
        page, w, h = int(f[0]), int(f[3]), int(f[4])
        if FIRST <= page <= LAST and w >= 300 and h >= 220:
            pages.add(page)
    return pages


def render(secs, wanted):
    import cv2
    from importlib import import_module
    trim = import_module("parse_components").trim
    tmp = os.path.join(ROOT, "txt", "smtmp")
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp, exist_ok=True)
    os.makedirs(IMG_DIR, exist_ok=True)

    needed = {s["pagina"] for s in secs} & wanted
    for s in secs:
        if s["nr"].startswith("7"):
            needed |= set(range(s["pagina"], s.get("tot", s["pagina"]) + 1))

    saved = {}
    for page in sorted(needed):
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
        if s["nr"].startswith("7"):
            pages = range(s["pagina"], s.get("tot", s["pagina"]) + 1)
            s["afbs"] = [saved[p] for p in pages if p in saved]
        elif s["pagina"] in saved:
            s["afb"] = saved[s["pagina"]]
    print(f"  {len(saved)} pagina's gerenderd", file=sys.stderr)


def main():
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    secs = [s for s in sections()
            if len(s["tekst"]) > 60 or s["pad"] or s.get("stappen") or s.get("doel")]
    render(secs, pages_with_images())
    for s in secs:
        s["bron"] = BRON
        s.pop("tot", None)
    json.dump(secs, open(os.path.join(ROOT, "data", "servicemenu.json"), "w"),
              ensure_ascii=False, indent=1)
    json.dump(secs, open(os.path.join(ROOT, "app/src/main/assets/servicemenu.json"), "w"),
              ensure_ascii=False, separators=(",", ":"))
    print(f"{len(secs)} servicemenu-onderwerpen, {sum(1 for s in secs if s.get('afb'))} met beeld, "
          f"{sum(1 for s in secs if s['pad'])} met menupad")


if __name__ == "__main__":
    main()
