#!/usr/bin/env python3
"""Small reader for the DUKE knowledge base — use it, or copy it.

    from dukekb import KB
    kb = KB("kb/dukekb.sqlite")
    kb.search("afvalbak vol")            # everything at once
    kb.faults(product="zia.cnd")         # screen messages for one machine
    kb.part("5KAF058")                   # where a part number is used
    kb.drawings("nio.cka")               # exploded views of a machine

From the command line:

    python3 kbtools/dukekb.py search "waste bucket"
    python3 kbtools/dukekb.py machines
    python3 kbtools/dukekb.py part 5KAF058
    python3 kbtools/dukekb.py fault zia.cnd
"""
import json
import os
import sqlite3
import sys

DEFAULT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "kb", "dukekb.sqlite")


class KB:
    def __init__(self, path=DEFAULT):
        self.db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
        self.db.row_factory = sqlite3.Row
        self.root = os.path.dirname(os.path.abspath(path))

    def _rows(self, sql, args=()):
        return [dict(r) for r in self.db.execute(sql, args).fetchall()]

    # --- catalogue --------------------------------------------------------
    def meta(self):
        return {r["key"]: json.loads(r["value"]) for r in self.db.execute("SELECT * FROM meta")}

    def machines(self, brand=None):
        sql = "SELECT * FROM product"
        args = ()
        if brand:
            sql += " WHERE brand = ?"
            args = (brand,)
        return self._rows(sql + " ORDER BY brand, model_code", args)

    def documents(self, product=None):
        if product:
            return self._rows(
                "SELECT d.* FROM document d JOIN product p ON p.brand = d.brand"
                " AND p.model_code = d.model_code WHERE p.id = ? ORDER BY doctype, lang",
                (product,))
        return self._rows("SELECT * FROM document ORDER BY doctype, doc_id")

    # --- knowledge --------------------------------------------------------
    def topics(self, kind=None, product=None, lang="NL", fallback="EN"):
        sql = ["SELECT t.id, t.kind, t.number, t.scope,",
               " coalesce(a.title, b.title) AS title,",
               " coalesce(a.text, b.text) AS text,",
               " coalesce(a.lang, b.lang) AS lang,",
               " coalesce(a.payload, b.payload) AS payload",
               " FROM topic t",
               " LEFT JOIN topic_text a ON a.topic_id = t.id AND a.lang = ?",
               " LEFT JOIN topic_text b ON b.topic_id = t.id AND b.lang = ?"]
        args = [lang, fallback]
        where = ["coalesce(a.text, b.text) IS NOT NULL"]
        if product:
            sql.append(" JOIN topic_product tp ON tp.topic_id = t.id")
            where.append("tp.product_id = ?")
            args.append(product)
        if kind:
            where.append("t.kind = ?")
            args.append(kind)
        sql.append(" WHERE " + " AND ".join(where) + " ORDER BY t.number, t.title")
        rows = self._rows("".join(sql), tuple(args))
        for r in rows:
            r["payload"] = json.loads(r["payload"]) if r["payload"] else {}
        return rows

    def faults(self, product=None, lang="NL"):
        return self.topics("fault", product, lang)

    def menu(self, product=None, lang="NL"):
        return self.topics("menu", product, lang)

    def components(self, product=None, lang="NL"):
        return self.topics("component", product, lang)

    def maintenance(self, product=None):
        sql = "SELECT * FROM maintenance"
        args = ()
        if product:
            sql += " WHERE product_id = ?"
            args = (product,)
        rows = self._rows(sql + " ORDER BY interval, title", args)
        for r in rows:
            r["steps"] = json.loads(r["steps"])
        return rows

    # --- parts ------------------------------------------------------------
    def part(self, part_nr):
        return self._rows(
            "SELECT p.*, pr.name AS machine FROM part p"
            " LEFT JOIN product pr ON pr.id = p.product_id"
            " WHERE p.part_nr = ? ORDER BY pr.brand, p.section", (part_nr.upper(),))

    def parts(self, product, section=None):
        sql = "SELECT * FROM part WHERE product_id = ?"
        args = [product]
        if section:
            sql += " AND section = ?"
            args.append(section)
        return self._rows(sql + " ORDER BY section, cast(pos AS integer), part_nr", args)

    def drawings(self, product=None):
        sql = "SELECT * FROM drawing"
        args = ()
        if product:
            sql += " WHERE product_id = ?"
            args = (product,)
        return self._rows(sql + " ORDER BY code", args)

    # --- search -----------------------------------------------------------
    def search(self, text, limit=25, kinds=None, lang=None):
        """One query across messages, parts, menu, machines and maintenance."""
        query = " ".join(f'"{w}"*' for w in text.split() if w)
        sql = ["SELECT ref, kind, lang, title, scope,"
               " snippet(search, 4, '[', ']', '…', 12) AS snippet,"
               " bm25(search, 0, 0, 0, 0, 8.0, 1.0) AS score FROM search"
               " WHERE search MATCH ?"]
        args = [query]
        if kinds:
            sql.append(" AND kind IN (%s)" % ",".join("?" * len(kinds)))
            args += list(kinds)
        if lang:
            sql.append(" AND lang = ?")
            args.append(lang)
        sql.append(" ORDER BY score LIMIT ?")
        args.append(limit)
        return self._rows("".join(sql), tuple(args))

    def image_path(self, file_or_hash):
        name = file_or_hash if "/" in file_or_hash else f"media/{file_or_hash}.webp"
        return os.path.join(self.root, name)


def main(argv):
    kb = KB(os.environ.get("DUKEKB", DEFAULT))
    if len(argv) < 2:
        print(__doc__)
        info = kb.meta()
        print("database:", info.get("name"), "version", info.get("version"),
              "\ncounts:", json.dumps(info.get("counts", {}), indent=1))
        return 0
    cmd, rest = argv[1], argv[2:]
    if cmd == "search":
        for r in kb.search(" ".join(rest)):
            print(f"{r['kind']:16} {r['lang']:4} {r['title'][:52]:54} {r['snippet'][:70]}")
    elif cmd == "machines":
        for m in kb.machines(*rest):
            print(f"{m['id']:14} {m['name']:28} {m['series'] or '':22} {m['menu_generation']}")
    elif cmd == "part":
        for r in kb.part(rest[0]):
            print(f"{r['machine'] or r['doc_id']:26} {r['section']:6} pos {r['pos'] or '-':4} "
                  f"{r['qty'] or '-':3} {r['description']}")
    elif cmd == "fault":
        for r in kb.faults(rest[0] if rest else None):
            print(f"{r['number']:9} {r['lang']} {r['title']}")
    elif cmd == "menu":
        for r in kb.menu(rest[0] if rest else None):
            print(f"{r['number']:9} {r['title']}")
    elif cmd == "maintenance":
        for r in kb.maintenance(rest[0] if rest else None):
            print(f"{r['interval']:10} {r['title']:22} {len(r['steps'])} steps  {r['doc_id']}")
    elif cmd == "drawings":
        for r in kb.drawings(rest[0] if rest else None):
            print(f"{r['code'] or '-':7} {r['title'][:44]:46} {r['part_count']:4} parts")
    else:
        print("unknown command", cmd)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
