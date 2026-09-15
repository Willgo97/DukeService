#!/usr/bin/env python3
"""Pull typed records out of the sectioned books.

The chapter numbering is the same in every translation — 8.1.4 is the cleaning
error in all nine languages — so the section number decides what a piece of
text is, and the labels inside it (Message / Bericht / Meldung ...) are learned
from the book itself instead of a dictionary.

Writes build/facts.json
"""
import gzip
import json
import os
import re
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import BUILD, read_json, write_json

DOCS = os.path.join(BUILD, "docs")

# A chapter number only means something within its own kind of book. Chapter 6
# is the service menu in a technical manual and the troubleshooting list in a
# user manual; reading one with the other's map files the text under the wrong
# heading and loses it.
KIND_BY_CHAPTER = {
    "TM": {"1": "safety", "2": "view", "3": "installation", "4": "component",
           "5": "electronics", "6": "menu", "7": "howto", "8": "fault",
           "9": "spec", "10": "appendix"},
    "UM": {"1": "safety", "2": "view", "3": "howto", "4": "howto",
           "5": "howto", "6": "fault", "7": "spec", "8": "appendix"},
    "QSG": {"1": "front", "2": "safety", "3": "installation", "4": "howto",
            "5": "howto", "6": "howto"},
    "IM": {},                      # every chapter is a job on its own
}
# Which sections hold one message, per kind of book.
MESSAGE_SECTION = {"TM": re.compile(r"^8\.\d+\.\d+$"),
                   "UM": re.compile(r"^6\.\d+\.\d+$")}
DEFAULT_KIND = {"IM": "howto", "QSG": "howto", "UM": "howto"}
NOTE = re.compile(r"^\s*(NOTE|NOTA|OPMERKING|LET OP|HINWEIS|BEMÆRK|HUOMAA|MERK|OBS|"
                  r"POZNÁMKA|CAUTION|VOORZICHTIG|ACHTUNG|FORSIGTIG|VARO|FORSIKTIG|"
                  r"VAR FÖRSIKTIG|UPOZORNĚNÍ|WARNING|WAARSCHUWING|WARNUNG|ADVARSEL|"
                  r"VAROITUS|VARNING|VAROVÁNÍ|DANGER|GEVAAR|GEFAHR|FARE|VAARA|FARA|"
                  r"NEBEZPEČÍ|IMPORTANT|BELANGRIJK|WICHTIG|VIGTIGT|TÄRKEÄÄ|VIKTIG|"
                  r"VIKTIGT|DŮLEŽITÉ|TIP|ADVICE)\s*[:!]?\s*(.*)$", re.I)
LEVEL = {"note": "note", "nota": "note", "opmerking": "note", "let op": "note",
         "hinweis": "note", "bemærk": "note", "huomaa": "note", "merk": "note",
         "obs": "note", "poznámka": "note", "tip": "note", "advice": "note",
         "caution": "caution", "voorzichtig": "caution", "achtung": "caution",
         "forsigtig": "caution", "varo": "caution", "forsiktig": "caution",
         "var försiktig": "caution", "upozornění": "caution",
         "warning": "warning", "waarschuwing": "warning", "warnung": "warning",
         "advarsel": "warning", "varoitus": "warning", "varning": "warning",
         "varování": "warning", "danger": "danger", "gevaar": "danger",
         "gefahr": "danger", "fare": "danger", "vaara": "danger", "fara": "danger",
         "nebezpečí": "danger", "important": "important", "belangrijk": "important",
         "wichtig": "important", "vigtigt": "important", "tärkeää": "important",
         "viktig": "important", "viktigt": "important", "důležité": "important"}
STEP = re.compile(r"^\s*(\d{1,2})[.)]\s+(.*)$")
BULLET = re.compile(r"^\s*[•▪◦·‣]\s*(.*)$")
PATH = re.compile(r"^[^\n]{0,80}?\s>\s[^\n]{0,120}$")
RUNNING = re.compile(r"5D[A-Z]{2,}\d|Technical Manual \d|Technische handleiding \d", re.I)
PAGEREF = re.compile(r"\s*\([^()]*?(?:op pagina|on page|auf Seite|à la page|"
                     r"på side|sivulla|på sidan|na stran\w*)\s*\d+\)", re.I)


