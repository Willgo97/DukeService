#!/usr/bin/env python3
"""Build the service-menu guide (chapters 6 and 7 of every technical manual).

Chapter 6 walks through the menu the engineer sees on the machine, chapter 7
through the jobs done from it -- descaling, cleaning, calibrating. The books
overlap heavily because the menu is the same software, so a topic that appears
word for word in several manuals is stored once with all machines listed on it.

Run after parse_components.py: that one clears the image folder.
"""
import json, os, re, subprocess, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from parse_components import BOOKS, Renderer, illustrated   # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

HEAD = re.compile(r'^\s*([67](?:\.\d+){1,2})\s+([A-Z][^.\n]{3,70})\s*$')
STOP = re.compile(r'^\s*(?:[89]|1\d)(?:\.\d+)*\s+[A-Z][^.\n]{3,70}\s*$')
FOOT = re.compile(
    r'^.*(?:technische handleiding|technical manual)\s*5D\w+.*$'
    r'|^\s*\d{1,3}\s*$'
    r'|^\s*(?:\d{1,3}\s+)?\w+\s+(?:CEC|CND|XEA|XKA|FND|INB)\s+(?:Medium|Small).*$'
    r'|^.*(?:cabinet|kast)\s+\d+[:.]\d+.*$', re.I)
PATH = re.compile(r'((?:Hoofdmenu|Main menu)\s*->[^.\n]+)')
LEVEL = re.compile(r'\((?:Niveau|Level) (\d)\)', re.I)
LABEL = re.compile(r'^(\s+)(Doel|Purpose|Vereist|Required|Interval|Procedure|Resultaat|Result)'
                   r'(?:\s{2,}(.*))?\s*$')
NOTE = re.compile(r'\s*(?:OPMERKING|NOTE)\s+')
BULL = re.compile(r'^\s*[•▪]\s*(.*)$')
STEP = re.compile(r'^\s*(\d{1,2})\.\s+(.*)$')
FIELDS = {"doel": "doel", "purpose": "doel", "vereist": "nodig", "required": "nodig",
          "interval": "interval", "procedure": "procedure", "resultaat": "procedure",
          "result": "procedure"}


def join(parts):
    return re.sub(r'\s+([.,;])', r'\1', re.sub(r'\s+', ' ', " ".join(parts)).strip())


def sections(pdf):
    text = subprocess.run(["pdftotext", "-layout", pdf, "-"], capture_output=True, text=True).stdout
    secs, cur = [], None
    for page_no, page in enumerate(text.split("\x0c"), 1):
        if cur is not None:
            cur["tot"] = page_no
        for line in page.split("\n"):
            if ". . ." in line or FOOT.match(line) or not line.strip():
                continue
            m = HEAD.match(line)
            if m and not m.group(2).strip().endswith("."):
                cur = {"nr": m.group(1), "titel": m.group(2).strip(), "pagina": page_no,
                       "tot": page_no, "regels": [], "ruw": [], "pad": ""}
                secs.append(cur)
                continue
            if cur is None:
                continue
            if STOP.match(line):
                cur["tot"] = page_no
                cur = None
                continue
            stripped = line.strip()
            if PATH.fullmatch(stripped) and not cur["pad"]:
                cur["pad"] = re.sub(r'\s+', ' ', stripped)
                continue
            cur["ruw"].append(line)
            cur["regels"].append(stripped)
    for s in secs:
        finish(s)
    return secs


def finish(s):
    body = join(s.pop("regels")).lstrip('. ').strip()
    lvl = LEVEL.search(s["titel"])
    s["niveau"] = lvl.group(1) if lvl else ""
    s["titel"] = LEVEL.sub("", s["titel"]).strip()

    # The manual drops NOTE blocks into the running text; pull them out so they
    # can be shown as what they are.
    notes = []
    while True:
        m = NOTE.search(body)
        if not m:
            break
        rest = body[m.end():]
        cut = re.search(r'(?<=\.)\s+(?=[A-Z][a-z]{3,})', rest)
        notes.append((rest[:cut.start()] if cut else rest).strip())
        body = (body[:m.start()] + ' ' + (rest[cut.start():] if cut else '')).strip()
    s["opmerkingen"] = [join([n]) for n in notes if len(n) > 10]

    # Numbered enumerations read as a list, not as one long line.
    punten = re.findall(r'(?:(?<=\s)|^)(\d{1,2})\.\s+([^0-9]{4,120}?)(?=\s+\d{1,2}\.\s|$)', body)
    if len(punten) >= 3:
        s["punten"] = [f"{n}. {t.strip()}" for n, t in punten]
        body = body[:body.find(punten[0][0] + '.')].strip()
    else:
        s["punten"] = []
    s["tekst"] = body
    if s["nr"].startswith("7"):
        structure(s)
    s.pop("ruw", None)


