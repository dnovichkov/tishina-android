# RuStore phone screenshots — capture checklist

RuStore accepts up to 10 phone screenshots. Portrait Phone is the default preset and the one the carousel renders without letterboxing.

Required dimensions per RuStore documentation:

- Format: PNG or JPEG.
- Portrait Phone: 1080 × 1920 (or equivalent 9:16 ratio up to 2880 × 5120).
- Minimum: 320 px on the shorter side.

## Capture order

Same eight screenshots as Google Play (see `../../google-play/screenshots/README.md`) **plus** two RuStore-specific:

1. `01-measure-active.png`
2. `02-measure-saved.png`
3. `03-history-list.png`
4. `04-history-selection.png`
5. `05-detail.png`
6. `06-detail-share.png`
7. `07-history-export.png`
8. `08-about.png`
9. `09-settings-theme-locale.png` — Settings showing theme + locale switchers (proof "fully Russian" claim).
10. `10-calibration.png` — Calibration screen with the offset slider centred and the disclaimer visible.

## How to capture

Same flow as Google Play; capture in `ru` locale only (RuStore is Russian-first).

Save into:

```
screenshots/
├── 01-measure-active.png
├── ... (10 files)
└── README.md   ← you are here
```

Captures deferred to Post-Completion of Phase 6.
