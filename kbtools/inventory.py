#!/usr/bin/env python3
"""Take stock of every PDF in manuals/ before a single page is parsed.

Reading the file names and the PDF properties is enough to work out which
machine a book belongs to, which language it is in and which other file it is
a newer or older copy of. That map drives both the tidy-up of the folder and
everything the parsers do later.

Writes build/corpus.json
"""
import os
import re
import sys
from datetime import datetime

import pymupdf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import (BUILD, CODE_BRAND, CODE_LANG, CODE_LETTER_DOCTYPE, LANGS,
                    MANUALS, MODEL_CODES, ROOT, menu_generation, product_id,
                    sha256, slug, write_json)

B = r"(?<![A-Za-z0-9])"        # "_" counts as a word character, \b will not do
DOC_CODE = re.compile(B + r"(5D[A-Z][A-Z0-9]{5,9})(?![A-Za-z0-9])", re.I)
SPARE_CODE = re.compile(B + r"9(CEC|CND|XEA|XNA|FEC|FND|IEA|INB|CKA|XKA)(?![A-Za-z0-9])", re.I)
VERSION = re.compile(B + r"V\.?\s?(\d)[._]?(\d{1,2})(?![A-Za-z0-9])", re.I)
DATE = re.compile(B + r"(20\d{2})(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)(\d{1,2})(?![A-Za-z0-9])", re.I)
MONTHS = dict(jan=1, feb=2, mar=3, apr=4, may=5, jun=6, jul=7, aug=8, sep=9,
              oct=10, nov=11, dec=12)

BRAND_WORDS = {
    "avy": "avy", "blu": "blu", "edge": "edge", "lina": "lina", "lua": "lua",
    "nio": "nio", "rosa": "rosa", "virtu": "virtu", "zia": "zia", "w100": "w100",
}
BREWER_WORDS = {
    "coexxl": ("CoEx XL", None), "coex": ("CoEx", None),
    "filterfresh": ("Filterfresh", None), "instant": ("Instant", None),
    "unibrewer": ("Filterfresh", None),
}
SIZE_WORDS = {"small": "Small", "medium": "Medium", "large": "Large"}

# Books whose file name says nothing useful.
KNOWN = {
    "Virtumanual": dict(doctype="UM", brand="virtu", lang="EN", code="5DUCEK20I"),
    "Luamanual": dict(doctype="UM", brand="lua", lang="EN", code="5DUXES20I"),
    "Virtu_70_90_User": dict(doctype="UM", brand="virtu", lang="EN", note="Virtu 70/90"),
    "W100 User manual English": dict(doctype="UM", brand="w100", lang="EN",
                                     note="other product family (T0642EN00)"),
    "Nioparts": dict(doctype="SPM", brand="nio", lang="EN", legacy=True),
    "Avyparts": dict(doctype="SPM", brand="avy", lang="EN", legacy=True),
    "Luaparts": dict(doctype="SPM", brand="lua", lang="EN", legacy=True),
    "VirtuParts": dict(doctype="SPM", brand="virtu", lang="EN", legacy=True),
    "Ziaparts": dict(doctype="SPM", brand="zia", lang="EN", legacy=True),
    "Rosaparts": dict(doctype="SPM", brand="rosa", lang="EN", legacy=True),
}


def tokens(stem):
    return [t for t in re.split(r"[^A-Za-z0-9+]+", stem) if t]


def decode_doc_code(code):
    """5DTCESB20M -> technical manual, CoEx Medium, Blu, English."""
    out = {}
    code = code.upper()
    body = code[2:]
    out["doctype"] = CODE_LETTER_DOCTYPE.get(body[0])
    rest = body[1:]
    m = re.match(r"([A-Z]+)(\d{2})([A-Z]?)$", rest)
    if not m:
        return out
    letters, digits, _tail = m.groups()
    out["lang"] = CODE_LANG.get(digits)
    # letters = brewer letter + size letter + brand letter(s)
    for blen in (2, 1):
        brand = CODE_BRAND.get(letters[-blen:])
        if brand and len(letters) - blen >= 2:
            out["brand"] = brand
            out["model_letters"] = letters[:-blen]
            break
    return out


