#!/usr/bin/env python3
"""Read the troubleshooting chapter out of every manual.

The books are the same document in two languages, so one parser handles both:
"8.1.4 Bericht: …" with Oorzaak/Oplossing, and "8.1.4 Message: …" with
Cause/Solution. Each manual covers a particular machine and brewer, which is how
a message gets tagged.

Writes data/faults_raw.json for the merge step.
"""
import json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# manual -> (machine ids, brewer, short source label)
BOOKS = {
    "tm_avy_coex_medium_cec_nl_v10_5dtcet10m.pdf": (["avy"], "CoEx", "TM Avy CoEx Medium NL V1.0 (5DTCET10M)", "nl"),
    "tm_avy_coex_small_cnd_en_v11_5dtcnt20m.pdf": (["avy"], "CoEx", "TM Avy CoEx Small EN V1.1 (5DTCNT20M)", "en"),
    "TM_Avy_CoExXL_Medium_XEA_EN_V1.1_5DTXET20M.pdf": (["avy"], "CoEx XL", "TM Avy CoEx XL Medium EN V1.1 (5DTXET20M)", "en"),
    "tm_lua_instant_small_inb_nl_v10_5dtins10m.pdf": (["lua"], "Instant", "TM Lua Instant Small NL V1.0 (5DTINS10M)", "nl"),
    "tm_nio_coexxl_xka_en_v111_5dtxka20m.pdf": (["nio"], "CoEx XL", "TM Nio CoEx XL EN V1.1.1 (5DTXKA20M)", "en"),
    "TM_Rosa_Filterfresh_Small_FND_EN_V1.1_5DTFNV20M.pdf": (["rosa"], "Uni-Brewer", "TM Rosa Filterfresh EN V1.1 (5DTFNV20M)", "en"),
    "User_Manual_Avy_CoExXL_Medium_XEA_EN_V2.3_5DUXET20M_0.pdf": (["avy"], "CoEx XL", "UM Avy CoEx XL EN V2.3 (5DUXET20M)", "en"),
    "User_Manual_Rosa_Filterfresh_Small_FND_EN_V2.3_5DUFNV20M.pdf": (["rosa"], "Uni-Brewer", "UM Rosa Filterfresh EN V2.3 (5DUFNV20M)", "en"),
    "Luamanual.pdf": (["lua"], "CoEx XL", "UM Lua CoEx XL EN V2.3 (5DUXES20I)", "en"),
    "Virtumanual.pdf": (["virtu"], "CoEx", "UM Virtu CoEx EN V2.3 (5DUCEK20I)", "en"),
}

HEAD = re.compile(r'^\s*\d+\.\d+\.(\d+)\s+(?:Bericht|Message):\s+(.+?)\s*$')
LABEL = re.compile(r'^(\s+)(Bericht|Message|Oorzaak|Cause|Oplossing|Solution|Resultaat|Result)(?:\s{2,}(.*))?\s*$')
# The running footer names the book; it differs in case and wording per manual.
FOOT = re.compile(
    r'^.*(?:technische handleiding|technical manual|user manual|gebruikershandleiding|'
    r'handleiding|manual)\s*5D\w+.*$'
    r'|^\s*\d{1,3}\s*$'
    r'|^\s*\d{1,3}\s+\S.*(?:cabinet|kast|brewer|series|serie).*$'
    r'|^.*(?:cabinet|kast)\s+\d+[:.]\d+.*(?:brewer|brewers).*$',
    re.I)
BULL = re.compile(r'^\s*[•▪]\s*(.*)$')
STEP = re.compile(r'^\s*(\d{1,2})[.)]\s+(.*)$')
PAGEREF = re.compile(r'\s*\((?:zie|see)?\s*[^()]*?(?:op pagina|on page)\s*\d+\)')
FIELD = {"bericht": "msg", "message": "msg", "oorzaak": "cause", "cause": "cause",
         "oplossing": "fix", "solution": "fix", "resultaat": "fix", "result": "fix"}


def clean(t):
    t = PAGEREF.sub('', re.sub(r'\s+', ' ', t)).strip()
    t = re.sub(r'\s+([.,;])', r'\1', t)
    return re.sub(r'^[•▪\d]+[.)]?\s*', '', t).strip()


def parse(path):
    raw = subprocess.run(["pdftotext", "-layout", path, "-"],
                         capture_output=True, text=True).stdout.replace("\x0c", "")
    lines = [l for l in raw.split("\n") if ". . ." not in l]
    out, i = [], 0
    while i < len(lines):
        m = HEAD.match(lines[i])
        if not m:
            i += 1
            continue
        e = {"titel": m.group(2).strip(), "msg": "", "cause": "", "fix": [], "note": ""}
        i += 1
        field = None
        while i < len(lines):
            l = lines[i]
            if HEAD.match(l) or re.match(r'^\s*(8\.[2-9]|9(\.\d+)?)\s+[A-Z]', l):
                break
            if FOOT.match(l) or not l.strip():
                i += 1
                continue
            lm = LABEL.match(l)
            if lm:
                field = FIELD[lm.group(2).lower()]
                rest = (lm.group(3) or "").strip()
                if field == "fix":
                    low = rest.lower()
                    if rest and not low.startswith(("voer de volgende", "perform the following")):
                        e["fix"].append(rest)
                elif rest:
                    e[field] += " " + rest
                i += 1
                continue
            text = l.strip()
            if text.upper().startswith(("OPMERKING", "NOTE")):
                e["note"] += " " + re.sub(r'^(OPMERKING|NOTE):?\s*', '', text, flags=re.I)
                field = "note"
                i += 1
                continue
            bm, sm = BULL.match(l), STEP.match(l)
            if field == "fix" and (bm or sm):
                e["fix"].append((bm or sm).group(1 if bm else 2).strip())
            elif field == "fix" and e["fix"]:
                e["fix"][-1] += " " + text
            elif field == "fix":
                low = text.lower()
                if not low.startswith(("voer de volgende", "perform the following")):
                    e["fix"].append(text)
            elif field in ("cause", "msg", "note"):
                e[field] += " " + text
            i += 1
        e["msg"] = clean(e["msg"]).rstrip('.') or e["titel"]
        e["cause"] = clean(e["cause"])
        e["note"] = clean(e["note"])
        e["fix"] = [clean(x) for x in e["fix"] if clean(x)]
        out.append(e)
    return out


def main():
    alles = []
    for name, (machines, brewer, bron, taal) in BOOKS.items():
        path = os.path.join(ROOT, "manuals", name)
        if not os.path.exists(path):
            print(f"  ontbreekt: {name}", file=sys.stderr)
            continue
        got = parse(path)
        for e in got:
            e.update(machines=machines, brewer=brewer, bron=bron, taal=taal)
        alles.extend(got)
        print(f"  {len(got):3} meldingen  {brewer:10} {name[:46]}", file=sys.stderr)
    json.dump(alles, open(os.path.join(ROOT, "data", "faults_raw.json"), "w"),
              ensure_ascii=False, indent=1)
    print(f"{len(alles)} meldingen uit {len(BOOKS)} handleidingen")


if __name__ == "__main__":
    main()
