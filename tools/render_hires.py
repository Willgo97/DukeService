#!/usr/bin/env python3
"""Re-render every drawing at OCR resolution.

The app ships small WebPs; reading the balloon numbers needs more pixels. This
writes PNGs named after the same hash, so the OCR results map straight back.

Run from the project root:  python3 tools/render_hires.py [uitvoermap]
"""
import json, os, shutil, subprocess, sys

import cv2

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from parse_drawings import trim  # same crop as the shipped image

DPI = 200


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "txt", "hires")
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out, exist_ok=True)
    tmp = os.path.join(ROOT, "txt", "hirestmp")
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp, exist_ok=True)

    sources = json.load(open(os.path.join(ROOT, "data", "drawing_sources.json")))
    done = 0
    for digest, src in sorted(sources.items()):
        pdf = os.path.join(ROOT, "manuals", src["pdf"])
        if not os.path.exists(pdf):
            continue
        subprocess.run(["pdftoppm", "-png", "-r", str(DPI), "-f", str(src["page"]),
                        "-l", str(src["page"]), pdf, os.path.join(tmp, "pg")], check=True)
        hits = [f for f in os.listdir(tmp) if f.startswith("pg")]
        if not hits:
            continue
        path = os.path.join(tmp, hits[0])
        img = cv2.imread(path)
        os.remove(path)
        if img is None:
            continue
        img = trim(img[int(img.shape[0] * 0.115):int(img.shape[0] * 0.94)])
        cv2.imwrite(os.path.join(out, f"{digest}.png"), img)
        done += 1
    shutil.rmtree(tmp, ignore_errors=True)
    size = sum(os.path.getsize(os.path.join(out, f)) for f in os.listdir(out))
    print(f"{done} tekeningen op {DPI} dpi -> {out} ({size // 1024 // 1024} MB)")


if __name__ == "__main__":
    main()
