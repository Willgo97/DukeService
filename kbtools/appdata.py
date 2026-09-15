#!/usr/bin/env python3
"""Make the app's assets out of the knowledge base.

What was written by hand stays: the Dutch fault descriptions, the machine
texts, the maintenance schedules and the procedures in data/*.json are kept and
only added to. Everything that was generated is generated again, now from all
207 books instead of ten.

Writes app/src/main/assets/
"""
import glob
import io
import json
import os
import re
import shutil
import sys
from collections import defaultdict

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import BUILD, KB, MODEL_CODES, ROOT, read_json, write_json
from text import clean, prose

ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
DATA = os.path.join(ROOT, "data")
LANG = ["NL", "EN", "DE"]
# The manuals exist in nine languages, so the app does too. The key is the
# Android locale, the value the language code the knowledge base uses.
LOCALES = {"nl": "NL", "en": "EN", "de": "DE", "fr": "FRCA", "sv": "SV",
           "no": "NO", "da": "DA", "fi": "FI", "cs": "CZ"}
# The other way round: the knowledge base says FRCA and CZ, Android says fr and cs.
LOCALE_OF = {code.lower(): locale for locale, code in LOCALES.items()}
# How wide a picture needs to be on a phone.
WIDTH = {"tek": 1400, "img": 900, "stap": 700}
QUALITY = {"tek": 78, "img": 74, "stap": 74}


def first(mapping, langs=LANG):
    """The wanted language if the manual has it, otherwise English, otherwise Dutch."""
    if not isinstance(mapping, dict):
        return mapping, "nl"
    for lang in langs:
        if mapping.get(lang):
            return mapping[lang], LOCALE_OF.get(lang.lower(), lang.lower())
    for lang, value in mapping.items():
        if value:
            return value, LOCALE_OF.get(lang.lower(), lang.lower())
    return None, "nl"


def heading(topic, langs, fallback):
    """The section heading in the reader's language.

    Topics carry one title, in whichever language the book was first read in;
    a Finnish engineer should not be looking at a Dutch heading. The body text
    per language starts with that language's own heading, so it is taken from
    there and the section number in front of it dropped.
    """
    text, _ = first(topic.get("text") or {}, langs)
    if not text:
        return fallback
    line = clean(text.split("\n", 1)[0])
    line = re.sub(r"^\d+(\.\d+)*\.?\s*", "", line).strip()
    # A section without its own heading starts straight into a sentence.
    if not line or len(line) > 80 or line.endswith("."):
        return fallback
    return line


def brand_of(product_id):
    return (product_id or "").split(".")[0]


def code_of(product_id):
    return (product_id or "").split(".")[-1].upper()


def brands(applies_to):
    return sorted({brand_of(p) for p in applies_to if p})


def codes(applies_to):
    return sorted({code_of(p) for p in applies_to if "." in (p or "")})


def trim(image, threshold=244, pad=6):
    """Cut the empty page around an illustration.

    A figure is rendered from the area it occupies on the page, which often
    leaves half a column of white beside it. On a phone that white is the
    difference between a readable picture and a stamp.
    """
    mask = image.convert("L").point(lambda v: 0 if v > threshold else 255)
    box = mask.getbbox()
    if not box:
        return image
    width, height = image.size
    box = (max(0, box[0] - pad), max(0, box[1] - pad),
           min(width, box[2] + pad), min(height, box[3] + pad))
    if (box[2] - box[0]) * (box[3] - box[1]) > width * height * 0.97:
        return image
    return image.crop(box)


class Pictures:
    """Copies a picture into the assets at the size the app shows it."""

    def __init__(self):
        self.done = {}
        self.bytes = 0

    def add(self, hash_or_file, bucket):
        digest = os.path.basename(hash_or_file).removesuffix(".webp")
        key = (digest, bucket)
        if key in self.done:
            return self.done[key]
        src = os.path.join(KB, "media", digest + ".webp")
        if not os.path.exists(src):
            return None
        out_dir = os.path.join(ASSETS, bucket)
        os.makedirs(out_dir, exist_ok=True)
        dst = os.path.join(out_dir, digest + ".webp")
        image = trim(Image.open(src))
        target = WIDTH[bucket]
        if max(image.size) > target:
            scale = target / max(image.size)
            image = image.resize((max(1, int(image.width * scale)),
                                  max(1, int(image.height * scale))), Image.LANCZOS)
        buf = io.BytesIO()
        image.save(buf, "WEBP", quality=QUALITY[bucket], method=5)
        with open(dst, "wb") as fh:
            fh.write(buf.getvalue())
        self.bytes += len(buf.getvalue())
        path = f"{bucket}/{digest}.webp"
        self.done[key] = path
        return path