def clean(text):
    return re.sub(r"\s+", " ", PAGEREF.sub("", text)).strip()


def chapter_of(number):
    return (number or "").split(".")[0]


def learn_labels(sections):
    """The field names of a chapter, learned from how often they repeat.

    In the troubleshooting chapter every entry has the same three short lines
    above its paragraphs. Whatever those lines are in this language, they are
    the labels.
    """
    bodies = [s["text"].split("\n")[1:] for s in sections if s["text"]]
    counts = Counter()
    for body in bodies:
        for line in {l.strip() for l in body}:
            if 0 < len(line.split()) <= 3 and not line.endswith((".", ":", ",", "?")):
                counts[line] += 1
    need = max(2, len(bodies) * 0.5)
    return [l for l, n in counts.items() if n >= need]


def split_by_labels(text, labels):
    """Break a section into the fields its labels announce, in order."""
    out, order = {}, []
    current = None
    for line in text.split("\n")[1:]:
        stripped = line.strip()
        if stripped in labels:
            current = stripped
            out.setdefault(current, [])
            order.append(current)
            continue
        if current is None:
            current = "_intro"
            out.setdefault(current, [])
        out[current].append(line)
    return {k: "\n".join(v).strip() for k, v in out.items()}, order


def pull_notes(text):
    """Separate the warning lines from the running text."""
    notes, rest = [], []
    for line in text.split("\n"):
        m = NOTE.match(line)
        if m and (m.group(2) or line.strip().endswith(":")):
            notes.append(dict(level=LEVEL.get(m.group(1).lower().strip(), "note"),
                              label=m.group(1).strip(), text=clean(m.group(2))))
        else:
            rest.append(line)
    return notes, "\n".join(rest).strip()


def as_list(text):
    """Numbered or bulleted lines as a list, keeping wrapped lines together."""
    items = []
    for line in text.split("\n"):
        m = STEP.match(line) or BULLET.match(line)
        if m:
            items.append(clean(m.groups()[-1]))
        elif items and line.strip():
            items[-1] = clean(items[-1] + " " + line)
    return [i for i in items if i]


def rows_from_lines(lines, tol=4.0):
    """Table rows rebuilt from the x/y of each line: for the spec tables."""
    rows = []
    for ln in sorted(lines, key=lambda l: (l["p"], l["y"], l["x"])):
        if rows and rows[-1][0]["p"] == ln["p"] and abs(ln["y"] - rows[-1][0]["y"]) <= tol:
            rows[-1].append(ln)
        else:
            rows.append([ln])
    return rows


def parse_specs(section):
    """Key/value pairs out of a technical data table."""
    out = []
    for row in rows_from_lines(section["lines"][1:]):
        cells = [clean(l["t"]) for l in sorted(row, key=lambda l: l["x"]) if l["t"].strip()]
        if len(cells) == 1 and out and not out[-1]["value"]:
            out[-1]["value"] = cells[0]
        elif len(cells) >= 2:
            key = cells[0]
            if len(key) <= 2 and len(cells) >= 3:       # leading letter of a drawing
                out.append(dict(mark=key, key=cells[1], value=" ".join(cells[2:])))
            else:
                out.append(dict(key=key, value=" ".join(cells[1:])))
        elif cells:
            out.append(dict(key=cells[0], value=""))
    return [r for r in out if r.get("key")
            and not RUNNING.search(r["key"] + " " + r.get("value", ""))]


