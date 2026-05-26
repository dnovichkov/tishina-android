#!/usr/bin/env python3
"""
render_launcher_icon.py — one-shot renderer for Tishina's launcher icon.

The adaptive launcher artwork is the single source of truth (vector drawable at
`app/src/main/res/drawable/ic_launcher_foreground.xml`). This script reproduces the
same geometry in raster form so we can ship:

    * `app/src/main/play-store-icon.png` — 512x512 PNG required by Google Play, RuStore
      and Samsung Galaxy Store catalogues (not packaged in the APK).
    * `app/src/main/res/mipmap-{ldpi..xxxhdpi}/ic_launcher.png` — legacy raster fallback
      for devices below API 26 that don't support `<adaptive-icon>` (the Phase 6 plan
      Task 1 calls these out as a discovered subtask).

Re-run after editing the vector path so the rasters stay in sync.

Requires: Pillow (`pip install Pillow`).
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

# ---------------------------------------------------------------------------
# Vector geometry — kept verbatim with `ic_launcher_foreground.xml` so the bitmap
# rasteriser produces the same curve as Android's `VectorDrawable`.
# ---------------------------------------------------------------------------
CANVAS = 108  # vector viewportWidth / viewportHeight
STROKE_W = 5  # vector strokeWidth (vector units)
STROKE_COLOR = (15, 181, 186, 255)  # #0FB5BA
BACKGROUND_COLOR = (14, 36, 51, 255)  # #0E2433

# Bezier control & end points for the damped-wave-to-flat-line path.
# (matches `pathData="M24,54 C28,22 32,22 36,54 C40,76 44,76 48,54 C52,44 56,44 60,54 L84,54"`)
WAVE_PATH = [
    ("M", 24.0, 54.0),
    ("C", 28.0, 22.0, 32.0, 22.0, 36.0, 54.0),
    ("C", 40.0, 76.0, 44.0, 76.0, 48.0, 54.0),
    ("C", 52.0, 44.0, 56.0, 44.0, 60.0, 54.0),
    ("L", 84.0, 54.0),
]


def _cubic(t: float, p0: tuple[float, float], c1: tuple[float, float],
           c2: tuple[float, float], p1: tuple[float, float]) -> tuple[float, float]:
    """Standard cubic Bezier evaluator at parameter t ∈ [0, 1]."""
    mt = 1.0 - t
    x = mt ** 3 * p0[0] + 3 * mt ** 2 * t * c1[0] + 3 * mt * t ** 2 * c2[0] + t ** 3 * p1[0]
    y = mt ** 3 * p0[1] + 3 * mt ** 2 * t * c1[1] + 3 * mt * t ** 2 * c2[1] + t ** 3 * p1[1]
    return x, y


def _sample_path(samples_per_segment: int = 96) -> list[tuple[float, float]]:
    """Walk the vector path and return densely sampled (x, y) pairs in viewport units."""
    points: list[tuple[float, float]] = []
    cursor = (0.0, 0.0)
    for cmd in WAVE_PATH:
        op = cmd[0]
        if op == "M":
            cursor = (cmd[1], cmd[2])
            points.append(cursor)
        elif op == "C":
            c1 = (cmd[1], cmd[2])
            c2 = (cmd[3], cmd[4])
            end = (cmd[5], cmd[6])
            for i in range(1, samples_per_segment + 1):
                t = i / samples_per_segment
                points.append(_cubic(t, cursor, c1, c2, end))
            cursor = end
        elif op == "L":
            end = (cmd[1], cmd[2])
            for i in range(1, samples_per_segment + 1):
                t = i / samples_per_segment
                points.append((cursor[0] + (end[0] - cursor[0]) * t,
                               cursor[1] + (end[1] - cursor[1]) * t))
            cursor = end
        else:
            raise ValueError(f"unsupported path command: {op!r}")
    return points


def render(size: int, *, with_background: bool) -> Image.Image:
    """
    Rasterise the launcher artwork at the given square edge size.

    Pillow has no built-in stroked-bezier primitive, so we sample the path densely and
    paint each segment with `ImageDraw.line` — `width` already includes round caps when
    `joint='curve'` is requested.
    """
    img = Image.new("RGBA", (size, size), BACKGROUND_COLOR if with_background else (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    scale = size / CANVAS
    pts = [(round(x * scale), round(y * scale)) for x, y in _sample_path()]
    # Round-cap effect: draw the polyline then daub a filled circle at each endpoint.
    stroke_px = max(2, round(STROKE_W * scale))
    draw.line(pts, fill=STROKE_COLOR, width=stroke_px, joint="curve")
    radius = stroke_px / 2
    for end in (pts[0], pts[-1]):
        draw.ellipse((end[0] - radius, end[1] - radius, end[0] + radius, end[1] + radius),
                     fill=STROKE_COLOR)
    return img


def _project_root() -> Path:
    return Path(__file__).resolve().parents[2]


def main() -> None:
    root = _project_root()

    # 1) Play / RuStore / Samsung catalogue icon (512x512 with brand background).
    catalogue_path = root / "app" / "src" / "main" / "play-store-icon.png"
    catalogue_path.parent.mkdir(parents=True, exist_ok=True)
    render(512, with_background=True).save(catalogue_path, optimize=True)
    print(f"wrote {catalogue_path.relative_to(root)}  (512x512)")

    # 2) Legacy mipmap PNGs for < API 26 launchers (no adaptive-icon support).
    #    Density buckets follow the standard Android scale ladder:
    #        ldpi 36 / mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192
    legacy_buckets = {
        "ldpi": 36,
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    for bucket, size in legacy_buckets.items():
        out = root / "app" / "src" / "main" / "res" / f"mipmap-{bucket}" / "ic_launcher.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        render(size, with_background=True).save(out, optimize=True)
        print(f"wrote {out.relative_to(root)}  ({size}x{size})")

    # 3) Round variant — same artwork, identical bitmap on Android (the OS masks at
    #    runtime). We still ship `ic_launcher_round.png` because older launchers look
    #    for that exact resource name when AdaptiveIconDrawable is unavailable.
    for bucket, size in legacy_buckets.items():
        out = root / "app" / "src" / "main" / "res" / f"mipmap-{bucket}" / "ic_launcher_round.png"
        render(size, with_background=True).save(out, optimize=True)
        print(f"wrote {out.relative_to(root)}  ({size}x{size}, round)")


if __name__ == "__main__":
    main()