# --- sizes and weights ------------------------------------------------------
# The figures come out of the technical manuals themselves rather than out of a
# brochure: a brochure gives a range over every build, the book gives the size
# of exactly this model.
DIMENSION = [
    ("Height", r"^(hoogte|height)$"),
    ("Height with bean canister",
     r"^(hoogte|height)[\s(]*(met|with)\s+((grote|extended|verlengde)\s+)?(bonencontainer|bean canister)\)?$"),
    ("Width", r"^(breedte|width)$"),
    ("Depth", r"^(diepte|depth)$"),
    ("Weight", r"^(gewicht \(leeg\)|weight \(empty\))$"),
]
METRIC = re.compile(r"\b(mm|cm|kg)\b", re.I)


def dimension_tables(kb_specs):
    """The size rows of every specification table that counts in mm and kg."""
    out = []
    for topic in kb_specs:
        if not topic["title"].lower().startswith(("technische spec", "technical spec")):
            continue
        rows = (topic["rows"].get("NL") or topic["rows"].get("EN")
                or next(iter(topic["rows"].values()), []))
        found = {}
        for row in rows:
            key = clean(row.get("key") or "").strip().lower()
            value = re.sub(r"\bKg\b", "kg", clean(row.get("value") or "").strip())
            for label, pattern in DIMENSION:
                if re.fullmatch(pattern, key):
                    found.setdefault(label, value)
        if found and any(METRIC.search(v) for v in found.values()):
            out.append((set(topic["applies_to"]), found, (topic["sources"] or [""])[0]))
    return out


def machine_dimensions(brand, model_codes, tables):
    """One machine's size table: the small cabinet beside the medium one."""
    own = [t for t in tables if t[2].split("-")[1:2] == [brand]]
    per_label = {}
    sources = []
    for code in model_codes:
        size = MODEL_CODES.get(code, ("", ""))[1] or ""
        found = next((t for t in own if f"{brand}.{code.lower()}" in t[0]), None)
        found = found or next((t for t in tables if f"{brand}.{code.lower()}" in t[0]), None)
        if not found:
            continue
        sources.append(found[2])
        for label, value in found[1].items():
            per_label.setdefault(label, {}).setdefault(size, set()).add(value)

    def cell(values):
        if not values:
            return ""
        if len(values) == 1:
            return next(iter(values))
        # Two builds that are not equally deep: give the range.
        order = sorted(values, key=lambda v: float(re.sub(r"[^\d.,]", "", v).replace(",", ".") or 0))
        return f"{order[0]} \u2013 {order[-1]}"

    rows = []
    for label, _ in DIMENSION:
        sizes = per_label.get(label)
        if not sizes:
            continue
        rows.append(dict(label=label, small=cell(sizes.get("Small", set())),
                         medium=cell(sizes.get("Medium", set()))))
    return rows, sorted(set(sources))


def build_machines(products, pictures, locale="nl", kb_specs=()):
    """The eleven machine lines, with the builds each one is sold in.

    The words around the machine — what it is, which service menu it runs, what
    the specification rows are called — are written by hand in Dutch and
    translated in data/machines-i18n.json, because a machine screen in a
    language you do not read is no use to the engineer standing in front of it.
    """
    words = read_json(os.path.join(DATA, "machines-i18n.json"), {})
    per_machine = words.get("machines", {})
    notes = words.get("serviceMenuNote", {})
    labels = words.get("specLabel", {})

    def say(table, text):
        """The translation of a hand-written line, or the Dutch it was written in."""
        return (table.get(text) or {}).get(locale, text) if locale != "nl" else text

    tables = dimension_tables(kb_specs)
    base = read_json(os.path.join(DATA, "machines.json"), [])
    by_brand = defaultdict(list)
    for p in products:
        by_brand[p["brand"]].append(p)
    out = []
    for machine in base:
        brand = machine["id"]
        for field in ("summary", "description"):
            said = (per_machine.get(brand, {}).get(field) or {}).get(locale)
            if said:
                machine[field] = said
        if machine.get("serviceMenuNote"):
            machine["serviceMenuNote"] = say(notes, machine["serviceMenuNote"])
        for row in machine.get("specs", []):
            # Every language, Dutch included: the label itself is written in
            # English in the code.
            row["label"] = (labels.get(row["label"]) or {}).get(locale, row["label"])
        variants = sorted(by_brand.get(brand, []), key=lambda p: p["model_code"])
        machine["variants"] = [dict(
            code=v["model_code"],
            cabinet=v["size"] or "",
            brewer=v["brewer"] or "",
            doc=", ".join(v["series"]) or (v.get("series_code") or ""),
        ) for v in variants]
        if variants:
            machine["typeCode"] = " / ".join(v["model_code"] for v in variants)
            # The sizes come from the books, not from what was written by hand
            # in machines.json.
            rows, sources = machine_dimensions(
                brand, [v["model_code"] for v in variants], tables)
            machine["specs"] = rows or machine.get("specs", [])
            machine["specsSource"] = ", ".join(sources[:2])
            sizes = sorted({v["size"] for v in variants if v["size"]})
            machine["cabinet"] = ", ".join(sizes)
            brewers = sorted({v["brewer"] for v in variants if v["brewer"]})
            machine["brewer"] = ", ".join(brewers)
            # "1000, 2000, 18000" reads as a range; sorted as text it comes out
            # "1000, 18000, 19000, 2000" and looks like a mistake.
            series = {s for v in variants for s in v["series"]}
            if series:
                def first_number(text):
                    digits = re.findall(r"\d+", text)
                    return int(digits[0]) if digits else 0
                machine["series"] = ", ".join(
                    s.replace(" series", "") for s in sorted(series, key=first_number))
        out.append(machine)
    known = {m["id"] for m in out}
    for brand, variants in sorted(by_brand.items()):
        if brand in known:
            continue
        out.append(dict(id=brand, name=variants[0]["brand_name"], active=True,
                        variants=[dict(code=v["model_code"], cabinet=v["size"] or "",
                                           brewer=v["brewer"] or "",
                                           doc=", ".join(v["series"]))
                                      for v in variants]))
    return out