def parse_doc(meta, data):
    facts = []
    sections = data["sections"]

    doctype = meta.get("doctype")
    chapters = KIND_BY_CHAPTER.get(doctype, {})
    leaf = MESSAGE_SECTION.get(doctype)
    fault_sections = [s for s in sections
                      if leaf and s["number"] and leaf.match(s["number"])]
    fault_labels = set(learn_labels(fault_sections))

    for s in sections:
        number = s["number"] or ""
        kind = chapters.get(chapter_of(number))
        if doctype == "SMI":
            kind = "maintenance_step"
        elif doctype == "SPM":
            kind = "drawing"
        elif doctype == "BR":
            kind = "brochure"
        if kind == "fault" and not (leaf and leaf.match(number)):
            kind = "other"                      # chapter and paragraph headings
        if not kind and number:
            kind = DEFAULT_KIND.get(doctype, "other")
        if not kind:
            kind = "front"
        rec = dict(id=s["id"], doc_id=meta["doc_id"], kind=kind, number=number,
                   title=s["title"], level=s["level"], page=s["page"],
                   page_to=s["page_to"], lang=meta.get("lang", "EN"),
                   text=s["text"], images=s["images"])

        if kind == "fault":
            fields, order = split_by_labels(s["text"], fault_labels)
            notes, _ = pull_notes(s["text"])
            values = [fields[k] for k in order if fields.get(k)]
            rec["message"] = clean(re.sub(r"^[^:]{0,20}:\s*", "", s["title"]))
            if values:
                rec["display"] = clean(values[0])
            if len(values) > 1:
                rec["cause"] = clean(values[1])
            if len(values) > 2:
                _n, solution = pull_notes(values[2])
                steps = as_list(solution)
                rec["solution"] = steps if steps else (
                    [clean(solution)] if solution.strip() else [])
            rec["notes"] = notes
            rec["labels"] = order
        elif kind == "menu":
            body = s["text"].split("\n")[1:]
            path = next((clean(l) for l in body[:3] if PATH.match(l.strip())), "")
            rec["path"] = path
            notes, rest = pull_notes("\n".join(body))
            rec["notes"] = notes
            rec["steps"] = as_list(rest)
            rec["body"] = clean(re.sub(r"\n+", " ", rest))
        elif kind in ("howto", "appendix", "installation"):
            body = "\n".join(s["text"].split("\n")[1:])
            notes, rest = pull_notes(body)
            rec["notes"] = notes
            rec["steps"] = as_list(rest)
            rec["body"] = clean(re.sub(r"\n+", " ", rest))
        elif kind == "spec":
            rec["rows"] = parse_specs(s)
        elif kind == "safety":
            notes, rest = pull_notes("\n".join(s["text"].split("\n")[1:]))
            rec["notes"] = notes
            rec["points"] = as_list(rest)
        elif kind in ("view",):
            rec["callouts"] = as_list("\n".join(s["text"].split("\n")[1:]))
        elif kind in ("component", "electronics"):
            notes, rest = pull_notes("\n".join(s["text"].split("\n")[1:]))
            rec["notes"] = notes
            rec["body"] = rest.strip()
        elif kind == "maintenance_step":
            notes, rest = pull_notes(s["text"])
            rec["notes"] = notes
            rec["points"] = as_list(rest) or ([clean(rest)] if rest.strip() else [])
            rec["group"] = s["title"]
            rec["step"] = number or None
        facts.append(rec)
    return facts


def main():
    corpus = read_json(os.path.join(BUILD, "corpus.json"))
    docs = [d for d in corpus["documents"] if not d.get("duplicate_of")]
    out = []
    for meta in docs:
        path = os.path.join(DOCS, meta["doc_id"] + ".json.gz")
        if not os.path.exists(path):
            continue
        data = json.load(gzip.open(path, "rt", encoding="utf-8"))
        out += parse_doc(meta, data)
    kinds = Counter(r["kind"] for r in out)
    write_json(os.path.join(BUILD, "facts.json"), dict(count=len(out), records=out))
    print(f"{len(out):,} records")
    for k, n in kinds.most_common():
        print(f"  {k:18} {n:6}")


if __name__ == "__main__":
    main()
