#!/usr/bin/env python3
"""Build the component guide from every technical manual.

Chapters 4 and 5 explain how the machine works. Each book covers one brewer, so
the Dutch Avy manual describes the CoEx and the others add the CoEx XL, the
Uni-Brewer and the Instant — genuinely different water systems and brewers, not
translations of one another. Where two books share a section word for word (the
Avy and Nio CoEx XL books largely do) it is stored once and both machines are
listed on it.

Illustrations are whole page renders: the images embedded in the PDF are layered
fragments that mean nothing on their own. Pages that render identically are
stored once.

Run from the project root:  python3 tools/parse_components.py
"""
import hashlib, json, os, re, shutil, subprocess, sys

import cv2
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IMG_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "img")
MAX_PAGES = 10          # one long section should not swamp the APK

# 5.6 "Schematische diagrammen" is empty in every book: the chapter announces
# itself and says it is still being written. No point shipping that page.
LEEG = re.compile(r'wordt bijgewerkt|being updated|to be added|to be completed', re.I)

# pdf, machines, brewer, source label, language
BOOKS = [
    ("tm_avy_coex_medium_cec_nl_v10_5dtcet10m.pdf", ["avy", "virtu", "zia"], "CoEx",
     "TM Avy CoEx Medium NL V1.0 (5DTCET10M)", "nl"),
    ("TM_Avy_CoExXL_Medium_XEA_EN_V1.1_5DTXET20M.pdf", ["avy", "lua"], "CoEx XL",
     "TM Avy CoEx XL Medium EN V1.1 (5DTXET20M)", "en"),
    ("tm_nio_coexxl_xka_en_v111_5dtxka20m.pdf", ["nio"], "CoEx XL",
     "TM Nio CoEx XL EN V1.1.1 (5DTXKA20M)", "en"),
    ("TM_Rosa_Filterfresh_Small_FND_EN_V1.1_5DTFNV20M.pdf", ["rosa"], "Uni-Brewer",
     "TM Rosa Filterfresh EN V1.1 (5DTFNV20M)", "en"),
    ("tm_lua_instant_small_inb_nl_v10_5dtins10m.pdf", ["lua"], "Instant",
     "TM Lua Instant Small NL V1.0 (5DTINS10M)", "nl"),
]

HEAD = re.compile(r'^\s*([45](?:\.\d+){1,3})\s+([A-Z][^.\n]{3,70})\s*$')
STOP = re.compile(r'^\s*(?:[6-9]|1\d)(?:\.\d+)*\s+[A-Z][^.\n]{3,70}\s*$')
FOOT = re.compile(
    r'^.*(?:technische handleiding|technical manual|user manual|gebruikershandleiding)\s*5D\w+.*$'
    r'|^\s*\d{1,3}\s*$'
    r'|^.*(?:cabinet|kast)\s+\d+[:.]\d+.*brewer.*$', re.I)

# Which part of the machine a section is about. The books number things
# differently — the CoEx puts the brewer in 4.2, the CoEx XL in 4.1.16 — so the
# grouping follows the subject, not the digits.
GROUPS = [
    ("Elektronica", r'^5\.'),
    ("Verse melk", r'milk|melk'),
    ("Brewer", r'brew|zuiger|piston|filterkop|filter head|afdichting|seal|'
               r'clamp|paper|papier|chamber|kamer'),
    ("Molen", r'grinder|molen|maal|burr'),
    ("Mixer", r'mixer|whipper|klopper'),
    ("Ingrediënten", r'canister|container|ingred|beans|bonen'),
]


def groep(nr, titel):
    for naam, pat in GROUPS:
        if re.search(pat, nr if pat.startswith('^') else titel, re.I):
            return naam
    return "Watersysteem"


def trim(img, margin=12):
    grey = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    dark = (grey < 235).astype(np.uint8)
    if dark.sum() == 0:
        return None
    rows, cols = dark.sum(axis=1), dark.sum(axis=0)
    ys = np.where(rows > max(3, int(img.shape[1] * 0.012)))[0]
    xs = np.where(cols > max(3, int(img.shape[0] * 0.012)))[0]
    if len(ys) == 0 or len(xs) == 0:
        return None
    return img[max(0, ys.min() - margin):min(img.shape[0], ys.max() + margin),
               max(0, xs.min() - margin):min(img.shape[1], xs.max() + margin)]


def illustrated(pdf):
    """Pages carrying a picture worth rendering, by embedded image size."""
    listing = subprocess.run(["pdfimages", "-list", pdf], capture_output=True, text=True).stdout
    pages = set()
    for line in listing.split("\n")[2:]:
        f = line.split()
        if len(f) >= 6 and f[0].isdigit() and int(f[3]) >= 300 and int(f[4]) >= 220:
            pages.add(int(f[0]))
    return pages


