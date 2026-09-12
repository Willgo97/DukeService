#!/usr/bin/env python3
"""Add the Rosa Filterfresh procedures to the hand-checked Dutch ones.

data/procedures_base.json holds the Dutch procedures written from the Virtu
(CoEx) and Lua (CoEx XL) user manuals. The Uni-Brewer is a different machine --
it brews through filter paper -- and its only manual here is English, so those
procedures are taken from the book as they stand and marked as English. The
Avy CoEx XL manual documents the same procedure set as the Lua one, so that
machine is added to the existing CoEx XL entries instead of duplicating them.

Run from the project root:  python3 tools/parse_procedures.py
"""
import json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROSA = "User_Manual_Rosa_Filterfresh_Small_FND_EN_V2.3_5DUFNV20M.pdf"
BRON = "UM Rosa Filterfresh EN V2.3 (5DUFNV20M)"

HEAD = re.compile(r'^\s*(5\.[3-6](?:\.\d+)?)\s+([A-Z][^.\n]{3,70})\s*$')
STOP = re.compile(r'^\s*(?:5\.7|[6-9])(?:\.\d+)*\s+[A-Z][^.\n]{3,70}\s*$')
FOOT = re.compile(r'^.*User Manual\s*5D\w+.*$|^\s*\d{1,3}\s*$', re.I)
LABEL = re.compile(r'^(\s+)(Purpose|Required|Interval|Procedure|Result)(?:\s{2,}(.*))?\s*$')
WARN = re.compile(r'^\s*(NOTE|WARNING|CAUTION|DANGER)\s*$')
BULL = re.compile(r'^\s*[•▪]\s*(.*)$')
STEP = re.compile(r'^\s*(\d{1,2})\.\s+(.*)$')
PAGEREF = re.compile(r'\s*\(see .*? on page \d+\)|\s*on page \d+', re.I)

# The Dutch list reads as one set; only the body text stays English.
TITELS = {
    "Switch on": ("Machine inschakelen", "inschakelen"),
    "Open the door": ("Deur openen", "deur-openen"),
    "Close the door": ("Deur sluiten", "deur-sluiten"),
    "Open or close top lid": ("Bovenklep openen of sluiten", "bovenklep"),
    "Switch off / Putting out of order": ("Machine uitschakelen / buiten gebruik stellen", "uitschakelen"),
    "Scheduled rinse": ("Geplande spoeling", "geplande-spoeling"),
    "Flush mixers and brewer": ("Mixers en brewer doorspoelen", "mixers-spoelen"),
    "Clean waste bucket": ("Afvalemmer reinigen", "afvalemmer-reinigen"),
    "Clean the cup stand": ("Bekerstandaard reinigen", "bekerstandaard-reinigen"),
    "Clean the waste bin in the base cabinet": ("Afvalbak in de onderkast reinigen", "afvalbak-onderkast"),
    "Clean the drip tray": ("Lekbak reinigen", "lekbak-reinigen"),
    "Clean the mixing system": ("Mixsysteem reinigen", "mixsysteem-reinigen"),
    "Remove and clean the brewer": ("Brewer uitnemen en reinigen", "brewer-schoonmaken"),
    "Clean brewer with cleaning tablet": ("Brewer reinigen met reinigingstablet", "brewer-tablet"),
    "Unblock the grinder": ("Molen vrijmaken", "molen-vrijmaken"),
    "Place new filter paper": ("Nieuw filterpapier plaatsen", "filterpapier"),
    "Clean the canisters": ("Canisters reinigen", "canisters-reinigen"),
    "Clean the cold water outlet nozzle — optional": ("Koudwateruitloop reinigen", "koudwater-uitloop"),
    "Deep clean the cold water outlet nozzle — optional": ("Koudwateruitloop diepreinigen", "koudwater-diepreiniging"),
    "Fill up the ingredient canisters": ("Ingrediëntcanisters vullen", "canisters-vullen"),
    "Fill up the bean canister": ("Bonencanister vullen", "bonencanister-vullen"),
    "Enter canister ingredient levels — optional": ("Canisterniveaus invoeren", "niveaus-invoeren"),
    "Clean the outside of the machine": ("Buitenkant van de machine reinigen", "buitenkant-reinigen"),
    "Clean the touchscreen — optional": ("Touchscreen reinigen", "touchscreen-reinigen"),
    "The service key": ("De servicesleutel", "servicesleutel"),
}
SCHEMA = {"5.3": ("dag", "Dagelijks onderhoud"), "5.4": ("week", "Wekelijks onderhoud"),
          "5.5": ("maand", "Maandelijks onderhoud")}