def build_faults(kb_faults, langs=LANG):
    """Screen messages in one language, with the hand-written notes kept.

    The Dutch texts in data/faults.json are written by hand and stay the Dutch
    version. For the other eight languages the manufacturer's own translation
    of the same message is used, with the curation — which category it is,
    whether the operator can solve it, which procedure belongs to it — carried
    over, because that part is not language-bound.
    """
    dutch_first = bool(langs) and langs[0] == "NL"
    out = [dict(f, codes=f.get("codes", []),
                category=CATEGORY_WAS.get(f.get("category"), f.get("category") or "Other"))
           for f in read_json(os.path.join(DATA, "faults.json"), [])]

    def key_of(text):
        return re.sub(r"\W+", "", (text or "").lower())

    by_key = {key_of(f["message"]): f for f in out}

    for topic in sorted(kb_faults, key=lambda t: t["number"] or ""):
        english, _ = first(topic["message"], ["EN"])
        local, lang = first(topic["message"], langs)
        message = clean(english or local or topic["title"])
        cause, _ = first(topic["cause"], langs)
        solution, _ = first(topic["solution"], langs) or ([], "")
        notes, _ = first(topic["notes"], langs) or ([], "")
        cause = clean(cause or "")
        solution = [clean(s) for s in (solution or [])]
        note = clean(" ".join(n["text"] for n in (notes or []))[:400])
        local = clean(local or "")
        machines = brands(topic["applies_to"])
        model_codes = codes(topic["applies_to"])

        row = by_key.get(key_of(message))
        if row is not None:
            row["codes"] = sorted(set(row["codes"]) | set(model_codes))
            row["machines"] = sorted(set(row["machines"]) | set(machines))
            if not dutch_first:
                # The manufacturer translated this message themselves.
                row["dutch"] = local or message
                if cause:
                    row["cause"] = cause
                if solution:
                    row["solution"] = solution
                row["note"] = note
                row["engineerNote"] = ""
                row["language"] = lang
            continue

        by_key[key_of(message)] = row = dict(
            message=message, dutch=local or message, machines=machines,
            codes=model_codes, brewers=[], category=category(message),
            cause=cause or "", solution=solution or [], note=note,
            selfService=False,
            source=(topic["number"] or "") + " " + (topic["sources"] or [""])[0],
            language=lang)
        out.append(row)
    return out


# The name is a key, not a label: the app looks up the word the reader sees.
# The patterns match both languages a message can be written in.
CATEGORY = [
    ("Brewer", r"brewer|brew|zetgroep"), ("Water", r"water|boiler|kalk|filter|pomp|druk"),
    ("Waste", r"waste|afval|drip|lekbak"), ("Cleaning", r"clean|reinig|spoel|rinse"),
    ("Temperature", r"temp|heat|verwarm"), ("Grinder", r"grind|molen|bean|bonen"),
    ("Mixer", r"mixer|mengen"), ("Cups", r"cup|beker"),
    ("Payment", r"coin|payment|betaal|munt"), ("Controls", r"communicat|board|software|config|usb"),
    ("Ingredients", r"ingredient|canister|container"), ("Operation", r"door|deur|key|sleutel|screen|scherm"),
]

# What the hand-written faults in data/faults.json still call them.
CATEGORY_WAS = {
    "Afval": "Waste", "Reiniging": "Cleaning", "Temperatuur": "Temperature",
    "Molen": "Grinder", "Beker": "Cups", "Betaling": "Payment",
    "Besturing": "Controls", "Ingrediënten": "Ingredients",
    "Bediening": "Operation", "Overig": "Other",
}