def parse_name(path):
    stem = os.path.splitext(os.path.basename(path))[0]
    info = dict(stem=stem)
    known = KNOWN.get(stem)
    if known:
        info.update(known)

    toks = tokens(stem)
    low = [t.lower() for t in toks]

    # document type from the leading token
    head = low[0] if low else ""
    if head == "tm":
        info["doctype"] = "TM"
    elif head == "smi":
        info["doctype"] = "SMI"
    elif head == "qsg":
        info["doctype"] = "QSG"
    elif head in ("user", "um") or "user" in low and "manual" in low:
        info.setdefault("doctype", "UM")
    if "spare" in low and "parts" in low:
        info["doctype"] = "SPM"
    if "brochure" in low:
        info["doctype"] = "BR"
    if "installation" in low and "manual" in low:
        info["doctype"] = "IM"

    m = DOC_CODE.search(stem)
    if m:
        info["doc_code"] = m.group(1).upper()
        for k, v in decode_doc_code(m.group(1)).items():
            if v:
                info.setdefault(k, v)
    m = SPARE_CODE.search(stem)
    if m:
        info["model_code"] = m.group(1).upper()
        info.setdefault("doctype", "SPM")
    if "+PPSU" in stem.upper():
        info["valves"] = "PPSU"
    if re.search(r"_HC_|_HC$", stem, re.I):
        info["variant_note"] = "HC"
    if re.search(r"\bSim\b", stem, re.I):
        info["variant_note"] = "Simplified"

    for t in low:
        if t in BRAND_WORDS:
            info["brand"] = BRAND_WORDS[t]
        if t in BREWER_WORDS:
            info["brewer"] = BREWER_WORDS[t][0]
        if t in SIZE_WORDS:
            info["size"] = SIZE_WORDS[t]
        tu = t.upper()
        if tu in MODEL_CODES:
            info["model_code"] = tu
        if tu in LANGS and tu not in ("IT",):      # "IT" never appears as a word here
            info["lang"] = tu
        elif tu == "FRCA":
            info["lang"] = "FRCA"
    if "nio" in low and "next" in low:
        info["brand"] = "nionext"
    if re.search(r"\bcoex\s*\(?xl\)?", stem, re.I) or "coexxl" in "".join(low):
        info.setdefault("brewer", "CoEx XL")

    m = VERSION.search(stem)
    if m:
        minor = m.group(2)
        info["version"] = f"{m.group(1)}.{int(minor) if len(minor) == 1 else minor.rstrip('0') or '0'}"
        if len(minor) == 2 and minor.endswith("0"):
            info["version"] = f"{m.group(1)}.{minor[0]}"
    m = DATE.search(stem)
    if m:
        info["doc_date"] = "%s-%02d-%02d" % (m.group(1), MONTHS[m.group(2).lower()],
                                            int(m.group(3)))
    if re.search(r"_(\d)$", stem):
        info["dup_suffix"] = True

    code = info.get("model_code")
    if code in MODEL_CODES:
        brewer, size = MODEL_CODES[code]
        info.setdefault("brewer", brewer)
        info.setdefault("size", size)
    return info


SERIES_CODE = re.compile(r"(?<![A-Za-z0-9])9([A-Z]{3,4})(?![A-Za-z0-9])")
LONG_DATE = re.compile(r"\b(January|February|March|April|May|June|July|August|"
                       r"September|October|November|December)\s+(\d{1,2}),?\s+(20\d{2})\b", re.I)
MONTH_NAMES = ["january", "february", "march", "april", "may", "june", "july",
               "august", "september", "october", "november", "december"]
SERIES_LINE = re.compile(r"^(.*\bseries)\s*$", re.I | re.M)


