#!/usr/bin/env python3
"""Make the app's assets out of the knowledge base.

What was written by hand stays: the Dutch fault descriptions, the machine
texts, the maintenance schedules and the procedures in data/*.json are kept and
only added to. Everything that was generated is generated again, now from all
207 books instead of ten.

Writes app/src/main/assets/
"""
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

ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
DATA = os.path.join(ROOT, "data")
LANG = ["NL", "EN", "DE"]
# How wide a picture needs to be on a phone.
WIDTH = {"tek": 1400, "img": 900, "stap": 700}
QUALITY = {"tek": 78, "img": 74, "stap": 74}


def first(mapping, langs=LANG):
    """Dutch if the manual has it, otherwise English."""
    if not isinstance(mapping, dict):
        return mapping, "nl"
    for lang in langs:
        if mapping.get(lang):
            return mapping[lang], lang.lower()
    for lang, value in mapping.items():
        if value:
            return value, lang.lower()
    return None, "nl"


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


def build_machines(products, pictures):
    """The eleven machine lines, with the builds each one is sold in."""
    base = read_json(os.path.join(DATA, "machines.json"), [])
    by_brand = defaultdict(list)
    for p in products:
        by_brand[p["brand"]].append(p)
    out = []
    for machine in base:
        brand = machine["id"]
        variants = sorted(by_brand.get(brand, []), key=lambda p: p["model_code"])
        machine["variants"] = [dict(
            code=v["model_code"],
            cabinet=v["size"] or "",
            brewer=v["brewer"] or "",
            doc=", ".join(v["series"]) or (v.get("series_code") or ""),
        ) for v in variants]
        if variants:
            machine["typeCode"] = " / ".join(v["model_code"] for v in variants)
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


def build_books(documents):
    """The source books, so the app can say where something comes from."""
    out = []
    for d in documents:
        out.append(dict(
            id=d["doc_id"], kind=d.get("doctype") or "", brand=d.get("brand") or "",
            code=d.get("model_code") or "", brewer=d.get("brewer") or "",
            cabinet=d.get("size") or "", language=(d.get("lang") or "").lower(),
            version=d.get("version") or d.get("doc_date") or "",
            number=d.get("doc_code") or d.get("series_code") or "",
            pages=d.get("pages") or 0,
            title=d.get("pdf_title") or os.path.basename(d.get("path", "")),
            file=os.path.basename(d.get("path", "")),
            superseded=bool(d.get("superseded_by"))))
    return out


def build_faults(kb_faults):
    """Curated Dutch messages first, then everything the books add to them."""
    curated = read_json(os.path.join(DATA, "faults.json"), [])
    seen = {re.sub(r"\W+", "", (f.get("message") or "").lower()) for f in curated}
    out = []
    for f in curated:
        f.setdefault("codes", [])
        out.append(f)
    for topic in sorted(kb_faults, key=lambda t: t["number"] or ""):
        english, _ = first(topic["message"], ["EN"])
        dutch, lang = first(topic["message"])
        message = english or dutch or topic["title"]
        key = re.sub(r"\W+", "", message.lower())
        if key in seen:
            hit = next((f for f in out if re.sub(r"\W+", "", f["message"].lower()) == key), None)
            if hit is not None:
                hit["codes"] = sorted(set(hit.get("codes", [])) | set(codes(topic["applies_to"])))
                hit["machines"] = sorted(set(hit["machines"]) | set(brands(topic["applies_to"])))
            continue
        seen.add(key)
        cause, _ = first(topic["cause"])
        solution, _ = first(topic["solution"]) or ([], "nl")
        notes, _ = first(topic["notes"]) or ([], "nl")
        out.append(dict(
            message=message, dutch=dutch or message, machines=brands(topic["applies_to"]),
            codes=codes(topic["applies_to"]), brewers=[], category=category(message),
            cause=cause or "", solution=solution or [],
            note=" ".join(n["text"] for n in (notes or []))[:400],
            selfService=False,
            source=topic["number"] + " " + (topic["sources"] or [""])[0],
            language=lang))
    return out


CATEGORY = [
    ("Brewer", r"brewer|brew|zetgroep"), ("Water", r"water|boiler|kalk|filter|pomp|druk"),
    ("Afval", r"waste|afval|drip|lekbak"), ("Reiniging", r"clean|reinig|spoel|rinse"),
    ("Temperatuur", r"temp|heat|verwarm"), ("Molen", r"grind|molen|bean|bonen"),
    ("Mixer", r"mixer|mengen"), ("Beker", r"cup|beker"),
    ("Betaling", r"coin|payment|betaal|munt"), ("Besturing", r"communicat|board|software|config|usb"),
    ("Ingrediënten", r"ingredient|canister|container"), ("Bediening", r"door|deur|key|sleutel|screen|scherm"),
]