# Checklist lines that are not a procedure of their own.
LOSSE = {"Check correct functioning by taking a test beverage":
         "Controleer de werking met een testdrank"}
INTERVAL = [("dag", r'\bdaily\b'), ("week", r'\bweekly\b'), ("maand", r'\bmonthly\b'),
            ("halfjaar", r'six months|half year|semi-annual'), ("jaar", r'\byearly\b|annual')]


def clean(text):
    text = PAGEREF.sub("", re.sub(r'\s+', ' ', text)).strip()
    text = re.sub(r"\s*\(\s*\)", "", text)
    return re.sub(r"\s+([.,;])", r"\1", text)


def sections(pdf):
    text = subprocess.run(["pdftotext", "-layout", pdf, "-"], capture_output=True, text=True).stdout
    out, cur, seen = [], None, set()
    for page in text.split("\x0c"):
        for line in page.split("\n"):
            if ". . ." in line or FOOT.match(line) or not line.strip():
                continue
            m = HEAD.match(line)
            if m and m.group(1) not in seen:
                seen.add(m.group(1))
                cur = {"nr": m.group(1), "titel": m.group(2).strip(), "ruw": []}
                out.append(cur)
            elif cur is not None:
                if STOP.match(line):
                    cur = None
                    continue
                cur["ruw"].append(line)
    return out


def structure(sec):
    """Purpose / Required / Interval / Procedure, plus NOTE and WARNING blocks."""
    doel, nodig, interval, stappen, warns = [], [], [], [], []
    field, warn = None, None
    for line in sec["ruw"]:
        if WARN.match(line):
            warn = {"n": WARN.match(line).group(1).strip().lower(), "t": []}
            warns.append(warn)
            continue
        lm = LABEL.match(line)
        if lm:
            warn = None
            field = lm.group(2).lower()
            rest = (lm.group(3) or "").strip()
            if field == "purpose" and rest:
                doel.append(rest)
            elif field == "interval" and rest and not rest.lower().startswith("perform this"):
                interval.append(rest)
            continue
        text = line.strip()
        if not text:
            continue
        bm, sm = BULL.match(line), STEP.match(line)
        if warn is not None and not sm and not lm:
            warn["t"].append(text)
            continue
        if field == "required":
            if bm:
                nodig.append(bm.group(1).strip())
            elif nodig:
                nodig[-1] += " " + text
            continue
        if field == "interval":
            interval.append(bm.group(1).strip() if bm else text)
            continue
        if field in ("procedure", "result"):
            warn = None
            if sm:
                stappen.append({"t": sm.group(2).strip(), "s": []})
            elif bm and stappen:
                stappen[-1]["s"].append(bm.group(1).strip())
            elif stappen and stappen[-1]["s"]:
                stappen[-1]["s"][-1] += " " + text
            elif stappen:
                stappen[-1]["t"] += " " + text
            else:
                doel.append(text)
            continue
        if field == "purpose":
            doel.append(text)

    ivl = clean(" ".join(interval))
    sec["doel"] = clean(" ".join(doel))
    sec["nodig"] = [clean(n) for n in nodig]
    sec["interval"] = next((k for k, pat in INTERVAL if re.search(pat, ivl, re.I)), "")
    sec["intervalTekst"] = ivl
    sec["let"] = [{"n": w["n"], "t": clean(" ".join(w["t"]))} for w in warns if w["t"]]
    sec["stappen"] = [{"t": clean(s["t"]), "s": [clean(x) for x in s["s"]]}
                      for s in stappen if clean(s["t"])]
    return sec


