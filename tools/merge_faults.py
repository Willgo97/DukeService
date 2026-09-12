#!/usr/bin/env python3
"""Fold the troubleshooting from every manual into faults.json.

The Dutch Avy manual stays the base: its wording is the one an engineer reads on
a Dutch machine. The other books mostly confirm messages that are already there
and widen which machines they apply to; what they add are the ones specific to a
brewer the Dutch book does not cover — CoEx XL, Uni-Brewer, Instant.
"""
import json, os, re, sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATS = [
    ("Brewer", r"brewer|zetgroep|piston|zuiger"),
    ("Molen", r"grinder|molen|maal"),
    ("Mixer", r"mixer|meng"),
    ("Water", r"water|boiler lek|inlet|inlaat|filter"),
    ("Temperatuur", r"temperat|heating|opwarm|clixon|boiler"),
    ("Afval", r"waste|afval|drip|lekbak"),
    ("Beker", r"cup|beker|jug|kan"),
    ("Ingrediënten", r"ingredi|canister|container|bean|bonen"),
    ("Betaling", r"coin|geld|payment|betaal"),
    ("Besturing", r"configuration|configuratie|interface|communicat|recipe|recept|software"),
    ("Reiniging", r"clean|reinig|rinse|spoel|tablet"),
]


def norm(s):
    return re.sub(r"[^a-z0-9 ]+", " ", s.lower()).strip()


def category(text):
    low = text.lower()
    for naam, patroon in CATS:
        if re.search(patroon, low):
            return naam
    return "Bediening"


def main():
    faults = json.load(open(os.path.join(ROOT, "data", "faults.json")))
    raw = json.load(open(os.path.join(ROOT, "data", "faults_raw.json")))

    # what we already know, in both languages
    by_en = {norm(f["melding"]): f for f in faults}
    by_nl = {norm(f["nl"]): f for f in faults if f["nl"]}

    nieuw, verbreed, gezien = {}, 0, set()
    for e in raw:
        key_en = norm(e["msg"])
        bekend = by_en.get(key_en) or by_nl.get(key_en)
        if bekend is not None:
            for m in e["machines"]:
                if m not in bekend["machines"]:
                    bekend["machines"].append(m)
                    verbreed += 1
            bekend.setdefault("brewers", [])
            if e["brewer"] not in bekend["brewers"]:
                bekend["brewers"].append(e["brewer"])
            gezien.add(id(bekend))
            continue
        # not seen before: keep the fullest description of it
        slot = nieuw.setdefault(key_en, {
            "melding": e["msg"], "nl": e["msg"] if e["taal"] == "nl" else "",
            "machines": [], "brewers": [], "cat": category(e["msg"] + " " + e["cause"]),
            "oorzaak": "", "oplossing": [], "monteur": "", "proc": [],
            "zelf": True, "opmerking": "", "bron": e["bron"],
        })
        if e["taal"] == "nl" and not slot["nl"]:
            slot["nl"] = e["msg"]
        if len(e["cause"]) > len(slot["oorzaak"]):
            slot["oorzaak"] = e["cause"]
        if len(e["fix"]) > len(slot["oplossing"]):
            slot["oplossing"] = e["fix"]
            slot["bron"] = e["bron"]
        if e["note"] and len(e["note"]) > len(slot["opmerking"]):
            slot["opmerking"] = e["note"]
        for m in e["machines"]:
            if m not in slot["machines"]:
                slot["machines"].append(m)
        if e["brewer"] not in slot["brewers"]:
            slot["brewers"].append(e["brewer"])

    # a message only a service engineer can clear says so in its own steps
    for f in nieuw.values():
        tekst = " ".join(f["oplossing"]).lower()
        alleen_monteur = not f["oplossing"] or all(
            re.search(r"contact|neem contact|consult|fabrikant|manufacturer|service engineer", s.lower())
            for s in f["oplossing"])
        if alleen_monteur:
            f["zelf"] = False
            f["monteur"] = "Dit is een servicemelding."
        elif re.search(r"contact|fabrikant|manufacturer", tekst):
            f["monteur"] = "Blijft het probleem, dan is het een servicemelding."

    faults.extend(nieuw.values())
    for f in faults:
        f.setdefault("brewers", [])
        if not f["nl"]:
            f["nl"] = f["melding"]

    json.dump(faults, open(os.path.join(ROOT, "data", "faults.json"), "w"),
              ensure_ascii=False, indent=1)
    json.dump(faults, open(os.path.join(ROOT, "app/src/main/assets/faults.json"), "w"),
              ensure_ascii=False, separators=(",", ":"))
    groepen = len({norm(f["melding"]) for f in faults})
    print(f"{len(nieuw)} nieuwe meldingen, {verbreed} keer een machine toegevoegd")
    print(f"totaal {len(faults)} regels, {groepen} unieke schermmeldingen")
    per = defaultdict(int)
    for f in faults:
        for m in f["machines"]:
            per[m] += 1
    print("per machine:", dict(sorted(per.items(), key=lambda kv: -kv[1])))


if __name__ == "__main__":
    main()