def category(message):
    low = message.lower()
    for name, pattern in CATEGORY:
        if re.search(pattern, low):
            return name
    return "Overig"


def build_components(kb_components, pictures):
    out = []
    for topic in kb_components:
        # The body is the section minus its own heading. A chapter that only
        # introduces its subsections has none, and would otherwise show up as a
        # component whose whole text is its own title.
        text, lang = first(topic["body"])
        if not text or len(text.strip()) < 25:
            continue
        images = [pictures.add(i["file"], "img") for i in topic["images"]]
        out.append(dict(
            id=topic["id"], number=topic["number"] or "", title=topic["title"],
            text=text, group=topic.get("group") or "component",
            page=0, images=[i for i in images if i],
            machines=brands(topic["applies_to"]), codes=codes(topic["applies_to"]),
            brewer=", ".join(sorted({MODEL_CODES.get(c, ("", ""))[0]
                                     for c in codes(topic["applies_to"])} - {""})),
            source=(topic["sources"] or [""])[0], language=lang))
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
        return (row["title"], tuple(row["machines"]),
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

    # Whatever still collides says the same thing twice: keep the fullest one.
    best = {}
    for row in rows:
        current = best.get(key_of(row))
        if current is None or len(row.get("text", "")) > len(current.get("text", "")):
            best[key_of(row)] = row
    return [row for row in rows if best[key_of(row)] is row]


def build_menu(kb_menu, pictures):
    out = []
    for topic in kb_menu:
        body, lang = first(topic["body"])
        steps, _ = first(topic["steps"]) or ([], "nl")
        notes, _ = first(topic["notes"]) or ([], "nl")
        text = body or ""
        if not text and not steps:
            text, lang = first(topic.get("text", {}))
        images = [pictures.add(i["file"], "img") for i in topic["images"]]
        out.append(dict(
            id=topic["id"], number=topic["number"] or "", title=topic["title"],
            text=text or "", path=topic.get("path") or "", level="",
            images=[i for i in images if i], steps=steps or [],
            notes=[n["text"] for n in (notes or [])],
            machines=brands(topic["applies_to"]), codes=codes(topic["applies_to"]),
            source=(topic["sources"] or [""])[0], language=lang))
    return label_duplicates(out, extra="path")


def build_procedures(kb_procedures, pictures):
    curated = read_json(os.path.join(DATA, "procedures.json"), [])
    out = list(curated)
    have = {p["title"].lower() for p in curated}
    for topic in kb_procedures:
        steps, lang = first(topic["steps"]) or ([], "nl")
        body, body_lang = first(topic["body"])
        notes, _ = first(topic["notes"]) or ([], "nl")
        if not steps and len((body or "").strip()) < 40:
            continue
        title = topic["title"]
        if title.lower() in have:
            continue
        have.add(title.lower())
        images = [pictures.add(i["file"], "img") for i in topic["images"]]
        out.append(dict(
            id=topic["id"], title=title, images=[i for i in images if i],
            brewer=", ".join(sorted({MODEL_CODES.get(c, ("", ""))[0]
                                     for c in codes(topic["applies_to"])} - {""})),
            machines=brands(topic["applies_to"]), codes=codes(topic["applies_to"]),
            interval="", intervalText="", purpose=body or "",
            needed=[], warnings=[dict(n=n["level"], t=n["text"]) for n in (notes or [])],
            steps=[dict(t=s) for s in (steps or [])],
            source=(topic["number"] or "") + " " + (topic["sources"] or [""])[0],
            language=lang or body_lang))
    return out


# The sheets are English only; the heading is what the engineer scans for, so
# that one is given in Dutch. The steps stay in the manufacturer's words.
CARD_TITLES = {
    "daily maintenance": "Dagelijks onderhoud",
    "regular maintenance": "Periodiek onderhoud",
    "weekly maintenance": "Wekelijks onderhoud",
    "monthly maintenance": "Maandelijks onderhoud",
    "quarterly maintenance": "Onderhoud per kwartaal",
    "yearly maintenance": "Jaarlijks onderhoud",
}


def build_cards(maintenance, pictures):
    """The fold-out maintenance sheets: numbered steps with their pictures."""
    out = []
    for card in maintenance:
        steps = []
        for step in card["steps"]:
            images = [pictures.add(i["file"], "stap") for i in step["images"]]
            points = [p for p in step["points"] if p.strip()]
            notes = [note["text"] for note in step["notes"]]
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


def build_parts(parts):
    out = []
    for row in parts:
        product = (row["applies_to"] or [None])[0]
        out.append(dict(m=brand_of(product), u=code_of(product) if product else "",
                        s=row["section"] or "", d=row["drawing"] or "",
                        p=row["pos"] or "", n=row["part_nr"] or "",
                        q=row["qty"] or "", v=row["stock"] or "",
                        t=row["description"] or ""))
    return out


BALLOON_CODE = re.compile(r"^(\d{3,5})")


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


def build_drawings(drawings, pictures):
    """machine|build|section -> the exploded views of that section."""
    out = {}
    titles = {}
    legacy = legacy_drawings()
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
    return out, titles


def build_views(kb_views, pictures):
    """The front, back and inside views with their numbered call-outs."""
    out = []
    for topic in kb_views:
        callouts, lang = first(topic["callouts"]) or ([], "nl")
        images = [pictures.add(i["file"], "img") for i in topic["images"]]
        images = [i for i in images if i]
        if not callouts and not images:
            continue
        out.append(dict(id=topic["id"], number=topic["number"] or "",
                        title=topic["title"], callouts=callouts or [],
                        images=images, machines=brands(topic["applies_to"]),
                        codes=codes(topic["applies_to"]),
                        source=(topic["sources"] or [""])[0], language=lang))
    return label_duplicates(out)


def build_specs(kb_specs):
    out = []
    for topic in kb_specs:
        rows, lang = first(topic["rows"]) or ([], "nl")
        if not rows:
            continue
        out.append(dict(group=f"{topic['title']} ({', '.join(codes(topic['applies_to'])) or 'algemeen'})",
                        brewer=", ".join(sorted({MODEL_CODES.get(c, ("", ""))[0]
                                                 for c in codes(topic["applies_to"])} - {""})),
                        machines=brands(topic["applies_to"]),
                        codes=codes(topic["applies_to"]),
                        items=[dict(k=r["key"], v=r.get("value") or "") for r in rows]))
    return out


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
    pictures = Pictures()

    machines = build_machines(kb["products"], pictures)
    faults = build_faults(kb["faults"])
    components = build_components(kb["components"], pictures)
    menu = build_menu(kb["menu"], pictures)
    procedures = build_procedures(kb["procedures"], pictures)
    cards = build_cards(kb["maintenance"], pictures)
    parts = build_parts(kb["parts"])
    drawings, drawing_titles = build_drawings(kb["drawings"], pictures)
    specs = build_specs(kb["specs"])
    views = build_views(kb["views"], pictures)
    books = build_books(kb["documents"])

    sizes = {}
    sizes["machines.json"] = compact("machines.json", machines)
    sizes["faults.json"] = compact("faults.json", faults)
    sizes["components.json"] = compact("components.json", components)
    sizes["servicemenu.json"] = compact("servicemenu.json", menu)
    sizes["procedures.json"] = compact("procedures.json", procedures)
    sizes["cards.json"] = compact("cards.json", cards)
    sizes["parts.json"] = compact("parts.json", parts)
    sizes["drawings.json"] = compact("drawings.json", drawings)
    sizes["drawingnames.json"] = compact("drawingnames.json", drawing_titles)
    sizes["specs.json"] = compact("specs.json", specs)
    sizes["views.json"] = compact("views.json", views)
    sizes["books.json"] = compact("books.json", books)

    print(f"{len(machines)} machines, {len(faults)} messages, {len(components)} components,")
    print(f"{len(menu)} menu topics, {len(procedures)} procedures, {len(cards)} maintenance cards,")
    print(f"{len(parts)} part rows, {len(drawings)} drawings, {len(views)} views, "
          f"{len(books)} books")
    for name, size in sorted(sizes.items(), key=lambda kv: -kv[1]):
        print(f"  {name:22} {size/1024:8.0f} KB")
    print(f"  pictures               {pictures.bytes/1e6:8.1f} MB "
          f"({len(pictures.done)} files)")
    print(f"  assets total           {sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(ASSETS) for f in fs)/1e6:8.1f} MB")


if __name__ == "__main__":
    main()
