# RuStore phone screenshots — capture checklist

RuStore accepts up to 10 phone screenshots. Portrait Phone is the default preset and the one the carousel renders without letterboxing.

Required dimensions per RuStore documentation
(rustore.ru/help/en/developers/publishing-and-verifying-apps/app-publication, 2026-05):

- Format: PNG or JPEG.
- Aspect ratio: 9:16 (portrait) or 16:9 (landscape).
- Portrait phone recommended: 1080 × 1920 (max 2160 × 3840).
- Minimum: 320 px on the shorter side.
- Max file size: 3 MB per screenshot (phones), 5 MB (tablets).
- Count: 1–10 phone screenshots per locale (mandatory). We ship 10.
- Screenshots must demonstrate actual functionality — no splash, no login-only,
  no concept art (RuStore moderation enforces this).
- All text in screenshots must be in Russian or English.

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

## How to capture (current pipeline)

Roborazzi snapshot tests render the full screens at 1080 × 1920 px in the JVM —
no emulator, no adb, no manual screenshotting. Each scene has Russian (`_ru`)
and English (`_en`) variants captured under one `@Test` per locale.

1. Add or edit `StoreScreenshotTest.kt` in the relevant feature module.
2. Re-record baselines:

    ```bash
    ./gradlew :feature:measure:testDebugUnitTest \
              :feature:history:testDebugUnitTest \
              :feature:settings:testDebugUnitTest \
              :feature:about:testDebugUnitTest \
       --tests "*StoreScreenshotTest*" -Proborazzi.test.record=true
    ```

3. Publish the Russian set into this folder:

    ```bash
    python docs/tools/collect_store_screenshots.py --target rustore
    ```

## Current scenes (RuStore carousel order)

```
screenshots/
├── 01-measure-running.png   — main hook: large 65 dB readout + arc gauge + chart
├── 02-measure-idle.png      — ready-to-start state
├── 03-history-list.png      — five-card history with Russian labels
├── 04-history-detail.png    — chart + statistics + note
├── 05-settings.png          — calibration slider + theme + dynamic colors
├── 06-about.png             — brand + version + accuracy disclaimer
└── README.md                ← you are here
```
