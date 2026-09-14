#!/usr/bin/env python3
"""Write the knowledge base in the shapes other programs can read.

Three copies of the same thing: readable JSON to look at, JSONL to stream, and
a SQLite file with a full-text index for anything that wants to query it. The
pictures sit beside it, named by content.

Writes kb/
"""
import json
import os
import re
import shutil
import sqlite3
import sys
from collections import Counter, defaultdict
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import (BUILD, DOCTYPES, KB, LANGS, MODEL_CODES, read_json, write_json,
                    write_jsonl)

VERSION = "1.0"
LANG_ORDER = ["NL", "EN", "DE", "FRCA", "SV", "NO", "DA", "FI", "CZ"]


def pick(by_lang, field, order=LANG_ORDER):
    for lang in order:
        value = (by_lang.get(lang) or {}).get(field)
        if value:
            return value, lang
    for lang, payload in by_lang.items():
        if payload.get(field):
            return payload[field], lang
    return None, None


def media_index(media):
    """(document, page, box) -> picture, so a topic can find its illustrations."""
    index = {}
    for item in media["items"]:
        index[(item["doc_id"], item["page"], tuple(item["bbox"]))] = item
    return index


def topic_pictures(topic, index):
    out, seen = [], set()
    for img in topic.get("images", []):
        hit = index.get((img["doc_id"], img["page"], tuple(img["bbox"])))
        if hit and hit["hash"] not in seen:
            seen.add(hit["hash"])
            out.append(dict(file=f"media/{hit['hash']}.webp", hash=hit["hash"],
                            width=hit["width"], height=hit["height"],
                            page=hit["page"], doc_id=hit["doc_id"]))
    return out


def base(topic, pictures):
    title, title_lang = pick(topic["by_lang"], "title")
    return dict(id=topic["id"], kind=topic["kind"], number=topic["number"],
                title=title or topic["title"], title_lang=title_lang,
                doctype=topic["doctype"], platforms=topic["platforms"],
                applies_to=topic["applies_to"], scope=topic["scope"],
                languages=topic["languages"], sources=topic["docs"],
                images=pictures)


def build_collections(topics, index):
    out = defaultdict(list)
    for topic in topics:
        pictures = topic_pictures(topic, index)
        row = base(topic, pictures)
        kind = topic["kind"]
        by_lang = topic["by_lang"]
        if kind == "fault":
            row["message"] = {l: p.get("message") for l, p in by_lang.items() if p.get("message")}
            row["display"] = {l: p.get("display") for l, p in by_lang.items() if p.get("display")}
            row["cause"] = {l: p.get("cause") for l, p in by_lang.items() if p.get("cause")}
            row["solution"] = {l: p.get("solution") for l, p in by_lang.items() if p.get("solution")}
            row["notes"] = {l: p.get("notes") for l, p in by_lang.items() if p.get("notes")}
            out["faults"].append(row)
        elif kind == "menu":
            row["path"] = pick(by_lang, "path")[0] or ""
            row["body"] = {l: p.get("body") for l, p in by_lang.items() if p.get("body")}
            row["steps"] = {l: p.get("steps") for l, p in by_lang.items() if p.get("steps")}
            row["notes"] = {l: p.get("notes") for l, p in by_lang.items() if p.get("notes")}
            out["menu"].append(row)
        elif kind in ("component", "electronics"):
            row["group"] = kind
            row["body"] = {l: p.get("body") or p.get("text") for l, p in by_lang.items()}
            row["notes"] = {l: p.get("notes") for l, p in by_lang.items() if p.get("notes")}
            out["components"].append(row)
        elif kind in ("howto", "appendix", "installation"):
            row["group"] = kind
            row["steps"] = {l: p.get("steps") for l, p in by_lang.items() if p.get("steps")}
            row["body"] = {l: p.get("body") for l, p in by_lang.items() if p.get("body")}
            row["notes"] = {l: p.get("notes") for l, p in by_lang.items() if p.get("notes")}
            out["procedures"].append(row)
        elif kind == "spec":
            row["rows"] = {l: p.get("rows") for l, p in by_lang.items() if p.get("rows")}
            out["specs"].append(row)
        elif kind == "safety":
            row["notes"] = {l: p.get("notes") for l, p in by_lang.items() if p.get("notes")}
            row["points"] = {l: p.get("points") for l, p in by_lang.items() if p.get("points")}
            out["safety"].append(row)
        elif kind == "view":
            row["callouts"] = {l: p.get("callouts") for l, p in by_lang.items() if p.get("callouts")}
            out["views"].append(row)
        elif kind == "maintenance_step":
            continue                       # rebuilt per machine below
        elif kind == "drawing":
            continue                       # handled with the parts tables
        else:
            row["text"] = {l: p.get("text") for l, p in by_lang.items() if p.get("text")}
            out["other"].append(row)
        row["text"] = row.get("text") or {l: p.get("text") for l, p in by_lang.items()
                                          if p.get("text")}
    return out