def probe_cover(path, info):
    """Read the title page of a parts book, which is more exact than the file name.

    The older books are named Nioparts.pdf and such; their cover is the only
    place that says which machine series and which edition they are.
    """
    try:
        with pymupdf.open(path) as doc:
            text = "\n".join(doc[i].get_text() for i in range(min(2, doc.page_count)))
    except Exception:
        return info
    m = SERIES_CODE.search(text)
    if m:
        code = m.group(1).upper()
        info["series_code"] = "9" + code
        if not info.get("model_code") and code in MODEL_CODES:
            info["model_code"] = code
    m = LONG_DATE.search(text)
    if m and not info.get("doc_date"):
        info["doc_date"] = "%s-%02d-%02d" % (m.group(3),
                                             MONTH_NAMES.index(m.group(1).lower()) + 1,
                                             int(m.group(2)))
    m = SERIES_LINE.search(text)
    if m:
        info["series"] = _WSX.sub(" ", m.group(1)).strip()
    if re.search(r"\bPPSU\b", text) and re.search(r"\bMETAL\b", text, re.I):
        info.setdefault("valves", "METAL/PPSU")
    elif re.search(r"\bPPSU\b", text):
        info.setdefault("valves", "PPSU")
    m = re.search(r"with (CoEx XL|CoEx|Filterfresh|Instant|Uni-?Brewer)[^\n]*brewer", text, re.I)
    if m:
        info.setdefault("brewer", m.group(1).replace("Uni-Brewer", "Filterfresh"))
    return info


_WSX = re.compile(r"\s+")


def pdf_meta(path):
    out = {}
    try:
        with pymupdf.open(path) as doc:
            out["pages"] = doc.page_count
            md = doc.metadata or {}
            for k in ("title", "subject", "author", "creator", "producer"):
                if md.get(k):
                    out["pdf_" + k] = md[k].strip()
            raw = md.get("creationDate") or ""
            m = re.match(r"D:(\d{4})(\d{2})(\d{2})", raw)
            if m:
                out["created"] = "-".join(m.groups())
            out["toc"] = len(doc.get_toc())
            out["encrypted"] = bool(doc.is_encrypted)
    except Exception as exc:                       # pragma: no cover - diagnostics
        out["error"] = f"{type(exc).__name__}: {exc}"
    return out


def scan():
    rows = []
    for dirpath, dirnames, filenames in os.walk(MANUALS):
        dirnames[:] = [d for d in dirnames if not d.startswith(".")]
        for name in sorted(filenames):
            if not name.lower().endswith(".pdf"):
                continue
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, ROOT)
            info = parse_name(path)
            info.update(pdf_meta(path))
            if info.get("doctype") in ("SPM", "UM", "BR") or not info.get("model_code"):
                info = probe_cover(path, info)
            info["path"] = rel
            info["folder"] = os.path.relpath(dirpath, MANUALS)
            info["bytes"] = os.path.getsize(path)
            info["sha256"] = sha256(path)
            rows.append(info)
    return rows


def enrich(rows):
    """Fill gaps from the PDF properties and build the product identity."""
    for r in rows:
        subj = (r.get("pdf_subject") or "").lower()
        if "doctype" not in r:
            if "short maintenance" in subj:
                r["doctype"] = "SMI"
            elif "technical manual" in subj:
                r["doctype"] = "TM"
            elif "quick start" in subj:
                r["doctype"] = "QSG"
            elif "user manual" in subj:
                r["doctype"] = "UM"
        title = (r.get("pdf_title") or "")
        for word, brand in BRAND_WORDS.items():
            if re.search(r"\b" + word + r"\b", title, re.I):
                r.setdefault("brand", brand)
        for word, (brewer, _s) in BREWER_WORDS.items():
            if re.search(word.replace("coexxl", r"coex\s*xl"), title, re.I):
                r.setdefault("brewer", brewer)
        for word, size in SIZE_WORDS.items():
            if re.search(r"\b" + word + r"\b", title, re.I):
                r.setdefault("size", size)
        r.setdefault("lang", "EN")
        r["lang_name"] = LANGS.get(r["lang"], r["lang"])
        if r.get("brand"):
            r["product"] = product_id(r["brand"], r.get("brewer"), r.get("size"))
            r["menu_generation"] = menu_generation(r["brand"])
        r["doc_id"] = make_doc_id(r)
    return rows


