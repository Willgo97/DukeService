#!/usr/bin/env python3
"""Repair the words a PDF hands over.

Text pulled out of a manual carries the printer's tricks: words broken over a
line with a soft hyphen, "fi" as one glyph followed by a stray space, two
spaces where a column ended. None of that is in the manual as it reads on
paper, and all of it is in the way on a screen.

[clean] is the repair itself and runs over everything that goes into the
knowledge base. [pair_legend] and [split_callouts] put a two-column legend and
a doubled call-out line back together; those change how a page reads rather
than what it says, so they are for the app.
"""
import re

LIGATURES = str.maketrans({"\ufb00": "ff", "\ufb01": "fi", "\ufb02": "fl",
                           "\ufb03": "ffi", "\ufb04": "ffl", "\ufb05": "st",
                           "\ufb06": "st"})
GLUED_LIGATURE = re.compile(r"([\ufb00-\ufb06]) +(?=[a-z])")
SOFT_HYPHEN = re.compile(r"\u00ad[ \t]*\n?[ \t]*")
RUN_OF_SPACE = re.compile(r"[ \t]{2,}")
SPACE_BEFORE = re.compile(r"[ \t]+([.,])(?=[\s)\]]|$)")
# A word broken over a line keeps its hyphen but loses the space the join left
# behind: "120VAC- verwarming" is one word on the page. Dutch, German and the
# Scandinavian languages do print a hanging hyphen before "and"/"or", and there
# the space is right, so those are left alone.
CONJUNCTIONS = ("en|of|und|oder|och|eller|og|eða|ja|tai|et|ou|ni|and|or|a|i|nebo|ani")
LINE_BREAK_HYPHEN = re.compile(
    r"([A-Za-z\u00c0-\u024f])- (?!(?:%s)\b)([a-z\u00e0-\u024f])" % CONJUNCTIONS)
SPACE_INSIDE = re.compile(r"\(\s+|\s+\)")
URL_BREAK = re.compile(r"(https?://[^\s]*)\s+(?=[^\s])")
# The Symbol font prints "less than or equal" as a pound sign, and the
# extractor takes it at face value: "£ 61,97 dB(A)" is "≤ 61,97 dB(A)". Only a
# pound in front of a measurement is read that way, so a price in an English
# parts book stays a price.
UNITS = "dB|bar|kPa|MPa|°C|°dH|fH|mm|cm|m|kg|g|ms|s|min|h|ml|l|V|VAC|W|kW|A|mA|Hz|%"
SYMBOL_LE = re.compile(r"£\s*(?=[\d.,]+\s*(?:%s)\b)" % UNITS)

BLANK_LINES = re.compile(r"\n{3,}")


def clean(text):
    """Put the words back the way the manual reads them."""
    if not isinstance(text, str) or not text:
        return text
    text = text.replace("\u00a0", " ").replace("\u200b", "").replace("\u2028", "\n")
    # "h<?> p://" is a tt-ligature the extractor could not name.
    text = text.replace("h\ufffd p://", "http://").replace("\ufffd", "")
    text = GLUED_LIGATURE.sub(r"\1", text).translate(LIGATURES)
    text = SOFT_HYPHEN.sub("", text)
    text = LINE_BREAK_HYPHEN.sub(r"\1-\2", text)
    text = URL_BREAK.sub(r"\1", text)
    text = SYMBOL_LE.sub("\u2264 ", text)
    text = "\n".join(RUN_OF_SPACE.sub(" ", line).strip() for line in text.split("\n"))
    text = SPACE_BEFORE.sub(r"\1", text)
    text = SPACE_INSIDE.sub(lambda m: "(" if m.group(0).startswith("(") else ")", text)
    return BLANK_LINES.sub("\n\n", text).strip()


# A legend is printed as two columns — the letters used in the drawing beside
# what they stand for — and comes out of the page as one line each. Put the two
# back on one line, so "BO" and "Open boiler" read as the pair they are.
LEGEND_CODE = re.compile(
    r"^(?:[A-Z0-9][A-Z0-9*/+.,()\u2013-]{0,11}|\*{1,3}\)|[A-Z][a-z]*[A-Z][A-Za-z]{0,8})$")


def is_code(line):
    return bool(LEGEND_CODE.match(line)) and not line.endswith(".")


def pair_legend(text):
    """Join a run of code/description lines into one line per entry."""
    if not isinstance(text, str) or "\n" not in text:
        return text
    lines = text.split("\n")
    out, i = [], 0
    while i < len(lines):
        # How far does code, text, code, text run from here?
        run = 0
        while (i + 2 * run + 1 < len(lines)
               and is_code(lines[i + 2 * run].strip())
               and lines[i + 2 * run + 1].strip()
               and not is_code(lines[i + 2 * run + 1].strip())
               and len(lines[i + 2 * run + 1]) <= 80):
            run += 1
        if run >= 3:
            for n in range(run):
                out.append(f"{lines[i + 2 * n].strip()} \u2014 {lines[i + 2 * n + 1].strip()}")
            i += 2 * run
        else:
            out.append(lines[i])
            i += 1
    return "\n".join(out)


TWO_CALLOUTS = re.compile(r"^(\d{1,2})\.\s.*?\s(\d{1,2})\.\s")


def split_callouts(text):
    """One line holding two call-outs becomes two lines.

    "3. Water flow meter 4. Inlet valve" is two entries of a numbered list that
    happened to be printed on one line of the page.
    """
    out = []
    for line in text.split("\n"):
        match = TWO_CALLOUTS.match(line)
        while match and int(match.group(2)) == int(match.group(1)) + 1:
            cut = match.end() - len(match.group(2)) - 2
            out.append(line[:cut].strip())
            line = line[cut:].strip()
            match = TWO_CALLOUTS.match(line)
        out.append(line)
    return "\n".join(out)


def prose(text):
    """Text and legends cleaned up, for the long bodies the app shows whole."""
    return split_callouts(pair_legend(clean(text)))