def build_maintenance(facts, corpus_by_doc, index):
    """The fold-out maintenance sheets, one procedure per machine and interval."""
    groups = defaultdict(list)
    for rec in facts:
        if rec["kind"] != "maintenance_step":
            continue
        groups[(rec["doc_id"], rec["group"])].append(rec)
    out = []
    for (doc_id, group), steps in sorted(groups.items()):
        meta = corpus_by_doc.get(doc_id)
        if not meta or not group or group.lower().startswith("short maintenance"):
            continue
        steps.sort(key=lambda r: int(r["step"]) if (r["step"] or "").isdigit() else 0)
        rows = []
        for rec in steps:
            pictures = []
            for img in rec["images"]:
                hit = index.get((rec["doc_id"], img["page"], tuple(img["bbox"])))
                if hit:
                    pictures.append(dict(file=f"media/{hit['hash']}.webp",
                                         hash=hit["hash"], width=hit["width"],
                                         height=hit["height"]))
            if not rec["step"] and not rec["points"]:
                continue
            rows.append(dict(step=rec["step"], points=rec["points"],
                             notes=rec["notes"], images=pictures, page=rec["page"]))
        if not rows:
            continue
        product = f"{meta['brand']}.{meta['model_code'].lower()}" if meta.get("model_code") else None
        out.append(dict(id=f"m_{doc_id}_{re.sub(r'[^a-z0-9]+', '-', group.lower()).strip('-')}",
                        title=group, doc_id=doc_id, lang=meta.get("lang"),
                        machine=meta.get("pdf_title") or "",
                        applies_to=[product] if product else [],
                        interval=interval_of(group), steps=rows))
    return out


def interval_of(group):
    low = group.lower()
    for key, value in (("daily", "dag"), ("dagelijks", "dag"), ("regular", "periodiek"),
                       ("weekly", "week"), ("monthly", "maand"), ("quarterly", "kwartaal"),
                       ("yearly", "jaar"), ("annual", "jaar")):
        if key in low:
            return value
    return "overig"


def build_drawings(parts, media, corpus_by_doc):
    """Exploded views with the table that belongs to them."""
    by_doc_section = defaultdict(dict)
    for item in media["items"]:
        # some books split a drawing over a page the classifier reads as a
        # figure; the biggest picture in the section is the drawing either way
        by_doc_section[(item["doc_id"], item["section"])][item["hash"]] = item
    rows_by_section = defaultdict(list)
    for row in parts["rows"]:
        rows_by_section[(row["doc_id"], row["section"])].append(row)
    # Overview sheets carry no table of their own but are worth having.
    for key, items in by_doc_section.items():
        if key[1] and key not in rows_by_section and any(
                i["kind"] == "drawing" for i in items.values()):
            rows_by_section[key] = []
    out = []
    for (doc_id, section), rows in sorted(rows_by_section.items()):
        meta = corpus_by_doc.get(doc_id) or {}
        pictures = sorted(by_doc_section.get((doc_id, section), {}).values(),
                          key=lambda p: (p["kind"] != "drawing",
                                         -p["width"] * p["height"]))
        pictures = [p for p in pictures
                    if p["width"] * p["height"] >= 120_000 or p["kind"] == "drawing"]
        title = (rows[0].get("section_title") if rows
                 else next(iter(pictures), {}).get("section_title")) or ""
        product = (f"{meta.get('brand')}.{meta['model_code'].lower()}"
                   if meta.get("model_code") and meta.get("brand") else None)
        out.append(dict(
            id=f"d_{doc_id}_{section or 'x'}",
            code=section, title=title, doc_id=doc_id,
            applies_to=[product] if product else [],
            page=rows[0].get("page") if rows else (pictures[0]["page"] if pictures else None),
            images=[dict(file=f"media/{p['hash']}.webp", hash=p["hash"],
                         width=p["width"], height=p["height"], page=p["page"])
                    for p in pictures],
            part_count=sum(1 for r in rows if r["kind"] == "part"),
            refs=[r["ref_drawing"] for r in rows if r["kind"] == "ref"]))
    return out


