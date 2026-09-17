#!/usr/bin/env python3
"""Our own translations, for the chapters the manufacturer never translated.

Where a manual exists in one language only, every other language falls back to
it: a German engineer ends up reading Dutch. What is written here fills that
gap.

A translation is stored against a fingerprint of the source text, not against
the topic it happened to sit in. Topics are grouped by chapter number, and the
manuals do not number their chapters the same way in every language, so a topic
can move; the source sentence does not. Keying on the text means a translation
follows its sentence wherever that sentence ends up.

data/translations/<lang>.json holds {fingerprint: {"src": ..., "txt": ...}} and
is written by hand — `src` is there to read, the fingerprint is what counts.
"""
import hashlib
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORE = os.path.join(ROOT, "data", "translations")
# Keys that hold a name or a code rather than a sentence, and are left alone.
# "n" is the strength of a safety banner, normalised to note/caution/warning/
# danger and turned back into a word by the app, so it is not prose either.
NOT_PROSE = ("id", "source", "language", "machines", "codes", "images",
             "group", "page", "interval", "number", "doc_id", "n",
             # which brewer a topic belongs to: a key the app filters on
             "brewer", "brewers",
             # a fault message is what the machine's own screen shows, in the
             # language the machine is set to; "dutch" is its Dutch twin. An
             # engineer matches the screen against these, so they stay put.
             "message", "dutch",
             # the ids of the procedures a fault links to
             "procedures",
             # a facet the app filters on and labels itself (categoryLabel)
             "category")

_cache = {}


def key(text):
    """What a source sentence is filed under."""
    return hashlib.sha1(" ".join((text or "").split()).encode("utf-8")).hexdigest()[:16]


def load(lang):
    """Everything written for one language, by fingerprint."""
    lang = lang.lower()
    if lang not in _cache:
        path = os.path.join(STORE, f"{lang}.json")
        try:
            with open(path, encoding="utf-8") as fh:
                _cache[lang] = {k: v["txt"] for k, v in json.load(fh).items() if v.get("txt")}
        except FileNotFoundError:
            _cache[lang] = {}
    return _cache[lang]


def apply(value, lang, missing=None, used=None):
    """Swap every sentence we have a translation for; leave the rest as it is.

    `missing` collects what is still untranslated, so the gap can be listed
    without walking the assets a second time. `used` is filled when at least
    one sentence came from here rather than from the manufacturer.
    """
    table = load(lang)
    if not table and missing is None:
        return value

    def walk(node):
        if isinstance(node, str):
            if len(node.strip()) < 2:
                return node
            hit = table.get(key(node))
            if hit:
                if used is not None:
                    used.append(True)
                return hit
            if missing is not None:
                missing.setdefault(key(node), node)
            return node
        if isinstance(node, dict):
            return {k: (v if k in NOT_PROSE else walk(v)) for k, v in node.items()}
        if isinstance(node, list):
            return [walk(v) for v in node]
        return node

    return walk(value)


def counts(lang):
    return len(load(lang))