def make_doc_id(r):
    bits = [r.get("doctype", "DOC").lower()]
    for key in ("brand", "brewer", "size"):
        if r.get(key):
            bits.append(slug(r[key]))
    if r.get("model_code"):
        bits.append(r["model_code"].lower())
    bits.append(r.get("lang", "en").lower())
    if r.get("version"):
        bits.append("v" + r["version"].replace(".", ""))
    elif r.get("doc_date"):
        bits.append(r["doc_date"].replace("-", ""))
    base = "-".join(bits)
    if r.get("valves"):
        base += "-" + slug(r["valves"])
    if r.get("variant_note"):
        base += "-" + slug(r["variant_note"])
    return base


def unique_ids(rows):
    seen = {}
    for r in rows:
        base = r["doc_id"]
        if base not in seen:
            seen[base] = r
            continue
        for extra in (r.get("series_code"), r.get("series"), r.get("doc_date"),
                      str(r.get("pages"))):
            if extra and slug(extra) not in base:
                r["doc_id"] = base + "-" + slug(extra)
                break
        else:
            r["doc_id"] = base + "-" + r["sha256"][:6]
        if r["doc_id"] in seen:
            r["doc_id"] = base + "-" + r["sha256"][:6]
        seen[r["doc_id"]] = r
    return rows


def find_duplicates(rows):
    """Exact copies, and books that a newer edition replaces."""
    by_hash = {}
    for r in rows:
        by_hash.setdefault(r["sha256"], []).append(r)
    for group in by_hash.values():
        if len(group) > 1:
            keep = sorted(group, key=keep_rank)[0]
            for r in group:
                if r is not keep:
                    r["duplicate_of"] = keep["path"]

    # same book, different edition
    by_edition = {}
    for r in rows:
        if r.get("duplicate_of"):
            continue
        if r.get("doc_code"):
            key = ("code", r["doc_code"])
        elif r.get("doctype") == "SPM" and r.get("brand") and r.get("model_code"):
            key = ("spm", r["brand"], r["model_code"], r.get("valves"),
                   r.get("variant_note"), r.get("lang"))
        else:
            continue
        by_edition.setdefault(key, []).append(r)
    for key, group in by_edition.items():
        if len(group) < 2:
            continue
        group.sort(key=lambda r: (version_key(r.get("version")),
                                  r.get("doc_date") or r.get("created") or "",
                                  r["bytes"]))
        newest = group[-1]
        for r in group[:-1]:
            if (r.get("version"), r.get("doc_date")) == (newest.get("version"),
                                                         newest.get("doc_date")):
                r["duplicate_of"] = newest["path"]     # same edition, second copy
            else:
                r["superseded_by"] = newest["path"]
    return rows


def keep_rank(r):
    """Of two identical files, keep the one whose name says the most."""
    return (0 if "handleidingen" in r["path"] else 1,
            1 if r.get("dup_suffix") else 0,
            0 if r.get("doc_code") or r.get("doc_date") else 1,
            0 if r.get("model_code") else 1,
            -len(os.path.basename(r["path"])), r["path"])


def version_key(v):
    if not v:
        return (0, 0)
    a, _, b = v.partition(".")
    return (int(a or 0), int(b or 0))


def main():
    rows = unique_ids(enrich(scan()))
    rows = find_duplicates(rows)
    rows.sort(key=lambda r: (r.get("doctype", ""), r.get("brand", ""),
                             r.get("model_code", ""), r.get("lang", "")))
    out = dict(generated=datetime.now().isoformat(timespec="seconds"),
               count=len(rows), documents=rows)
    write_json(os.path.join(BUILD, "corpus.json"), out)

    active = [r for r in rows if not r.get("duplicate_of") and not r.get("superseded_by")]
    print(f"{len(rows)} PDFs, {len(active)} active, "
          f"{sum(1 for r in rows if r.get('duplicate_of'))} duplicates, "
          f"{sum(1 for r in rows if r.get('superseded_by'))} superseded")
    per = {}
    for r in active:
        per[r.get("doctype", "?")] = per.get(r.get("doctype", "?"), 0) + 1
    print("active per type:", per)
    missing = [r["path"] for r in active if not r.get("brand")]
    if missing:
        print("no brand identified:", *missing, sep="\n  ")
    print("pages total:", sum(r.get("pages", 0) for r in active))


if __name__ == "__main__":
    main()