def build_parts(parts, corpus_by_doc):
    out = []
    for i, row in enumerate(parts["rows"]):
        if row["kind"] != "part":
            continue
        meta = corpus_by_doc.get(row["doc_id"]) or {}
        product = (f"{meta.get('brand')}.{meta['model_code'].lower()}"
                   if meta.get("model_code") and meta.get("brand") else None)
        out.append(dict(id=f"p{i}", part_nr=row.get("part_nr"),
                        description=row.get("description") or "",
                        pos=row.get("pos"), qty=row.get("qty"),
                        stock=row.get("stock"), available=row.get("available", True),
                        drawing=row.get("drawing"), section=row.get("section"),
                        section_title=row.get("section_title"),
                        group=row.get("group"), doc_id=row["doc_id"],
                        applies_to=[product] if product else [], page=row.get("page")))
    return out


def sqlite_build(path, data):
    if os.path.exists(path):
        os.remove(path)
    db = sqlite3.connect(path)
    db.executescript("""
    PRAGMA journal_mode = OFF;
    CREATE TABLE product (
        id TEXT PRIMARY KEY, brand TEXT, brand_name TEXT, model_code TEXT,
        brewer TEXT, size TEXT, valves TEXT, series TEXT, series_code TEXT,
        menu_generation TEXT, name TEXT);
    CREATE TABLE document (
        doc_id TEXT PRIMARY KEY, doctype TEXT, doctype_name TEXT, brand TEXT,
        model_code TEXT, brewer TEXT, size TEXT, lang TEXT, version TEXT,
        doc_date TEXT, doc_code TEXT, pages INTEGER, path TEXT, sha256 TEXT,
        superseded_by TEXT, title TEXT);
    CREATE TABLE topic (
        id TEXT PRIMARY KEY, kind TEXT, doctype TEXT, number TEXT, title TEXT,
        scope TEXT, languages TEXT, platforms TEXT);
    CREATE TABLE topic_text (
        topic_id TEXT, lang TEXT, title TEXT, text TEXT, payload TEXT,
        doc_id TEXT, page INTEGER, PRIMARY KEY (topic_id, lang));
    CREATE TABLE topic_product (topic_id TEXT, product_id TEXT,
        PRIMARY KEY (topic_id, product_id));
    CREATE TABLE topic_source (topic_id TEXT, doc_id TEXT,
        PRIMARY KEY (topic_id, doc_id));
    CREATE TABLE topic_image (topic_id TEXT, hash TEXT, page INTEGER,
        PRIMARY KEY (topic_id, hash));
    CREATE TABLE part (
        id TEXT PRIMARY KEY, part_nr TEXT, description TEXT, pos TEXT, qty TEXT,
        stock TEXT, available INTEGER, drawing TEXT, section TEXT,
        section_title TEXT, "group" TEXT, doc_id TEXT, product_id TEXT,
        page INTEGER);
    CREATE TABLE drawing (
        id TEXT PRIMARY KEY, code TEXT, title TEXT, doc_id TEXT, product_id TEXT,
        page INTEGER, part_count INTEGER, hash TEXT, width INTEGER, height INTEGER);
    CREATE TABLE media (hash TEXT PRIMARY KEY, kind TEXT, width INTEGER,
        height INTEGER, file TEXT);
    CREATE TABLE media_use (hash TEXT, doc_id TEXT, page INTEGER, section TEXT);
    CREATE TABLE maintenance (
        id TEXT PRIMARY KEY, title TEXT, interval TEXT, doc_id TEXT, lang TEXT,
        product_id TEXT, steps TEXT);
    CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
    """)
    for p in data["products"]:
        db.execute("INSERT INTO product VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                   (p["id"], p["brand"], p["brand_name"], p["model_code"], p["brewer"],
                    p["size"], p.get("valves"), ", ".join(p.get("series") or []),
                    p.get("series_code"), p["menu_generation"], p["name"]))
    for d in data["documents"]:
        db.execute("INSERT INTO document VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                   (d["doc_id"], d.get("doctype"),
                    DOCTYPES.get(d.get("doctype"), ("", ""))[0], d.get("brand"),
                    d.get("model_code"), d.get("brewer"), d.get("size"), d.get("lang"),
                    d.get("version"), d.get("doc_date"), d.get("doc_code"),
                    d.get("pages"), d.get("path"), d.get("sha256"),
                    d.get("superseded_by"), d.get("pdf_title") or d.get("stem")))
    for t in data["topics"]:
        db.execute("INSERT INTO topic VALUES (?,?,?,?,?,?,?,?)",
                   (t["id"], t["kind"], t["doctype"], t["number"], t["title"],
                    t["scope"], ",".join(t["languages"]), ",".join(t["platforms"])))
        for lang, payload in t["by_lang"].items():
            db.execute("INSERT INTO topic_text VALUES (?,?,?,?,?,?,?)",
                       (t["id"], lang, payload.get("title"), payload.get("text"),
                        json.dumps({k: v for k, v in payload.items()
                                    if k not in ("title", "text")}, ensure_ascii=False),
                        payload.get("doc_id"), payload.get("page")))
        for pid in t["applies_to"]:
            db.execute("INSERT OR IGNORE INTO topic_product VALUES (?,?)", (t["id"], pid))
        for doc in t["docs"]:
            db.execute("INSERT OR IGNORE INTO topic_source VALUES (?,?)", (t["id"], doc))
        for img in t.get("pictures", []):
            db.execute("INSERT OR IGNORE INTO topic_image VALUES (?,?,?)",
                       (t["id"], img["hash"], img.get("page")))
    for p in data["parts"]:
        db.execute("INSERT INTO part VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                   (p["id"], p["part_nr"], p["description"], p["pos"], p["qty"],
                    p["stock"], int(bool(p["available"])), p["drawing"], p["section"],
                    p["section_title"], p["group"], p["doc_id"],
                    (p["applies_to"] or [None])[0], p["page"]))
    for d in data["drawings"]:
        first = (d["images"] or [{}])[0]
        db.execute("INSERT INTO drawing VALUES (?,?,?,?,?,?,?,?,?,?)",
                   (d["id"], d["code"], d["title"], d["doc_id"],
                    (d["applies_to"] or [None])[0], d["page"], d["part_count"],
                    first.get("hash"), first.get("width"), first.get("height")))
    for item in data["media"]["items"]:
        db.execute("INSERT OR IGNORE INTO media VALUES (?,?,?,?,?)",
                   (item["hash"], item["kind"], item["width"], item["height"],
                    f"media/{item['hash']}.webp"))
        db.execute("INSERT INTO media_use VALUES (?,?,?,?)",
                   (item["hash"], item["doc_id"], item["page"], item["section"]))
    for m in data["maintenance"]:
        db.execute("INSERT INTO maintenance VALUES (?,?,?,?,?,?,?)",
                   (m["id"], m["title"], m["interval"], m["doc_id"], m["lang"],
                    (m["applies_to"] or [None])[0],
                    json.dumps(m["steps"], ensure_ascii=False)))

    db.executescript("""
    CREATE VIRTUAL TABLE search USING fts5(
        ref UNINDEXED, kind UNINDEXED, lang UNINDEXED, title, body,
        scope UNINDEXED,
        tokenize = 'unicode61 remove_diacritics 2');
    CREATE INDEX part_nr_idx ON part(part_nr);
    CREATE INDEX part_product_idx ON part(product_id);
    CREATE INDEX topic_kind_idx ON topic(kind);
    CREATE INDEX topic_product_idx ON topic_product(product_id);
    CREATE INDEX media_use_idx ON media_use(doc_id, page);
    """)
    rows, seen = [], set()

    def add(ref, kind, lang, title, body, scope=""):
        key = (kind, lang, title, (body or "")[:400])
        if key in seen:
            return                      # same wording in ten books is one hit
        seen.add(key)
        rows.append((ref, kind, lang, title, body or "", scope))

    for t in data["topics"]:
        scope = ",".join(t["platforms"])
        for lang, payload in t["by_lang"].items():
            add(t["id"], t["kind"], lang, payload.get("title") or t["title"],
                payload.get("text"), scope)
    for p in data["parts"]:
        add(p["id"], "part", "EN", p["part_nr"] or "n/a", p["description"],
            ",".join(p["applies_to"]))
    for p in data["products"]:
        add(p["id"], "machine", "NL", p["name"],
            " ".join(filter(None, [p["brand_name"], p["brewer"], p["size"],
                                   p["model_code"], p.get("series_code") or "",
                                   ", ".join(p.get("series") or [])])), p["id"])
    for m in data["maintenance"]:
        body = " ".join(point for step in m["steps"] for point in step["points"])
        add(m["id"], "maintenance", m["lang"] or "EN", m["title"], body,
            ",".join(m["applies_to"]))
    db.executemany("INSERT INTO search VALUES (?,?,?,?,?,?)", rows)
    for key, value in data["meta"].items():
        db.execute("INSERT INTO meta VALUES (?,?)", (key, json.dumps(value, ensure_ascii=False)))
    db.commit()
    db.execute("VACUUM")
    db.close()
    return len(rows)