def category(message):
    low = message.lower()
    for name, pattern in CATEGORY:
        if re.search(pattern, low):
            return name
    return "Other"


# Which subject a section belongs to, from where it sits in the manual:
# 4.1 is the water system, 4.2 the brewer, 5 the electronics.
GROUPS = [("5", "electronics"), ("4.1", "water"), ("4.2", "brewer"), ("4.3", "grinder"),
          ("4.4", "mixer"), ("4.5", "ingredients"), ("4.6", "ingredients")]


def component_group(topic):
    if topic["kind"] == "electronics":
        return "electronics"
    title = (topic.get("title") or "").lower()
    if "melk" in title or "milk" in title:
        return "milk"
    number = topic.get("number") or ""
    for prefix, name in GROUPS:
        if number == prefix or number.startswith(prefix + "."):
            return name
    return "other"


SECTION_NUMBER = re.compile(r"^\d+(\.\d+)*\.?\s*")


def meat(body, title=""):
    """The body without the heading the extractor left in front of it."""
    text = SECTION_NUMBER.sub("", (body or "").strip())
    if title and text.lower().startswith(title.lower()):
        text = text[len(title):].strip()
    return text


def body_of(topic, langs, minimum=25):
    """The body in the reader's language, or the nearest one that has words.

    A book that was never translated leaves the section standing with nothing
    but its own heading in that language; then the English one is worth more
    than an empty screen.
    """
    title = topic.get("title") or ""
    bodies = topic.get("body") or {}
    order = list(langs) + [l for l in sorted(bodies) if l not in langs]
    for lang in order:
        text = meat(bodies.get(lang), title)
        if len(text) >= minimum:
            return text, LOCALE_OF.get(lang.lower(), lang.lower())
    return "", "nl"


def has_body(topic, minimum=25):
    """Whether any language has something to read.

    Decided over all languages at once, not per language: the same sections
    have to be in the app whatever it is set to, or a screen that exists in
    Dutch would be missing in Finnish — and a link to it would dead-end. A
    section whose whole body is its own heading says nothing.
    """
    title = topic.get("title") or ""
    return any(len(meat(body, title)) >= minimum
               for body in (topic.get("body") or {}).values())


def build_components(kb_components, pictures, langs=LANG):
    out = []
    for topic in kb_components:
        # The body is the section minus its own heading. A chapter that only
        # introduces its subsections has none, and would otherwise show up as a
        # component whose whole text is its own title.
        if not has_body(topic):
            continue
        text, lang = body_of(topic, langs)
        images = [pictures.add(i["file"], "img") for i in topic["images"]]
        out.append(dict(
            id=topic["id"], number=topic["number"] or "",
            title=heading(topic, langs, topic["title"]),
            text=prose(text), group=component_group(topic),
            page=0, images=[i for i in images if i],
            machines=brands(topic["applies_to"]), codes=codes(topic["applies_to"]),
            brewer=", ".join(sorted({MODEL_CODES.get(c, ("", ""))[0]
                                     for c in codes(topic["applies_to"])} - {""})),
            source=(topic["sources"] or [""])[0], language=lang,
            key=topic["title"], size=sum(len(v or "") for v in topic["body"].values())))
    return label_duplicates(out)


def variant_label(codes):
    """"Instant Small" for the machines a set of model codes stands for."""
    pairs = {MODEL_CODES[c] for c in codes if c in MODEL_CODES}
    if not pairs or len(pairs) > 2:
        return ""
    return " / ".join(sorted(f"{brewer} {size}".strip() for brewer, size in pairs))


def label_duplicates(rows, extra=None):
    """Tell apart records that would read as the same row in a list.

    The same subject is written once per brewer, so a machine sold with four of
    them has four "Mixer" sections. They differ in their text, not in their
    title, which is no help when you are looking at a list of them.
    """
    def key_of(row):
        # The title is translated, so it cannot decide what counts as the same
        # row: that has to come out the same in all nine languages.
        return (row.get("key") or row["title"], tuple(row["machines"]),
                row.get(extra, "") if extra else "")

    groups = defaultdict(list)
    for row in rows:
        groups[key_of(row)].append(row)
    for group in groups.values():
        if len(group) < 2:
            continue
        for row in group:
            label = variant_label(row.get("codes") or [])
            if not label or label in row["title"]:
                continue
            # The manual's own title may already carry a bracket
            # ("2-way outlet valve (Open boiler)"); a second one reads badly.
            row["title"] = (f"{row['title']} — {label}" if row["title"].endswith(")")
                            else f"{row['title']} ({label})")
            # What was told apart here has to stay apart below, in every
            # language: the key carries the same distinction as the title.
            row["key"] = f"{row.get('key') or row['title']} ({label})"

    # Whatever still collides says the same thing twice: keep the fullest one.
    # "Fullest" is measured over all languages at once — measured in the one
    # being built, a different one of the two would win per language and the
    # same list would hold different sections in Czech than in Dutch.
    def size_of(row):
        return row.get("size", len(row.get("text", "")))

    best = {}
    for row in rows:
        current = best.get(key_of(row))
        if current is None or size_of(row) > size_of(current):
            best[key_of(row)] = row
    kept = [row for row in rows if best[key_of(row)] is row]
    for row in kept:
        row.pop("key", None)
        row.pop("size", None)
    return spell_out(kept)