def sections(pdf):
    text = subprocess.run(["pdftotext", "-layout", pdf, "-"], capture_output=True, text=True).stdout
    pages = text.split("\x0c")
    out, cur = [], None
    for page_no, page in enumerate(pages, 1):
        if cur is not None:
            cur["tot"] = page_no      # a page of nothing but drawings still counts
        for line in page.split("\n"):
            if ". . ." in line or FOOT.match(line) or not line.strip():
                continue
            m = HEAD.match(line)
            if m and not m.group(2).strip().endswith("."):
                cur = {"nr": m.group(1), "titel": m.group(2).strip(),
                       "pagina": page_no, "tot": page_no, "regels": []}
                out.append(cur)
            elif cur is not None:
                if STOP.match(line):          # chapter 6 onwards is not ours
                    cur["tot"] = page_no
                    cur = None
                    continue
                cur["regels"].append(line.strip())
    for s in out:
        body = re.sub(r'\s+', ' ', " ".join(s.pop("regels"))).strip()
        s["tekst"] = re.sub(r'\s+([.,;])', r'\1', body)
    return out


class Renderer:
    """Renders manual pages to webp, storing each distinct page once."""

    def __init__(self):
        self.by_hash = {}
        self.tmp = os.path.join(ROOT, "txt", "comptmp")
        shutil.rmtree(self.tmp, ignore_errors=True)
        os.makedirs(self.tmp, exist_ok=True)

    def page(self, pdf, page_no):
        subprocess.run(["pdftoppm", "-png", "-r", "110", "-f", str(page_no), "-l", str(page_no),
                        pdf, os.path.join(self.tmp, "pg")], check=True)
        hits = [f for f in os.listdir(self.tmp) if f.startswith("pg")]
        if not hits:
            return None
        path = os.path.join(self.tmp, hits[0])
        img = cv2.imread(path)
        os.remove(path)
        if img is None:
            return None
        img = trim(img[int(img.shape[0] * 0.10):int(img.shape[0] * 0.94)])
        if img is None or img.shape[0] < 80 or img.shape[1] < 80:
            return None
        h, w = img.shape[:2]
        if w > 820:
            img = cv2.resize(img, (820, int(h * 820 / w)), interpolation=cv2.INTER_AREA)
        ok, buf = cv2.imencode(".webp", img, [cv2.IMWRITE_WEBP_QUALITY, 66])
        if not ok:
            return None
        digest = hashlib.sha1(buf.tobytes()).hexdigest()[:12]
        if digest not in self.by_hash:
            self.by_hash[digest] = f"{digest}.webp"
            open(os.path.join(IMG_DIR, self.by_hash[digest]), "wb").write(buf.tobytes())
        return "img/" + self.by_hash[digest]

    def done(self):
        shutil.rmtree(self.tmp, ignore_errors=True)


def main():
    shutil.rmtree(IMG_DIR, ignore_errors=True)
    os.makedirs(IMG_DIR, exist_ok=True)
    render = Renderer()

    alles, seen = [], {}
    for name, machines, brewer, bron, taal in BOOKS:
        pdf = os.path.join(ROOT, "manuals", name)
        if not os.path.exists(pdf):
            print(f"  ontbreekt: {name}", file=sys.stderr)
            continue
        art = illustrated(pdf)
        keep = [s for s in sections(pdf)
                if len(s["tekst"]) > 100
                or any(p in art for p in range(s["pagina"], s["tot"] + 1))]
        boek = re.sub(r'[^a-z0-9]+', '', name.lower())[:10]
        nieuw = plaatjes = 0
        for s in keep:
            if LEEG.search(s["tekst"][:300]):
                continue
            key = (brewer, re.sub(r'\W+', '', s["titel"].lower()),
                   re.sub(r'\W+', '', s["tekst"].lower())[:400])
            if key in seen:
                for m in machines:               # same section, one more machine
                    if m not in seen[key]["machines"]:
                        seen[key]["machines"].append(m)
                continue
            afbs = []
            for p in range(s["pagina"], s["tot"] + 1):
                if p in art and len(afbs) < MAX_PAGES:
                    got = render.page(pdf, p)
                    if got and got not in afbs:
                        afbs.append(got)
            plaatjes += len(afbs)
            item = {
                "id": f"{boek}-{s['nr']}",
                "nr": s["nr"],
                "titel": s["titel"],
                "groep": groep(s["nr"], s["titel"]),
                "tekst": s["tekst"][:9000],
                "pagina": s["pagina"],
                "afbs": afbs,
                "machines": list(machines),
                "brewer": brewer,
                "bron": bron,
                "taal": taal,
            }
            seen[key] = item
            alles.append(item)
            nieuw += 1
        print(f"  {nieuw:3} nieuw ({len(keep) - nieuw:2} dubbel), {plaatjes:3} paginas  "
              f"{brewer:10} {name[:38]}", file=sys.stderr)

    render.done()
    json.dump(alles, open(os.path.join(ROOT, "data", "components.json"), "w"),
              ensure_ascii=False, indent=1)
    json.dump(alles, open(os.path.join(ROOT, "app/src/main/assets/components.json"), "w"),
              ensure_ascii=False, separators=(",", ":"))
    size = sum(os.path.getsize(os.path.join(IMG_DIR, f)) for f in os.listdir(IMG_DIR))
    print(f"{len(alles)} componenten, {len(os.listdir(IMG_DIR))} afbeeldingen, {size // 1024} kB")


if __name__ == "__main__":
    main()
