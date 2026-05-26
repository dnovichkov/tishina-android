# Google Play phone screenshots — capture checklist

Google Play accepts 2–8 phone screenshots per locale. Spec § 6 / § 15 say we ship 8 per locale (ru-RU and en-US).

Required dimensions per Google Play guidelines:

- Format: 16:9 portrait OR landscape, PNG (preferred) or JPEG.
- Minimum: 320 px on the shorter side.
- Maximum: 3840 px on the longer side.
- Recommended for phone: 1080 × 1920 portrait.

## Capture order (matches store carousel flow — hook → reassure → detail)

1. `01-measure-active.png` — Measure screen mid-measurement, dB chart visible, current value ~55 dB.
2. `02-measure-saved.png` — Save dialog with title field focused (shows the "history with notes" promise).
3. `03-history-list.png` — History screen with 4–6 cards, no selection.
4. `04-history-selection.png` — Bulk selection mode with 3 cards selected and "Delete (3)" CTA.
5. `05-detail.png` — Detail screen with full chart, statistics, and note.
6. `06-detail-share.png` — Detail screen with Share intent chooser overlay (Telegram, WhatsApp, Email).
7. `07-history-export.png` — System SAF picker over the History screen during CSV export.
8. `08-about.png` — About screen with brand logo, version, GitHub / Privacy links, and the accuracy disclaimer visible.

## How to capture

Until `:app:generateStoreScreenshots` (proposed Gradle task, Phase 7) lands, capture manually:

1. Boot Pixel 6 API 34 emulator (1080 × 2400, exact ratio matches Play preferred).
2. Switch system language between ru / en for each locale set.
3. Take screenshots from Android Studio's Logcat → screenshot tool (`adb shell screencap`).
4. Crop status bar if the screenshot policy requires (Play accepts full-frame).
5. Save into `ru-RU/` / `en-US/` subdirectories of this folder.

## Subdirectory layout once captured

```
screenshots/
├── ru-RU/
│   ├── 01-measure-active.png
│   ├── ... (8 files)
├── en-US/
│   ├── 01-measure-active.png
│   ├── ... (8 files)
└── README.md   ← you are here
```

Captures are deferred to Post-Completion of Phase 6 because:

- They depend on real release-signed builds installed on a physical device or stable emulator, which is a one-time manual step per release.
- Binary PNGs do not roundtrip cleanly through code review and inflate repo size — they belong here only when actually generated for an upload.