def spell_out(rows):
    """Say which machine a row is about when its name alone does not.

    Two sections can be written for different builds and still be printed under
    the same heading. In a list they then read as the same thing three times,
    and the only way to tell them apart is to open them.
    """
    groups = defaultdict(list)
    for row in rows:
        groups[row["title"]].append(row)
    def brewer_label(row):
        return variant_label(row.get("codes") or [])

    def code_label(row):
        return ", ".join((row.get("codes") or [])[:3])

    def number_label(row):
        return row.get("number") or ""

    def machine_label(row):
        return ", ".join(m.capitalize() for m in (row.get("machines") or []))

    for group in groups.values():
        if len(group) < 2:
            continue
        # Whichever of the three tells them apart: which brewer it is written
        # for, else the model code, else the machine it belongs to.
        for naming in (brewer_label, code_label, number_label, machine_label):
            labels = [naming(row) for row in group]
            if all(labels) and len(set(labels)) == len(labels):
                break
        for row, label in zip(group, labels):
            if not label or label in row["title"]:
                continue
            row["title"] = (f"{row['title']} — {label}" if row["title"].endswith(")")
                            else f"{row['title']} ({label})")
    return rows


def build_menu(kb_menu, pictures, langs=LANG):
    out = []
    for topic in kb_menu:
        # A chapter that only announces the settings underneath it has nothing
        # of its own to read: its whole text is its own heading.
        if not (has_body(topic, 12) or topic["images"]
                or any(topic["steps"].values()) or any(topic["notes"].values())):
            continue
        body, lang = body_of(topic, langs, 12)
        steps, _ = first(topic["steps"], langs)
        notes, _ = first(topic["notes"], langs)
        text = body or ""
        if not text and not steps:
            whole, lang = first(topic.get("text", {}), langs)
            text = meat(whole, topic["title"])
        images = [pictures.add(i["file"], "img") for i in topic["images"]]
        out.append(dict(
            id=topic["id"], number=topic["number"] or "",
            title=heading(topic, langs, topic["title"]), key=topic["title"],
            size=sum(len(v or "") for v in topic["body"].values()),
            text=prose(text), path=topic.get("path") or "", level="",
            images=[i for i in images if i], steps=[clean(s) for s in (steps or [])],
            notes=[clean(n["text"]) for n in (notes or [])],
            machines=brands(topic["applies_to"]), codes=codes(topic["applies_to"]),
            source=(topic["sources"] or [""])[0], language=lang))
    return label_duplicates(out, extra="path")


def build_procedures(kb_procedures, pictures, langs=LANG):
    curated = read_json(os.path.join(DATA, "procedures.json"), [])
    out = list(curated)
    have = {p["title"].lower() for p in curated}
    for topic in kb_procedures:
        # Present in every language or in none, so a procedure cannot go
        # missing on a phone that is set to Czech.
        if not any(topic["steps"].values()) and not has_body(topic, 40):
            continue
        steps, lang = first(topic["steps"], langs)
        body, body_lang = body_of(topic, langs, 40)
        notes, _ = first(topic["notes"], langs)
        if topic["title"].lower() in have:
            continue
        have.add(topic["title"].lower())
        title = heading(topic, langs, topic["title"])
        images = [pictures.add(i["file"], "img") for i in topic["images"]]
        out.append(dict(
            id=topic["id"], title=title, images=[i for i in images if i],
            brewer=", ".join(sorted({MODEL_CODES.get(c, ("", ""))[0]
                                     for c in codes(topic["applies_to"])} - {""})),
            machines=brands(topic["applies_to"]), codes=codes(topic["applies_to"]),
            interval="", intervalText="", purpose=prose(body),
            needed=[], warnings=[dict(n=n["level"], t=clean(n["text"])) for n in (notes or [])],
            steps=[dict(t=clean(s)) for s in (steps or [])],
            source=(topic["number"] or "") + " " + (topic["sources"] or [""])[0],
            language=lang or body_lang))
    return spell_out(out)


