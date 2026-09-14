"""Shared vocabulary for the DUKE knowledge base.

The manual set is a matrix: brand x brewer x size x language x document type.
Nearly everything in here is about turning the manufacturer's own codes into
that matrix, so the rest of the pipeline can talk about products instead of
file names.
"""
import hashlib
import os
import re
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANUALS = os.path.join(ROOT, "manuals")
BUILD = os.path.join(ROOT, "build")
KB = os.path.join(ROOT, "kb")

# --- document types -------------------------------------------------------
# The letter after 5D in a DUKE document code says what kind of book it is.
DOCTYPES = {
    "TM": ("Technical Manual", "technische handleiding"),
    "SMI": ("Short Maintenance Instruction", "korte onderhoudsinstructie"),
    "UM": ("User Manual", "gebruikershandleiding"),
    "QSG": ("Quick Start Guide", "snelstartgids"),
    "IM": ("Installation Manual", "installatiehandleiding"),
    "SPM": ("Spare Parts Manual", "onderdelenboek"),
    "BR": ("Brochure", "brochure"),
}
CODE_LETTER_DOCTYPE = {"T": "TM", "S": "SMI", "U": "UM", "Q": "QSG", "I": "IM"}

# --- languages ------------------------------------------------------------
LANGS = {
    "EN": "English", "NL": "Nederlands", "DE": "Deutsch", "FR": "Français",
    "FRCA": "Français (CA)", "DA": "Dansk", "FI": "Suomi", "NO": "Norsk",
    "SV": "Svenska", "CZ": "Čeština", "ES": "Español", "IT": "Italiano",
}
# trailing digits of a document code
CODE_LANG = {"10": "NL", "20": "EN", "30": "DE", "40": "FR", "41": "FRCA",
             "53": "CZ", "71": "SV", "72": "NO", "73": "DA", "74": "FI"}

# --- products -------------------------------------------------------------
# Brand line = the cabinet / user interface family.
BRANDS = {
    "avy": "Avy", "blu": "Blu", "edge": "Edge", "lina": "Lina", "lua": "Lua",
    "nio": "Nio", "nionext": "Nio Next", "rosa": "Rosa", "virtu": "Virtu",
    "zia": "Zia", "w100": "W100",
}
BRAND_ALIASES = {
    "nio_next": "nionext", "nionext": "nionext", "nio-next": "nionext",
    "rosa": "rosa", "lina": "lina", "virtu": "virtu", "zia": "zia",
}
# The three-letter model code is the real technical identity of a machine:
# brewer family + cabinet size.
MODEL_CODES = {
    "CEC": ("CoEx", "Medium"), "CND": ("CoEx", "Small"),
    "XEA": ("CoEx XL", "Medium"), "XNA": ("CoEx XL", "Small"),
    "FEC": ("Filterfresh", "Medium"), "FND": ("Filterfresh", "Small"),
    "IEA": ("Instant", "Medium"), "INB": ("Instant", "Small"),
    "CKA": ("CoEx", "Small"), "XKA": ("CoEx XL", "Small"),
    # the older Virtu 70/90, which has a parts book of its own
    "CECK": ("CoEx", "70/90"),
}
BREWERS = ["CoEx", "CoEx XL", "Filterfresh", "Instant"]
# Brand letter(s) inside a 5D document code, after the brewer/size pair.
CODE_BRAND = {
    "T": "avy", "SB": "blu", "Q": "edge", "S": "lua", "A": "nio", "K": "virtu",
    "P": "zia", "V": "rosa", "L": "lina",
}
# Spare parts books use 9 + model code; the brand is spelled out in the title.
SPARE_CODE = re.compile(r"\b9(CEC|CND|XEA|XNA|FEC|FND|IEA|INB|CKA|XKA)\b", re.I)

# Which controller generation, i.e. which service menu the machine shows.
OLD_MENU = {"virtu", "zia", "nio", "edge"}          # ICeQ2 only
BOTH_MENU = {"lua", "avy", "rosa", "blu"}            # old until sw 6.30/6.40
NEW_MENU = {"lina", "nionext"}                       # new menu only


def slug(text, sep="-"):
    text = unicodedata.normalize("NFKD", str(text))
    text = "".join(c for c in text if not unicodedata.combining(c))
    text = re.sub(r"[^A-Za-z0-9]+", sep, text).strip(sep).lower()
    return re.sub(sep + "{2,}", sep, text)


def sha256(path, chunk=1 << 20):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for block in iter(lambda: fh.read(chunk), b""):
            h.update(block)
    return h.hexdigest()


def fingerprint(text):
    """Stable id for a piece of prose, ignoring layout noise."""
    return hashlib.sha1(norm_text(text).encode("utf-8")).hexdigest()


_WS = re.compile(r"\s+")
_BRANDWORD = re.compile(
    r"\b(avy|blu|edge|lina|lua|nio next|nio|rosa|virtu|zia)\b", re.I)
_DOCCODE = re.compile(r"\b5D[A-Z]{1,3}[A-Z0-9]{2,6}\b", re.I)


def norm_text(text):
    """Normalise for comparison: same words, brand and code noise removed.

    Two manuals for the same brewer differ mainly in the brand name in the
    running text and in the document code in the footer. Strip those and the
    shared chapters hash to the same value, which is what lets one record
    stand for a dozen books.
    """
    text = _DOCCODE.sub("", text)
    text = _BRANDWORD.sub("~", text)
    text = text.replace("®", "").replace("™", "")
    text = _WS.sub(" ", text.lower()).strip()
    return text


def product_id(brand, brewer, size):
    parts = [brand]
    if brewer:
        parts.append(slug(brewer))
    if size:
        parts.append(slug(size))
    return ".".join(p for p in parts if p)


def menu_generation(brand):
    if brand in NEW_MENU:
        return "new"
    if brand in BOTH_MENU:
        return "both"
    if brand in OLD_MENU:
        return "old"
    return "unknown"


def read_json(path, default=None):
    import json
    if not os.path.exists(path):
        return default
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def write_json(path, data, compact=False):
    import json
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        if compact:
            json.dump(data, fh, ensure_ascii=False, separators=(",", ":"))
        else:
            json.dump(data, fh, ensure_ascii=False, indent=1)
            fh.write("\n")


def write_jsonl(path, rows):
    import json
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        for row in rows:
            fh.write(json.dumps(row, ensure_ascii=False) + "\n")
