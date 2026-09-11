#!/usr/bin/env python3
"""Cut the machine photo out of each product brochure cover.

Every brochure uses the same cover layout: model name on top, the machine in
the middle, the DUKE logo at the bottom. This renders page 1, finds the machine
against the two-tone background and writes a WebP into the app's assets.

Run from the project root:  python3 tools/parse_photos.py
"""
import glob, os, re, shutil, subprocess, sys

import cv2
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BROCHURES = os.path.join(ROOT, "manuals", "brochures")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "machines")

# brochure file stem -> machine id in the app
NAMES = {
    "AVY": "avy", "BLU": "blu", "LINA": "lina", "LUA": "lua",
    "NIO-NEXT": "nionext", "ROSA": "rosa", "VIRTU": "virtu", "ZIA": "zia",
}


def machine_box(img):
    """The machine is the grey/black object in the middle of the cover."""
    h, w = img.shape[:2]
    # ignore the title band on top and the logo band at the bottom
    top, bottom = int(h * 0.17), int(h * 0.88)
    band = img[top:bottom]
    hsv = cv2.cvtColor(band, cv2.COLOR_BGR2HSV)
    # the machine is unsaturated (grey, silver, black); the backdrop is pink or white
    grey = (hsv[:, :, 1] < 60) & (hsv[:, :, 2] < 235)
    grey = cv2.morphologyEx(grey.astype(np.uint8), cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    contours, _ = cv2.findContours(grey, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    # a coffee machine stands upright and fills a decent part of the cover
    upright = []
    for c in contours:
        x, y, bw, bh = cv2.boundingRect(c)
        if bh < band.shape[0] * 0.3 or bw < band.shape[1] * 0.15:
            continue
        upright.append(c)
    if not upright:
        return None
    biggest = max(upright, key=cv2.contourArea)
    if cv2.contourArea(biggest) < (band.shape[0] * band.shape[1]) * 0.02:
        return None
    x, y, bw, bh = cv2.boundingRect(biggest)
    pad = int(min(bw, bh) * 0.06)
    x0 = max(0, x - pad)
    y0 = max(0, top + y - pad)
    x1 = min(w, x + bw + pad)
    y1 = min(h, top + y + bh + pad)
    return x0, y0, x1, y1


def flatten(img):
    """Put the cut-out on a neutral card background instead of the pink band."""
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    backdrop = (hsv[:, :, 1] > 60) | (hsv[:, :, 2] > 242)
    backdrop = cv2.morphologyEx(backdrop.astype(np.uint8), cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    out = img.copy()
    out[backdrop == 1] = (246, 246, 246)
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    tmp = os.path.join(ROOT, "txt", "phototmp")
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp, exist_ok=True)

    done = 0
    for pdf in sorted(glob.glob(os.path.join(BROCHURES, "DJD_Brochure_*_NL_*.pdf"))):
        stem = re.sub(r'^DJD_Brochure_|_NL.*$', '', os.path.basename(pdf))
        machine = NAMES.get(stem)
        if not machine:
            continue
        subprocess.run(["pdftoppm", "-png", "-r", "150", "-f", "1", "-l", "1", pdf,
                        os.path.join(tmp, machine)], check=True)
        hits = sorted(glob.glob(os.path.join(tmp, f"{machine}-*.png")))
        if not hits:
            continue
        img = cv2.imread(hits[0])
        box = machine_box(img)
        if box is None:
            print(f"  {machine}: machine niet gevonden", file=sys.stderr)
            continue
        x0, y0, x1, y1 = box
        cut = img[y0:y1, x0:x1]
        h, w = cut.shape[:2]
        if h > 900:
            cut = cv2.resize(cut, (int(w * 900 / h), 900), interpolation=cv2.INTER_AREA)
        cv2.imwrite(os.path.join(OUT, f"{machine}.webp"), cut, [cv2.IMWRITE_WEBP_QUALITY, 82])
        done += 1
        print(f"  {machine}: {cut.shape[1]}x{cut.shape[0]}", file=sys.stderr)

    shutil.rmtree(tmp, ignore_errors=True)
    print(f"{done} machinefoto's")


if __name__ == "__main__":
    main()
