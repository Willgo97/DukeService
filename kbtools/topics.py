#!/usr/bin/env python3
"""Fold the per-book records into one knowledge base.

Two things happen here. First the machine catalogue is worked out from the
books themselves: which brand is sold with which brewer and cabinet, and under
which series number. Then every piece of text is reduced to a topic: one
subject, in as many languages as the books provide, with the list of machines
it holds for.

A technical manual exists for Avy and a few others, but the machine behind the
door is shared — an Avy CND and a Zia CND are the same brewer in a different
cabinet. Chapters that describe the machine rather than the cabinet are
therefore marked as holding for the whole platform, and that is recorded as
such so it stays honest.

Writes build/products.json and build/topics.json
"""
import os
import re
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import (BRANDS, BUILD, MODEL_CODES, fingerprint, menu_generation,
                    norm_text, read_json, write_json)

# Chapters that describe the cabinet and the user interface rather than the
# machine behind them, so they do not carry over to another brand.
BRAND_ONLY_CHAPTERS = {"2"}
BRAND_ONLY_KINDS = {"view", "brochure", "maintenance_step", "drawing"}
LANG_ORDER = ["NL", "EN", "DE", "FRCA", "SV", "NO", "DA", "FI", "CZ"]


def platform_of(meta):
    """The technical identity of a machine: brewer plus cabinet size."""
    code = meta.get("model_code")
    if code:
        return code
    if meta.get("brewer"):
        return (meta["brewer"] + " " + (meta.get("size") or "")).strip()
    return meta.get("doc_id")


def build_products(corpus):
    """Every machine the books describe, with the documents that cover it."""
    products = {}
    series_by_code = defaultdict(set)
    for doc in corpus["documents"]:
        if doc.get("duplicate_of"):
            continue
        brand, code = doc.get("brand"), doc.get("model_code")
        series = doc.get("series")
        if code and series:
            m = re.search(r"([\d/\s\-]*\d{3,5})\s*(?:and|en|/)?\s*(\d{3,5})?\s*series",
                          series, re.I)
            if m:
                series_by_code[(brand, code)].add(re.sub(r"\s+", " ", m.group(0)).strip())
        if not brand or not code:
            continue
        pid = f"{brand}.{code.lower()}"
        brewer, size = MODEL_CODES.get(code, (doc.get("brewer"), doc.get("size")))
        p = products.setdefault(pid, dict(
            id=pid, brand=brand, brand_name=BRANDS.get(brand, brand),
            model_code=code, brewer=brewer, size=size,
            valves=doc.get("valves"), series=[],
            menu_generation=menu_generation(brand), docs=[], series_code=None))
        p["docs"].append(doc["doc_id"])
        if doc.get("series_code"):
            p["series_code"] = doc["series_code"]
        if doc.get("valves") and not p.get("valves"):
            p["valves"] = doc["valves"]
    for pid, p in products.items():
        p["series"] = sorted(series_by_code.get((p["brand"], p["model_code"]), ()))
        p["name"] = f"{p['brand_name']} {p['brewer']} {p['size']}".strip()
        p["docs"] = sorted(set(p["docs"]))
    return products


def main():
    corpus = read_json(os.path.join(BUILD, "corpus.json"))
    facts = read_json(os.path.join(BUILD, "facts.json"))["records"]
    meta_by_doc = {d["doc_id"]: d for d in corpus["documents"] if not d.get("duplicate_of")}

    products = build_products(corpus)
    by_code = defaultdict(list)
    for p in products.values():
        by_code[p["model_code"]].append(p["id"])
    write_json(os.path.join(BUILD, "products.json"),
               dict(count=len(products), products=sorted(products.values(),
                                                         key=lambda p: p["id"])))

    # slot = one subject in one book family, holding every translation of it
    slots = defaultdict(dict)
    for rec in facts:
        meta = meta_by_doc.get(rec["doc_id"])
        if not meta:
            continue
        key = (meta.get("doctype"), platform_of(meta), rec["kind"], rec["number"],
               rec["title"] if not rec["number"] else "")
        slots[key].setdefault(rec["lang"], []).append(rec)

    topics = []
    merge = defaultdict(list)
    for key, langs in slots.items():
        doctype, platform, kind, number, _title = key
        ref_lang = next((l for l in ("EN", "NL") if l in langs), sorted(langs)[0])
        ref = langs[ref_lang][0]
        sig = fingerprint(ref["text"])[:16]
        merge[(doctype, kind, number, sig)].append((key, langs, platform))

    for (doctype, kind, number, sig), members in merge.items():
        all_brands = set()
        platforms, docs, applies, langs_all = set(), set(), set(), {}
        images = []
        for key, langs, platform in members:
            platforms.add(platform)
            brands = set()
            for lang, recs in langs.items():
                rec = recs[0]
                docs.add(rec["doc_id"])
                meta = meta_by_doc[rec["doc_id"]]
                if meta.get("brand"):
                    brands.add(meta["brand"])
                    all_brands.add(meta["brand"])
                if lang not in langs_all:
                    payload = {k: v for k, v in rec.items()
                               if k not in ("doc_id", "kind", "number", "level",
                                            "lang", "images", "id")}
                    payload["doc_id"] = rec["doc_id"]
                    payload["page"] = rec["page"]
                    langs_all[lang] = payload
                for img in rec["images"]:
                    images.append(dict(doc_id=rec["doc_id"], lang=lang, **img))
            chapter = (number or "").split(".")[0]
            brand_only = chapter in BRAND_ONLY_CHAPTERS or kind in BRAND_ONLY_KINDS
            if brand_only:
                applies |= {p["id"] for p in products.values()
                            if p["model_code"] == platform and p["brand"] in brands}
            else:
                applies |= set(by_code.get(platform, []))
        chapter = (number or "").split(".")[0]
        brand_only = chapter in BRAND_ONLY_CHAPTERS or kind in BRAND_ONLY_KINDS
        if not applies:
            applies = {p["id"] for p in products.values() if p["brand"] in all_brands}
        ref_lang = next((l for l in LANG_ORDER if l in langs_all), sorted(langs_all)[0])
        title = langs_all[ref_lang].get("title", "")
        topics.append(dict(
            id="t_" + fingerprint(f"{doctype}|{kind}|{number}|{sig}")[:12],
            doctype=doctype, kind=kind, number=number, title=title,
            platforms=sorted(platforms), applies_to=sorted(applies),
            scope="brand" if brand_only else "platform",
            languages=sorted(langs_all), text=langs_all[ref_lang].get("text", ""),
            by_lang=langs_all, docs=sorted(docs),
            images=images[:12]))

    topics.sort(key=lambda t: (t["doctype"] or "", t["kind"],
                               [int(x) for x in re.findall(r"\d+", t["number"] or "")] or [999],
                               t["title"]))
    write_json(os.path.join(BUILD, "topics.json"),
               dict(count=len(topics), topics=topics))
    print(f"{len(products)} machines, {len(slots)} slots -> {len(topics)} topics")
    from collections import Counter
    for k, n in Counter(t["kind"] for t in topics).most_common():
        print(f"  {k:18} {n:5}")
    multi = sum(1 for t in topics if len(t["platforms"]) > 1)
    print(f"  topics shared by more than one platform: {multi}")
    print("  languages:", Counter(l for t in topics for l in t["languages"]).most_common())


if __name__ == "__main__":
    main()