def structure(sec):
    """Split a chapter-7 job into purpose, needs, interval and numbered steps."""
    doel, nodig, interval, stappen = [], [], [], []
    field = None
    for line in sec["ruw"]:
        lm = LABEL.match(line)
        if lm:
            field = FIELDS[lm.group(2).lower()]
            rest = (lm.group(3) or "").strip()
            if field == "doel" and rest:
                doel.append(rest)
            elif field == "interval" and rest and not re.match(
                    r'(?:voer deze procedure|perform this procedure)', rest, re.I):
                interval.append(rest)
            continue
        text = line.strip()
        if not text:
            continue
        bm, sm = BULL.match(line), STEP.match(line)
        if field == "nodig":
            if bm:
                nodig.append(bm.group(1).strip())
            elif nodig:
                nodig[-1] += " " + text
            continue
        if field == "interval":
            interval.append(bm.group(1).strip() if bm else text)
            continue
        if field == "procedure":
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

    # pdftotext flattens the phase table into noise; the page image shows it properly.
    sec["doel"] = re.split(r'\s*(?:De ontkalkprocedure is|The descaling procedure is|#\s*ACTIE|#\s*ACTION)',
                           join(doel))[0].strip()
    sec["nodig"] = [join([n]) for n in nodig]
    sec["interval"] = join(interval)
    sec["stappen"] = [join([st]) for st in stappen if join([st])]
    if sec["stappen"] or sec["doel"]:
        sec["tekst"] = ""
    return sec


def main():
    render = Renderer()
    alles, seen = [], {}
    for name, machines, brewer, bron, taal in BOOKS:
        pdf = os.path.join(ROOT, "manuals", name)
        if not os.path.exists(pdf):
            continue
        art = illustrated(pdf)
        boek = re.sub(r'[^a-z0-9]+', '', name.lower())[:10]
        secs = [s for s in sections(pdf)
                if len(s["tekst"]) > 60 or s["pad"] or s.get("stappen") or s.get("doel")
                or s["punten"] or s["opmerkingen"]]
        nieuw = plaatjes = 0
        for s in secs:
            key = (re.sub(r'\W+', '', s["titel"].lower()),
                   re.sub(r'\W+', '', (s["tekst"] + s["doel"] if s["nr"].startswith("7")
                                       else s["tekst"]).lower())[:300])
            if key in seen:
                for m in machines:
                    if m not in seen[key]["machines"]:
                        seen[key]["machines"].append(m)
                continue
            # Chapter 7 jobs run over several pages of screenshots; a chapter 6
            # topic only needs the page it sits on.
            span = range(s["pagina"], s["tot"] + 1) if s["nr"].startswith("7") else [s["pagina"]]
            afbs = []
            for p in span:
                if p in art and len(afbs) < 8:
                    got = render.page(pdf, p)
                    if got and got not in afbs:
                        afbs.append(got)
            plaatjes += len(afbs)
            item = {
                "id": f"{boek}-{s['nr']}",
                "nr": s["nr"], "titel": s["titel"], "tekst": s["tekst"],
                "pad": s["pad"], "niveau": s["niveau"], "pagina": s["pagina"],
                "afbs": afbs, "afb": afbs[0] if afbs else "",
                "opmerkingen": s["opmerkingen"], "punten": s["punten"],
                "doel": s.get("doel", ""), "nodig": s.get("nodig", []),
                "interval": s.get("interval", ""), "stappen": s.get("stappen", []),
                "machines": list(machines), "bron": bron, "taal": taal,
            }
            seen[key] = item
            alles.append(item)
            nieuw += 1
        print(f"  {nieuw:3} nieuw ({len(secs) - nieuw:2} dubbel), {plaatjes:3} paginas  "
              f"{name[:38]}", file=sys.stderr)
    render.done()

    json.dump(alles, open(os.path.join(ROOT, "data", "servicemenu.json"), "w"),
              ensure_ascii=False, indent=1)
    json.dump(alles, open(os.path.join(ROOT, "app/src/main/assets/servicemenu.json"), "w"),
              ensure_ascii=False, separators=(",", ":"))
    print(f"{len(alles)} servicemenu-onderwerpen, {sum(1 for s in alles if s['afb'])} met beeld, "
          f"{sum(1 for s in alles if s['pad'])} met menupad")


if __name__ == "__main__":
    main()