# The sheets are English only; the heading is what the engineer scans for, so
# the two the books use are given a fixed name the app can translate.
CARD_TITLES = {
    "daily maintenance": "Daily maintenance",
    "regular maintenance": "Regular maintenance",
    "weekly maintenance": "Weekly maintenance",
    "monthly maintenance": "Monthly maintenance",
    "quarterly maintenance": "Quarterly maintenance",
    "yearly maintenance": "Yearly maintenance",
}


def build_cards(maintenance, pictures):
    """The fold-out maintenance sheets: numbered steps with their pictures."""
    out = []
    for card in maintenance:
        steps = []
        for step in card["steps"]:
            images = [pictures.add(i["file"], "stap") for i in step["images"]]
            points = [clean(p) for p in step["points"] if p.strip()]
            notes = [clean(note["text"]) for note in step["notes"]]
            if not points and not notes and not images:
                continue
            steps.append(dict(n=step["step"] or "", t=points, o=notes,
                              a=[i for i in images if i]))
        title = CARD_TITLES.get(card["title"].strip().lower(), card["title"])
        out.append(dict(id=card["id"], title=title, interval=card["interval"],
                        machines=brands(card["applies_to"]),
                        codes=codes(card["applies_to"]),
                        language=(card["lang"] or "en").lower(),
                        source=card["doc_id"], steps=steps))
    return out


# A cell that is not a part number: a plus sign holding a table together, a
# column header that ran into the number column, "TBD".
GLUED = re.compile(r"(Productgroup|Product group).*$", re.I)


def part_number(text):
    """The number as it should be looked up, or "" when the cell is not one."""
    number = GLUED.sub("", clean(text or "")).strip().upper()
    if (len(number) < 3 or len(number) > 20 or not any(c.isdigit() for c in number)
            or "=" in number or "#" in number):
        return ""
    return number


def build_parts(parts):
    out = []
    for row in parts:
        # A part without a number is a real line — "not available separately"
        # — and the balloon on the drawing points at its position. Only the
        # cell that is not a number at all is emptied.
        number = part_number(row["part_nr"])
        description = clean(row["description"] or "")
        if not number and not (row["pos"] or "").strip() and not description:
            continue
        product = (row["applies_to"] or [None])[0]
        out.append(dict(m=brand_of(product), u=code_of(product) if product else "",
                        s=row["section"] or "", d=row["drawing"] or "",
                        p=row["pos"] or "", n=number,
                        q=row["qty"] or "", v=row["stock"] or "",
                        t=description))
    return out


BALLOON_CODE = re.compile(r"^(\d{3,5})")


