#!/usr/bin/env python3
"""Write a JSON Schema for every collection in kb/json.

The shape is read back from the data that was just written, so the schema
cannot drift from it; the wording of each field comes from the table below.

Writes kb/schema/*.schema.json
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import KB, read_json, write_json

DESCRIBE = {
    "id": "Identifier, stable between builds as long as the source text is unchanged.",
    "kind": "What sort of knowledge this is: fault, menu, component, spec, safety, view, howto.",
    "number": "Section number in the manual; the same number means the same subject in every language.",
    "title": "Heading, in the first language available (Dutch before English).",
    "title_lang": "Language the title was taken from.",
    "doctype": "Kind of source book: TM, SMI, UM, QSG, SPM, IM, BR.",
    "platforms": "Model codes this holds for (CEC, CND, XEA, XNA, FEC, FND, IEA, INB, CKA, XKA).",
    "applies_to": "Machine ids this holds for, as <brand>.<model code>.",
    "scope": "platform = the machine behind the door, so it carries over to other brands; brand = cabinet or interface only.",
    "languages": "Languages this subject is available in.",
    "sources": "Documents the text was taken from.",
    "images": "Pictures, as a path into kb/media plus size.",
    "message": "The message as the manual names it, per language.",
    "display": "What the machine shows on the screen, per language.",
    "cause": "Why the machine reports it, per language.",
    "solution": "What to do, step by step, per language.",
    "notes": "Warning lines (note / caution / warning / danger / important) pulled out of the text.",
    "path": "Where the function sits in the service menu, as a breadcrumb.",
    "steps": "Numbered steps, per language.",
    "body": "Running text with the steps and warnings taken out.",
    "rows": "Table rows as key/value, per language; mark is the letter used in the drawing.",
    "points": "Bulleted lines.",
    "callouts": "Numbered call-outs belonging to the illustration.",
    "part_nr": "Manufacturer part number; null when the book marks the part as not available.",
    "description": "Part description as printed.",
    "pos": "Balloon number on the drawing.",
    "qty": "How many are fitted.",
    "stock": "SW = warehouse stock, SE = engineer stock.",
    "available": "False when the book marks the line n/a.",
    "drawing": "Drawing the row belongs to, including its variant letter.",
    "section": "Four digit drawing code of the parts book.",
    "section_title": "Name of that drawing.",
    "group": "SETS or PARTS heading the row sits under.",
    "doc_id": "Source document id.",
    "page": "Page in the source document.",
    "code": "Drawing code.",
    "part_count": "Number of part rows in the table beside the drawing.",
    "refs": "Other drawings this one refers to.",
    "brand": "Brand line: avy, blu, edge, lina, lua, nio, nionext, rosa, virtu, zia.",
    "brand_name": "Brand as written.",
    "model_code": "Three letter code for brewer and cabinet size.",
    "brewer": "CoEx, CoEx XL, Filterfresh or Instant.",
    "size": "Small, Medium or Large cabinet.",
    "valves": "Valve material where the book distinguishes it (METAL/PPSU).",
    "series": "Series numbers the machine is sold under.",
    "series_code": "Series code on the parts book cover, such as 9CKA.",
    "menu_generation": "Which service menu the machine shows: old (ICeQ2), new, or both.",
    "name": "Machine name for display.",
    "docs": "Documents covering this machine.",
    "lang": "Language code.",
    "version": "Document version.",
    "doc_date": "Date of the edition.",
    "doc_code": "Manufacturer document code, such as 5DTCET20M.",
    "pages": "Number of pages.",
    "path_": "Where the PDF sits in manuals/.",
    "sha256": "Checksum of the PDF.",
    "superseded_by": "Set when a newer edition of the same book exists.",
    "interval": "dag, week, maand, kwartaal, jaar, periodiek.",
    "machine": "Machine as the sheet names it.",
    "step": "Step number on the maintenance sheet.",
    "by_lang": "The full record per language.",
    "text": "The section text, per language.",
}
TITLES = {
    "products": "Machines the documentation covers",
    "documents": "Source books",
    "topics": "Every subject in the books, in every language it appears in",
    "faults": "Screen messages with cause and remedy",
    "menu": "Service menu functions",
    "components": "How the parts of the machine work",
    "procedures": "Step-by-step jobs from the manuals",
    "specs": "Technical data",
    "safety": "Safety instructions and warnings",
    "views": "Machine views with numbered call-outs",
    "parts": "Parts table rows",
    "drawings": "Exploded drawings",
    "maintenance": "Maintenance sheets, step by step with pictures",
    "other": "Front matter and anything not otherwise typed",
}


def infer(value, depth=0):
    if value is None:
        return {"type": ["null", "string"]}
    if isinstance(value, bool):
        return {"type": "boolean"}
    if isinstance(value, int):
        return {"type": "integer"}
    if isinstance(value, float):
        return {"type": "number"}
    if isinstance(value, str):
        return {"type": "string"}
    if isinstance(value, list):
        if not value or depth > 4:
            return {"type": "array"}
        return {"type": "array", "items": infer(value[0], depth + 1)}
    if isinstance(value, dict):
        if depth > 4:
            return {"type": "object"}
        props = {}
        for k, v in list(value.items())[:40]:
            props[k] = infer(v, depth + 1)
            note = DESCRIBE.get(k)
            if note and depth == 0:
                props[k]["description"] = note
        return {"type": "object", "properties": props}
    return {}


def merge(a, b):
    if a == b:
        return a
    if a.get("type") == "object" and b.get("type") == "object":
        props = dict(a.get("properties", {}))
        for k, v in b.get("properties", {}).items():
            props[k] = merge(props[k], v) if k in props else v
        return {"type": "object", "properties": props}
    if a.get("type") == "array" and b.get("type") == "array":
        if "items" in a and "items" in b:
            return {"type": "array", "items": merge(a["items"], b["items"])}
        return a if "items" in a else b
    types = {t for x in (a, b) for t in ([x["type"]] if isinstance(x.get("type"), str)
                                         else x.get("type", []))}
    out = dict(a)
    out["type"] = sorted(types) if len(types) > 1 else list(types)[0]
    return out


def main():
    out_dir = os.path.join(KB, "schema")
    os.makedirs(out_dir, exist_ok=True)
    written = []
    for name in sorted(os.listdir(os.path.join(KB, "json"))):
        if not name.endswith(".json"):
            continue
        collection = name[:-5]
        data = read_json(os.path.join(KB, "json", name))
        items = data.get("items", [])
        shape = {}
        for item in items[:200]:
            shape = merge(shape, infer(item)) if shape else infer(item)
        schema = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": f"https://dukeservice.local/kb/schema/{collection}.schema.json",
            "title": TITLES.get(collection, collection),
            "type": "object",
            "properties": {
                "count": {"type": "integer"},
                "items": {"type": "array", "items": shape},
            },
            "required": ["count", "items"],
        }
        write_json(os.path.join(out_dir, collection + ".schema.json"), schema)
        written.append(collection)
    print("schemas:", ", ".join(written))


if __name__ == "__main__":
    main()