def schemas(secs, procs):
    """Turn the daily/weekly/monthly lists into tickable checklists."""
    # Every task line is the title of a procedure further along in the book.
    op_titel = {t[0]: p["id"] for t, p in
                ((TITELS[s["titel"]], p) for s in secs if s["titel"] in TITELS
                 for p in procs if p["id"] == TITELS[s["titel"]][1] + "-ff")}
    uit = []
    for s in secs:
        soort = SCHEMA.get(s["nr"])
        if soort is None or not s["stappen"]:
            continue
        taken = []
        for stap in s["stappen"]:
            # "Clean the cup stand in the door." -> the procedure "Clean the cup
            # stand"; the book adds a few words of context per checklist.
            tekst = stap["t"].strip()
            naam = next((v for k, v in TITELS.items()
                         if tekst.startswith(k) or tekst.startswith(k.split(" —")[0])), None)
            los = next((v for k, v in LOSSE.items() if tekst.startswith(k)), None)
            taken.append({"t": naam[0] if naam else los or stap["t"],
                          "p": f"{naam[1]}-ff" if naam and f"{naam[1]}-ff" in op_titel.values() else ""})
        uit.append({
            "id": f"unibrewer-{soort[0]}",
            "brewer": "Uni-Brewer",
            "interval": soort[0],
            "titel": soort[1],
            "machines": ["rosa"],
            "let": next((w["t"] for w in s["let"] if w["n"] != "note"), ""),
            "taken": taken,
        })
    return uit


def main():
    base = json.load(open(os.path.join(ROOT, "data", "procedures_base.json")))
    for p in base:
        p.setdefault("taal", "nl")
        # The Avy CoEx XL user manual documents the same procedures as the Lua one.
        if p.get("brewer") == "CoEx XL" and "avy" not in p["machines"]:
            p["machines"] = p["machines"] + ["avy"]

    secs = [structure(s) for s in sections(os.path.join(ROOT, "manuals", ROSA))]
    nieuw, over = [], []
    for s in secs:
        naam = TITELS.get(s["titel"])
        if naam is None:
            over.append(s["titel"])
            continue
        if not s["stappen"] and not s["doel"]:
            over.append(s["titel"] + " (leeg)")
            continue
        nieuw.append({
            "id": f"{naam[1]}-ff",
            "titel": naam[0],
            "brewer": "Uni-Brewer",
            "machines": ["rosa"],
            "interval": s["interval"],
            "intervalTekst": s["intervalTekst"],
            "doel": s["doel"],
            "nodig": s["nodig"],
            "let": s["let"],
            "stappen": s["stappen"],
            "bron": BRON,
            "taal": "en",
        })

    # The Touchless Interface has its own short installation manual; those
    # procedures are written out by hand in data/procedures_touchless.json.
    extra = json.load(open(os.path.join(ROOT, "data", "procedures_touchless.json")))
    alles = base + nieuw + extra

    onderhoud = json.load(open(os.path.join(ROOT, "data", "maintenance_base.json")))
    for sch in onderhoud:
        if sch.get("brewer") == "CoEx XL" and "avy" not in sch["machines"]:
            sch["machines"] = sch["machines"] + ["avy"]
    onderhoud += schemas(secs, nieuw)
    json.dump(onderhoud, open(os.path.join(ROOT, "data", "maintenance.json"), "w"),
              ensure_ascii=False, indent=1)
    json.dump(onderhoud, open(os.path.join(ROOT, "app/src/main/assets/maintenance.json"), "w"),
              ensure_ascii=False, separators=(",", ":"))

    json.dump(alles, open(os.path.join(ROOT, "data", "procedures.json"), "w"),
              ensure_ascii=False, indent=1)
    json.dump(alles, open(os.path.join(ROOT, "app/src/main/assets/procedures.json"), "w"),
              ensure_ascii=False, separators=(",", ":"))
    if over:
        print("  overgeslagen: " + ", ".join(over), file=sys.stderr)
    print(f"{len(alles)} procedures ({len(nieuw)} Uni-Brewer, {len(extra)} Touchless), "
          f"{sum(len(p['stappen']) for p in alles)} stappen, "
          f"{len(onderhoud)} onderhoudsschema's")


if __name__ == "__main__":
    main()
