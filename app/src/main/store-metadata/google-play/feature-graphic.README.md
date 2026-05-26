# Feature graphic — Google Play

Required by Google Play before publication. Specs:

- Exactly **1024 × 500** px.
- PNG or JPEG.
- No screenshots, no application chrome.
- Brand mark left-aligned, generous safe zone (Play overlays a Play badge in the bottom-right corner on some surfaces).

## Design brief

- Background: brand dark teal `#0E2433` (matches adaptive launcher icon background).
- Centerpiece: the brand mark (`ic_brand_logo.xml` from `:core:designsystem`) at ~280 × 280 px, vertically centered, anchored ~120 px from the left edge.
- Right side: app tagline in white, two-line — RU "Шум вокруг — в цифрах в руке." / EN "The noise around you — in your pocket."
- Typography: Roboto (system default) or the same system stack used in-app; weight 600 for the tagline.
- Accent stroke: teal `#0FB5BA` underline beneath the tagline, 4 px tall, 240 px wide.

## How to generate

Until a dedicated Gradle screenshot/feature-graphic task lands (see `screenshots/README.md`), build manually:

- Reuse `docs/tools/render_launcher_icon.py` (Pillow-based, already in repo) — extend with a 1024×500 canvas function rendering the same glyph plus typography.
- Or: open the brand logo SVG in Figma / Inkscape, compose against the spec, export PNG @ 1× resolution (Play scales internally; no need for 2×).

Save as:

```
feature-graphic.png        # English-leaning version, used as default
feature-graphic-ru.png     # Localized RU tagline, optional
```

Both files go in this directory (`google-play/`) alongside this README.

Deferred to Post-Completion of Phase 6 for the same reasons phone screenshots are deferred — binary PNGs belong in the repo only once generated for an actual upload.
