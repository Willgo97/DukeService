#!/usr/bin/env python3
"""Tidy up manuals/: one folder per kind of book, no copies, old editions apart.

Byte-identical copies are deleted, editions that a newer one replaces move to
_superseded/, and the handful of books with a name that says nothing get the
house name. Everything that moves is written down in manuals/index.json, so a
script that still asks for "Nioparts.pdf" can be pointed at the new place.

    python3 kbtools/organize.py            # show what would happen
    python3 kbtools/organize.py --apply    # do it
"""
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import BUILD, DOCTYPES, MANUALS, ROOT, read_json, write_json

FOLDER = {"TM": "TM-technical", "SMI": "SMI-maintenance", "UM": "UM-user",
          "QSG": "QSG-quickstart", "SPM": "SPM-parts", "IM": "IM-installation",
          "BR": "BR-brochure"}
ARCHIVE = "_superseded"

# The few books whose file name says nothing about what is inside.
RENAME = {
    "Virtumanual.pdf": "User_Manual_Virtu_CoEx_EN_V2.3_5DUCEK20I.pdf",
    "Luamanual.pdf": "User_Manual_Lua_CoExXL_EN_V2.3_5DUXES20I.pdf",
    "Virtu_70_90_User.pdf": "User_Manual_Virtu_70-90_CoEx_9CECK_EN_revB.pdf",
    "W100 User manual English.pdf": "User_Manual_W100_EN_T0642EN00.pdf",
    "VirtuParts.pdf": "Spare_Parts_Manual_Virtu_70-90_CoEx_9CECK_EN_2022sep13.pdf",
    "Nioparts.pdf": "Spare_Parts_Manual_Nio_CoEx_9CKA_EN_2025jan29.pdf",
    "Avyparts.pdf": "Spare_Parts_Manual_Avy_CoExXL_Medium_9XEA_EN_2025jan29.pdf",
    "Luaparts.pdf": "Spare_Parts_Manual_Lua_CoExXL_Medium_9XEA_EN_2025jan29.pdf",
    "Ziaparts.pdf": "Spare_Parts_Manual_Zia_CoEx_Medium_9CEC_EN_2025jan28.pdf",
    "5diaxa820_touchless-interface_installation_manual_en_01-03.pdf":
        "IM_Touchless_Interface_EN_V01-03_5DIAXA820.pdf",
    "tm_avy_coex_medium_cec_nl_v10_5dtcet10m.pdf":
        "TM_Avy_CoEx_Medium_CEC_NL_V1.0_5DTCET10M.pdf",
    "tm_avy_coex_small_cnd_en_v11_5dtcnt20m.pdf":
        "TM_Avy_CoEx_Small_CND_EN_V1.1_5DTCNT20M.pdf",
    "tm_lua_instant_small_inb_nl_v10_5dtins10m.pdf":
        "TM_Lua_Instant_Small_INB_NL_V1.0_5DTINS10M.pdf",
    "tm_nio_coexxl_xka_en_v111_5dtxka20m.pdf":
        "TM_Nio_CoExXL_Small_XKA_EN_V1.1.1_5DTXKA20M.pdf",
}


def plan(corpus):
    actions = []
    for r in corpus["documents"]:
        src = os.path.join(ROOT, r["path"])
        name = RENAME.get(os.path.basename(r["path"]), os.path.basename(r["path"]))
        if r.get("duplicate_of"):
            actions.append(("delete", src, None, r))
            continue
        folder = ARCHIVE if r.get("superseded_by") else FOLDER.get(r.get("doctype"), "OTHER")
        dst = os.path.join(MANUALS, folder, name)
        actions.append(("move" if os.path.abspath(src) != os.path.abspath(dst) else "keep",
                        src, dst, r))
    return actions


def apply(actions):
    for kind, src, dst, r in actions:
        if kind == "delete":
            os.remove(src)
        elif kind == "move":
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            if os.path.exists(dst):
                raise SystemExit(f"target already there: {dst}")
            shutil.move(src, dst)
            r["path"] = os.path.relpath(dst, ROOT)
    # drop the folders that are now empty
    for dirpath, dirnames, filenames in os.walk(MANUALS, topdown=False):
        if dirpath != MANUALS and not os.listdir(dirpath):
            os.rmdir(dirpath)


def write_index(corpus, actions):
    moved = {}
    for kind, src, dst, r in actions:
        old = os.path.basename(src)
        if kind == "delete":
            moved[old] = None
        elif dst:
            moved[old] = os.path.relpath(dst, ROOT)
    index = {"generated": corpus["generated"], "by_old_name": moved, "documents": []}
    for r in corpus["documents"]:
        if r.get("duplicate_of"):
            continue
        index["documents"].append({k: r.get(k) for k in (
            "doc_id", "doctype", "brand", "brewer", "size", "model_code",
            "series", "series_code", "lang", "version", "doc_date", "doc_code",
            "pages", "path", "superseded_by", "sha256") if r.get(k) is not None})
    write_json(os.path.join(MANUALS, "index.json"), index)

    lines = ["# Handleidingen", "",
             "Automatisch geordend door `kbtools/organize.py`. Namen zijn die van",
             "de fabrikant; alleen boeken zonder zeggende naam zijn hernoemd.", ""]
    by_type = {}
    for d in index["documents"]:
        by_type.setdefault(d["doctype"], []).append(d)
    for t, docs in sorted(by_type.items()):
        lines.append(f"## {t} — {DOCTYPES.get(t, (t, ''))[0]} ({len(docs)})")
        lines.append("")
        lines.append("| machine | brewer | maat | code | taal | versie | pag | bestand |")
        lines.append("|---|---|---|---|---|---|---|---|")
        for d in sorted(docs, key=lambda d: (d.get("brand") or "", d.get("model_code") or "",
                                             d.get("lang") or "")):
            lines.append("| {} | {} | {} | {} | {} | {} | {} | `{}` |".format(
                d.get("brand") or "–", d.get("brewer") or "–", d.get("size") or "–",
                d.get("model_code") or d.get("series_code") or "–", d.get("lang"),
                d.get("version") or d.get("doc_date") or "–", d.get("pages"),
                os.path.basename(d["path"])))
        lines.append("")
    with open(os.path.join(MANUALS, "INDEX.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))


def main():
    corpus = read_json(os.path.join(BUILD, "corpus.json"))
    actions = plan(corpus)
    counts = {}
    for kind, src, dst, r in actions:
        counts[kind] = counts.get(kind, 0) + 1
    for kind, src, dst, r in actions:
        if kind == "delete":
            print(f"  delete  {os.path.relpath(src, ROOT)}  (copy of {r['duplicate_of']})")
        elif kind == "move":
            print(f"  move    {os.path.relpath(src, ROOT)}\n       -> {os.path.relpath(dst, ROOT)}")
    print(counts)
    if "--apply" in sys.argv:
        apply(actions)
        write_index(corpus, actions)
        write_json(os.path.join(BUILD, "corpus.json"), corpus)
        print("done")


if __name__ == "__main__":
    main()