def main():
    corpus = read_json(os.path.join(BUILD, "corpus.json"))
    facts = read_json(os.path.join(BUILD, "facts.json"))["records"]
    topics = read_json(os.path.join(BUILD, "topics.json"))["topics"]
    products = read_json(os.path.join(BUILD, "products.json"))["products"]
    parts = read_json(os.path.join(BUILD, "parts.json"))
    media = read_json(os.path.join(BUILD, "media.json"))

    documents = [d for d in corpus["documents"] if not d.get("duplicate_of")]
    corpus_by_doc = {d["doc_id"]: d for d in documents}
    by_path = {d["path"]: d["doc_id"] for d in corpus["documents"]}
    for d in documents:
        if d.get("superseded_by"):
            d["superseded_by"] = by_path.get(d["superseded_by"], d["superseded_by"])
    index = media_index(media)
    for topic in topics:
        topic["pictures"] = topic_pictures(topic, index)

    collections = build_collections(topics, index)
    collections["maintenance"] = build_maintenance(facts, corpus_by_doc, index)
    collections["drawings"] = build_drawings(parts, media, corpus_by_doc)
    collections["parts"] = build_parts(parts, corpus_by_doc)
    collections["products"] = products
    collections["documents"] = [
        {k: v for k, v in d.items() if k in (
            "doc_id", "doctype", "brand", "brewer", "size", "model_code", "lang",
            "version", "doc_date", "doc_code", "pages", "path", "sha256",
            "series", "series_code", "superseded_by", "pdf_title")}
        for d in documents]

    meta = dict(name="DUKE Service Knowledge Base", version=VERSION,
                generated=date.today().isoformat(),
                source_documents=len(documents),
                source_pages=sum(d.get("pages", 0) for d in documents),
                languages={l: LANGS.get(l, l) for l in LANG_ORDER},
                model_codes={k: dict(brewer=v[0], size=v[1]) for k, v in MODEL_CODES.items()},
                stock_legend=parts.get("stock_legend", {}),
                counts={k: len(v) for k, v in collections.items()},
                licence=("Derived from De Jong DUKE service documentation. "
                         "The manufacturer's manuals are copyrighted; this database "
                         "is for private service use and must not be republished."))

    os.makedirs(os.path.join(KB, "json"), exist_ok=True)
    os.makedirs(os.path.join(KB, "jsonl"), exist_ok=True)
    for name, rows in collections.items():
        write_json(os.path.join(KB, "json", name + ".json"),
                   dict(count=len(rows), items=rows))
        write_jsonl(os.path.join(KB, "jsonl", name + ".jsonl"), rows)
    write_json(os.path.join(KB, "json", "topics.json"),
               dict(count=len(topics), items=topics))
    write_jsonl(os.path.join(KB, "jsonl", "topics.jsonl"), topics)
    write_json(os.path.join(KB, "index.json"), meta)

    indexed = sqlite_build(os.path.join(KB, "dukekb.sqlite"),
                           dict(products=products, documents=documents, topics=topics,
                                parts=collections["parts"], drawings=collections["drawings"],
                                media=media, maintenance=collections["maintenance"],
                                meta=meta))
    size = os.path.getsize(os.path.join(KB, "dukekb.sqlite"))
    print(f"kb/ written: {len(topics)} topics, {len(collections['parts'])} parts, "
          f"{len(collections['drawings'])} drawings, {indexed} search rows, "
          f"sqlite {size/1e6:.1f} MB")
    for name, rows in sorted(collections.items()):
        print(f"  {name:14} {len(rows):6}")


if __name__ == "__main__":
    main()