def checked_hotspots(legacy, parts):
    """Balloon numbers that really do have a row in the parts books.

    The drawings with clickable balloons come from an earlier run. Where such a
    drawing is from a different printing than the parts book it now belongs to,
    a balloon points at a row that does not exist. That balloon is dropped; if
    too little is left the whole drawing is dropped and the one rendered from
    the manual is shown instead.
    """
    hotspots = read_json(os.path.join(DATA, "hotspots.json"), {})
    rows = defaultdict(set)
    for row in parts:
        if row["s"]:
            rows[(row["m"], row["s"])].add((row["p"] or "").lstrip("0").lower())

    keep, dropped, thin = {}, 0, []
    for (brand, code), path in legacy.items():
        name = os.path.basename(path).removesuffix(".webp")
        spots = hotspots.get(name)
        if not spots:
            continue
        have = rows.get((brand, code), set())
        good = [s for s in spots if str(s["n"]).lstrip("0").lower() in have]
        dropped += len(spots) - len(good)
        if len(good) < max(2, len(spots) // 2):
            thin.append(f"{brand} {code}")
            continue
        keep[name] = good
    return keep, dropped, thin


def legacy_drawings():
    """The drawings whose balloon numbers were read earlier.

    Those pictures were cropped differently, and the balloon positions are
    fractions of that crop, so the picture has to stay with them. They cover
    six books; the rest of the drawings come from the new run without balloons.
    """
    old = read_json(os.path.join(DATA, "drawings_ballon.json"), {})
    hotspots = read_json(os.path.join(DATA, "hotspots.json"), {})
    out = {}
    for key, path in old.items():
        brand, _, rest = key.partition("|")
        m = BALLOON_CODE.match(rest.strip())
        name = os.path.basename(path).removesuffix(".webp")
        if not m or name not in hotspots:
            continue
        out[(brand, m.group(1))] = path
    return out


def build_drawings(drawings, pictures, parts):
    """machine|build|section -> the exploded views of that section."""
    out = {}
    titles = {}
    legacy = legacy_drawings()
    hotspots, dropped, thin = checked_hotspots(legacy, parts)
    # A drawing without usable balloons comes from the new render.
    legacy = {k: v for k, v in legacy.items()
              if os.path.basename(v).removesuffix(".webp") in hotspots}
    print(f"  balloons: {dropped} without a row in the parts books left out, "
          f"{len(thin)} drawing(s) replaced{' (' + ', '.join(thin[:6]) + ')' if thin else ''}")
    src = os.path.join(DATA, "tek_ballon")
    dst = os.path.join(ASSETS, "tek")
    os.makedirs(dst, exist_ok=True)
    for path in set(legacy.values()):
        name = os.path.basename(path)
        if os.path.exists(os.path.join(src, name)):
            shutil.copyfile(os.path.join(src, name), os.path.join(dst, name))
    for d in drawings:
        product = (d["applies_to"] or [None])[0]
        if not product or not d["images"] or not d["code"]:
            continue
        legacy_path = legacy.get((brand_of(product), d["code"]))
        if legacy_path:
            paths = [legacy_path]
        else:
            paths = [pictures.add(i["file"], "tek") for i in d["images"]]
            paths = [p for p in paths if p]
        if not paths:
            continue
        key = f"{brand_of(product)}|{code_of(product)}|{d['code']}"
        out[key] = paths
        titles[key] = d["title"]
    return out, titles, hotspots


def build_views(kb_views, pictures, langs=LANG):
    """The front, back and inside views with their numbered call-outs."""
    out = []
    for topic in kb_views:
        callouts, lang = first(topic["callouts"], langs)
        images = [pictures.add(i["file"], "img") for i in topic["images"]]
        images = [i for i in images if i]
        if not any(topic["callouts"].values()) and not images:
            continue
        out.append(dict(id=topic["id"], number=topic["number"] or "",
                        title=heading(topic, langs, topic["title"]),
                        key=topic["title"],
                        callouts=[clean(c) for c in (callouts or [])],
                        images=images, machines=brands(topic["applies_to"]),
                        codes=codes(topic["applies_to"]),
                        source=(topic["sources"] or [""])[0], language=lang))
    return label_duplicates(out)


NUMBERED = re.compile(r"^(\d{1,2})[.)]\s*(.+)$")
CONTINUES = re.compile(r"^[a-z0-9\u00b1\u00b0<>\u2264\u2265~\u00a3+\u2013-]")
MARKER = re.compile(r"^[A-Z\u00c4\u00d6\u00dc]{4,}[.:]?$")


def repair_rows(rows):
    """Put a specification table back together.

    A table is read out of the page cell by cell, and a printed table does not
    only hold pairs: it has headings that run across both columns, values that
    wrap onto the next line, and numbered call-outs printed in two columns
    beside a picture. Read as pairs those come out as nonsense — call-out 1
    paired with call-out 5 — so they are sorted back out here.

    An item with an empty value is a heading inside the table; an item with an
    empty key is a sentence that belongs to the whole table.
    """
    pairs = [(clean(r.get("key") or ""), clean(r.get("value") or "")) for r in rows]
    pairs = [(k, v) for k, v in pairs if k or v]

    items, numbered, at, last = [], [], None, None
    for key, value in pairs:
        left, right = NUMBERED.match(key), NUMBERED.match(value)
        if left and right:
            # Two columns of one numbered legend, printed beside the picture.
            if at is None:
                at = len(items)
            for match in (left, right):
                last = dict(n=int(match.group(1)), k=f"{match.group(1)}.",
                            v=match.group(2))
                numbered.append(last)
            continue
        if last is not None and CONTINUES.match(key) and (not value or MARKER.match(value)):
            # The line before ran on into this one. "OPMERKING" beside it is
            # the start of a note whose text is somewhere else on the page.
            field = "k" if key.startswith("(") else "v"
            last[field] = (last[field] + " " + key).strip()
            continue
        if not key:
            last = dict(k="", v=value)
        elif not value:
            # A heading names what follows; a sentence ends in a full stop.
            last = (dict(k="", v=key) if key.endswith(".") or len(key.split()) > 8
                    else dict(k=key, v=""))
        elif len(key) > 45 and len(key.split()) > 6:
            last = dict(k="", v=f"{key} {value}".strip())   # a sentence the column cut in two
        else:
            last = dict(k=key, v=value)
        items.append(last)

    if numbered:
        seen, block = set(), []
        for entry in sorted(numbered, key=lambda e: e["n"]):
            if entry["n"] in seen:
                continue
            seen.add(entry["n"])
            block.append(dict(k=entry["k"], v=entry["v"]))
        items[at:at] = block
    return [i for i in items if i["k"] or i["v"]]


def build_specs(kb_specs, langs=LANG):
    out = {}
    for topic in kb_specs:
        if not any(topic["rows"].values()):
            continue
        # A table the extractor could make little of in one language is read
        # in another rather than left out, so every language lists the same
        # tables.
        def pairs_in(rows):
            # A real table has short keys: "Height", "Working pressure". Two
            # halves of a sentence, split where the column was, do not.
            return sum(1 for i in rows if i["k"] and i["v"] and len(i["k"]) <= 40)

        items, chosen = [], langs[0]
        for language in list(langs) + sorted(topic["rows"]):
            items = repair_rows(topic["rows"].get(language) or [])
            chosen = language
            if pairs_in(items) >= 2:
                break
        # A section of running text that the column cut in two is not a table,
        # however much it looks like one on the page. It is already in the app
        # as part of its own chapter; here it would only read as broken.
        if pairs_in(items) < 2:
            continue
        model_codes = codes(topic["applies_to"])
        # The heading comes from the same book as the rows below it.
        title = heading(topic, [chosen] + list(langs), topic["title"])
        row = dict(group=f"{title} ({', '.join(model_codes)})" if model_codes else title,
                   brewer=", ".join(sorted({MODEL_CODES.get(c, ("", ""))[0]
                                            for c in model_codes} - {""})),
                   machines=brands(topic["applies_to"]),
                   codes=model_codes,
                   items=items)
        # The same table is printed in every book; keep the fullest reading of it.
        key = (topic["title"], tuple(model_codes))
        if key not in out or len(items) > len(out[key]["items"]):
            out[key] = row
    return list(out.values())


def compact(name, data):
    path = os.path.join(ASSETS, name)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, separators=(",", ":"))
    return os.path.getsize(path)


def main():
    kb = {name: read_json(os.path.join(KB, "json", name + ".json"))["items"]
          for name in ("products", "documents", "faults", "menu", "components",
                       "procedures", "specs", "parts", "drawings", "maintenance",
                       "safety", "views")}
    for bucket in ("img", "tek", "stap"):
        shutil.rmtree(os.path.join(ASSETS, bucket), ignore_errors=True)
    for stale in (glob.glob(os.path.join(ASSETS, "content-*.json"))
                  + glob.glob(os.path.join(ASSETS, "machines*.json"))):
        os.remove(stale)
    pictures = Pictures()

    # --- the same for everyone ---------------------------------------------
    cards = build_cards(kb["maintenance"], pictures)
    parts = build_parts(kb["parts"])
    drawings, drawing_titles, hotspots = build_drawings(kb["drawings"], pictures, parts)

    sizes = {}
    sizes["cards.json"] = compact("cards.json", cards)
    sizes["parts.json"] = compact("parts.json", parts)
    sizes["drawings.json"] = compact("drawings.json", drawings)
    sizes["drawingnames.json"] = compact("drawingnames.json", drawing_titles)
    sizes["hotspots.json"] = compact("hotspots.json", hotspots)

    # --- once per language --------------------------------------------------
    # The manuals were translated by the manufacturer; the app hands the
    # engineer the language their machine and their manual are in, and falls
    # back to English where a book was never translated.
    counts = {}
    machines = {}
    for locale, code in LOCALES.items():
        langs = [code, "EN", "NL"]
        machines[locale] = build_machines(kb["products"], pictures, locale, kb["specs"])
        sizes[f"machines-{locale}.json"] = compact(
            f"machines-{locale}.json", machines[locale])
        content = dict(
            faults=build_faults(kb["faults"], langs),
            components=build_components(kb["components"], pictures, langs),
            menu=build_menu(kb["menu"], pictures, langs),
            procedures=build_procedures(kb["procedures"], pictures, langs),
            specs=build_specs(kb["specs"], langs),
            views=build_views(kb["views"], pictures, langs),
        )
        name = f"content-{locale}.json"
        sizes[name] = compact(name, content)
        counts[locale] = {k: len(v) for k, v in content.items()}
        native = sum(1 for row in content["components"] if row["language"] == locale)
        print(f"  {locale}  {sizes[name]/1024:7.0f} KB   "
              f"{native}/{len(content['components'])} components in their own language")

    print(f"\n{len(machines['nl'])} machines, {len(cards)} maintenance cards, "
          f"{len(parts)} part rows, {len(drawings)} drawings")
    print("per language:", counts["nl"])
    for name, size in sorted(sizes.items(), key=lambda kv: -kv[1])[:6]:
        print(f"  {name:22} {size/1024:8.0f} KB")
    print(f"  pictures               {pictures.bytes/1e6:8.1f} MB "
          f"({len(pictures.done)} files)")
    total = sum(os.path.getsize(os.path.join(dp, f))
                for dp, _, fs in os.walk(ASSETS) for f in fs)
    print(f"  assets total           {total/1e6:8.1f} MB")


if __name__ == "__main__":
    main()
